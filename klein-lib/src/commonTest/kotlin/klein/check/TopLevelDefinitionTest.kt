package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TopLevelDefinitionTest {
    @Test
    fun forwardReference_simpleCall() =
        assertEquals(
            TNum,
            infer(
                """
                fun f() = g()
                fun g() = 1
                f()
                """.trimIndent(),
            ).type,
        )

    @Test
    fun forwardReference_withArgs() =
        assertEquals(
            TNum,
            infer(
                """
                fun f(x: Num) = g(x + 1)
                fun g(y: Num) = y * 2
                f(5)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun forwardReference_expressionBeforeFunDef() =
        assertEquals(
            TNum,
            infer(
                """
                x = f(10)
                fun f(n: Num) = n + 1
                x
                """.trimIndent(),
            ).type,
        )

    @Test
    fun forwardReference_multipleCallsites() =
        assertEquals(
            TNum,
            infer(
                """
                fun h() = g() + g()
                fun g() = 42
                h()
                """.trimIndent(),
            ).type,
        )

    @Test
    fun forwardReference_valBeforeMultipleFuns() =
        assertEquals(
            TNum,
            infer(
                """
                x = f(g(1))
                fun f(a: Num) = a + 1
                fun g(b: Num) = b * 2
                x
                """.trimIndent(),
            ).type,
        )

    @Test
    fun nonRecursiveHelperStaysPolymorphic() =
        assertInfersType(
            TFun(listOf(tv("A")), tv("A"), listOf("x")),
            """
            fun id(x: 'A) = x
            fun f(x: Num) = id(3)
            fun g(x: Num) = id("Hello")
            id
            """.trimIndent(),
        )

    @Test
    fun callerSpecializesSharedHelperToNum() =
        assertEquals(
            TNum,
            infer(
                """
                fun id(x: 'A) = x
                fun f(x: Num) = id(3)
                fun g(x: Num) = id("Hi")
                f(0)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun callerSpecializesSharedHelperToString() =
        assertEquals(
            TStr,
            infer(
                """
                fun id(x: 'A) = x
                fun f(x: Num) = id(3)
                fun g(x: Num) = id("Hi")
                g(0)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun functionCanReferenceTopLevelValDefinedAbove() =
        assertEquals(
            TNum,
            infer(
                """
                base = 10
                fun addBase(x: Num) = x + base
                addBase(5)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun functionCannotReferenceTopLevelValDefinedBelow() {
        val e =
            infer(
                """
                fun useLater(x: Num) = x + later
                later = 10
                useLater(5)
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnboundVariable>(e)
        assertEquals("later", e.name)
    }

    @Test
    fun valInsideRecursiveCycleIsRejected() {
        val e =
            infer(
                """
                v = h()
                fun h() = k()
                fun k() = v
                v
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.RecursiveVal>().single()
        assertEquals(listOf("v", "h", "k", "v"), e.cycle)
    }
}
