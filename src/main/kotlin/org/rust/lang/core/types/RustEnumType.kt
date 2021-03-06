package org.rust.lang.core.types

import com.intellij.codeInsight.completion.CompletionUtil
import org.rust.lang.core.psi.RustEnumItemElement
import org.rust.lang.core.types.visitors.RustTypeVisitor

class RustEnumType(enum: RustEnumItemElement) : RustStructOrEnumTypeBase() {

    override val item = CompletionUtil.getOriginalOrSelf(enum)

    override fun <T> accept(visitor: RustTypeVisitor<T>): T = visitor.visitEnum(this)

    override fun toString(): String = item.name ?: "<anonymous>"

}
