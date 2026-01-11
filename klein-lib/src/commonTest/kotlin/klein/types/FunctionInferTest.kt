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
        // y only appears negatively (parameter), simplifies to Any
        assertType("(a, Any) -> a", infer("|x, y -> x|"))
    }

    @Test
    fun lambda_bodyUsesParam() {
        val env = TypeEnv.empty()
        env.bind("add", TFun(listOf(TNum, TNum), TNum))
        // x and y have upper bounds of Num, simplify to Num
        assertType("(Num, Num) -> Num", infer("|x, y -> add(x, y)|", env))
    }

    @Test
    fun apply_knownFunction() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(listOf(TNum), TString))
        // Result variable is positive-only with String lower bound
        assertType("String", infer("f(1)", env))
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
        // Applying identity to Num gives Num
        assertType("Num", infer("|x -> x|(1)"))
    }

    @Test
    fun apply_nestedCalls() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(listOf(TNum), TNum))
        // Nested calls still give Num
        assertType("Num", infer("f(f(1))", env))
    }

    @Test
    fun apply_noArgs() {
        val env = TypeEnv.empty()
        env.bind("f", TFun(emptyList(), TNum))
        // Thunk application gives Num
        assertType("Num", infer("f()", env))
    }

    @Test
    fun lambda_nestedLambda() {
        // y only appears negatively, simplifies to Any
        assertType("(a) -> (Any) -> a", infer("|x -> |y -> x||"))
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

    // ==========================================
    // Higher-order functions
    // ==========================================

    @Test
    fun higherOrder_paramAppliedToValue() {
        // fun x -> x 42 : (int -> 'a) -> 'a
        assertType("((Num) -> a) -> a", infer("|x -> x(42)|"))
    }

    @Test
    fun higherOrder_functionOverBools() {
        // fun x -> not x : bool -> bool
        assertType("(Bool) -> Bool", infer("|x -> not x|"))
    }

    @Test
    fun higherOrder_appliedNegation() {
        // (fun x -> not x) true : bool
        assertType("Bool", infer("|x -> not x|(true)"))
    }

    // ==========================================
    // Self-application (requires subtyping)
    // ==========================================

    @Test
    fun selfApplication_basic() {
        // fun x -> x x : 'a ∧ ('a -> 'b) -> 'b
        val result = inferWithErrors("|x -> x(x)|")
        assertEquals(0, result.errors.size, "Self-application should type-check: ${result.errors}")
    }

    @Test
    fun selfApplication_triple() {
        // fun x -> x x x
        val result = inferWithErrors("|x -> x(x)(x)|")
        assertEquals(0, result.errors.size, "Triple self-application should type-check: ${result.errors}")
    }

    @Test
    fun selfApplication_mixed1() {
        // fun x -> fun y -> x y x
        val result = inferWithErrors("|x -> |y -> x(y)(x)||")
        assertEquals(0, result.errors.size, "Mixed self-application should type-check: ${result.errors}")
    }

    @Test
    fun selfApplication_mixed2() {
        // fun x -> fun y -> x x y
        val result = inferWithErrors("|x -> |y -> x(x)(y)||")
        assertEquals(0, result.errors.size, "Mixed self-application 2 should type-check: ${result.errors}")
    }

    @Test
    fun selfApplication_omega() {
        // (fun x -> x x) (fun x -> x x) : ⊥ (omega combinator)
        val result = inferWithErrors("|x -> x(x)|(|x -> x(x)|)")
        assertEquals(0, result.errors.size, "Omega combinator should type-check: ${result.errors}")
    }

    @Test
    fun selfApplication_inRecord() {
        // fun x -> { l = x x, r = x }
        val result = inferWithErrors("|x -> { l = x(x), r = x }|")
        assertEquals(0, result.errors.size, "Self-application in record should type-check: ${result.errors}")
    }
}
