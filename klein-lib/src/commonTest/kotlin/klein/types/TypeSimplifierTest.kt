package klein.types

import klein.types.DisplayType.*
import klein.types.SimpleType.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeSimplifierTest {
    private fun simplified(type: SimpleType): DisplayType = TypeSimplifier.simplify(type)

    @Test
    fun simplify_primitives_unchanged() {
        assertEquals(DNum, simplified(TNum))
        assertEquals(DString, simplified(TString))
        assertEquals(DBool, simplified(TBool))
        assertEquals(DUnit, simplified(TUnit))
    }

    @Test
    fun simplify_polarPositiveVar_withNumLowerBound_becomesNum() {
        val v = TVar()
        v.lowerBounds.add(TNum)
        assertEquals(DNum, simplified(v))
    }

    @Test
    fun simplify_polarPositiveVar_withStringLowerBound_becomesString() {
        val v = TVar()
        v.lowerBounds.add(TString)
        assertEquals(DString, simplified(v))
    }

    @Test
    fun simplify_polarPositiveVar_noBounds_becomesNothing() {
        val v = TVar()
        assertEquals(DBottom, simplified(v))
    }

    @Test
    fun simplify_function_identity_staysPolymorphic() {
        val a = TVar()
        val fn = TFun(listOf(a), a)
        assertEquals(DFun(listOf(DVar("a")), DVar("a")), simplified(fn))
    }

    @Test
    fun simplify_function_constNum_paramsSimplified() {
        val a = TVar()
        val fn = TFun(listOf(a), TNum)
        assertEquals(DFun(listOf(DTop), DNum), simplified(fn))
    }

    @Test
    fun simplify_function_twoParams_identity() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a, b), a)
        assertEquals(DFun(listOf(DVar("a"), DTop), DVar("a")), simplified(fn))
    }

    @Test
    fun simplify_function_paramWithUpperBound() {
        val a = TVar()
        a.upperBounds.add(TNum)
        val fn = TFun(listOf(a), TNum)
        assertEquals(DFun(listOf(DNum), DNum), simplified(fn))
    }

    @Test
    fun simplify_record_unchanged() {
        val rec = TRecord(mapOf("x" to TNum, "y" to TString))
        assertEquals(DRecord(mapOf("x" to DNum, "y" to DString)), simplified(rec))
    }

    @Test
    fun simplify_record_withPolymorphicField() {
        val v = TVar()
        v.lowerBounds.add(TNum)
        val rec = TRecord(mapOf("value" to v))
        assertEquals(DRecord(mapOf("value" to DNum)), simplified(rec))
    }

    @Test
    fun simplify_functionApplication_resultSimplified() {
        val a = TVar()
        val result = TVar()
        result.lowerBounds.add(a)
        a.lowerBounds.add(TNum)
        assertEquals(DNum, simplified(result))
    }

    @Test
    fun simplify_chainedVariables_followBounds() {
        val a = TVar()
        val b = TVar()
        val c = TVar()
        a.lowerBounds.add(TNum)
        b.lowerBounds.add(a)
        c.lowerBounds.add(b)
        assertEquals(DNum, simplified(c))
    }

    @Test
    fun simplify_higherOrderFunction_preservesStructure() {
        val a = TVar()
        val b = TVar()
        val innerFn = TFun(listOf(a), b)
        val fn = TFun(listOf(innerFn), TFun(listOf(a), b))
        assertEquals(
            DFun(listOf(DFun(listOf(DVar("a")), DVar("b"))), DFun(listOf(DVar("a")), DVar("b"))),
            simplified(fn),
        )
    }

    @Test
    fun simplify_functionReturningRecord() {
        val a = TVar()
        val rec = TRecord(mapOf("value" to a))
        val fn = TFun(listOf(a), rec)
        assertEquals(DFun(listOf(DVar("a")), DRecord(mapOf("value" to DVar("a")))), simplified(fn))
    }

    @Test
    fun simplify_recordWithFunctionField() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a), b)
        val rec = TRecord(mapOf("f" to fn))
        assertEquals(DRecord(mapOf("f" to DFun(listOf(DTop), DBottom))), simplified(rec))
    }

    @Test
    fun simplify_nestedFunctions() {
        val a = TVar()
        val b = TVar()
        val inner = TFun(listOf(b), a)
        val outer = TFun(listOf(a), inner)
        assertEquals(DFun(listOf(DVar("a")), DFun(listOf(DTop), DVar("a"))), simplified(outer))
    }

    @Test
    fun simplify_varWithBothBounds_usesLowerBound() {
        val v = TVar()
        v.lowerBounds.add(TNum)
        v.upperBounds.add(TString)
        assertEquals(DNum, simplified(v))
    }

    @Test
    fun integration_unusedParam_becomesAny() {
        assertType(DFun(listOf(DTop), DNum), infer("|x -> 42|"))
    }

    @Test
    fun integration_multipleUnusedParams_allBecomeAny() {
        assertType(DFun(listOf(DTop, DTop, DTop), DNum), infer("|a, b, c -> 1|"))
    }

    @Test
    fun integration_unusedParamInNestedLambda_becomesAny() {
        assertType("(a) -> (Any) -> a", infer("|x -> |y -> x||"))
    }

    @Test
    fun integration_constantFunction_paramIsAny() {
        assertType(DFun(listOf(DTop), DString), infer("|_ -> 'hello'|"))
    }

    @Test
    fun integration_recordWithUnusedFieldFunction() {
        assertType(DRecord(mapOf("f" to DFun(listOf(DTop), DNum))), infer("{ f = |x -> 1| }"))
    }

    @Test
    fun integration_higherOrder_unusedFunctionParam() {
        assertType(DFun(listOf(DTop), DNum), infer("|f -> 42|"))
    }

    @Test
    fun integration_thunk_noParams() {
        assertType(DFun(emptyList(), DNum), infer("|42|"))
    }

    @Test
    fun integration_nestedThunks() {
        assertType(DFun(emptyList(), DFun(emptyList(), DString)), infer("|| 'hello' ||"))
    }

    @Test
    fun integration_functionReturningNothing_viaPolarResult() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a), b)
        assertEquals(DFun(listOf(DTop), DBottom), simplified(fn))
    }

    @Test
    fun integration_recordWithNothingField() {
        val v = TVar()
        val rec = TRecord(mapOf("x" to v))
        assertEquals(DRecord(mapOf("x" to DBottom)), simplified(rec))
    }

    @Test
    fun integration_polarNegativeVar_withNoUpperBound_becomesAny() {
        val a = TVar()
        val fn = TFun(listOf(a), TNum)
        assertEquals(DFun(listOf(DTop), DNum), simplified(fn))
    }

    @Test
    fun integration_polarNegativeVar_withUpperBound_usesBound() {
        val a = TVar()
        a.upperBounds.add(TNum)
        val fn = TFun(listOf(a), TString)
        assertEquals(DFun(listOf(DNum), DString), simplified(fn))
    }
}
