package klein.types

import klein.Type
import klein.TypePrinter
import kotlin.test.Test
import kotlin.test.assertEquals

class TypePrinterTest {
    // ========== Primitive Types ==========

    @Test
    fun printInt() {
        assertEquals("Int", TypePrinter.print(Type.TInt))
    }

    @Test
    fun printDouble() {
        assertEquals("Double", TypePrinter.print(Type.TDouble))
    }

    @Test
    fun printString() {
        assertEquals("String", TypePrinter.print(Type.TString))
    }

    @Test
    fun printBool() {
        assertEquals("Bool", TypePrinter.print(Type.TBool))
    }

    @Test
    fun printUnit() {
        assertEquals("Unit", TypePrinter.print(Type.TUnit))
    }

    // ========== Type Variables ==========

    @Test
    fun printTypeVarA() {
        val v = Type.TVar(0)
        assertEquals("a", TypePrinter.print(v))
    }

    @Test
    fun printTypeVarB() {
        val v = Type.TVar(1)
        assertEquals("b", TypePrinter.print(v))
    }

    @Test
    fun printTypeVarC() {
        val v = Type.TVar(2)
        assertEquals("c", TypePrinter.print(v))
    }

    @Test
    fun printTypeVarZ() {
        val v = Type.TVar(25)
        assertEquals("z", TypePrinter.print(v))
    }

    @Test
    fun printTypeVarA1() {
        // After z (25), it wraps to a1
        val v = Type.TVar(26)
        assertEquals("a1", TypePrinter.print(v))
    }

    @Test
    fun printTypeVarB1() {
        val v = Type.TVar(27)
        assertEquals("b1", TypePrinter.print(v))
    }

    @Test
    fun printTypeVarZ1() {
        val v = Type.TVar(51)
        assertEquals("z1", TypePrinter.print(v))
    }

    @Test
    fun printTypeVarA2() {
        val v = Type.TVar(52)
        assertEquals("a2", TypePrinter.print(v))
    }

    @Test
    fun typeVarWithoutQuotes() {
        // Ensure type variables are printed without quote prefix
        val v = Type.TVar(0)
        val printed = TypePrinter.print(v)
        assertEquals(false, printed.startsWith("'"), "Type variable should not start with quote")
        assertEquals("a", printed)
    }

    // ========== Top and Bottom ==========

    @Test
    fun printTop() {
        assertEquals("Top", TypePrinter.print(Type.TTop))
    }

    @Test
    fun printBottom() {
        assertEquals("Bottom", TypePrinter.print(Type.TBottom))
    }

    // ========== Function Types - Single Parameter ==========

