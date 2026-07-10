package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopLevelDefinitionTest {
    @Test
    fun forwardReference_simpleCall() =
        assertEquals(TNum, infer("fun f() = g()\nfun g() = 1\nf()").type)

    @Test
    fun forwardReference_withArgs() =
        assertEquals(TNum, infer("fun f(x: Num) = g(x + 1)\nfun g(y: Num) = y * 2\nf(5)").type)

    @Test
    fun forwardReference_expressionBeforeFunDef() =
        assertEquals(TNum, infer("x = f(10)\nfun f(n: Num) = n + 1\nx").type)

    @Test
    fun forwardReference_multipleCallsites() =
        assertEquals(TNum, infer("fun h() = g() + g()\nfun g() = 42\nh()").type)

    @Test
    fun forwardReference_valBeforeMultipleFuns() =
        assertEquals(TNum, infer("x = f(g(1))\nfun f(a: Num) = a + 1\nfun g(b: Num) = b * 2\nx").type)

    @Test
    fun callerSpecializesSharedHelperToNum() =
        assertEquals(TNum, infer("fun id(x: 'A) = x\nfun f(x: Num) = id(3)\nfun g(x: Num) = id(\"Hi\")\nf(0)").type)

    @Test
    fun callerSpecializesSharedHelperToString() =
        assertEquals(TStr, infer("fun id(x: 'A) = x\nfun f(x: Num) = id(3)\nfun g(x: Num) = id(\"Hi\")\ng(0)").type)

    @Test
    fun functionCanReferenceTopLevelValDefinedAbove() =
        assertEquals(TNum, infer("base = 10\nfun addBase(x: Num) = x + base\naddBase(5)").type)

    @Test
    fun functionCannotReferenceTopLevelValDefinedBelow() {
        val errors = infer("fun useLater(x: Num) = x + later\nlater = 10\nuseLater(5)").errors
        assertTrue(
            errors.any { it is TypeError.UnboundVariable && it.name == "later" },
            "expected an unbound-variable error for the forward val reference, got: $errors",
        )
    }

    @Test
    fun valInsideRecursiveCycleIsRejected() {
        val errors = infer("v = h()\nfun h() = k()\nfun k() = v\nv").errors
        val recursiveVal = errors.filterIsInstance<TypeError.RecursiveVal>()
        assertTrue(recursiveVal.isNotEmpty(), "expected a recursive-val error for the val-in-cycle, got: $errors")
        assertEquals(listOf("v", "h", "k", "v"), recursiveVal.first().cycle)
    }
}
