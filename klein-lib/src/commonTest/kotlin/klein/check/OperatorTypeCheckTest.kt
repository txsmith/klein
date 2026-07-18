package klein.check

import klein.Type as L
import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OperatorTypeCheckTest {
    @Test
    fun add_intPlusInt() = assertEquals(TNum, infer("1 + 2").type)

    @Test
    fun add_doublePlusDouble() = assertEquals(TNum, infer("1.0 + 2.0").type)

    @Test
    fun sub_intMinusInt() = assertEquals(TNum, infer("5 - 3").type)

    @Test
    fun mul_intTimesInt() = assertEquals(TNum, infer("2 * 3").type)

    @Test
    fun div_intDivInt() = assertEquals(TNum, infer("10 / 2").type)

    @Test
    fun mod_intModInt() = assertEquals(TNum, infer("10 % 3").type)

    @Test
    fun lt_intLtInt() = assertEquals(TBool, infer("1 < 2").type)

    @Test
    fun lteq_intLteqInt() = assertEquals(TBool, infer("1 <= 2").type)

    @Test
    fun gt_intGtInt() = assertEquals(TBool, infer("1 > 2").type)

    @Test
    fun gteq_intGteqInt() = assertEquals(TBool, infer("1 >= 2").type)

    @Test
    fun eq_intEqInt() = assertEquals(TBool, infer("1 == 2").type)

    @Test
    fun eq_stringEqString() = assertEquals(TBool, infer("\"a\" == \"b\"").type)

    @Test
    fun neq_intNeqInt() = assertEquals(TBool, infer("1 != 2").type)

    @Test
    fun and_boolAndBool() = assertEquals(TBool, infer("true and false").type)

    @Test
    fun or_boolOrBool() = assertEquals(TBool, infer("true or false").type)

    @Test
    fun neg_int() = assertEquals(TNum, infer("-1").type)

    @Test
    fun neg_double() = assertEquals(TNum, infer("-1.0").type)

    @Test
    fun not_bool() = assertEquals(TBool, infer("not true").type)

    @Test
    fun complexArithmetic() = assertEquals(TNum, infer("1 + 2 * 3").type)

    @Test
    fun comparisonChain() = assertEquals(TBool, infer("1 < 2 == true").type)

    @Test
    fun booleanChain() = assertEquals(TBool, infer("true and false or true").type)

    // Each operand is checked against the operator's expected type, so a binary op with two bad
    // operands reports one mismatch per operand.
    @Test
    fun add_stringPlusString_fails() {
        val errors = infer("\"a\" + \"b\"").errors
        assertEquals(2, errors.size)
        assertMismatch(errors[0], L.Str, L.Num)
        assertMismatch(errors[1], L.Str, L.Num)
    }

    @Test
    fun and_intAndInt_fails() {
        val errors = infer("1 and 2").errors
        assertEquals(2, errors.size)
        assertMismatch(errors[0], L.Num, L.Bool)
        assertMismatch(errors[1], L.Num, L.Bool)
    }

    @Test
    fun not_int_fails() {
        val errors = infer("not 1").errors
        assertEquals(1, errors.size)
        assertMismatch(errors[0], L.Num, L.Bool)
    }

    @Test
    fun neg_bool_fails() {
        val errors = infer("-true").errors
        assertEquals(1, errors.size)
        assertMismatch(errors[0], L.Bool, L.Num)
    }

    /** Assert [error] is a [TypeError.TypeMismatch] reporting [subtype] cannot be used as [supertype]. */
    private fun assertMismatch(
        error: TypeError,
        subtype: L,
        supertype: L,
    ) {
        assertIs<TypeError.TypeMismatch>(error)
        assertEquals(subtype, error.subtype)
        assertEquals(supertype, error.supertype)
    }
}
