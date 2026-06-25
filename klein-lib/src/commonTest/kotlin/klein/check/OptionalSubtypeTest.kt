package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Subtyping relation over `T?` (Optional) and `Null`, ported from the old `OptionalSubtypingTest`.
 *
 * Rules: Null <: Null; Null <: T?; T <: T?; T? <: U? iff T <: U; T? is NOT <: T (no implicit
 * unwrap); Null is NOT <: a non-optional type (null safety). The old suite's TVar-bound and
 * `constrainEqual` cases test the deleted constraint solver and are not ported.
 */
class OptionalSubtypeTest {
    private val checker = Checker()
    private val env = TypeEnv.empty()

    private infix fun Type.subOf(other: Type) = checker.isSubtype(this, other, env)

    private fun rec(vararg fields: Pair<String, Type>) = TRecord(fields.toMap())

    private fun opt(t: Type) = TOptional(t)

    private fun fn(
        param: Type,
        result: Type,
    ) = TFun(listOf(param), result)

    // --- Null reflexivity ---

    @Test
    fun nullSubtypeOfNull() = assertTrue(TNull subOf TNull)

    // --- Null injection: Null <: T? ---

    @Test
    fun nullSubtypeOfOptionalNum() = assertTrue(TNull subOf opt(TNum))

    @Test
    fun nullSubtypeOfOptionalString() = assertTrue(TNull subOf opt(TStr))

    @Test
    fun nullSubtypeOfOptionalBool() = assertTrue(TNull subOf opt(TBool))

    @Test
    fun nullSubtypeOfOptionalUnit() = assertTrue(TNull subOf opt(TUnit))

    @Test
    fun nullSubtypeOfOptionalRecord() = assertTrue(TNull subOf opt(rec("x" to TNum)))

    @Test
    fun nullSubtypeOfOptionalFunction() = assertTrue(TNull subOf opt(fn(TNum, TStr)))

    @Test
    fun nullSubtypeOfNestedOptional() = assertTrue(TNull subOf opt(opt(TNum)))

    // --- Embedding: T <: T? ---

    @Test
    fun numSubtypeOfOptionalNum() = assertTrue(TNum subOf opt(TNum))

    @Test
    fun stringSubtypeOfOptionalString() = assertTrue(TStr subOf opt(TStr))

    @Test
    fun boolSubtypeOfOptionalBool() = assertTrue(TBool subOf opt(TBool))

    @Test
    fun unitSubtypeOfOptionalUnit() = assertTrue(TUnit subOf opt(TUnit))

    @Test
    fun recordSubtypeOfOptionalRecord() = assertTrue(rec("x" to TNum, "y" to TStr) subOf opt(rec("x" to TNum, "y" to TStr)))

    @Test
    fun functionSubtypeOfOptionalFunction() = assertTrue(fn(TNum, TStr) subOf opt(fn(TNum, TStr)))

    // --- Covariance: T? <: U? iff T <: U ---

    @Test
    fun optionalNumSubtypeOfOptionalNum() = assertTrue(opt(TNum) subOf opt(TNum))

    @Test
    fun optionalWideRecordSubtypeOfOptionalNarrowRecord() =
        assertTrue(opt(rec("x" to TNum, "y" to TStr)) subOf opt(rec("x" to TNum)))

    // --- No implicit unwrap: T? NOT <: T ---

    @Test
    fun optionalNumNotSubtypeOfNum() = assertFalse(opt(TNum) subOf TNum)

    @Test
    fun optionalStringNotSubtypeOfString() = assertFalse(opt(TStr) subOf TStr)

    @Test
    fun optionalRecordNotSubtypeOfRecord() = assertFalse(opt(rec("x" to TNum)) subOf rec("x" to TNum))

    @Test
    fun optionalFunctionNotSubtypeOfFunction() = assertFalse(opt(fn(TNum, TStr)) subOf fn(TNum, TStr))

    // --- Null safety: Null NOT <: a non-optional type ---

    @Test
    fun nullNotSubtypeOfNum() = assertFalse(TNull subOf TNum)

    @Test
    fun nullNotSubtypeOfString() = assertFalse(TNull subOf TStr)

