package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    // A non-recursive helper used by sibling functions must stay polymorphic — its type
    // should not absorb the argument types of its callers. (Requires SCC-based grouping:
    // id is its own component, generalized before f and g instantiate it.)
    @Test
    fun nonRecursiveHelperStaysPolymorphic() {
        assertType(
            "('A) -> 'A",
            infer(
                """
                fun id(x) = x
                fun f(x) = id(3)
                fun g(x) = id("Hello")
                id
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun callerSpecializesSharedHelperToNum() {
        assertType(
            "Num",
            infer(
                """
                fun id(x) = x
                fun f(x) = id(3)
                fun g(x) = id("Hello")
                f(0)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun callerSpecializesSharedHelperToString() {
        assertType(
            "String",
            infer(
                """
                fun id(x) = x
                fun f(x) = id(3)
                fun g(x) = id("Hello")
                g(0)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun functionCanReferenceTopLevelValDefinedAbove() {
        assertType(
            "Num",
            infer(
                """
                base = 10
                fun addBase(x) = x + base
                addBase(5)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun functionCannotReferenceTopLevelValDefinedBelow() {
        val errors =
            inferErrors(
                """
                fun useLater(x) = x + later
                later = 10
                useLater(5)
                """.trimIndent(),
            )
        assertTrue(
            errors.any { it is TypeError.UnboundVariable && it.name == "later" },
            "expected an unbound-variable error for the forward val reference, got: $errors",
        )
    }

    // A cycle that runs through a val (v -> h -> k -> v) is a use-before-initialization error,
    // and must be rejected specifically as a recursive-val error. Today inference instead emits an
    // incidental "unbound variable: v" (k can't see the val v at all), so this is RED until the
    // val-in-cycle check exists. We assert the real target error rather than "any error", so the
    // test cannot pass for the wrong reason.
    @Test
    fun valInsideRecursiveCycleIsRejected() {
        val errors =
            inferErrors(
                """
                v = h()
                fun h() = k()
                fun k() = v
                v
                """.trimIndent(),
            )
        val recursiveVal = errors.filterIsInstance<TypeError.RecursiveVal>()
        assertTrue(recursiveVal.isNotEmpty(), "expected a recursive-val error for the val-in-cycle, got: $errors")
        assertEquals(listOf("v", "h", "k", "v"), recursiveVal.first().cycle)
    }
}
