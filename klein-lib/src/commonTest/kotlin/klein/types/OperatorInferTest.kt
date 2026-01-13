package klein.types

import klein.Type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperatorInferTest {
    @Test
    fun add_intPlusInt() {
        assertType(Type.Num, infer("1 + 2"))
    }

    @Test
    fun add_doublePlusDouble() {
        assertType(Type.Num, infer("1.0 + 2.0"))
    }

    @Test
    fun sub_intMinusInt() {
        assertType(Type.Num, infer("5 - 3"))
    }

    @Test
    fun mul_intTimesInt() {
        assertType(Type.Num, infer("2 * 3"))
    }

    @Test
    fun div_intDivInt() {
        assertType(Type.Num, infer("10 / 2"))
    }

    @Test
    fun mod_intModInt() {
        assertType(Type.Num, infer("10 % 3"))
    }

    @Test
    fun lt_intLtInt() {
        assertType(Type.Bool, infer("1 < 2"))
    }

    @Test
    fun lteq_intLteqInt() {
        assertType(Type.Bool, infer("1 <= 2"))
    }

    @Test
    fun gt_intGtInt() {
        assertType(Type.Bool, infer("1 > 2"))
    }

    @Test
    fun gteq_intGteqInt() {
        assertType(Type.Bool, infer("1 >= 2"))
    }

    @Test
    fun eq_intEqInt() {
        assertType(Type.Bool, infer("1 == 2"))
    }

    @Test
    fun eq_stringEqString() {
        assertType(Type.Bool, infer("'a' == 'b'"))
    }

    @Test
    fun neq_intNeqInt() {
        assertType(Type.Bool, infer("1 != 2"))
    }

    @Test
    fun and_boolAndBool() {
        assertType(Type.Bool, infer("true and false"))
    }

    @Test
    fun or_boolOrBool() {
        assertType(Type.Bool, infer("true or false"))
    }

    @Test
    fun neg_int() {
        assertType(Type.Num, infer("-1"))
    }

    @Test
    fun neg_double() {
        assertType(Type.Num, infer("-1.0"))
    }

    @Test
    fun not_bool() {
        assertType(Type.Bool, infer("not true"))
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
        assertType(Type.Num, infer("1 + 2 * 3"))
    }

    @Test
    fun comparisonChain() {
        assertType(Type.Bool, infer("1 < 2 == true"))
    }

    @Test
    fun booleanChain() {
        assertType(Type.Bool, infer("true and false or true"))
    }
}