    @Test
    fun nullNotSubtypeOfBool() = assertFalse(TNull subOf TBool)

    @Test
    fun nullNotSubtypeOfUnit() = assertFalse(TNull subOf TUnit)

    @Test
    fun nullNotSubtypeOfRecord() = assertFalse(TNull subOf rec("x" to TNum))

    @Test
    fun nullNotSubtypeOfFunction() = assertFalse(TNull subOf fn(TNum, TStr))

    // --- Functions with optional params / results ---

    @Test
    fun functionWithOptionalParam_contravariance() = assertTrue(fn(opt(TNum), TStr) subOf fn(TNum, TStr))

    @Test
    fun functionWithOptionalParam_nullContravariance() = assertTrue(fn(opt(TNum), TStr) subOf fn(TNull, TStr))

    @Test
    fun functionWithOptionalParam_notSubtypeOfNonOptionalParam() = assertFalse(fn(TNum, TStr) subOf fn(opt(TNum), TStr))

    @Test
    fun functionWithNullParam_notSubtypeOfOptionalParam() = assertFalse(fn(TNull, TStr) subOf fn(opt(TNum), TStr))

    @Test
    fun functionReturningOptional_reflexive() = assertTrue(fn(TNum, opt(TNum)) subOf fn(TNum, opt(TNum)))

    @Test
    fun functionReturningNum_subtypeOfReturningOptionalNum() = assertTrue(fn(TNum, TNum) subOf fn(TNum, opt(TNum)))

    @Test
    fun functionReturningNull_subtypeOfReturningOptional() = assertTrue(fn(TNum, TNull) subOf fn(TNum, opt(TNum)))

    @Test
    fun functionReturningOptional_notSubtypeOfReturningNonOptional() = assertFalse(fn(TNum, opt(TNum)) subOf fn(TNum, TNum))

    // --- Records with optional fields ---

    @Test
    fun recordWithOptionalField_reflexive() = assertTrue(rec("x" to opt(TNum)) subOf rec("x" to opt(TNum)))

    @Test
    fun recordWithNonOptionalField_subtypeOfOptionalField() = assertTrue(rec("x" to TNum) subOf rec("x" to opt(TNum)))

    @Test
    fun recordWithNullField_subtypeOfOptionalField() = assertTrue(rec("x" to TNull) subOf rec("x" to opt(TNum)))

    @Test
    fun recordWithOptionalField_notSubtypeOfNonOptionalField() = assertFalse(rec("x" to opt(TNum)) subOf rec("x" to TNum))

    // --- Nested optionals ---

    @Test
    fun nestedOptionalReflexive() = assertTrue(opt(opt(TNum)) subOf opt(opt(TNum)))

    @Test
    fun numSubtypeOfOptionalOptionalNum() = assertTrue(TNum subOf opt(opt(TNum)))

    @Test
    fun nullSubtypeOfOptionalOptionalNum() = assertTrue(TNull subOf opt(opt(TNum)))

    @Test
    fun optionalNumSubtypeOfOptionalOptionalNum() = assertTrue(opt(TNum) subOf opt(opt(TNum)))

    // --- Complex / higher-order with optional ---

    @Test
    fun complexFunction_optionalInAndOut() = assertTrue(fn(opt(TNum), opt(TNum)) subOf fn(TNum, opt(TNum)))

    @Test
    fun higherOrderFunction_withOptional() =
        // outer param contravariant ⇒ need (Num)->Num <: (Num)->Num? ✓
        assertTrue(fn(fn(TNum, opt(TNum)), TStr) subOf fn(fn(TNum, TNum), TStr))

    @Test
    fun higherOrder_optionalInCallbackParam_covariant() =
        // need Num? <: Num — false
        assertFalse(fn(fn(opt(TNum), TStr), TBool) subOf fn(fn(TNum, TStr), TBool))

    @Test
    fun higherOrder_optionalInCallbackParam_contravariant() =
        assertTrue(fn(fn(TNum, TStr), TBool) subOf fn(fn(opt(TNum), TStr), TBool))

