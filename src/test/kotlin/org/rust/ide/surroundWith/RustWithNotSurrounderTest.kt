package org.rust.ide.surroundWith

class RustWithNotSurrounderTest : RustSurrounderTestCaseBase(RustWithNotSurrounder()) {
    fun testSimple() {
        doTest(
            """
            fn main() {
                <selection>true</selection>
            }
            """
        ,
            """
            fn main() {
                !(true)<caret>
            }
            """
        )
    }
    fun testCall() {
        doTest(
            """
            fn func() -> bool {
                false
            }

            fn main() {
                <selection>func()</selection>
            }
            """
        ,
            """
            fn func() -> bool {
                false
            }

            fn main() {
                !(func())<caret>
            }
            """
        )
    }

    fun testNumber() {
        doTestNotApplicable(
            """
            fn main() {
                <selection>1234</selection>
            }
            """
        )
    }

    fun testNumberCall() {
        doTestNotApplicable(
            """
            fn func() -> i32 {
                1234
            }

            fn main() {
                <selection>func()</selection>
            }
            """
        )
    }
}
