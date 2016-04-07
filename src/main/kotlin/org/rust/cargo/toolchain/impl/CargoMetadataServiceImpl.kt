package org.rust.cargo.toolchain.impl

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.Alarm
import com.intellij.util.containers.MultiMap
import com.intellij.util.xmlb.annotations.AbstractCollection
import com.intellij.util.xmlb.annotations.Transient
import org.rust.cargo.CargoProjectDescription
import org.rust.cargo.toolchain.CargoMetadataService
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.toolchain
import java.util.*

private val LOG = Logger.getInstance(CargoMetadataServiceImpl::class.java);

@State(
    name = "CargoMetadata",
    storages = arrayOf(Storage(file = StoragePathMacros.MODULE_FILE))
)
class CargoMetadataServiceImpl(private val module: Module) : CargoMetadataService, PersistentStateComponent<CargoProjectState>, BulkFileListener {
    private var cargoProjectState: CargoProjectState = CargoProjectState()

    private val alarm = Alarm()
    private val DELAY_MILLIS = 1000

    init {
        module.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    /*
     * Updates Rust libraries asynchronously. Consecutive updates are coalesced.
     *
     * Works in two phases. First `cargo metadata` is executed on the background thread. Then,
     * the actual Library update happens on the event dispatch thread.
     */
    override fun scheduleUpdate(toolchain: RustToolchain) {
        val contentRoot = ModuleRootManager.getInstance(module).contentRoots.firstOrNull() ?: return
        if (contentRoot.findChild(RustToolchain.CARGO_TOML) == null) {
            return
        }

        val task = UpdateTask(toolchain, contentRoot.path)
        alarm.cancelAllRequests()
        alarm.addRequest({ task.queue() }, DELAY_MILLIS)
    }

    override val cargoProject: CargoProjectDescription?
        get() = state.cargoProjectDescription

    override fun loadState(state: CargoProjectState?) {
        cargoProjectState = state ?: CargoProjectState()
    }

    override fun getState(): CargoProjectState = cargoProjectState

    override fun before(events: MutableList<out VFileEvent>) {
    }

    override fun after(events: MutableList<out VFileEvent>) {
        val toolchain = module.toolchain ?: return
        val needsUpdate = events.any {
            val file = it.file ?: return@any false
            file.name == RustToolchain.CARGO_TOML && ModuleUtilCore.findModuleForFile(file, module.project) == module
        }
        if (needsUpdate) {
            scheduleUpdate(toolchain)
        }
    }

    private inner class UpdateTask(
        private val toolchain: RustToolchain,
        private val projectDirectory: String
    ) : Task.Backgroundable(module.project, "Updating cargo") {

        private var result: Result? = null

        override fun run(indicator: ProgressIndicator) {
            LOG.info("Cargo project update started")
            val cargo = toolchain.cargo(projectDirectory)
            result = try {
                if (cargo == null) {
                    Result.Err(ExecutionException("Cargo not found"))
                } else {
                    val description = cargo.fullProjectDescription(object : ProcessAdapter() {
                        override fun onTextAvailable(event: ProcessEvent, outputType: Key<Any>) {
                            val text = event.text.trim { it <= ' ' }
                            if (text.startsWith("Updating") || text.startsWith("Downloading")) {
                                indicator.text = text
                            }
                        }
                    })

                    Result.Ok(description)
                }
            } catch (e: ExecutionException) {
                Result.Err(e)
            }
        }

        override fun onSuccess() {
            val result = requireNotNull(result)

            when (result) {
                is Result.Err -> LOG.info("Cargo project update failed", result.error)
                is Result.Ok  -> ApplicationManager.getApplication().runWriteAction {
                    if (!module.isDisposed) {
                        updateLibraries(module, result.cargoProject)
                        cargoProjectState.cargoProjectDescription = result.cargoProject
                    }
                }
            }
        }
    }

    private sealed class Result {
        class Ok(val cargoProject: CargoProjectDescription) : Result()
        class Err(val error: ExecutionException) : Result()
    }
}

private fun updateLibraries(module: Module, cargoProject: CargoProjectDescription) {
    check(ApplicationManager.getApplication().isWriteAccessAllowed)

    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(module.project)
    val cargoLibrary = libraryTable.getLibraryByName(module.cargoLibraryName)
        ?: libraryTable.createLibrary(module.cargoLibraryName)
        ?: return

    fillLibrary(cargoLibrary, cargoProject)

    ModuleRootModificationUtil.addDependency(module, cargoLibrary)
    LOG.info("Cargo project successfully updated")
}

fun fillLibrary(cargoLibrary: Library, cargoProject: CargoProjectDescription) {
    val fs = LocalFileSystem.getInstance()
    val model = cargoLibrary.modifiableModel
    for (url in cargoLibrary.getUrls(OrderRootType.CLASSES)) {
        model.removeRoot(url, OrderRootType.CLASSES)
    }

    for (pkg in cargoProject.packages.filter { !it.isModule }) {
        val root = fs.findFileByPath(pkg.contentRoot)
        if (root == null) {
            LOG.warn("Can't find root for ${pkg.name}")
            continue
        }
        model.addRoot(root, OrderRootType.CLASSES)
    }
    model.commit()
}

private val Module.cargoLibraryName: String get() = "Cargo <$name>"


class CargoProjectState {
    @AbstractCollection
    private var packages: MutableCollection<SerializablePackage> = ArrayList()