    @Test
    fun printFunctionIntToInt() {
        val fn = Type.TFun(listOf(Type.TInt), Type.TInt)
        assertEquals("Int -> Int", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionIntToString() {
        val fn = Type.TFun(listOf(Type.TInt), Type.TString)
        assertEquals("Int -> String", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionBoolToBool() {
        val fn = Type.TFun(listOf(Type.TBool), Type.TBool)
        assertEquals("Bool -> Bool", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionVarToVar() {
        val v = Type.TVar(0)
        val fn = Type.TFun(listOf(v), v)
        assertEquals("a -> a", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionVarToInt() {
        val v = Type.TVar(0)
        val fn = Type.TFun(listOf(v), Type.TInt)
        assertEquals("a -> Int", TypePrinter.print(fn))
    }

    // ========== Function Types - Multiple Parameters ==========

    @Test
    fun printFunctionTwoParams() {
        val fn = Type.TFun(listOf(Type.TInt, Type.TInt), Type.TInt)
        assertEquals("(Int, Int) -> Int", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionThreeParams() {
        val fn = Type.TFun(listOf(Type.TInt, Type.TString, Type.TBool), Type.TDouble)
        assertEquals("(Int, String, Bool) -> Double", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionMixedTypeVarsAndPrimitives() {
        val a = Type.TVar(0)
        val b = Type.TVar(1)
        val fn = Type.TFun(listOf(a, Type.TInt), b)
        assertEquals("(a, Int) -> b", TypePrinter.print(fn))
    }

    // ========== Function Types - Zero Parameters (Thunks) ==========

    @Test
    fun printThunkInt() {
        val fn = Type.TFun(emptyList(), Type.TInt)
        assertEquals("() -> Int", TypePrinter.print(fn))
    }

    @Test
    fun printThunkString() {
        val fn = Type.TFun(emptyList(), Type.TString)
        assertEquals("() -> String", TypePrinter.print(fn))
    }

    @Test
    fun printThunkVar() {
        val v = Type.TVar(0)
        val fn = Type.TFun(emptyList(), v)
        assertEquals("() -> a", TypePrinter.print(fn))
    }

    // ========== Function Types - Nested/Higher-Order ==========

    @Test
    fun printFunctionReturningFunction() {
        // Int -> Int -> Int (curried)
        val inner = Type.TFun(listOf(Type.TInt), Type.TInt)
        val outer = Type.TFun(listOf(Type.TInt), inner)
        assertEquals("Int -> Int -> Int", TypePrinter.print(outer))
    }

    @Test
    fun printFunctionTakingFunction() {
        // (Int -> Int) -> Int
        val paramFn = Type.TFun(listOf(Type.TInt), Type.TInt)
        val outer = Type.TFun(listOf(paramFn), Type.TInt)
        assertEquals("(Int -> Int) -> Int", TypePrinter.print(outer))
    }

    @Test
    fun printHigherOrderWithMultipleParams() {
        // (a -> b, a) -> b
        val a = Type.TVar(0)
        val b = Type.TVar(1)
        val paramFn = Type.TFun(listOf(a), b)
        val outer = Type.TFun(listOf(paramFn, a), b)
        assertEquals("((a -> b), a) -> b", TypePrinter.print(outer))
    }

    @Test
    fun printDoublyCurried() {
        // a -> b -> c -> a
        val a = Type.TVar(0)
        val b = Type.TVar(1)
        val c = Type.TVar(2)
        val fn = Type.TFun(listOf(a), Type.TFun(listOf(b), Type.TFun(listOf(c), a)))
        assertEquals("a -> b -> c -> a", TypePrinter.print(fn))
    }

    // ========== Record Types ==========

    @Test
    fun printEmptyRecord() {
        val rec = Type.TRecord(emptyMap())
        assertEquals("{}", TypePrinter.print(rec))
    }

    @Test
    fun printRecordOneField() {
        val rec = Type.TRecord(mapOf("a" to Type.TInt))
        assertEquals("{ a: Int }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordTwoFields() {
        val rec = Type.TRecord(mapOf("name" to Type.TString, "age" to Type.TInt))
        // Fields are sorted alphabetically
        assertEquals("{ age: Int, name: String }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordThreeFields() {
        val rec = Type.TRecord(mapOf("x" to Type.TInt, "y" to Type.TInt, "z" to Type.TInt))
        assertEquals("{ x: Int, y: Int, z: Int }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordWithVarField() {
        val a = Type.TVar(0)
        val rec = Type.TRecord(mapOf("value" to a))
        assertEquals("{ value: a }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordWithFunctionField() {
        val fn = Type.TFun(listOf(Type.TInt), Type.TInt)
        val rec = Type.TRecord(mapOf("f" to fn))
        assertEquals("{ f: Int -> Int }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordWithMixedTypes() {
        val rec = Type.TRecord(
            mapOf(
                "i" to Type.TInt,
                "d" to Type.TDouble,
                "s" to Type.TString,
                "b" to Type.TBool,
            ),
        )
        // Fields are sorted alphabetically: b, d, i, s
        assertEquals("{ b: Bool, d: Double, i: Int, s: String }", TypePrinter.print(rec))
    }

    // ========== Nested Records ==========

    @Test
    fun printNestedRecord() {
        val inner = Type.TRecord(mapOf("x" to Type.TInt))
        val outer = Type.TRecord(mapOf("inner" to inner))
        assertEquals("{ inner: { x: Int } }", TypePrinter.print(outer))
    }

    @Test
    fun printDeeplyNestedRecord() {
        val innermost = Type.TRecord(mapOf("c" to Type.TInt))
        val middle = Type.TRecord(mapOf("b" to innermost))
        val outer = Type.TRecord(mapOf("a" to middle))
        assertEquals("{ a: { b: { c: Int } } }", TypePrinter.print(outer))
    }

    // ========== Complex Combinations ==========

    @Test
    fun printFunctionReturningRecord() {
        val a = Type.TVar(0)
        val rec = Type.TRecord(mapOf("value" to a))
        val fn = Type.TFun(listOf(a), rec)
        assertEquals("a -> { value: a }", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionTakingRecord() {
        val rec = Type.TRecord(mapOf("name" to Type.TString))
        val fn = Type.TFun(listOf(rec), Type.TString)
        assertEquals("{ name: String } -> String", TypePrinter.print(fn))
    }

    @Test
    fun printRecordOfFunctions() {
        val inc = Type.TFun(listOf(Type.TInt), Type.TInt)
        val dec = Type.TFun(listOf(Type.TInt), Type.TInt)
        val rec = Type.TRecord(mapOf("inc" to inc, "dec" to dec))
        // Fields are sorted alphabetically: dec, inc
        assertEquals("{ dec: Int -> Int, inc: Int -> Int }", TypePrinter.print(rec))
    }

    @Test
    fun printComplexPolymorphicFunction() {
        // (a -> b, { x: a, y: a }) -> { x: b, y: b }
        val a = Type.TVar(0)
        val b = Type.TVar(1)
        val paramFn = Type.TFun(listOf(a), b)
        val inputRec = Type.TRecord(mapOf("x" to a, "y" to a))
        val outputRec = Type.TRecord(mapOf("x" to b, "y" to b))
        val fn = Type.TFun(listOf(paramFn, inputRec), outputRec)
        assertEquals("((a -> b), { x: a, y: a }) -> { x: b, y: b }", TypePrinter.print(fn))
    }
}
