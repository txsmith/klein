package klein.types

import klein.SourceSpan
import klein.Subtyping
import klein.Type
import klein.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtypingTest {
    private fun subtype(): Pair<Subtyping, MutableList<TypeError>> {
        val errors = mutableListOf<TypeError>()
        return Subtyping { errors.add(it) } to errors
    }

    // ========== Primitive Subtyping ==========

    @Test
    fun intSubtypeOfInt() {
        val (sub, errors) = subtype()
        sub.constrain(Type.TInt, Type.TInt, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun intNotSubtypeOfString() {
        val (sub, errors) = subtype()
        sub.constrain(Type.TInt, Type.TString, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun doubleSubtypeOfDouble() {
        val (sub, errors) = subtype()
        sub.constrain(Type.TDouble, Type.TDouble, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun stringSubtypeOfString() {
        val (sub, errors) = subtype()
        sub.constrain(Type.TString, Type.TString, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun boolSubtypeOfBool() {
        val (sub, errors) = subtype()
        sub.constrain(Type.TBool, Type.TBool, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun unitSubtypeOfUnit() {
        val (sub, errors) = subtype()
        sub.constrain(Type.TUnit, Type.TUnit, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun allPrimitivesSubtypeOfTop() {
        val (sub, errors) = subtype()
        sub.constrain(Type.TInt, Type.TTop, SourceSpan.zero)
        sub.constrain(Type.TString, Type.TTop, SourceSpan.zero)
        sub.constrain(Type.TBool, Type.TTop, SourceSpan.zero)
        sub.constrain(Type.TDouble, Type.TTop, SourceSpan.zero)
        sub.constrain(Type.TUnit, Type.TTop, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun bottomSubtypeOfAllPrimitives() {
        val (sub, errors) = subtype()
        sub.constrain(Type.TBottom, Type.TInt, SourceSpan.zero)
        sub.constrain(Type.TBottom, Type.TString, SourceSpan.zero)
        sub.constrain(Type.TBottom, Type.TBool, SourceSpan.zero)
        sub.constrain(Type.TBottom, Type.TDouble, SourceSpan.zero)
        sub.constrain(Type.TBottom, Type.TUnit, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun bottomSubtypeOfTop() {
        val (sub, errors) = subtype()
        sub.constrain(Type.TBottom, Type.TTop, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    // ========== Type Variable Bounds ==========

    @Test
    fun varGetsUpperBound() {
        val (sub, _) = subtype()
        val v = Type.TVar(0)
        sub.constrain(v, Type.TInt, SourceSpan.zero)
        assertTrue(Type.TInt in v.upperBounds)
    }

    @Test
    fun varGetsLowerBound() {
        val (sub, _) = subtype()
        val v = Type.TVar(0)
        sub.constrain(Type.TInt, v, SourceSpan.zero)
        assertTrue(Type.TInt in v.lowerBounds)
    }

    @Test
    fun varBoundsPropagateUp() {
        // When a var has a lower bound and we add an upper bound,
        // the lower bound must be <: upper bound
        val errors = mutableListOf<TypeError>()
        val sub = Subtyping { errors.add(it) }
        val v = Type.TVar(0)
        sub.constrain(Type.TInt, v, SourceSpan.zero) // lower bound: Int
        sub.constrain(v, Type.TString, SourceSpan.zero) // upper bound: String - error!
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun varBoundsPropagateDown() {
        // When a var has an upper bound and we add a lower bound,
        // the lower bound must be <: upper bound
        val errors = mutableListOf<TypeError>()
        val sub = Subtyping { errors.add(it) }
        val v = Type.TVar(0)
        sub.constrain(v, Type.TInt, SourceSpan.zero) // upper bound: Int
        sub.constrain(Type.TString, v, SourceSpan.zero) // lower bound: String - error!
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun varSubtypeOfItself() {
        val (sub, errors) = subtype()
        val v = Type.TVar(0)
        sub.constrain(v, v, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        assertTrue(v.upperBounds.isEmpty())
        assertTrue(v.lowerBounds.isEmpty())
    }

    @Test
    fun twoVarsCanRelate() {
        val (sub, errors) = subtype()
        val v1 = Type.TVar(0)
        val v2 = Type.TVar(1)
        sub.constrain(v1, v2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        assertTrue(v2 in v1.upperBounds)
        assertTrue(v1 in v2.lowerBounds)
    }

    @Test
    fun varBoundsWithTop() {
        val (sub, errors) = subtype()
        val v = Type.TVar(0)
        sub.constrain(v, Type.TTop, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        // Top shouldn't be added as a bound since it's always satisfied
    }

    @Test
    fun varBoundsWithBottom() {
        val (sub, errors) = subtype()
        val v = Type.TVar(0)
        sub.constrain(Type.TBottom, v, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        // Bottom shouldn't be added as a bound since it's always satisfied
    }

    // ========== Function Subtyping ==========

    @Test
    fun functionSubtypingCovariantResult() {
        val (sub, errors) = subtype()
        // (Int -> Int) <: (Int -> Top)  -- covariant in result
        val f1 = Type.TFun(listOf(Type.TInt), Type.TInt)
        val f2 = Type.TFun(listOf(Type.TInt), Type.TTop)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionSubtypingContravariantParam() {
        val (sub, errors) = subtype()
        // (Top -> Int) <: (Int -> Int)  -- contravariant in params
        val f1 = Type.TFun(listOf(Type.TTop), Type.TInt)
        val f2 = Type.TFun(listOf(Type.TInt), Type.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionSubtypingBothVariances() {
        val (sub, errors) = subtype()
        // (Top -> Int) <: (Int -> Top)  -- both contravariant param and covariant result
        val f1 = Type.TFun(listOf(Type.TTop), Type.TInt)
        val f2 = Type.TFun(listOf(Type.TInt), Type.TTop)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionArityMismatch() {
        val (sub, errors) = subtype()
        val f1 = Type.TFun(listOf(Type.TInt), Type.TInt)
        val f2 = Type.TFun(listOf(Type.TInt, Type.TInt), Type.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.ArityMismatch)
    }

    @Test
    fun functionArityMismatch_zeroToOne() {
        val (sub, errors) = subtype()
        val f1 = Type.TFun(emptyList(), Type.TInt)
        val f2 = Type.TFun(listOf(Type.TInt), Type.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.ArityMismatch)
    }

    @Test
    fun functionSameTypeSubtypes() {
        val (sub, errors) = subtype()
        val f1 = Type.TFun(listOf(Type.TInt, Type.TString), Type.TBool)
        val f2 = Type.TFun(listOf(Type.TInt, Type.TString), Type.TBool)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionNotSubtypeOfPrimitive() {
        val (sub, errors) = subtype()
        val f = Type.TFun(listOf(Type.TInt), Type.TInt)
        sub.constrain(f, Type.TInt, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun functionWithTypeVarParam() {
        val (sub, errors) = subtype()
        val v = Type.TVar(0)
        val f1 = Type.TFun(listOf(v), Type.TInt)
        val f2 = Type.TFun(listOf(Type.TInt), Type.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        // Contravariant: Int <: v, so v gets Int as lower bound
        assertTrue(Type.TInt in v.lowerBounds)
    }

    @Test
    fun functionWithTypeVarResult() {
        val (sub, errors) = subtype()
        val v = Type.TVar(0)
        val f1 = Type.TFun(listOf(Type.TInt), v)
        val f2 = Type.TFun(listOf(Type.TInt), Type.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        // Covariant: v <: Int, so v gets Int as upper bound
        assertTrue(Type.TInt in v.upperBounds)
    }

    // ========== Record Subtyping (Width) ==========

    @Test
    fun recordWithMoreFieldsIsSubtype() {
        val (sub, errors) = subtype()
        // { a: Int, b: String } <: { a: Int }
        val wider = Type.TRecord(mapOf("a" to Type.TInt, "b" to Type.TString))
        val narrower = Type.TRecord(mapOf("a" to Type.TInt))
        sub.constrain(wider, narrower, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun recordMissingField() {
        val (sub, errors) = subtype()
        // { a: Int } NOT <: { a: Int, b: String }
        val narrower = Type.TRecord(mapOf("a" to Type.TInt))
        val wider = Type.TRecord(mapOf("a" to Type.TInt, "b" to Type.TString))
        sub.constrain(narrower, wider, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }

    @Test
    fun recordFieldTypeMismatch() {
        val (sub, errors) = subtype()
        // { a: Int } NOT <: { a: String }
        val r1 = Type.TRecord(mapOf("a" to Type.TInt))
        val r2 = Type.TRecord(mapOf("a" to Type.TString))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun emptyRecordIsSupertype() {
        val (sub, errors) = subtype()
        // { a: Int } <: {}
        val rec = Type.TRecord(mapOf("a" to Type.TInt))
        val empty = Type.TRecord(emptyMap())
        sub.constrain(rec, empty, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun emptyRecordSubtypeOfEmptyRecord() {
        val (sub, errors) = subtype()
        val empty1 = Type.TRecord(emptyMap())
        val empty2 = Type.TRecord(emptyMap())
        sub.constrain(empty1, empty2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun recordNotSubtypeOfPrimitive() {
        val (sub, errors) = subtype()
        val rec = Type.TRecord(mapOf("a" to Type.TInt))
        sub.constrain(rec, Type.TInt, SourceSpan.zero)
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun recordWithTypeVarField() {
        val (sub, errors) = subtype()
        val v = Type.TVar(0)
        val r1 = Type.TRecord(mapOf("a" to v))
        val r2 = Type.TRecord(mapOf("a" to Type.TInt))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        // v <: Int, so v gets Int as upper bound
        assertTrue(Type.TInt in v.upperBounds)
    }

    // ========== Complex Cases ==========

    @Test
    fun nestedRecordSubtyping() {
        val (sub, errors) = subtype()
        // { inner: { a: Int, b: String } } <: { inner: { a: Int } }
        val innerWide = Type.TRecord(mapOf("a" to Type.TInt, "b" to Type.TString))
        val innerNarrow = Type.TRecord(mapOf("a" to Type.TInt))
        val outerWide = Type.TRecord(mapOf("inner" to innerWide))
        val outerNarrow = Type.TRecord(mapOf("inner" to innerNarrow))
        sub.constrain(outerWide, outerNarrow, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionReturningRecordSubtyping() {
        val (sub, errors) = subtype()
        // (Int -> { a: Int, b: String }) <: (Int -> { a: Int })
        val wideRec = Type.TRecord(mapOf("a" to Type.TInt, "b" to Type.TString))
        val narrowRec = Type.TRecord(mapOf("a" to Type.TInt))
        val f1 = Type.TFun(listOf(Type.TInt), wideRec)
        val f2 = Type.TFun(listOf(Type.TInt), narrowRec)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun functionAcceptingRecordSubtyping() {
        val (sub, errors) = subtype()
        // ({ a: Int } -> Int) <: ({ a: Int, b: String } -> Int)
        // Contravariant: { a: Int, b: String } <: { a: Int } - OK (width subtyping)
        val narrowRec = Type.TRecord(mapOf("a" to Type.TInt))
        val wideRec = Type.TRecord(mapOf("a" to Type.TInt, "b" to Type.TString))
        val f1 = Type.TFun(listOf(narrowRec), Type.TInt)
        val f2 = Type.TFun(listOf(wideRec), Type.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun nestedFunctionSubtyping() {
        val (sub, errors) = subtype()
        // ((Int -> Int) -> Int) <: ((Int -> Top) -> Int)
        // Contravariant in param, so we need (Int -> Top) <: (Int -> Int)
        // But that requires Int <: Top (covariant) which is fine
        // Wait, this is actually: (Int -> Top) needs to subtype (Int -> Int)
        // That means Top <: Int for result which fails
        val inner1 = Type.TFun(listOf(Type.TInt), Type.TInt)
        val inner2 = Type.TFun(listOf(Type.TInt), Type.TTop)
        val f1 = Type.TFun(listOf(inner1), Type.TInt)
        val f2 = Type.TFun(listOf(inner2), Type.TInt)
        sub.constrain(f1, f2, SourceSpan.zero)
        // Contravariant: inner2 <: inner1
        // inner2 has Top result, inner1 has Int result
        // Covariant in result: Top <: Int - should fail
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    // ========== ConstrainEqual Tests ==========

    @Test
    fun constrainEqualSameType() {
        val (sub, errors) = subtype()
        sub.constrainEqual(Type.TInt, Type.TInt, SourceSpan.zero)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun constrainEqualDifferentTypes() {
        val (sub, errors) = subtype()
        sub.constrainEqual(Type.TInt, Type.TString, SourceSpan.zero)
        // Both directions should fail
        assertEquals(2, errors.size)
    }

    @Test
    fun constrainEqualTypeVar() {
        val (sub, errors) = subtype()
        val v = Type.TVar(0)
        sub.constrainEqual(v, Type.TInt, SourceSpan.zero)
        assertTrue(errors.isEmpty())
        // Both bounds should be set
        assertTrue(Type.TInt in v.upperBounds)
        assertTrue(Type.TInt in v.lowerBounds)
    }

    @Test
    fun constrainEqualRecordsWithWidthDifference() {
        val (sub, errors) = subtype()
        val r1 = Type.TRecord(mapOf("a" to Type.TInt))
        val r2 = Type.TRecord(mapOf("a" to Type.TInt, "b" to Type.TString))
        sub.constrainEqual(r1, r2, SourceSpan.zero)
        // r1 <: r2 fails (missing field b)
        // r2 <: r1 succeeds
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }
}
