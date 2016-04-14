package org.rust.lang.core.resolve

import com.intellij.openapi.module.Module
import com.intellij.testFramework.LightProjectDescriptor
import org.rust.cargo.project.CargoProjectDescriptionData
import java.util.*

class RustPackageLibraryResolveTestCase : RustMultiFileResolveTestCaseBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = object : RustProjectDescriptor() {

        override fun testCargoProject(module: Module, contentRoot: String): CargoProjectDescriptionData =
            CargoProjectDescriptionData(
                0,
                mutableListOf(testCargoPackage(contentRoot, name = "my_lib")),
                ArrayList()
            )
    }

    fun testLibraryAsCrate() = doTestResolved("library_as_crate/main.rs", "library_as_crate/lib.rs")
    fun testCrateAlias() = doTestResolved("crate_alias/main.rs", "crate_alias/lib.rs")
}
