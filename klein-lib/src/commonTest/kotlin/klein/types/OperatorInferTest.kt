package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperatorInferTest {
    @Test
    fun add_intPlusInt() {
        assertType("Num", infer("1 + 2"))
    }

    @Test
    fun add_doublePlusDouble() {
        assertType("Num", infer("1.0 + 2.0"))
    }

    @Test
    fun sub_intMinusInt() {
        assertType("Num", infer("5 - 3"))
    }

    @Test
    fun mul_intTimesInt() {
        assertType("Num", infer("2 * 3"))
    }

    @Test
    fun div_intDivInt() {
        assertType("Num", infer("10 / 2"))
    }

    @Test
    fun mod_intModInt() {
        assertType("Num", infer("10 % 3"))
    }

    @Test
    fun lt_intLtInt() {
        assertType("Bool", infer("1 < 2"))
    }

    @Test
    fun lteq_intLteqInt() {
        assertType("Bool", infer("1 <= 2"))
    }

    @Test
    fun gt_intGtInt() {
        assertType("Bool", infer("1 > 2"))
    }

    @Test
    fun gteq_intGteqInt() {
        assertType("Bool", infer("1 >= 2"))
    }

    @Test
    fun eq_intEqInt() {
        assertType("Bool", infer("1 == 2"))
    }

    @Test
    fun eq_stringEqString() {
        assertType("Bool", infer("'a' == 'b'"))
    }

    @Test
    fun neq_intNeqInt() {
        assertType("Bool", infer("1 != 2"))
    }

    @Test
    fun and_boolAndBool() {
        assertType("Bool", infer("true and false"))
    }

    @Test
    fun or_boolOrBool() {
        assertType("Bool", infer("true or false"))
    }

    @Test
    fun neg_int() {
        assertType("Num", infer("-1"))
    }

    @Test
    fun neg_double() {
        assertType("Num", infer("-1.0"))
    }

    @Test
    fun not_bool() {
        assertType("Bool", infer("not true"))
    }

    @Test
    fun add_stringPlusString_fails() {
        val result = inferWithErrors("'a' + 'b'")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun and_intAndInt_fails() {
        val result = inferWithErrors("1 and 2")
        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.all { it is TypeError.TypeMismatch })
    }

    @Test
    fun not_int_fails() {
        val result = inferWithErrors("not 1")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun neg_bool_fails() {
        val result = inferWithErrors("-true")
        assertEquals(1, result.errors.size)
        assertTrue(result.errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun complexArithmetic() {
        assertType("Num", infer("1 + 2 * 3"))
    }

    @Test
    fun comparisonChain() {
        assertType("Bool", infer("1 < 2 == true"))
    }

    @Test
    fun booleanChain() {
        assertType("Bool", infer("true and false or true"))
    }
}
