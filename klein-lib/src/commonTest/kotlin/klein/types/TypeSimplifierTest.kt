package klein.types

import klein.types.SimpleType.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TypeSimplifierTest {

    // Helper to create simplified type string
    private fun simplified(type: SimpleType): String = TypePrinter.print(type)

    // Helper to create raw (unsimplified) type string
    private fun raw(type: SimpleType): String = TypePrinter.printRaw(type)

    @Test
    fun simplify_primitives_unchanged() {
        assertEquals("Num", simplified(TNum))
        assertEquals("String", simplified(TString))
        assertEquals("Bool", simplified(TBool))
        assertEquals("Unit", simplified(TUnit))
    }

    @Test
    fun simplify_polarPositiveVar_withNumLowerBound_becomesNum() {
        // A variable that only appears positively with Num lower bound
        // should simplify to Num
        val v = TVar()
        v.lowerBounds.add(TNum)
        assertEquals("Num", simplified(v))
    }

    @Test
    fun simplify_polarPositiveVar_withStringLowerBound_becomesString() {
        val v = TVar()
        v.lowerBounds.add(TString)
        assertEquals("String", simplified(v))
    }

    @Test
    fun simplify_polarPositiveVar_noBounds_becomesNothing() {
        // A positive-only variable with no lower bounds becomes Nothing (bottom)
        val v = TVar()
        assertEquals("Nothing", simplified(v))
    }

    @Test
    fun simplify_function_identity_staysPolymorphic() {
        // Identity function: (a) -> a
        // The variable appears both positively (result) and negatively (param)
        // so it should stay polymorphic
        val a = TVar()
        val fn = TFun(listOf(a), a)
        assertEquals("(a) -> a", simplified(fn))
    }

    @Test
    fun simplify_function_constNum_paramsSimplified() {
        // Constant Num function: (a) -> Num
        // The param 'a' only appears negatively, should simplify to Any (top)
        val a = TVar()
        val fn = TFun(listOf(a), TNum)
        assertEquals("(Any) -> Num", simplified(fn))
    }

    @Test
    fun simplify_function_twoParams_identity() {
        // (a, b) -> a : param b only appears negatively
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a, b), a)
        assertEquals("(a, Any) -> a", simplified(fn))
    }

    @Test
    fun simplify_function_paramWithUpperBound() {
        // Function where param has upper bound Num
        // (a & Num) -> Num where 'a' only appears negatively
        val a = TVar()
        a.upperBounds.add(TNum)
        val fn = TFun(listOf(a), TNum)
        assertEquals("(Num) -> Num", simplified(fn))
    }

    @Test
    fun simplify_record_unchanged() {
        val rec = TRecord(mapOf("x" to TNum, "y" to TString))
        assertEquals("{ x: Num, y: String }", simplified(rec))
    }

    @Test
    fun simplify_record_withPolymorphicField() {
        // Record with a polar variable field
        val v = TVar()
        v.lowerBounds.add(TNum)
        val rec = TRecord(mapOf("value" to v))
        assertEquals("{ value: Num }", simplified(rec))
    }

    @Test
    fun simplify_functionApplication_resultSimplified() {
        // Simulating f(1) where f: (a) -> a
        // The result variable has Num as lower bound from the argument
        val a = TVar()
        val result = TVar()
        // Constraint: a <: result (from function application)
        result.lowerBounds.add(a)
        // Constraint: Num <: a (from argument)
        a.lowerBounds.add(TNum)
        // result is positive-only, should simplify to Num
        assertEquals("Num", simplified(result))
    }

    @Test
    fun simplify_chainedVariables_followBounds() {
        // a <: b <: c, where c is the result and has lower bound chain to Num
        val a = TVar()
        val b = TVar()
        val c = TVar()
        a.lowerBounds.add(TNum)
        b.lowerBounds.add(a)
        c.lowerBounds.add(b)
        // c should simplify to Num
        assertEquals("Num", simplified(c))
    }

    @Test
    fun simplify_higherOrderFunction_preservesStructure() {
        // ((a) -> b) -> (a) -> b
        // Both a and b appear in both polarities
        val a = TVar()
        val b = TVar()
        val innerFn = TFun(listOf(a), b)
        val fn = TFun(listOf(innerFn), TFun(listOf(a), b))
        assertEquals("((a) -> b) -> (a) -> b", simplified(fn))
    }

    @Test
    fun simplify_functionReturningRecord() {
        val a = TVar()
        val rec = TRecord(mapOf("value" to a))
        val fn = TFun(listOf(a), rec)
        assertEquals("(a) -> { value: a }", simplified(fn))
    }

    @Test
    fun simplify_recordWithFunctionField() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a), b)
        val rec = TRecord(mapOf("f" to fn))
        // a appears negatively (param), b appears positively (result) in f
        // But they only appear once each, so they're polar
        assertEquals("{ f: (Any) -> Nothing }", simplified(rec))
    }

    @Test
    fun simplify_nestedFunctions() {
        // (a) -> (b) -> a : 'b' is polar (negative only)
        val a = TVar()
        val b = TVar()
        val inner = TFun(listOf(b), a)
        val outer = TFun(listOf(a), inner)
        assertEquals("(a) -> (Any) -> a", simplified(outer))
    }

    // Test raw printing still works
    @Test
    fun printRaw_showsBounds() {
        val v = TVar()
        v.lowerBounds.add(TNum)
        v.upperBounds.add(TString)
        val rawStr = raw(v)
        assert("Num" in rawStr) { "Expected Num in bounds: $rawStr" }
        assert("String" in rawStr) { "Expected String in bounds: $rawStr" }
    }
}
