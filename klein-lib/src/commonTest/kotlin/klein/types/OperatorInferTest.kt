package klein.types

import klein.types.DisplayType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperatorInferTest {
    @Test
    fun add_intPlusInt() {
        assertType(DNum, infer("1 + 2"))
    }

    @Test
    fun add_doublePlusDouble() {
        assertType(DNum, infer("1.0 + 2.0"))
    }

    @Test
    fun sub_intMinusInt() {
        assertType(DNum, infer("5 - 3"))
    }

    @Test
    fun mul_intTimesInt() {
        assertType(DNum, infer("2 * 3"))
    }

    @Test
    fun div_intDivInt() {
        assertType(DNum, infer("10 / 2"))
    }

    @Test
    fun mod_intModInt() {
        assertType(DNum, infer("10 % 3"))
    }

    @Test
    fun lt_intLtInt() {
        assertType(DBool, infer("1 < 2"))
    }

    @Test
    fun lteq_intLteqInt() {
        assertType(DBool, infer("1 <= 2"))
    }

    @Test
    fun gt_intGtInt() {
        assertType(DBool, infer("1 > 2"))
    }

    @Test
    fun gteq_intGteqInt() {
        assertType(DBool, infer("1 >= 2"))
    }

    @Test
    fun eq_intEqInt() {
        assertType(DBool, infer("1 == 2"))
    }

    @Test
    fun eq_stringEqString() {
        assertType(DBool, infer("'a' == 'b'"))
    }

    @Test
    fun neq_intNeqInt() {
        assertType(DBool, infer("1 != 2"))
    }

    @Test
    fun and_boolAndBool() {
        assertType(DBool, infer("true and false"))
    }

    @Test
    fun or_boolOrBool() {
        assertType(DBool, infer("true or false"))
    }

    @Test
    fun neg_int() {
        assertType(DNum, infer("-1"))
    }

    @Test
    fun neg_double() {
        assertType(DNum, infer("-1.0"))
    }

    @Test
    fun not_bool() {
        assertType(DBool, infer("not true"))
    }

    @Test
    fun add_stringPlusString_fails() {
        val result = inferWithErrors("'a' + 'b'")
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
        assertType(DNum, infer("1 + 2 * 3"))
    }

    @Test
    fun comparisonChain() {
        assertType(DBool, infer("1 < 2 == true"))
    }

    @Test
    fun booleanChain() {
        assertType(DBool, infer("true and false or true"))
    }
}
