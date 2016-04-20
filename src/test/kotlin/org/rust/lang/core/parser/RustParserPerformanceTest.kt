package org.rust.lang.core.parser

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.PlatformTestUtil
import org.junit.experimental.categories.Category
import org.rust.Performance
import org.rust.lang.RustFileType
import org.rust.lang.RustTestCaseBase
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

@Category(Performance::class)
class RustParserPerformanceTest : RustTestCaseBase() {
    override val dataPath: String = ""

    fun testParsingCompilerSources() {
        val sources = rustSources()
        parseRustFiles(sources,
            ignored = setOf("test", "doc", "etc", "grammar"),
            expectedNumberOfFiles = 800,
            checkForErrors = true,
            expectedTimeMs = TimeUnit.SECONDS.toMillis(40)
        )
    }

    fun testParsingCompilerTests() {
        val sources = rustSources()
        val testDir = sources.children.single().findFileByRelativePath("src/test")!!
        parseRustFiles(testDir,
            ignored = emptyList(),
            expectedNumberOfFiles = 4000,
            checkForErrors = false,
            expectedTimeMs = TimeUnit.SECONDS.toMillis(40)
        )
    }

    private data class FileStats(
        val path: String,
        val time: Long,
        val fileLength: Int
    )

    private fun parseRustFiles(directory: VirtualFile,
                               ignored: Collection<String>,
                               expectedNumberOfFiles: Int,
                               checkForErrors: Boolean,
                               expectedTimeMs: Long
    ) {
        val processed = ArrayList<FileStats>()
        PlatformTestUtil.startPerformanceTest(name, expectedTimeMs.toInt()) {
            VfsUtilCore.visitChildrenRecursively(directory, object : VirtualFileVisitor<Void>() {
                override fun visitFileEx(file: VirtualFile): Result {
                    if (file.isDirectory && file.name in ignored) return SKIP_CHILDREN
                    if (file.fileType != RustFileType) return CONTINUE
                    val fileContent = String(file.contentsToByteArray())

                    val time = measureTimeMillis {
                        val psi = PsiFileFactory.getInstance(project).createFileFromText(file.name, file.fileType, fileContent)
                        val psiString = DebugUtil.psiToString(psi, /* skipWhitespace = */ true)

                        if (checkForErrors) {
                            check("PsiErrorElement" !in psiString) {
                                "Failed to parse ${file.path}\n\n$fileContent\n\n$psiString\n\n${file.path}"
                            }
                        }
                    }

                    val relPath = FileUtil.getRelativePath(directory.path, file.path, '/')!!
                    processed += FileStats(relPath, time, fileContent.length)
                    return CONTINUE
                }
            })
        }.cpuBound().assertTiming()
        check(processed.size > expectedNumberOfFiles)

        printReport(processed)
    }

    private fun printReport(processed: ArrayList<FileStats>) {
        val slowest = processed.sortedByDescending { it.time }.take(10)
        println("Slowest files")
        for (stats in slowest) {
            println("${"%3d".format(stats.time)}ms ${"%3d".format(stats.fileLength / 1024)}kb: ${stats.path}")
        }
        println()
    }
}

