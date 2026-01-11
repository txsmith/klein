package klein.types

import klein.types.SimpleType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionInferTest {
    @Test
    fun lambda_noParams() {
        assertType("() -> Num", infer("|1|"))
    }

    @Test
    fun lambda_oneParam() {
        assertType("(a) -> a", infer("|x -> x|"))
    }

    @Test
    fun lambda_twoParams() {
        assertType("(a, b) -> a", infer("|x, y -> x|"))
    }

    @Test
    fun lambda_bodyUsesParam() {
        val env = TypeEnv.empty()
        env.bind("add", TFun(listOf(TNum, TNum), TNum))
        assertType("(a & Num, b & Num) -> c | Num", infer("|x, y -> add(x, y)|", env))
    }

    @Test
    fun apply_knownFunction() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(listOf(TNum), TString))
        assertType("a | String", infer("f(1)", env))
    }

    @Test
    fun apply_arityMismatch() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(listOf(TNum), TString))
        val result = inferWithErrors("f(1, 2)", env)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.ArityMismatch)
    }

    @Test
    fun apply_typeMismatch() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(listOf(TNum), TString))
        val result = inferWithErrors("f('hello')", env)
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun apply_unboundFunction() {
        val result = inferWithErrors("unknown(1)")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.UnboundVariable)
    }

    @Test
    fun apply_lambdaDirectly() {
        assertType("a | Num | b", infer("|x -> x|(1)"))
    }

    @Test
    fun apply_nestedCalls() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(listOf(TNum), TNum))
        assertType("a | Num", infer("f(f(1))", env))
    }

    @Test
    fun apply_noArgs() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(emptyList(), TNum))
        assertType("a | Num", infer("f()", env))
    }

    @Test
    fun lambda_nestedLambda() {
        assertType("(a) -> (b) -> a", infer("|x -> |y -> x||"))
    }

    @Test
    fun lambda_duplicateParam() {
        val result = inferWithErrors("|x, x -> x|")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.DuplicateParameter)
    }

    @Test
    fun lambda_tripleDuplicateParam() {
        val result = inferWithErrors("|x, x, x -> x|")
        assertEquals(2, result.errors.size)
        assertTrue(result.errors.all { it is TypeError.DuplicateParameter })
    }
}