    @Test
    fun higherOrder_optionalInCallbackResult_covariant() =
        assertTrue(fn(fn(TNum, opt(TNum)), TStr) subOf fn(fn(TNum, TNum), TStr))

    @Test
    fun higherOrder_optionalInCallbackResult_contravariant() =
        assertFalse(fn(fn(TNum, TNum), TStr) subOf fn(fn(TNum, opt(TNum)), TStr))

    @Test
    fun higherOrder_optionalHofResult() = assertFalse(fn(fn(TNum, TNum), opt(TNum)) subOf fn(fn(TNum, TNum), TNum))

    @Test
    fun higherOrder_nonOptionalHofResult_subtypeOfOptional() = assertTrue(fn(fn(TNum, TNum), TNum) subOf fn(fn(TNum, TNum), opt(TNum)))

    @Test
    fun higherOrder_threeDeep_optionalAtBottom() =
        // triple flip lands contravariant ⇒ need Num <: Num? ✓
        assertTrue(fn(fn(fn(opt(TNum), TNum), TNum), TNum) subOf fn(fn(fn(TNum, TNum), TNum), TNum))

    @Test
    fun higherOrder_threeDeep_optionalAtBottom_reversed() =
        assertFalse(fn(fn(fn(TNum, TNum), TNum), TNum) subOf fn(fn(fn(opt(TNum), TNum), TNum), TNum))

    @Test
    fun higherOrder_functionReturningOptionalFunction() =
        assertFalse(fn(TNum, fn(TNum, opt(TNum))) subOf fn(TNum, fn(TNum, TNum)))

    @Test
    fun higherOrder_functionReturningNonOptionalFunction_subtypeOfOptional() =
        assertTrue(fn(TNum, fn(TNum, TNum)) subOf fn(TNum, fn(TNum, opt(TNum))))

    // --- Deep nested records with optional ---

    @Test
    fun nestedRecord_optionalInInnerField() =
        assertTrue(rec("outer" to rec("inner" to TNum)) subOf rec("outer" to rec("inner" to opt(TNum))))

    @Test
    fun nestedRecord_optionalInInnerField_reversed() =
        assertFalse(rec("outer" to rec("inner" to opt(TNum))) subOf rec("outer" to rec("inner" to TNum)))

    @Test
    fun nestedRecord_optionalOuterRecord() =
        assertTrue(rec("outer" to rec("inner" to TNum)) subOf rec("outer" to opt(rec("inner" to TNum))))

    @Test
    fun nestedRecord_nullInOuterField() =
        assertTrue(rec("outer" to TNull) subOf rec("outer" to opt(rec("inner" to TNum))))

    @Test
    fun nestedRecord_threeDeep_optionalAtBottom() =
        assertTrue(
            rec("a" to rec("b" to rec("c" to TNum))) subOf rec("a" to rec("b" to rec("c" to opt(TNum)))),
        )

    @Test
    fun nestedRecord_threeDeep_optionalAtBottom_reversed() =
        assertFalse(
            rec("a" to rec("b" to rec("c" to opt(TNum)))) subOf rec("a" to rec("b" to rec("c" to TNum))),
        )

    @Test
    fun nestedRecord_mixedOptionalLevels() =
        assertTrue(rec("a" to opt(rec("b" to TNum))) subOf rec("a" to opt(rec("b" to opt(TNum)))))

    @Test
    fun nestedRecord_withFunctionField_optionalResult() =
        assertTrue(rec("f" to fn(TNum, TNum)) subOf rec("f" to fn(TNum, opt(TNum))))

    @Test
    fun nestedRecord_withFunctionField_optionalParam() =
        assertTrue(rec("f" to fn(opt(TNum), TNum)) subOf rec("f" to fn(TNum, TNum)))

    @Test
    fun nestedRecord_withFunctionField_optionalParam_reversed() =
        assertFalse(rec("f" to fn(TNum, TNum)) subOf rec("f" to fn(opt(TNum), TNum)))

    @Test
    fun record_containingHOF_withOptionalCallback() =
        assertFalse(
            rec("hof" to fn(fn(opt(TNum), TStr), TBool)) subOf rec("hof" to fn(fn(TNum, TStr), TBool)),
        )
}
