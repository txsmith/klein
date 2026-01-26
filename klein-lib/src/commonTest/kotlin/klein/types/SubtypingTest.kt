package klein.types

import klein.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtypingTest {
    private fun subtype(): Subtyping = Subtyping(TypeEnv.empty())

    @Test
    fun intSubtypeOfInt() {
        val sub = subtype()
        sub.constrain(SimpleType.TNum, SimpleType.TNum, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun intNotSubtypeOfString() {
        val sub = subtype()
        sub.constrain(SimpleType.TNum, SimpleType.TString, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    @Test
    fun doubleSubtypeOfDouble() {
        val sub = subtype()
        sub.constrain(SimpleType.TNum, SimpleType.TNum, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun stringSubtypeOfString() {
        val sub = subtype()
        sub.constrain(SimpleType.TString, SimpleType.TString, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun boolSubtypeOfBool() {
        val sub = subtype()
        sub.constrain(SimpleType.TBool, SimpleType.TBool, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun unitSubtypeOfUnit() {
        val sub = subtype()
        sub.constrain(SimpleType.TUnit, SimpleType.TUnit, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun varGetsUpperBound() {
        val sub = subtype()
        val v = SimpleType.TVar()
        sub.constrain(v, SimpleType.TNum, SourceSpan.zero)
        assertTrue(SimpleType.TNum in v.upperBounds)
    }

    @Test
    fun varGetsLowerBound() {
        val sub = subtype()
        val v = SimpleType.TVar()
        sub.constrain(SimpleType.TNum, v, SourceSpan.zero)
        assertTrue(SimpleType.TNum in v.lowerBounds)
    }

    @Test
    fun varBoundsPropagateUp() {
        val sub = subtype()
        val v = SimpleType.TVar()
        sub.constrain(SimpleType.TNum, v, SourceSpan.zero)
        sub.constrain(v, SimpleType.TString, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    @Test
    fun varBoundsPropagateDown() {
        val sub = subtype()
        val v = SimpleType.TVar()
        sub.constrain(v, SimpleType.TNum, SourceSpan.zero)
        sub.constrain(SimpleType.TString, v, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    @Test
    fun varSubtypeOfItself() {
        val sub = subtype()
        val v = SimpleType.TVar()
        sub.constrain(v, v, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(v.upperBounds.isEmpty())
        assertTrue(v.lowerBounds.isEmpty())
    }

    @Test
    fun functionSubtypingCovariantResult() {
        // (Num) -> Num <: (Num) -> a  (where a gets Num as lower bound)
        val sub = subtype()
        val resultVar = SimpleType.TVar()
        val f1 = SimpleType.TFun(listOf(SimpleType.TNum), SimpleType.TNum)
        val f2 = SimpleType.TFun(listOf(SimpleType.TNum), resultVar)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(SimpleType.TNum in resultVar.lowerBounds)
    }

    @Test
    fun functionSubtypingContravariantParam() {
        // (a) -> Num <: (Num) -> Num
        // For f1 <: f2: f2.param <: f1.param (contravariant)
        // So: Num <: a (a gets Num as lower bound)
        val sub = subtype()
        val paramVar = SimpleType.TVar()
        val f1 = SimpleType.TFun(listOf(paramVar), SimpleType.TNum)
        val f2 = SimpleType.TFun(listOf(SimpleType.TNum), SimpleType.TNum)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(SimpleType.TNum in paramVar.lowerBounds)
    }

    @Test
    fun functionSubtypingBothVariances() {
        // (a) -> Num <: (Num) -> b
        // For f1 <: f2: f2.param <: f1.param (contravariant), f1.result <: f2.result (covariant)
        // So: Num <: a (a gets Num as lower bound), Num <: b (b gets Num as lower bound)
        val sub = subtype()
        val paramVar = SimpleType.TVar()
        val resultVar = SimpleType.TVar()
        val f1 = SimpleType.TFun(listOf(paramVar), SimpleType.TNum)
        val f2 = SimpleType.TFun(listOf(SimpleType.TNum), resultVar)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(SimpleType.TNum in paramVar.lowerBounds)
        assertTrue(SimpleType.TNum in resultVar.lowerBounds)
    }

    @Test
    fun functionArityMismatch() {
        val sub = subtype()
        val f1 = SimpleType.TFun(listOf(SimpleType.TNum), SimpleType.TNum)
        val f2 = SimpleType.TFun(listOf(SimpleType.TNum, SimpleType.TNum), SimpleType.TNum)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.ArityMismatch)
    }

    @Test
    fun functionArityMismatch_zeroToOne() {
        val sub = subtype()
        val f1 = SimpleType.TFun(emptyList(), SimpleType.TNum)
        val f2 = SimpleType.TFun(listOf(SimpleType.TNum), SimpleType.TNum)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.ArityMismatch)
    }

    @Test
    fun functionSameTypeSubtypes() {
        val sub = subtype()
        val f1 = SimpleType.TFun(listOf(SimpleType.TNum, SimpleType.TString), SimpleType.TBool)
        val f2 = SimpleType.TFun(listOf(SimpleType.TNum, SimpleType.TString), SimpleType.TBool)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun functionNotSubtypeOfPrimitive() {
        val sub = subtype()
        val f = SimpleType.TFun(listOf(SimpleType.TNum), SimpleType.TNum)
        sub.constrain(f, SimpleType.TNum, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    @Test
    fun functionWithTypeVarParam() {
        val sub = subtype()
        val v = SimpleType.TVar()
        val f1 = SimpleType.TFun(listOf(v), SimpleType.TNum)
        val f2 = SimpleType.TFun(listOf(SimpleType.TNum), SimpleType.TNum)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(SimpleType.TNum in v.lowerBounds)
    }

    @Test
    fun functionWithTypeVarResult() {
        val sub = subtype()
        val v = SimpleType.TVar()
        val f1 = SimpleType.TFun(listOf(SimpleType.TNum), v)
        val f2 = SimpleType.TFun(listOf(SimpleType.TNum), SimpleType.TNum)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(SimpleType.TNum in v.upperBounds)
    }

    @Test
    fun recordWithMoreFieldsIsSubtype() {
        val sub = subtype()
        val wider = SimpleType.TRecord(mapOf("a" to SimpleType.TNum, "b" to SimpleType.TString))
        val narrower = SimpleType.TRecord(mapOf("a" to SimpleType.TNum))
        sub.constrain(wider, narrower, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun recordMissingField() {
        val sub = subtype()
        val narrower = SimpleType.TRecord(mapOf("a" to SimpleType.TNum))
        val wider = SimpleType.TRecord(mapOf("a" to SimpleType.TNum, "b" to SimpleType.TString))
        sub.constrain(narrower, wider, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.MissingField)
    }

    @Test
    fun recordFieldTypeMismatch() {
        val sub = subtype()
        val r1 = SimpleType.TRecord(mapOf("a" to SimpleType.TNum))
        val r2 = SimpleType.TRecord(mapOf("a" to SimpleType.TString))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    @Test
    fun emptyRecordIsSupertype() {
        val sub = subtype()
        val rec = SimpleType.TRecord(mapOf("a" to SimpleType.TNum))
        val empty = SimpleType.TRecord(emptyMap())
        sub.constrain(rec, empty, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun emptyRecordSubtypeOfEmptyRecord() {
        val sub = subtype()
        val empty1 = SimpleType.TRecord(emptyMap())
        val empty2 = SimpleType.TRecord(emptyMap())
        sub.constrain(empty1, empty2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun recordNotSubtypeOfPrimitive() {
        val sub = subtype()
        val rec = SimpleType.TRecord(mapOf("a" to SimpleType.TNum))
        sub.constrain(rec, SimpleType.TNum, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    @Test
    fun recordWithTypeVarField() {
        val sub = subtype()
        val v = SimpleType.TVar()
        val r1 = SimpleType.TRecord(mapOf("a" to v))
        val r2 = SimpleType.TRecord(mapOf("a" to SimpleType.TNum))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(SimpleType.TNum in v.upperBounds)
    }

    @Test
    fun nestedRecordSubtyping() {
        val sub = subtype()
        val innerWide = SimpleType.TRecord(mapOf("a" to SimpleType.TNum, "b" to SimpleType.TString))
        val innerNarrow = SimpleType.TRecord(mapOf("a" to SimpleType.TNum))
        val outerWide = SimpleType.TRecord(mapOf("inner" to innerWide))
        val outerNarrow = SimpleType.TRecord(mapOf("inner" to innerNarrow))
        sub.constrain(outerWide, outerNarrow, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun functionReturningRecordSubtyping() {
        val sub = subtype()
        val wideRec = SimpleType.TRecord(mapOf("a" to SimpleType.TNum, "b" to SimpleType.TString))
        val narrowRec = SimpleType.TRecord(mapOf("a" to SimpleType.TNum))
        val f1 = SimpleType.TFun(listOf(SimpleType.TNum), wideRec)
        val f2 = SimpleType.TFun(listOf(SimpleType.TNum), narrowRec)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun functionAcceptingRecordSubtyping() {
        val sub = subtype()
        val narrowRec = SimpleType.TRecord(mapOf("a" to SimpleType.TNum))
        val wideRec = SimpleType.TRecord(mapOf("a" to SimpleType.TNum, "b" to SimpleType.TString))
        val f1 = SimpleType.TFun(listOf(narrowRec), SimpleType.TNum)
        val f2 = SimpleType.TFun(listOf(wideRec), SimpleType.TNum)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nestedFunctionSubtyping() {
        // ((Num) -> Num) -> Num <: ((Num) -> String) -> Num should fail
        // Because the param is contravariant, inner1 needs to be supertype of inner2
        // But (Num) -> Num is not a supertype of (Num) -> String
        val sub = subtype()
        val inner1 = SimpleType.TFun(listOf(SimpleType.TNum), SimpleType.TNum)
        val inner2 = SimpleType.TFun(listOf(SimpleType.TNum), SimpleType.TString)
        val f1 = SimpleType.TFun(listOf(inner1), SimpleType.TNum)
        val f2 = SimpleType.TFun(listOf(inner2), SimpleType.TNum)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    @Test
    fun constrainEqualSameType() {
        val sub = subtype()
        sub.constrainEqual(SimpleType.TNum, SimpleType.TNum, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun constrainEqualDifferentTypes() {
        val sub = subtype()
        sub.constrainEqual(SimpleType.TNum, SimpleType.TString, SourceSpan.zero)
        assertEquals(2, sub.getErrors().size)
    }

    @Test
    fun constrainEqualTypeVar() {
        val sub = subtype()
        val v = SimpleType.TVar()
        sub.constrainEqual(v, SimpleType.TNum, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(SimpleType.TNum in v.upperBounds)
        assertTrue(SimpleType.TNum in v.lowerBounds)
    }

    @Test
    fun constrainEqualRecordsWithWidthDifference() {
        val sub = subtype()
        val r1 = SimpleType.TRecord(mapOf("a" to SimpleType.TNum))
        val r2 = SimpleType.TRecord(mapOf("a" to SimpleType.TNum, "b" to SimpleType.TString))
        sub.constrainEqual(r1, r2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.MissingField)
    }

    @Test
    fun functionWithIndependentParams_subtypeOfSameParamVar() {
        // (Any, Any) -> Num <: ('A, 'A) -> Num
        // A function that accepts any two arguments should be usable
        // where a function expecting same-typed arguments is needed.
        // This models: fun wide(a, b) = 1; restrict(wide)
        // where restrict expects ('A, 'A) -> Num
        val sub = subtype()
        val a = SimpleType.TVar()
        val b = SimpleType.TVar()
        val c = SimpleType.TVar()
        val wide = SimpleType.TFun(listOf(a, b), SimpleType.TNum)
        val restricted = SimpleType.TFun(listOf(c, c), SimpleType.TNum)
        sub.constrain(wide, restricted, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }
}
