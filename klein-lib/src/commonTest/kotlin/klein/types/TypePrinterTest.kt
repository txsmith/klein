package klein.types

import klein.types.SimpleType.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TypePrinterTest {
    @Test
    fun printInt() {
        assertEquals("Int", TypePrinter.print(TInt))
    }

    @Test
    fun printDouble() {
        assertEquals("Double", TypePrinter.print(TDouble))
    }

    @Test
    fun printString() {
        assertEquals("String", TypePrinter.print(TString))
    }

    @Test
    fun printBool() {
        assertEquals("Bool", TypePrinter.print(TBool))
    }

    @Test
    fun printUnit() {
        assertEquals("Unit", TypePrinter.print(TUnit))
    }

    @Test
    fun printTypeVar_firstGetsA() {
        val v = TVar()
        assertEquals("a", TypePrinter.print(v))
    }

    @Test
    fun printTypeVar_sameInstanceSameName() {
        val v = TVar()
        val fn = TFun(listOf(v), v)
        assertEquals("a -> a", TypePrinter.print(fn))
    }

    @Test
    fun printTypeVar_differentInstancesDifferentNames() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a), b)
        assertEquals("a -> b", TypePrinter.print(fn))
    }

    @Test
    fun printTypeVar_namesAToZ() {
        val vars = (0 until 26).map { TVar() }
        val rec = TRecord(vars.mapIndexed { i, v -> "f$i" to v }.toMap())
        val printed = TypePrinter.print(rec)
        ('a'..'z').forEach { c ->
            assert(": $c" in printed || ": $c," in printed) { "Expected '$c' in $printed" }
        }
    }

    @Test
    fun printTypeVar_wrapsToA1AfterZ() {
        val vars = (0 until 27).map { TVar() }
        val fn = TFun(vars.dropLast(1), vars.last())
        val printed = TypePrinter.print(fn)
        assert(printed.endsWith("-> a1")) { "Expected 'a1' for 27th var, got: $printed" }
    }

    @Test
    fun printTypeVar_continuesB1C1() {
        val vars = (0 until 29).map { TVar() }
        val fn = TFun(vars.dropLast(1), vars.last())
        val printed = TypePrinter.print(fn)
        assert(printed.endsWith("-> c1")) { "Expected 'c1' for 29th var, got: $printed" }
    }

    @Test
    fun printTop() {
        assertEquals("Any", TypePrinter.print(TTop))
    }

    @Test
    fun printBottom() {
        assertEquals("Nothing", TypePrinter.print(TBottom))
    }

    @Test
    fun printFunctionIntToInt() {
        val fn = TFun(listOf(TInt), TInt)
        assertEquals("Int -> Int", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionIntToString() {
        val fn = TFun(listOf(TInt), TString)
        assertEquals("Int -> String", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionBoolToBool() {
        val fn = TFun(listOf(TBool), TBool)
        assertEquals("Bool -> Bool", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionVarToVar() {
        val v = TVar()
        val fn = TFun(listOf(v), v)
        assertEquals("a -> a", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionVarToInt() {
        val v = TVar()
        val fn = TFun(listOf(v), TInt)
        assertEquals("a -> Int", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionTwoParams() {
        val fn = TFun(listOf(TInt, TInt), TInt)
        assertEquals("(Int, Int) -> Int", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionThreeParams() {
        val fn = TFun(listOf(TInt, TString, TBool), TDouble)
        assertEquals("(Int, String, Bool) -> Double", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionMixedTypeVarsAndPrimitives() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a, TInt), b)
        assertEquals("(a, Int) -> b", TypePrinter.print(fn))
    }

    @Test
    fun printThunkInt() {
        val fn = TFun(emptyList(), TInt)
        assertEquals("() -> Int", TypePrinter.print(fn))
    }

    @Test
    fun printThunkString() {
        val fn = TFun(emptyList(), TString)
        assertEquals("() -> String", TypePrinter.print(fn))
    }

    @Test
    fun printThunkVar() {
        val v = TVar()
        val fn = TFun(emptyList(), v)
        assertEquals("() -> a", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionReturningFunction() {
        val inner = TFun(listOf(TInt), TInt)
        val outer = TFun(listOf(TInt), inner)
        assertEquals("Int -> Int -> Int", TypePrinter.print(outer))
    }

    @Test
    fun printFunctionTakingFunction() {
        val function = TFun(listOf(TFun(listOf(TInt), TInt)), TInt)
        assertEquals("(Int -> Int) -> Int", TypePrinter.print(function))
    }

    @Test
    fun printHigherOrderWithMultipleParams() {
        // (a -> b, a) -> b
        val a = TVar()
        val b = TVar()
        val outer = TFun(listOf(TFun(listOf(a), b), a), b)
        assertEquals("(a -> b, a) -> b", TypePrinter.print(outer))
    }

    @Test
    fun printDoublyCurried() {
        // a -> b -> c -> a
        val a = TVar()
        val b = TVar()
        val c = TVar()
        val fn = TFun(listOf(a), TFun(listOf(b), TFun(listOf(c), a)))
        assertEquals("a -> b -> c -> a", TypePrinter.print(fn))
    }

    @Test
    fun printEmptyRecord() {
        val rec = TRecord(emptyMap())
        assertEquals("{}", TypePrinter.print(rec))
    }

    @Test
    fun printRecordOneField() {
        val rec = TRecord(mapOf("a" to TInt))
        assertEquals("{ a: Int }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordTwoFields() {
        val rec = TRecord(mapOf("name" to TString, "age" to TInt))
        // Fields are sorted alphabetically
        assertEquals("{ age: Int, name: String }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordThreeFields() {
        val rec = TRecord(mapOf("x" to TInt, "y" to TInt, "z" to TInt))
        assertEquals("{ x: Int, y: Int, z: Int }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordWithVarField() {
        val a = TVar()
        val rec = TRecord(mapOf("value" to a))
        assertEquals("{ value: a }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordWithFunctionField() {
        val fn = TFun(listOf(TInt), TInt)
        val rec = TRecord(mapOf("f" to fn))
        assertEquals("{ f: Int -> Int }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordWithMixedTypes() {
        val rec =
            TRecord(
                mapOf(
                    "i" to TInt,
                    "d" to TDouble,
                    "s" to TString,
                    "b" to TBool,
                ),
            )
        // Fields are sorted alphabetically: b, d, i, s
        assertEquals("{ b: Bool, d: Double, i: Int, s: String }", TypePrinter.print(rec))
    }

    @Test
    fun printNestedRecord() {
        val inner = TRecord(mapOf("x" to TInt))
        val outer = TRecord(mapOf("inner" to inner))
        assertEquals("{ inner: { x: Int } }", TypePrinter.print(outer))
    }

    @Test
    fun printDeeplyNestedRecord() {
        val innermost = TRecord(mapOf("c" to TInt))
        val middle = TRecord(mapOf("b" to innermost))
        val outer = TRecord(mapOf("a" to middle))
        assertEquals("{ a: { b: { c: Int } } }", TypePrinter.print(outer))
    }

    @Test
    fun printFunctionReturningRecord() {
        val a = TVar()
        val rec = TRecord(mapOf("value" to a))
        val fn = TFun(listOf(a), rec)
        assertEquals("a -> { value: a }", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionTakingRecord() {
        val rec = TRecord(mapOf("name" to TString))
        val fn = TFun(listOf(rec), TString)
        assertEquals("{ name: String } -> String", TypePrinter.print(fn))
    }

    @Test
    fun printRecordOfFunctions() {
        val inc = TFun(listOf(TInt), TInt)
        val dec = TFun(listOf(TInt), TInt)
        val rec = TRecord(mapOf("inc" to inc, "dec" to dec))
        assertEquals("{ dec: Int -> Int, inc: Int -> Int }", TypePrinter.print(rec))
    }

    @Test
    fun printComplexPolymorphicFunction() {
        // (a -> b, { x: a, y: a }) -> { x: b, y: b }
        val a = TVar()
        val b = TVar()
        val paramFn = TFun(listOf(a), b)
        val inputRec = TRecord(mapOf("x" to a, "y" to a))
        val outputRec = TRecord(mapOf("x" to b, "y" to b))
        val fn = TFun(listOf(paramFn, inputRec), outputRec)
        assertEquals("(a -> b, { x: a, y: a }) -> { x: b, y: b }", TypePrinter.print(fn))
    }

    @Test
    fun printVarWithUpperBound() {
        val v = TVar()
        v.upperBounds.add(TInt)
        assertEquals("a & Int", TypePrinter.print(v))
    }

    @Test
    fun printVarWithLowerBound() {
        val v = TVar()
        v.lowerBounds.add(TString)
        assertEquals("a | String", TypePrinter.print(v))
    }

    @Test
    fun printVarWithBothBounds() {
        val v = TVar()
        v.lowerBounds.add(TString)
        v.upperBounds.add(TTop)
        // TTop is filtered out
        assertEquals("a | String", TypePrinter.print(v))
    }

    @Test
    fun printVarWithMultipleUpperBounds() {
        val v = TVar()
        v.upperBounds.add(TInt)
        v.upperBounds.add(TString)
        // Sorted alphabetically
        assertEquals("a & Int & String", TypePrinter.print(v))
    }

    @Test
    fun printFunctionWithBoundedVar() {
        val v = TVar()
        v.upperBounds.add(TInt)
        val fn = TFun(listOf(v), TInt)
        assertEquals("a & Int -> Int", TypePrinter.print(fn))
    }
}
