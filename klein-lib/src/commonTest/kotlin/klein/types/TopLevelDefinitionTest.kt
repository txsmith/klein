package klein.types

import kotlin.test.Test

class TopLevelDefinitionTest {
    @Test
    fun forwardReference_simpleCall() {
        assertType(
            "Num",
            infer(
                """
                fun f() = g()
                fun g() = 1
                f()
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun forwardReference_withArgs() {
        assertType(
            "Num",
            infer(
                """
                fun f(x) = g(x + 1)
                fun g(y) = y * 2
                f(5)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun forwardReference_expressionBeforeFunDef() {
        assertType(
            "Num",
            infer(
                """
                x = f(10)
                fun f(n) = n + 1
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun forwardReference_multipleCallsites() {
        assertType(
            "Num",
            infer(
                """
                fun h() = g() + g()
                fun g() = 42
                h()
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun forwardReference_valBeforeMultipleFuns() {
        assertType(
            "Num",
            infer(
                """
                x = f(g(1))
                fun f(a) = a + 1
                fun g(b) = b * 2
                x
                """.trimIndent(),
            ),
        )
    }
}
