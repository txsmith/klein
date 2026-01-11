package klein.types

import klein.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtypingTest {
    private fun subtype(): Pair<Subtyping, MutableList<TypeError>> {
        val errors = mutableListOf<TypeError>()
        return Subtyping { errors.add(it) } to errors
    }

    @Test
    fun intSubtypeOfInt() {
        val (sub, errors) = subtype()
        sub.constrain(SimpleType.TInt, SimpleType.TInt, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun intNotSubtypeOfString() {
        val (sub, errors) = subtype()
        sub.constrain(SimpleType.TInt, SimpleType.TString, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun doubleSubtypeOfDouble() {
        val (sub, errors) = subtype()
        sub.constrain(SimpleType.TDouble, SimpleType.TDouble, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun stringSubtypeOfString() {
        val (sub, errors) = subtype()
        sub.constrain(SimpleType.TString, SimpleType.TString, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun boolSubtypeOfBool() {
        val (sub, errors) = subtype()
        sub.constrain(SimpleType.TBool, SimpleType.TBool, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun unitSubtypeOfUnit() {
        val (sub, errors) = subtype()
        sub.constrain(SimpleType.TUnit, SimpleType.TUnit, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun allPrimitivesSubtypeOfTop() {
        val (sub, errors) = subtype()
        sub.constrain(SimpleType.TInt, SimpleType.TTop, SourceSpan.zero)
        sub.constrain(SimpleType.TString, SimpleType.TTop, SourceSpan.zero)
        sub.constrain(SimpleType.TBool, SimpleType.TTop, SourceSpan.zero)
        sub.constrain(SimpleType.TDouble, SimpleType.TTop, SourceSpan.zero)
        sub.constrain(SimpleType.TUnit, SimpleType.TTop, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun bottomSubtypeOfAllPrimitives() {
        val (sub, errors) = subtype()
        sub.constrain(SimpleType.TBottom, SimpleType.TInt, SourceSpan.zero)
        sub.constrain(SimpleType.TBottom, SimpleType.TString, SourceSpan.zero)
        sub.constrain(SimpleType.TBottom, SimpleType.TBool, SourceSpan.zero)
        sub.constrain(SimpleType.TBottom, SimpleType.TDouble, SourceSpan.zero)
        sub.constrain(SimpleType.TBottom, SimpleType.TUnit, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun bottomSubtypeOfTop() {
        val (sub, errors) = subtype()
        sub.constrain(SimpleType.TBottom, SimpleType.TTop, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun varGetsUpperBound() {
        val (sub, _) = subtype()
        val v = SimpleType.TVar()
        sub.constrain(v, SimpleType.TInt, SourceSpan.zero)
        assertTrue(SimpleType.TInt in v.upperBounds)
    }

    @Test
    fun varGetsLowerBound() {
        val (sub, _) = subtype()
        val v = SimpleType.TVar()
        sub.constrain(SimpleType.TInt, v, SourceSpan.zero)
        assertTrue(SimpleType.TInt in v.lowerBounds)
    }

    @Test
    fun varBoundsPropagateUp() {
        val errors = mutableListOf<TypeError>()
        val sub = Subtyping { errors.add(it) }
        val v = SimpleType.TVar()
        sub.constrain(SimpleType.TInt, v, SourceSpan.zero)
        sub.constrain(v, SimpleType.TString, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun varBoundsPropagateDown() {
        val errors = mutableListOf<TypeError>()
        val sub = Subtyping { errors.add(it) }
        val v = SimpleType.TVar()
        sub.constrain(v, SimpleType.TInt, SourceSpan.zero)
        sub.constrain(SimpleType.TString, v, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun varSubtypeOfItself() {
        val (sub, errors) = subtype()
        val v = SimpleType.TVar()
        sub.constrain(v, v, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        assertTrue(v.upperBounds.isEmpty())
        assertTrue(v.lowerBounds.isEmpty())
    }

    @Test
    fun twoVarsCanRelate() {
        val (sub, errors) = subtype()
        val v1 = SimpleType.TVar()
        val v2 = SimpleType.TVar()
        sub.constrain(v1, v2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        assertTrue(v2 in v1.upperBounds)
        assertTrue(v1 in v2.lowerBounds)
    }

    @Test
    fun varBoundsWithTop() {
        val (sub, errors) = subtype()
        val v = SimpleType.TVar()
        sub.constrain(v, SimpleType.TTop, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun varBoundsWithBottom() {
        val (sub, errors) = subtype()
        val v = SimpleType.TVar()
        sub.constrain(SimpleType.TBottom, v, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionSubtypingCovariantResult() {
        val (sub, errors) = subtype()
        val f1 = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TInt)
        val f2 = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TTop)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionSubtypingContravariantParam() {
        val (sub, errors) = subtype()
        val f1 = SimpleType.TFun(listOf(SimpleType.TTop), SimpleType.TInt)
        val f2 = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionSubtypingBothVariances() {
        val (sub, errors) = subtype()
        val f1 = SimpleType.TFun(listOf(SimpleType.TTop), SimpleType.TInt)
        val f2 = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TTop)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionArityMismatch() {
        val (sub, errors) = subtype()
        val f1 = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TInt)
        val f2 = SimpleType.TFun(listOf(SimpleType.TInt, SimpleType.TInt), SimpleType.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.ArityMismatch)
    }

    @Test
    fun functionArityMismatch_zeroToOne() {
        val (sub, errors) = subtype()
        val f1 = SimpleType.TFun(emptyList(), SimpleType.TInt)
        val f2 = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.ArityMismatch)
    }

    @Test
    fun functionSameTypeSubtypes() {
        val (sub, errors) = subtype()
        val f1 = SimpleType.TFun(listOf(SimpleType.TInt, SimpleType.TString), SimpleType.TBool)
        val f2 = SimpleType.TFun(listOf(SimpleType.TInt, SimpleType.TString), SimpleType.TBool)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionNotSubtypeOfPrimitive() {
        val (sub, errors) = subtype()
        val f = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TInt)
        sub.constrain(f, SimpleType.TInt, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun functionWithTypeVarParam() {
        val (sub, errors) = subtype()
        val v = SimpleType.TVar()
        val f1 = SimpleType.TFun(listOf(v), SimpleType.TInt)
        val f2 = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        assertTrue(SimpleType.TInt in v.lowerBounds)
    }

    @Test
    fun functionWithTypeVarResult() {
        val (sub, errors) = subtype()
        val v = SimpleType.TVar()
        val f1 = SimpleType.TFun(listOf(SimpleType.TInt), v)
        val f2 = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        assertTrue(SimpleType.TInt in v.upperBounds)
    }

    @Test
    fun recordWithMoreFieldsIsSubtype() {
        val (sub, errors) = subtype()
        val wider = SimpleType.TRecord(mapOf("a" to SimpleType.TInt, "b" to SimpleType.TString))
        val narrower = SimpleType.TRecord(mapOf("a" to SimpleType.TInt))
        sub.constrain(wider, narrower, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun recordMissingField() {
        val (sub, errors) = subtype()
        val narrower = SimpleType.TRecord(mapOf("a" to SimpleType.TInt))
        val wider = SimpleType.TRecord(mapOf("a" to SimpleType.TInt, "b" to SimpleType.TString))
        sub.constrain(narrower, wider, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }

    @Test
    fun recordFieldTypeMismatch() {
        val (sub, errors) = subtype()
        val r1 = SimpleType.TRecord(mapOf("a" to SimpleType.TInt))
        val r2 = SimpleType.TRecord(mapOf("a" to SimpleType.TString))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun emptyRecordIsSupertype() {
        val (sub, errors) = subtype()
        val rec = SimpleType.TRecord(mapOf("a" to SimpleType.TInt))
        val empty = SimpleType.TRecord(emptyMap())
        sub.constrain(rec, empty, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun emptyRecordSubtypeOfEmptyRecord() {
        val (sub, errors) = subtype()
        val empty1 = SimpleType.TRecord(emptyMap())
        val empty2 = SimpleType.TRecord(emptyMap())
        sub.constrain(empty1, empty2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun recordNotSubtypeOfPrimitive() {
        val (sub, errors) = subtype()
        val rec = SimpleType.TRecord(mapOf("a" to SimpleType.TInt))
        sub.constrain(rec, SimpleType.TInt, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun recordWithTypeVarField() {
        val (sub, errors) = subtype()
        val v = SimpleType.TVar()
        val r1 = SimpleType.TRecord(mapOf("a" to v))
        val r2 = SimpleType.TRecord(mapOf("a" to SimpleType.TInt))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        assertTrue(SimpleType.TInt in v.upperBounds)
    }

    @Test
    fun nestedRecordSubtyping() {
        val (sub, errors) = subtype()
        val innerWide = SimpleType.TRecord(mapOf("a" to SimpleType.TInt, "b" to SimpleType.TString))
        val innerNarrow = SimpleType.TRecord(mapOf("a" to SimpleType.TInt))
        val outerWide = SimpleType.TRecord(mapOf("inner" to innerWide))
        val outerNarrow = SimpleType.TRecord(mapOf("inner" to innerNarrow))
        sub.constrain(outerWide, outerNarrow, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionReturningRecordSubtyping() {
        val (sub, errors) = subtype()
        val wideRec = SimpleType.TRecord(mapOf("a" to SimpleType.TInt, "b" to SimpleType.TString))
        val narrowRec = SimpleType.TRecord(mapOf("a" to SimpleType.TInt))
        val f1 = SimpleType.TFun(listOf(SimpleType.TInt), wideRec)
        val f2 = SimpleType.TFun(listOf(SimpleType.TInt), narrowRec)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionAcceptingRecordSubtyping() {
        val (sub, errors) = subtype()
        val narrowRec = SimpleType.TRecord(mapOf("a" to SimpleType.TInt))
        val wideRec = SimpleType.TRecord(mapOf("a" to SimpleType.TInt, "b" to SimpleType.TString))
        val f1 = SimpleType.TFun(listOf(narrowRec), SimpleType.TInt)
        val f2 = SimpleType.TFun(listOf(wideRec), SimpleType.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun nestedFunctionSubtyping() {
        val (sub, errors) = subtype()
        val inner1 = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TInt)
        val inner2 = SimpleType.TFun(listOf(SimpleType.TInt), SimpleType.TTop)
        val f1 = SimpleType.TFun(listOf(inner1), SimpleType.TInt)
        val f2 = SimpleType.TFun(listOf(inner2), SimpleType.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun constrainEqualSameType() {
        val (sub, errors) = subtype()
        sub.constrainEqual(SimpleType.TInt, SimpleType.TInt, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun constrainEqualDifferentTypes() {
        val (sub, errors) = subtype()
        sub.constrainEqual(SimpleType.TInt, SimpleType.TString, SourceSpan.zero)
        assertEquals(2, errors.size)
    }

    @Test
    fun constrainEqualTypeVar() {
        val (sub, errors) = subtype()
        val v = SimpleType.TVar()
        sub.constrainEqual(v, SimpleType.TInt, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        assertTrue(SimpleType.TInt in v.upperBounds)
        assertTrue(SimpleType.TInt in v.lowerBounds)
    }

    @Test
    fun constrainEqualRecordsWithWidthDifference() {
        val (sub, errors) = subtype()
        val r1 = SimpleType.TRecord(mapOf("a" to SimpleType.TInt))
        val r2 = SimpleType.TRecord(mapOf("a" to SimpleType.TInt, "b" to SimpleType.TString))
        sub.constrainEqual(r1, r2, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }
}
