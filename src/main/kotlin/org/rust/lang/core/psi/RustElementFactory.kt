package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.childOfType

object RustElementFactory {
    fun createFileFromText(project: Project, text: String): RustFile? =
        PsiFileFactory.getInstance(project).createFileFromText("DUMMY.rs", RustLanguage, text) as RustFile?

    fun createExpression(project: Project, expression: String): RustExpr? {
        val file = createFileFromText(project, "fn main() {$expression;}")
        return file?.childOfType<RustExpr>()
    }

    fun createModDeclItem(project: Project, modName: String): RustModDeclItem? {
        val file = createFileFromText(project, "mod $modName;")
        return file?.childOfType<RustModDeclItem>()
    }

    fun createIdentifier(project: Project, name: String): PsiElement? {
        return createModDeclItem(project, name)?.identifier
    }
}
