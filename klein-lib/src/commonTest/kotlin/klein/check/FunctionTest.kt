package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
