package klein.types

import klein.Type
import klein.types.SimpleType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionInferTest {
    @Test
    fun lambda_noParams() {
        assertType(Type.Fun(emptyList(), Type.Num), infer("|1|"))
    }

    @Test
    fun lambda_oneParam() {
        assertType("('A) -> 'A", infer("|x -> x|"))
    }

    @Test
    fun lambda_twoParams() {
        assertType("('A, Any) -> 'A", infer("|x, y -> x|"))
    }

    @Test
    fun lambda_bodyUsesParam() {
        val env = TypeEnv.empty()
        env.bind("add", TFun(listOf(TNum, TNum), TNum))
        assertType(Type.Fun(listOf(Type.Num, Type.Num), Type.Num), infer("|x, y -> add(x, y)|", env))
    }

    @Test
    fun apply_knownFunction() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(listOf(TNum), TString))
        assertType(Type.Str, infer("f(1)", env))
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
        val result = inferWithErrors("f(\"hello\")", env)
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
        assertType(Type.Num, infer("|x -> x|(1)"))
    }

    @Test
    fun apply_nestedCalls() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(listOf(TNum), TNum))
        assertType(Type.Num, infer("f(f(1))", env))
    }

    @Test
    fun apply_noArgs() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(emptyList(), TNum))
        assertType(Type.Num, infer("f()", env))
    }

    @Test
    fun lambda_nestedLambda() {
        assertType("('A) -> (Any) -> 'A", infer("|x -> |y -> x||"))
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

    @Test
    fun higherOrder_paramAppliedToValue() {
        assertType("((Num) -> 'A) -> 'A", infer("|x -> x(42)|"))
    }

    @Test
    fun higherOrder_functionOverBools() {
        assertType(Type.Fun(listOf(Type.Bool), Type.Bool), infer("|x -> not x|"))
    }

    @Test
    fun higherOrder_appliedNegation() {
        assertType(Type.Bool, infer("|x -> not x|(true)"))
    }

    @Test
    fun selfApplication_basic() {
        val result = inferWithErrors("|x -> x(x)|")
        assertEquals(0, result.errors.size, "Self-application should type-check: ${result.errors}")
    }

    @Test
    fun selfApplication_basic_type() {
        assertType("('A & (('A) -> 'B)) -> 'B", infer("|x -> x(x)|"))
    }

    @Test
    fun selfApplication_triple() {
        val result = inferWithErrors("|x -> x(x)(x)|")
        assertEquals(0, result.errors.size, "Triple self-application should type-check: ${result.errors}")
    }

    @Test
    fun selfApplication_mixed1() {
        val result = inferWithErrors("|x -> |y -> x(y)(x)||")
        assertEquals(0, result.errors.size, "Mixed self-application should type-check: ${result.errors}")
    }

    @Test
    fun selfApplication_mixed2() {
        val result = inferWithErrors("|x -> |y -> x(x)(y)||")
        assertEquals(0, result.errors.size, "Mixed self-application 2 should type-check: ${result.errors}")
    }

    @Test
    fun selfApplication_omega() {
        val result = inferWithErrors("|x -> x(x)|(|x -> x(x)|)")
        assertEquals(0, result.errors.size, "Omega combinator should type-check: ${result.errors}")
    }

    @Test
    fun selfApplication_inRecord() {
        val result = inferWithErrors("|x -> { l = x(x), r = x }|")
        assertEquals(0, result.errors.size, "Self-application in record should type-check: ${result.errors}")
    }
}
