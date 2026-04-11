@file:Suppress("ktlint")

package klein.types

import klein.SourceSpan
import klein.types.SimpleType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Direct subtyping tests for TNull and TOptional types.
 *
 * These tests verify the constraint propagation behavior at the SimpleType level,
 * independent of parsing and full type inference.
 *
 * Key rules being tested:
 * 1. Null <: Null (reflexivity)
 * 2. Null <: T? for any T (null injection)
 * 3. T <: T? for any T (embedding)
 * 4. T? <: U? if T <: U (covariance)
 * 5. T? is NOT a subtype of T (no implicit unwrap)
 * 6. Null is NOT a subtype of non-optional types (null safety)
 */
class OptionalSubtypingTest {

    private fun subtype(): Subtyping = Subtyping(TypeEnv.empty())

    // =========================================================================
    // SECTION 1: TNull Reflexivity and Identity
    // =========================================================================

    @Test
    fun nullSubtypeOfNull() {
        val sub = subtype()
        sub.constrain(TNull, TNull, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    // Note: removed nullEqualToNull - it was identical to nullSubtypeOfNull
    // since constrainEqual(X, X) is just constrain(X, X) twice

    // =========================================================================
    // SECTION 2: Null Injection (Null <: T?)
    // =========================================================================

    @Test
    fun nullSubtypeOfOptionalNum() {
        val sub = subtype()
        sub.constrain(TNull, TOptional(TNum), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nullSubtypeOfOptionalString() {
        val sub = subtype()
        sub.constrain(TNull, TOptional(TString), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nullSubtypeOfOptionalBool() {
        val sub = subtype()
        sub.constrain(TNull, TOptional(TBool), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nullSubtypeOfOptionalUnit() {
        val sub = subtype()
        sub.constrain(TNull, TOptional(TUnit), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nullSubtypeOfOptionalRecord() {
        val sub = subtype()
        val rec = TRecord(mapOf("x" to TNum))
        sub.constrain(TNull, TOptional(rec), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nullSubtypeOfOptionalFunction() {
        val sub = subtype()
        val func = TFun(listOf(TNum), TString)
        sub.constrain(TNull, TOptional(func), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nullSubtypeOfOptionalTypeVar() {
        val sub = subtype()
        val v = TVar()
        sub.constrain(TNull, TOptional(v), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nullSubtypeOfOptionalTypeVar_varGetsBound() {
        // Null <: TOptional(v), then v gets constrained to Num
        // Should still work - Null <: Num?
        val sub = subtype()
        val v = TVar()
        sub.constrain(TNull, TOptional(v), SourceSpan.zero)
        sub.constrain(TNum, v, SourceSpan.zero)  // v := Num
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nullSubtypeOfOptionalTypeVar_thenVarUsedAsNum() {
        // Null <: TOptional(v), then v <: Num (v used in numeric context)
        // This should work - v can be anything, Null still <: v?
        val sub = subtype()
        val v = TVar()
        sub.constrain(TNull, TOptional(v), SourceSpan.zero)
        sub.constrain(v, TNum, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nullSubtypeOfNestedOptional() {
        // Null <: (Num?)? - should be valid
        val sub = subtype()
        sub.constrain(TNull, TOptional(TOptional(TNum)), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    // =========================================================================
    // SECTION 3: Embedding (T <: T?)
    // =========================================================================

    @Test
    fun numSubtypeOfOptionalNum() {
        val sub = subtype()
        sub.constrain(TNum, TOptional(TNum), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun stringSubtypeOfOptionalString() {
        val sub = subtype()
        sub.constrain(TString, TOptional(TString), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun boolSubtypeOfOptionalBool() {
        val sub = subtype()
        sub.constrain(TBool, TOptional(TBool), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun unitSubtypeOfOptionalUnit() {
        val sub = subtype()
        sub.constrain(TUnit, TOptional(TUnit), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun recordSubtypeOfOptionalRecord() {
        val sub = subtype()
        val rec = TRecord(mapOf("x" to TNum, "y" to TString))
        sub.constrain(rec, TOptional(rec), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun functionSubtypeOfOptionalFunction() {
        val sub = subtype()
        val func = TFun(listOf(TNum), TString)
        sub.constrain(func, TOptional(func), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun typeVarSubtypeOfOptionalTypeVar() {
        val sub = subtype()
        val v = TVar()
        sub.constrain(v, TOptional(v), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    // =========================================================================
    // SECTION 4: Covariance (T? <: U? if T <: U)
    // =========================================================================

    @Test
    fun optionalNumSubtypeOfOptionalNum() {
        val sub = subtype()
        sub.constrain(TOptional(TNum), TOptional(TNum), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun optionalWideRecordSubtypeOfOptionalNarrowRecord() {
        // { x, y }? <: { x }? (width subtyping preserved under optional)
        val sub = subtype()
        val wide = TRecord(mapOf("x" to TNum, "y" to TString))
        val narrow = TRecord(mapOf("x" to TNum))
        sub.constrain(TOptional(wide), TOptional(narrow), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun optionalFunctionCovariantResult() {
        // ((Num) -> Num)? <: ((Num) -> a)? where a gets Num as lower bound
        val sub = subtype()
        val resultVar = TVar()
        val f1 = TFun(listOf(TNum), TNum)
        val f2 = TFun(listOf(TNum), resultVar)
        sub.constrain(TOptional(f1), TOptional(f2), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(TNum in resultVar.lowerBounds)
    }

    @Test
    fun optionalFunctionContravariantParam() {
        // ((a) -> Num)? <: ((Num) -> Num)?
        val sub = subtype()
        val paramVar = TVar()
        val f1 = TFun(listOf(paramVar), TNum)
        val f2 = TFun(listOf(TNum), TNum)
        sub.constrain(TOptional(f1), TOptional(f2), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(TNum in paramVar.lowerBounds)
    }

    // =========================================================================
    // SECTION 5: No Implicit Unwrap (T? NOT <: T)
    // =========================================================================

    @Test
    fun optionalNumNotSubtypeOfNum() {
        val sub = subtype()
        sub.constrain(TOptional(TNum), TNum, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    @Test
    fun optionalStringNotSubtypeOfString() {
        val sub = subtype()
        sub.constrain(TOptional(TString), TString, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    @Test
    fun optionalRecordNotSubtypeOfRecord() {
        val sub = subtype()
        val rec = TRecord(mapOf("x" to TNum))
        sub.constrain(TOptional(rec), rec, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    @Test
    fun optionalFunctionNotSubtypeOfFunction() {
        val sub = subtype()
        val func = TFun(listOf(TNum), TString)
        sub.constrain(TOptional(func), func, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch)
    }

    // =========================================================================
    // SECTION 6: Null Safety (Null NOT <: T for non-optional T)
    // =========================================================================

    @Test
    fun nullNotSubtypeOfNum() {
        val sub = subtype()
        sub.constrain(TNull, TNum, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
        // Could be TypeMismatch or a specific NullNotAllowed error
        assertTrue(sub.getErrors()[0] is TypeError.TypeMismatch ||
                   sub.getErrors()[0] is TypeError.NullNotAllowed)
    }

    @Test
    fun nullNotSubtypeOfString() {
        val sub = subtype()
        sub.constrain(TNull, TString, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun nullNotSubtypeOfBool() {
        val sub = subtype()
        sub.constrain(TNull, TBool, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun nullNotSubtypeOfUnit() {
        val sub = subtype()
        sub.constrain(TNull, TUnit, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun nullNotSubtypeOfRecord() {
        val sub = subtype()
        val rec = TRecord(mapOf("x" to TNum))
        sub.constrain(TNull, rec, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun nullNotSubtypeOfFunction() {
        val sub = subtype()
        val func = TFun(listOf(TNum), TString)
        sub.constrain(TNull, func, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    // =========================================================================
    // SECTION 7: Type Variables with Optional
    // =========================================================================

    @Test
    fun typeVarGetsOptionalAsUpperBound() {
        val sub = subtype()
        val v = TVar()
        sub.constrain(v, TOptional(TNum), SourceSpan.zero)
        assertTrue(TOptional(TNum) in v.upperBounds)
    }

    @Test
    fun typeVarGetsNullAsLowerBound() {
        val sub = subtype()
        val v = TVar()
        sub.constrain(TNull, v, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(TNull in v.lowerBounds)
    }

    @Test
    fun typeVarGetsOptionalAsLowerBound() {
        val sub = subtype()
        val v = TVar()
        sub.constrain(TOptional(TNum), v, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
        assertTrue(TOptional(TNum) in v.lowerBounds)
    }

    @Test
    fun typeVarBoundedByBothNumAndNull_propagatesToOptional() {
        // If v has both Num and Null as lower bounds,
        // constraining v <: Num should fail (Null can't flow to Num)
        val sub = subtype()
        val v = TVar()
        sub.constrain(TNum, v, SourceSpan.zero)
        sub.constrain(TNull, v, SourceSpan.zero)
        sub.constrain(v, TNum, SourceSpan.zero)
        // This should produce an error because Null can't go to Num
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun typeVarBoundedByBothNumAndNull_constrainedToOptionalNum() {
        // If v has both Num and Null as lower bounds,
        // constraining v <: Num? should succeed
        val sub = subtype()
        val v = TVar()
        sub.constrain(TNum, v, SourceSpan.zero)
        sub.constrain(TNull, v, SourceSpan.zero)
        sub.constrain(v, TOptional(TNum), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    // =========================================================================
    // SECTION 8: Function Types with Optional Parameters/Results
    // =========================================================================
    @Test
    fun functionWithOptionalParam_contravariance() {
        // (Num?) -> String should accept Num as argument
        // i.e., (Num?) -> String <: (Num) -> String (contravariant param)
        val sub = subtype()
        val f1 = TFun(listOf(TOptional(TNum)), TString)
        val f2 = TFun(listOf(TNum), TString)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun functionWithOptionalParam_null_contravariance() {
        // (Num?) -> String <: (Null) -> String (Null <: Num?)
        val sub = subtype()
        val f1 = TFun(listOf(TOptional(TNum)), TString)
        val f2 = TFun(listOf(TNull), TString)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun functionWithOptionalParam_notSubtypeOfNonOptionalParam() {
        // (Num) -> String is NOT subtype of (Num?) -> String
        val sub = subtype()
        val f1 = TFun(listOf(TNum), TString)
        val f2 = TFun(listOf(TOptional(TNum)), TString)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().size == 1)
    }

    @Test
    fun functionWithNullParam_notSubtypeOfOptionalParam() {
        // (Null) -> String <: (Num?) -> String (Null <: Num?)
        val sub = subtype()
        val f1 = TFun(listOf(TNull), TString)
        val f2 = TFun(listOf(TOptional(TNum)), TString)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().size == 1)
    }

    @Test
    fun functionReturningOptional_isSubtypeOfFunctionReturningOptional() {
        // (Num) -> Num? <: (Num) -> Num? (reflexive)
        val sub = subtype()
        val f1 = TFun(listOf(TNum), TOptional(TNum))
        val f2 = TFun(listOf(TNum), TOptional(TNum))
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun functionReturningNum_subtypeOfFunctionReturningOptionalNum() {
        // (Num) -> Num <: (Num) -> Num? (covariant result, Num <: Num?)
        val sub = subtype()
        val f1 = TFun(listOf(TNum), TNum)
        val f2 = TFun(listOf(TNum), TOptional(TNum))
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun functionReturningNull_subtypeOfFunctionReturningOptional() {
        // (Num) -> Null <: (Num) -> Num? (Null <: Num?)
        val sub = subtype()
        val f1 = TFun(listOf(TNum), TNull)
        val f2 = TFun(listOf(TNum), TOptional(TNum))
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun functionReturningOptional_notSubtypeOfFunctionReturningNonOptional() {
        // (Num) -> Num? NOT <: (Num) -> Num (can't unwrap optional)
        val sub = subtype()
        val f1 = TFun(listOf(TNum), TOptional(TNum))
        val f2 = TFun(listOf(TNum), TNum)
        sub.constrain(f1, f2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    // =========================================================================
    // SECTION 9: Record Types with Optional Fields
    // =========================================================================

    @Test
    fun recordWithOptionalField_subtypeOfRecordWithOptionalField() {
        val sub = subtype()
        val r1 = TRecord(mapOf("x" to TOptional(TNum)))
        val r2 = TRecord(mapOf("x" to TOptional(TNum)))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun recordWithNonOptionalField_subtypeOfRecordWithOptionalField() {
        // { x: Num } <: { x: Num? }
        val sub = subtype()
        val r1 = TRecord(mapOf("x" to TNum))
        val r2 = TRecord(mapOf("x" to TOptional(TNum)))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun recordWithNullField_subtypeOfRecordWithOptionalField() {
        // { x: Null } <: { x: Num? }
        val sub = subtype()
        val r1 = TRecord(mapOf("x" to TNull))
        val r2 = TRecord(mapOf("x" to TOptional(TNum)))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun recordWithOptionalField_notSubtypeOfRecordWithNonOptionalField() {
        // { x: Num? } NOT <: { x: Num }
        val sub = subtype()
        val r1 = TRecord(mapOf("x" to TOptional(TNum)))
        val r2 = TRecord(mapOf("x" to TNum))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    // =========================================================================
    // SECTION 10: Edge Cases and Complex Scenarios
    // =========================================================================

    @Test
    fun optionalOptional_flattensOrNests() {
        // (Num?)? - behavior depends on design decision
        // For now, test that it's at least a valid type
        val sub = subtype()
        val nested = TOptional(TOptional(TNum))
        sub.constrain(nested, nested, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun numSubtypeOfOptionalOptionalNum() {
        // Num <: (Num?)? (embedding goes through both levels)
        val sub = subtype()
        sub.constrain(TNum, TOptional(TOptional(TNum)), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nullSubtypeOfOptionalOptionalNum() {
        // Null <: (Num?)?
        val sub = subtype()
        sub.constrain(TNull, TOptional(TOptional(TNum)), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun optionalNumSubtypeOfOptionalOptionalNum() {
        // Num? <: (Num?)?
        val sub = subtype()
        sub.constrain(TOptional(TNum), TOptional(TOptional(TNum)), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun constrainEqual_optionalNum() {
        val sub = subtype()
        sub.constrainEqual(TOptional(TNum), TOptional(TNum), SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun constrainEqual_optionalNumAndNum_fails() {
        val sub = subtype()
        sub.constrainEqual(TOptional(TNum), TNum, SourceSpan.zero)
        // Should have errors in both directions
        assertTrue(sub.getErrors().isNotEmpty())
    }

    @Test
    fun constrainEqual_nullAndOptionalNum_fails() {
        // Null != Num? (Null is subtype, not equal)
        val sub = subtype()
        sub.constrainEqual(TNull, TOptional(TNum), SourceSpan.zero)
        assertTrue(sub.getErrors().isNotEmpty())
    }

    @Test
    fun complexFunction_optionalInAndOut() {
        // ((Num?) -> Num?) <: ((Num) -> Num?)
        // Param: contravariant, Num <: Num? ✓
        // Result: covariant, Num? <: Num? ✓
        val sub = subtype()
        val f1 = TFun(listOf(TOptional(TNum)), TOptional(TNum))
        val f2 = TFun(listOf(TNum), TOptional(TNum))
        sub.constrain(f1, f2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun higherOrderFunction_withOptional() {
        // ((Num -> Num?) -> String) <: ((Num -> Num) -> String)
        // Inner function: Num -> Num <: Num -> Num? ✓ (result covariant)
        // Outer param is contravariant, so inner subtype relationship flips
        val sub = subtype()
        val inner1 = TFun(listOf(TNum), TOptional(TNum))
        val inner2 = TFun(listOf(TNum), TNum)
        val outer1 = TFun(listOf(inner1), TString)
        val outer2 = TFun(listOf(inner2), TString)
        // outer1 <: outer2 requires inner2 <: inner1 (contravariant)
        // i.e., (Num -> Num) <: (Num -> Num?)
        // which is true (Num <: Num? in covariant result position)
        sub.constrain(outer1, outer2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    // =========================================================================
    // SECTION 11: Deep Nested Higher-Order Function Tests
    // =========================================================================

    @Test
    fun higherOrder_optionalInCallbackParam_covariant() {
        // HOF takes callback with optional param
        // ((Num?) -> String) -> Bool  <:  ((Num) -> String) -> Bool
        // Outer param contravariant => inner function must have opposite relation
        // We need (Num) -> String <: (Num?) -> String
        // Param contravariant: Num? <: Num? ✓ (wait, need Num? <: Num which is FALSE)
        // Actually: For f <: g on functions, we need g.param <: f.param
        // So for inner: (Num) -> String <: (Num?) -> String requires Num? <: Num - FALSE
        val sub = subtype()
        val callback1 = TFun(listOf(TOptional(TNum)), TString)
        val callback2 = TFun(listOf(TNum), TString)
        val hof1 = TFun(listOf(callback1), TBool)
        val hof2 = TFun(listOf(callback2), TBool)
        sub.constrain(hof1, hof2, SourceSpan.zero)
        // This should FAIL because the callback param variance doesn't work out
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun higherOrder_optionalInCallbackParam_contravariant() {
        // Flip of above: ((Num) -> String) -> Bool  <:  ((Num?) -> String) -> Bool
        // Outer param contravariant => need (Num?) -> String <: (Num) -> String
        // Inner param contravariant: need Num <: Num? ✓
        val sub = subtype()
        val callback1 = TFun(listOf(TNum), TString)
        val callback2 = TFun(listOf(TOptional(TNum)), TString)
        val hof1 = TFun(listOf(callback1), TBool)
        val hof2 = TFun(listOf(callback2), TBool)
        sub.constrain(hof1, hof2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun higherOrder_optionalInCallbackResult_covariant() {
        // ((Num) -> Num?) -> String  <:  ((Num) -> Num) -> String
        // Outer param contravariant => need (Num) -> Num <: (Num) -> Num?
        // Inner result covariant: Num <: Num? ✓
        val sub = subtype()
        val callback1 = TFun(listOf(TNum), TOptional(TNum))
        val callback2 = TFun(listOf(TNum), TNum)
        val hof1 = TFun(listOf(callback1), TString)
        val hof2 = TFun(listOf(callback2), TString)
        sub.constrain(hof1, hof2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun higherOrder_optionalInCallbackResult_contravariant() {
        // ((Num) -> Num) -> String  <:  ((Num) -> Num?) -> String
        // Outer param contravariant => need (Num) -> Num? <: (Num) -> Num
        // Inner result covariant: Num? <: Num - FALSE
        val sub = subtype()
        val callback1 = TFun(listOf(TNum), TNum)
        val callback2 = TFun(listOf(TNum), TOptional(TNum))
        val hof1 = TFun(listOf(callback1), TString)
        val hof2 = TFun(listOf(callback2), TString)
        sub.constrain(hof1, hof2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun higherOrder_optionalHofResult() {
        // ((Num) -> Num) -> Num?  <:  ((Num) -> Num) -> Num
        // Result covariant: Num? <: Num - FALSE
        val sub = subtype()
        val callback = TFun(listOf(TNum), TNum)
        val hof1 = TFun(listOf(callback), TOptional(TNum))
        val hof2 = TFun(listOf(callback), TNum)
        sub.constrain(hof1, hof2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun higherOrder_nonOptionalHofResult_subtypeOfOptional() {
        // ((Num) -> Num) -> Num  <:  ((Num) -> Num) -> Num?
        // Result covariant: Num <: Num? ✓
        val sub = subtype()
        val callback = TFun(listOf(TNum), TNum)
        val hof1 = TFun(listOf(callback), TNum)
        val hof2 = TFun(listOf(callback), TOptional(TNum))
        sub.constrain(hof1, hof2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun higherOrder_threeDeep_optionalAtBottom() {
        // (((Num?) -> Num) -> Num) -> Num  <:  (((Num) -> Num) -> Num) -> Num
        // Level 1 (outermost param): contravariant
        // Level 2: contravariant again (double flip = covariant)
        // Level 3 (innermost param): contravariant (triple flip = contravariant)
        // So innermost: need Num <: Num? ✓
        val sub = subtype()
        val inner1 = TFun(listOf(TOptional(TNum)), TNum)
        val inner2 = TFun(listOf(TNum), TNum)
        val mid1 = TFun(listOf(inner1), TNum)
        val mid2 = TFun(listOf(inner2), TNum)
        val outer1 = TFun(listOf(mid1), TNum)
        val outer2 = TFun(listOf(mid2), TNum)
        sub.constrain(outer1, outer2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun higherOrder_threeDeep_optionalAtBottom_reversed() {
        // (((Num) -> Num) -> Num) -> Num  <:  (((Num?) -> Num) -> Num) -> Num
        // Innermost: need Num? <: Num - FALSE
        val sub = subtype()
        val inner1 = TFun(listOf(TNum), TNum)
        val inner2 = TFun(listOf(TOptional(TNum)), TNum)
        val mid1 = TFun(listOf(inner1), TNum)
        val mid2 = TFun(listOf(inner2), TNum)
        val outer1 = TFun(listOf(mid1), TNum)
        val outer2 = TFun(listOf(mid2), TNum)
        sub.constrain(outer1, outer2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun higherOrder_functionReturningOptionalFunction() {
        // Num -> (Num -> Num?)  <:  Num -> (Num -> Num)
        // Outer result covariant => need (Num -> Num?) <: (Num -> Num)
        // Inner result covariant: Num? <: Num - FALSE
        val sub = subtype()
        val inner1 = TFun(listOf(TNum), TOptional(TNum))
        val inner2 = TFun(listOf(TNum), TNum)
        val outer1 = TFun(listOf(TNum), inner1)
        val outer2 = TFun(listOf(TNum), inner2)
        sub.constrain(outer1, outer2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun higherOrder_functionReturningNonOptionalFunction_subtypeOfOptional() {
        // Num -> (Num -> Num)  <:  Num -> (Num -> Num?)
        // Outer result covariant => need (Num -> Num) <: (Num -> Num?)
        // Inner result covariant: Num <: Num? ✓
        val sub = subtype()
        val inner1 = TFun(listOf(TNum), TNum)
        val inner2 = TFun(listOf(TNum), TOptional(TNum))
        val outer1 = TFun(listOf(TNum), inner1)
        val outer2 = TFun(listOf(TNum), inner2)
        sub.constrain(outer1, outer2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    // =========================================================================
    // SECTION 12: Deep Nested Record Tests
    // =========================================================================

    @Test
    fun nestedRecord_optionalInInnerField() {
        // { outer: { inner: Num } } <: { outer: { inner: Num? } }
        val sub = subtype()
        val inner1 = TRecord(mapOf("inner" to TNum))
        val inner2 = TRecord(mapOf("inner" to TOptional(TNum)))
        val r1 = TRecord(mapOf("outer" to inner1))
        val r2 = TRecord(mapOf("outer" to inner2))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nestedRecord_optionalInInnerField_reversed() {
        // { outer: { inner: Num? } } NOT <: { outer: { inner: Num } }
        val sub = subtype()
        val inner1 = TRecord(mapOf("inner" to TOptional(TNum)))
        val inner2 = TRecord(mapOf("inner" to TNum))
        val r1 = TRecord(mapOf("outer" to inner1))
        val r2 = TRecord(mapOf("outer" to inner2))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun nestedRecord_optionalOuterRecord() {
        // { outer: { inner: Num }? } - outer field is optional record
        val sub = subtype()
        val inner = TRecord(mapOf("inner" to TNum))
        val r1 = TRecord(mapOf("outer" to inner))
        val r2 = TRecord(mapOf("outer" to TOptional(inner)))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nestedRecord_nullInOuterField() {
        // { outer: Null } <: { outer: { inner: Num }? }
        val sub = subtype()
        val inner = TRecord(mapOf("inner" to TNum))
        val r1 = TRecord(mapOf("outer" to TNull))
        val r2 = TRecord(mapOf("outer" to TOptional(inner)))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nestedRecord_threeDeep_optionalAtBottom() {
        // { a: { b: { c: Num } } } <: { a: { b: { c: Num? } } }
        val sub = subtype()
        val deepest1 = TRecord(mapOf("c" to TNum))
        val deepest2 = TRecord(mapOf("c" to TOptional(TNum)))
        val mid1 = TRecord(mapOf("b" to deepest1))
        val mid2 = TRecord(mapOf("b" to deepest2))
        val outer1 = TRecord(mapOf("a" to mid1))
        val outer2 = TRecord(mapOf("a" to mid2))
        sub.constrain(outer1, outer2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nestedRecord_threeDeep_optionalAtBottom_reversed() {
        // { a: { b: { c: Num? } } } NOT <: { a: { b: { c: Num } } }
        val sub = subtype()
        val deepest1 = TRecord(mapOf("c" to TOptional(TNum)))
        val deepest2 = TRecord(mapOf("c" to TNum))
        val mid1 = TRecord(mapOf("b" to deepest1))
        val mid2 = TRecord(mapOf("b" to deepest2))
        val outer1 = TRecord(mapOf("a" to mid1))
        val outer2 = TRecord(mapOf("a" to mid2))
        sub.constrain(outer1, outer2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun nestedRecord_mixedOptionalLevels() {
        // { a: { b: Num }? } <: { a: { b: Num? }? }
        // Inner b: Num <: Num? ✓, wrapped in optional
        val sub = subtype()
        val inner1 = TRecord(mapOf("b" to TNum))
        val inner2 = TRecord(mapOf("b" to TOptional(TNum)))
        val r1 = TRecord(mapOf("a" to TOptional(inner1)))
        val r2 = TRecord(mapOf("a" to TOptional(inner2)))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nestedRecord_withFunctionField_optionalResult() {
        // { f: Num -> Num } <: { f: Num -> Num? }
        val sub = subtype()
        val f1 = TFun(listOf(TNum), TNum)
        val f2 = TFun(listOf(TNum), TOptional(TNum))
        val r1 = TRecord(mapOf("f" to f1))
        val r2 = TRecord(mapOf("f" to f2))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nestedRecord_withFunctionField_optionalParam() {
        // { f: Num? -> Num } <: { f: Num -> Num }
        // Function param contravariant: Num <: Num? ✓
        val sub = subtype()
        val f1 = TFun(listOf(TOptional(TNum)), TNum)
        val f2 = TFun(listOf(TNum), TNum)
        val r1 = TRecord(mapOf("f" to f1))
        val r2 = TRecord(mapOf("f" to f2))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertTrue(sub.getErrors().isEmpty())
    }

    @Test
    fun nestedRecord_withFunctionField_optionalParam_reversed() {
        // { f: Num -> Num } NOT <: { f: Num? -> Num }
        // Would require Num? <: Num for param - FALSE
        val sub = subtype()
        val f1 = TFun(listOf(TNum), TNum)
        val f2 = TFun(listOf(TOptional(TNum)), TNum)
        val r1 = TRecord(mapOf("f" to f1))
        val r2 = TRecord(mapOf("f" to f2))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }

    @Test
    fun record_containingHOF_withOptionalCallback() {
        // { hof: ((Num?) -> String) -> Bool } <: { hof: ((Num) -> String) -> Bool }
        // HOF param contravariant, callback param contravariant = covariant
        // So need Num? <: Num - FALSE
        val sub = subtype()
        val callback1 = TFun(listOf(TOptional(TNum)), TString)
        val callback2 = TFun(listOf(TNum), TString)
        val hof1 = TFun(listOf(callback1), TBool)
        val hof2 = TFun(listOf(callback2), TBool)
        val r1 = TRecord(mapOf("hof" to hof1))
        val r2 = TRecord(mapOf("hof" to hof2))
        sub.constrain(r1, r2, SourceSpan.zero)
        assertEquals(1, sub.getErrors().size)
    }
}