    @AbstractCollection
    private var dependencies: MutableCollection<DependencyNode> = ArrayList()

    @get:Transient
    var cargoProjectDescription: CargoProjectDescription?
        get() {
            val packages = packages.map { it.into() ?: return null }
            val deps = MultiMap<Int, Int>().apply {
                for ((pkg, pkgDeps) in dependencies) {
                    putValues(pkg, pkgDeps)
                }
            }
            return CargoProjectDescription.create(packages, deps)
        }
        set(projectDescription) {
            if (projectDescription == null) {
                packages = ArrayList()
                dependencies = ArrayList()
            } else {
                packages = projectDescription.packages.map { SerializablePackage.from(it) }.toMutableList()
                dependencies = projectDescription.rawDependencies.entrySet().map {
                    DependencyNode(it.key, it.value)
                }.toMutableList()
            }
        }
}


// IDEA serializer requires objects to have default constructors,
// nullable fields and mutable collections, so we have to introduce
// an evil nullable twin of [CargoProjectDescription]
//
// `null`s in fields signal serialization failure, so we can't `!!` here safely.
//
// Ideally this should be private, but alas this also breaks serialization
data class DependencyNode(
    var index: Int = 0,
    var dependenciesIndices: MutableCollection<Int> = ArrayList()
)

data class SerializablePackage(
    var contentRoot: String? = null,
    var name: String? = null,
    var version: String? = null,
    var targets: Collection<SerializableTarget> = ArrayList(),
    var source: String? = null
) {
    fun into(): CargoProjectDescription.Package? {
        return CargoProjectDescription.Package(
            contentRoot ?: return null,
            name ?: return null,
            version ?: return null,
            targets.map { it.into() ?: return null },
            source
        )
    }

    companion object {
        fun from(pkg: CargoProjectDescription.Package): SerializablePackage {
            return SerializablePackage(
                pkg.contentRoot,
                pkg.name,
                pkg.version,
                pkg.targets.map { SerializableTarget.from(it) },
                pkg.source
            )
        }
    }
}


data class SerializableTarget(
    var path: String? = null,
    var kind: CargoProjectDescription.TargetKind? = null
) {
    fun into(): CargoProjectDescription.Target? {
        return CargoProjectDescription.Target(
            path ?: return null,
            kind ?: return null
        )
    }

    companion object {
        fun from(target: CargoProjectDescription.Target): SerializableTarget =
            SerializableTarget(target.path, target.kind)
    }
}
