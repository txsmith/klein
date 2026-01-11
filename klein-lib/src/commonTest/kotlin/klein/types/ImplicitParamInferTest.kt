package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImplicitParamInferTest {
    @Test
    fun implicitParam_bareIdentity() {
        assertType("(a) -> a", infer("|.|"))
    }

    @Test
    fun implicitParam_fieldAccess() {
        // Record constraint with field access - r appears in both polarities
        assertType("({ x: a }) -> a", infer("|.x|"))
    }

    @Test
    fun implicitParam_multipleFieldAccess() {
        // Both field types constrained to Num
        assertType("({ x: Num, y: Num }) -> Num", infer("|.x + .y|"))
    }

    @Test
    fun implicitParam_comparison() {
        // Param constrained to Num
        assertType("(Num) -> Bool", infer("|. > 100|"))
    }

    @Test
    fun implicitParam_inCondition() {
        // Record with Bool field, result is Num
        assertType("({ active: Bool }) -> Num", infer("|if .active then 1 else 0|"))
    }

    @Test
    fun implicitParam_outsideLambda_error() {
        val result = inferWithErrors(".")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.ImplicitParamOutsideLambda)
    }

    @Test
    fun implicitParam_outsideLambda_fieldAccess_error() {
        val result = inferWithErrors(".x")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.ImplicitParamOutsideLambda)
    }

    @Test
    fun implicitParam_mixedWithExplicit_error() {
        val result = inferWithErrors("|x -> . + x|")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.ImplicitParamWithExplicitParams)
    }

    @Test
    fun implicitParam_inNamedFunction_error() {
        val result = inferWithErrors("fun g() = .")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.ImplicitParamInNamedFunction)
    }

    @Test
    fun implicitParam_inNamedFunctionWithParams_error() {
        val result = inferWithErrors("fun f(x) = .")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.ImplicitParamInNamedFunction)
    }

    @Test
    fun implicitParam_mixedWithExplicit_fieldAccess_error() {
        val result = inferWithErrors("|x -> .y + x|")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.ImplicitParamWithExplicitParams)
    }

    @Test
    fun implicitParam_nestedLambda_separateScopes() {
        assertType("() -> () -> ({ x: a }) -> a", infer("|| |.x| ||"))
    }

    @Test
    fun implicitParam_nestedLambda_innerUsesImplicit() {
        // x is unused (polar), inner param constrained to Num
        assertType("(Any) -> (Num) -> Num", infer("|x -> |. * 2||"))
    }

    @Test
    fun implicitParam_constantLambda_noParam() {
        assertType("() -> Num", infer("|42|"))
    }

    @Test
    fun implicitParam_passthrough() {
        val env = TypeEnv.empty()
        env.bind("inc", SimpleType.TFun(listOf(SimpleType.TNum), SimpleType.TNum))
        // Param constrained to Num, result simplifies to Num
        assertType("(Num) -> Num", infer("|inc(.)|", env))
    }
}
