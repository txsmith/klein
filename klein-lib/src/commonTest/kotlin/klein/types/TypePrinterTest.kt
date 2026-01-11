package klein.types

import klein.types.SimpleType.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TypePrinterTest {
    @Test
    fun printInt() {
        assertEquals("Num", TypePrinter.print(TNum))
    }

    @Test
    fun printNum() {
        assertEquals("Num", TypePrinter.print(TNum))
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
    fun printTypeVar_positiveOnly_simplifiesToNothing() {
        val v = TVar()
        assertEquals("Nothing", TypePrinter.print(v))
    }

    @Test
    fun printTypeVar_inBothPositions_keptAsVariable() {
        val v = TVar()
        val fn = TFun(listOf(v), v)
        assertEquals("(a) -> a", TypePrinter.print(fn))
    }

    @Test
    fun printTypeVar_differentPositions_simplifiedSeparately() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a), b)
        // a is negative-only -> Any, b is positive-only -> Nothing
        assertEquals("(Any) -> Nothing", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionIntToInt() {
        val fn = TFun(listOf(TNum), TNum)
        assertEquals("(Num) -> Num", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionIntToString() {
        val fn = TFun(listOf(TNum), TString)
        assertEquals("(Num) -> String", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionBoolToBool() {
        val fn = TFun(listOf(TBool), TBool)
        assertEquals("(Bool) -> Bool", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionVarToVar() {
        val v = TVar()
        val fn = TFun(listOf(v), v)
        assertEquals("(a) -> a", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionVarToInt() {
        val v = TVar()
        val fn = TFun(listOf(v), TNum)
        // v is negative-only -> Any
        assertEquals("(Any) -> Num", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionTwoParams() {
        val fn = TFun(listOf(TNum, TNum), TNum)
        assertEquals("(Num, Num) -> Num", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionThreeParams() {
        val fn = TFun(listOf(TNum, TString, TBool), TNum)
        assertEquals("(Num, String, Bool) -> Num", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionMixedTypeVarsAndPrimitives() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a, TNum), b)
        // a negative-only -> Any, b positive-only -> Nothing
        assertEquals("(Any, Num) -> Nothing", TypePrinter.print(fn))
    }

    @Test
    fun printThunkInt() {
        val fn = TFun(emptyList(), TNum)
        assertEquals("() -> Num", TypePrinter.print(fn))
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
        // v is positive-only -> Nothing
        assertEquals("() -> Nothing", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionReturningFunction() {
        val inner = TFun(listOf(TNum), TNum)
        val outer = TFun(listOf(TNum), inner)
        assertEquals("(Num) -> (Num) -> Num", TypePrinter.print(outer))
    }

    @Test
    fun printFunctionTakingFunction() {
        val function = TFun(listOf(TFun(listOf(TNum), TNum)), TNum)
        assertEquals("((Num) -> Num) -> Num", TypePrinter.print(function))
    }

    @Test
    fun printHigherOrderWithMultipleParams() {
        // ((a) -> b, a) -> b
        val a = TVar()
        val b = TVar()
        val outer = TFun(listOf(TFun(listOf(a), b), a), b)
        // a: negative in outer param, positive in inner fn param -> both positions -> a
        // b: positive in inner fn result, positive in outer result -> positive only -> Nothing
        // Actually: inner fn is in negative position, so its param is positive, result is negative
        // So a: outer negative + inner positive = both -> a
        // b: inner negative + outer positive = both -> b
        assertEquals("((a) -> b, a) -> b", TypePrinter.print(outer))
    }

    @Test
    fun printDoublyCurried() {
        // (a) -> (b) -> (c) -> a
        val a = TVar()
        val b = TVar()
        val c = TVar()
        val fn = TFun(listOf(a), TFun(listOf(b), TFun(listOf(c), a)))
        // a: negative in outer, positive in innermost result -> both -> a
        // b: negative only (param of middle fn which is in positive position) -> Any
        // c: negative only (param of inner fn which is in positive position) -> Any
        assertEquals("(a) -> (Any) -> (Any) -> a", TypePrinter.print(fn))
    }

    @Test
    fun printEmptyRecord() {
        val rec = TRecord(emptyMap())
        assertEquals("{}", TypePrinter.print(rec))
    }

    @Test
    fun printRecordOneField() {
        val rec = TRecord(mapOf("a" to TNum))
        assertEquals("{ a: Num }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordTwoFields() {
        val rec = TRecord(mapOf("name" to TString, "age" to TNum))
        assertEquals("{ age: Num, name: String }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordThreeFields() {
        val rec = TRecord(mapOf("x" to TNum, "y" to TNum, "z" to TNum))
        assertEquals("{ x: Num, y: Num, z: Num }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordWithVarField() {
        val a = TVar()
        val rec = TRecord(mapOf("value" to a))
        // a is positive-only -> Nothing
        assertEquals("{ value: Nothing }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordWithFunctionField() {
        val fn = TFun(listOf(TNum), TNum)
        val rec = TRecord(mapOf("f" to fn))
        assertEquals("{ f: (Num) -> Num }", TypePrinter.print(rec))
    }

    @Test
    fun printRecordWithMixedTypes() {
        val rec =
            TRecord(
                mapOf(
                    "i" to TNum,
                    "d" to TNum,
                    "s" to TString,
                    "b" to TBool,
                ),
            )
        assertEquals("{ b: Bool, d: Num, i: Num, s: String }", TypePrinter.print(rec))
    }

    @Test
    fun printNestedRecord() {
        val inner = TRecord(mapOf("x" to TNum))
        val outer = TRecord(mapOf("inner" to inner))
        assertEquals("{ inner: { x: Num } }", TypePrinter.print(outer))
    }

    @Test
    fun printDeeplyNestedRecord() {
        val innermost = TRecord(mapOf("c" to TNum))
        val middle = TRecord(mapOf("b" to innermost))
        val outer = TRecord(mapOf("a" to middle))
        assertEquals("{ a: { b: { c: Num } } }", TypePrinter.print(outer))
    }

    @Test
    fun printFunctionReturningRecord() {
        val a = TVar()
        val rec = TRecord(mapOf("value" to a))
        val fn = TFun(listOf(a), rec)
        // a in both positions -> a
        assertEquals("(a) -> { value: a }", TypePrinter.print(fn))
    }

    @Test
    fun printFunctionTakingRecord() {
        val rec = TRecord(mapOf("name" to TString))
        val fn = TFun(listOf(rec), TString)
        assertEquals("({ name: String }) -> String", TypePrinter.print(fn))
    }

    @Test
    fun printRecordOfFunctions() {
        val inc = TFun(listOf(TNum), TNum)
        val dec = TFun(listOf(TNum), TNum)
        val rec = TRecord(mapOf("inc" to inc, "dec" to dec))
        assertEquals("{ dec: (Num) -> Num, inc: (Num) -> Num }", TypePrinter.print(rec))
    }

    @Test
    fun printComplexPolymorphicFunction() {
        // ((a) -> b, { x: a, y: a }) -> { x: b, y: b }
        val a = TVar()
        val b = TVar()
        val paramFn = TFun(listOf(a), b)
        val inputRec = TRecord(mapOf("x" to a, "y" to a))
        val outputRec = TRecord(mapOf("x" to b, "y" to b))
        val fn = TFun(listOf(paramFn, inputRec), outputRec)
        // a: in paramFn param (neg of neg = pos), in inputRec (neg) -> both -> a
        // b: in paramFn result (neg), in outputRec (pos) -> both -> b
        assertEquals("((a) -> b, { x: a, y: a }) -> { x: b, y: b }", TypePrinter.print(fn))
    }

    @Test
    fun printVarWithUpperBound_positiveOnly() {
        val v = TVar()
        v.upperBounds.add(TNum)
        // Positive-only var simplifies based on lower bounds, nothing here -> Nothing
        assertEquals("Nothing", TypePrinter.print(v))
    }

    @Test
    fun printVarWithLowerBound_positiveOnly() {
        val v = TVar()
        v.lowerBounds.add(TString)
        // Positive-only var with String lower bound simplifies to String
        assertEquals("String", TypePrinter.print(v))
    }

    @Test
    fun printVarWithBothBounds_positiveOnly() {
        val v = TVar()
        v.lowerBounds.add(TString)
        v.upperBounds.add(TNum)
        // Positive-only var with String lower bound and Num upper bound
        assertEquals("String", TypePrinter.print(v))
    }

    @Test
    fun printVarWithMultipleUpperBounds_positiveOnly() {
        val v = TVar()
        v.upperBounds.add(TNum)
        v.upperBounds.add(TString)
        // Positive-only var with no lower bounds simplifies to Nothing
        assertEquals("Nothing", TypePrinter.print(v))
    }

    @Test
    fun printFunctionWithBoundedVar() {
        val v = TVar()
        v.upperBounds.add(TNum)
        val fn = TFun(listOf(v), TNum)
        // v is negative-only with upper bound Num -> simplifies to Num
        assertEquals("(Num) -> Num", TypePrinter.print(fn))
    }

    @Test
    fun printRaw_preservesTypeVariables() {
        val a = TVar()
        val b = TVar()
        val fn = TFun(listOf(a), b)
        assertEquals("(a) -> b", TypePrinter.printRaw(fn))
    }

    @Test
    fun printRaw_preservesBounds() {
        val v = TVar()
        v.upperBounds.add(TNum)
        val fn = TFun(listOf(v), TNum)
        assertEquals(
            """
            |(a) -> Num
            |  where
            |    a <: Num
            """.trimMargin(),
            TypePrinter.printRaw(fn),
        )
    }
}
