package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Top-level `fun` definitions: signature binding, body checking, calls, recursion. */
class FunctionTest {
    @Test
    fun inferredReturn() =
        assertEquals(TNum, infer("fun double(x: Num) = x * 2\ndouble(5)").type)

    @Test
    fun declaredReturn() =
        assertEquals(TNum, infer("fun add(x: Num, y: Num): Num = x + y\nadd(1, 2)").type)

    @Test
    fun declaredReturnWiderThanBody_bindsDeclaredNotInferred() =
        // Body infers Num, but the signature declares the wider Any. The declared return must be
        // honored (bound as the function's result), not silently replaced by the inferred body type.
        assertEquals(
            TFun(listOf(TNum), TTop, listOf("x")),
            infer("fun f(x: Num): Any = x\nf").type,
        )

    @Test
    fun functionAsValue() =
        assertEquals(
            TFun(listOf(TNum), TNum, listOf("x")),
            infer("fun double(x: Num) = x * 2\ndouble").type,
        )

    @Test
    fun selfRecursionWithDeclaredReturn() =
        assertEquals(
            TNum,
            infer("fun fib(n: Num): Num = if n < 2 then n else fib(n - 1) + fib(n - 2)\nfib(10)").type,
        )

    // --- error cases ---

    @Test
    fun bareParamErrors() = assertTrue(infer("fun f(x) = x").errors.isNotEmpty())

    @Test
    fun returnMismatchErrors() = assertTrue(infer("fun f(x: Num): Bool = x").errors.isNotEmpty())

    @Test
    fun recursionWithoutDeclaredReturnErrors() =
        assertTrue(infer("fun loop(n: Num) = loop(n)").errors.isNotEmpty())

    // --- lambda in check position (checkLambda) ---

    @Test
    fun lambdaCheckedAgainstAny_passes() =
        // A lambda is a value, so it satisfies `Any` — must not error "found a function".
        assertTrue(infer("f: Any = |n: Num -> n|\nf").errors.isEmpty())

    @Test
    fun lambdaCheckedAgainstNonFunction_reportsTypeMismatchNotMisc() {
        // Non-function expected → subsumption fallback → a real TypeMismatch, never a Misc.
        val errors = infer("f: Num = |n: Num -> n|\nf").errors
        assertTrue(errors.any { it is TypeError.TypeMismatch })
        assertTrue(errors.none { it is TypeError.Misc })
    }

    @Test
    fun lambdaArityMismatch_reportsCallArityMismatchNotMisc() {
        val errors = infer("f: (Num) -> Num = |a, b -> a|\nf").errors
        assertEquals(1, errors.size)
        val e = errors[0]
        assertIs<TypeError.CallArityMismatch>(e)
        assertEquals(1, e.expected)
        assertEquals(2, e.actual)
    }
}
