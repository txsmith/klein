package klein.types

import klein.Type
import klein.types.SimpleType.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeSimplifierTest {
    private fun simplified(type: SimpleType): Type = TypeSimplifier.simplify(type, TypeEnv.empty())

    @Test
    fun simplify_primitives_unchanged() {
        assertEquals(Type.Num, simplified(TNum))
        assertEquals(Type.Str, simplified(TString))
        assertEquals(Type.Bool, simplified(TBool))
        assertEquals(Type.Unit, simplified(TUnit))
    }

    @Test
    fun simplify_polarPositiveVar_withNumLowerBound_becomesNum() {
        val v = TVar()
        v.lowerBounds.add(TNum)
        assertEquals(Type.Num, simplified(v))
    }

    @Test
    fun simplify_polarPositiveVar_withStringLowerBound_becomesString() {
        val v = TVar()
        v.lowerBounds.add(TString)
        assertEquals(Type.Str, simplified(v))
    }

    @Test
    fun simplify_polarPositiveVar_noBounds_becomesNothing() {
        val v = TVar()
        assertEquals(Type.Bottom, simplified(v))
    }

    @Test
    fun simplify_function_identity_staysPolymorphic() {
        val a = TVar()
        val fn = TFun(listOf(a), a)
        assertEquals(Type.Fun(listOf(Type.Var("'A")), Type.Var("'A")), simplified(fn))
    }

    @Test
    fun simplify_function_constNum_paramsSimplified() {
        val a = TVar()
        val fn = TFun(listOf(a), TNum)
        assertEquals(Type.Fun(listOf(Type.Top), Type.Num), simplified(fn))
    }

    @Test
    fun simplify_function_twoParams_identity() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a, b), a)
        assertEquals(Type.Fun(listOf(Type.Var("'A"), Type.Top), Type.Var("'A")), simplified(fn))
    }

    @Test
    fun simplify_function_paramWithUpperBound() {
        val a = TVar()
        a.upperBounds.add(TNum)
        val fn = TFun(listOf(a), TNum)
        assertEquals(Type.Fun(listOf(Type.Num), Type.Num), simplified(fn))
    }

    @Test
    fun simplify_record_unchanged() {
        val rec = TRecord(mapOf("x" to TNum, "y" to TString))
        assertEquals(Type.Record(mapOf("x" to Type.Num, "y" to Type.Str)), simplified(rec))
    }

    @Test
    fun simplify_record_withPolymorphicField() {
        val v = TVar()
        v.lowerBounds.add(TNum)
        val rec = TRecord(mapOf("value" to v))
        assertEquals(Type.Record(mapOf("value" to Type.Num)), simplified(rec))
    }

    @Test
    fun simplify_functionApplication_resultSimplified() {
        val a = TVar()
        val result = TVar()
        result.lowerBounds.add(a)
        a.lowerBounds.add(TNum)
        assertEquals(Type.Num, simplified(result))
    }

    @Test
    fun simplify_chainedVariables_followBounds() {
        val a = TVar()
        val b = TVar()
        val c = TVar()
        a.lowerBounds.add(TNum)
        b.lowerBounds.add(a)
        c.lowerBounds.add(b)
        assertEquals(Type.Num, simplified(c))
    }

    @Test
    fun simplify_higherOrderFunction_preservesStructure() {
        val a = TVar()
        val b = TVar()
        val innerFn = TFun(listOf(a), b)
        val fn = TFun(listOf(innerFn), TFun(listOf(a), b))
        assertEquals(
            Type.Fun(listOf(Type.Fun(listOf(Type.Var("'A")), Type.Var("'B"))), Type.Fun(listOf(Type.Var("'A")), Type.Var("'B"))),
            simplified(fn),
        )
    }

    @Test
    fun simplify_functionReturningRecord() {
        val a = TVar()
        val rec = TRecord(mapOf("value" to a))
        val fn = TFun(listOf(a), rec)
        assertEquals(Type.Fun(listOf(Type.Var("'A")), Type.Record(mapOf("value" to Type.Var("'A")))), simplified(fn))
    }

    @Test
    fun simplify_recordWithFunctionField() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a), b)
        val rec = TRecord(mapOf("f" to fn))
        assertEquals(Type.Record(mapOf("f" to Type.Fun(listOf(Type.Top), Type.Bottom))), simplified(rec))
    }

    @Test
    fun simplify_nestedFunctions() {
        val a = TVar()
        val b = TVar()
        val inner = TFun(listOf(b), a)
        val outer = TFun(listOf(a), inner)
        assertEquals(Type.Fun(listOf(Type.Var("'A")), Type.Fun(listOf(Type.Top), Type.Var("'A"))), simplified(outer))
    }

    @Test
    fun simplify_varWithBothBounds_usesLowerBound() {
        val v = TVar()
        v.lowerBounds.add(TNum)
        v.upperBounds.add(TString)
        assertEquals(Type.Num, simplified(v))
    }

    @Test
    fun integration_unusedParam_becomesAny() {
        assertType(Type.Fun(listOf(Type.Top), Type.Num), infer("|x -> 42|"))
    }

    @Test
    fun integration_multipleUnusedParams_allBecomeAny() {
        assertType(Type.Fun(listOf(Type.Top, Type.Top, Type.Top), Type.Num), infer("|a, b, c -> 1|"))
    }

    @Test
    fun integration_unusedParamInNestedLambda_becomesAny() {
        assertType("('A) -> (Any) -> 'A", infer("|x -> |y -> x||"))
    }

    @Test
    fun integration_constantFunction_paramIsAny() {
        assertType(Type.Fun(listOf(Type.Top), Type.Str), infer("|_ -> \"hello\"|"))
    }

    @Test
    fun integration_recordWithUnusedFieldFunction() {
        assertType(Type.Record(mapOf("f" to Type.Fun(listOf(Type.Top), Type.Num))), infer("{ f = |x -> 1| }"))
    }

    @Test
    fun integration_higherOrder_unusedFunctionParam() {
        assertType(Type.Fun(listOf(Type.Top), Type.Num), infer("|f -> 42|"))
    }

    @Test
    fun integration_thunk_noParams() {
        assertType(Type.Fun(emptyList(), Type.Num), infer("|42|"))
    }

    @Test
    fun integration_nestedThunks() {
        assertType(Type.Fun(emptyList(), Type.Fun(emptyList(), Type.Str)), infer("|| \"hello\" ||"))
    }

    @Test
    fun integration_functionReturningNothing_viaPolarResult() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a), b)
        assertEquals(Type.Fun(listOf(Type.Top), Type.Bottom), simplified(fn))
    }

    @Test
    fun integration_recordWithNothingField() {
        val v = TVar()
        val rec = TRecord(mapOf("x" to v))
        assertEquals(Type.Record(mapOf("x" to Type.Bottom)), simplified(rec))
    }

    @Test
    fun integration_polarNegativeVar_withNoUpperBound_becomesAny() {
        val a = TVar()
        val fn = TFun(listOf(a), TNum)
        assertEquals(Type.Fun(listOf(Type.Top), Type.Num), simplified(fn))
    }

    @Test
    fun integration_polarNegativeVar_withUpperBound_usesBound() {
        val a = TVar()
        a.upperBounds.add(TNum)
        val fn = TFun(listOf(a), TString)
        assertEquals(Type.Fun(listOf(Type.Num), Type.Str), simplified(fn))
    }
}
