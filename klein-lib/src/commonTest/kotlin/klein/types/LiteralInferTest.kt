package klein.types

import klein.types.DisplayType.*
import kotlin.test.Test

class LiteralInferTest {
    @Test
    fun intLiteral_zero() {
        assertType(DNum, infer("0"))
    }

    @Test
    fun intLiteral_positive() {
        assertType(DNum, infer("42"))
    }

    @Test
    fun intLiteral_negative() {
        assertType(DNum, infer("-17"))
    }

    @Test
    fun intLiteral_large() {
        assertType(DNum, infer("9999999999"))
    }

    @Test
    fun doubleLiteral_zero() {
        assertType(DNum, infer("0.0"))
    }

    @Test
    fun doubleLiteral_positive() {
        assertType(DNum, infer("3.14"))
    }

    @Test
    fun doubleLiteral_negative() {
        assertType(DNum, infer("-2.718"))
    }

    @Test
    fun doubleLiteral_noFraction() {
        assertType(DNum, infer("1.0"))
    }

    @Test
    fun doubleLiteral_smallFraction() {
        assertType(DNum, infer("0.001"))
    }

    @Test
    fun stringLiteral_empty() {
        assertType(DString, infer("''"))
    }

    @Test
    fun stringLiteral_simple() {
        assertType(DString, infer("'hello'"))
    }

    @Test
    fun stringLiteral_withSpaces() {
        assertType(DString, infer("'hello world'"))
    }

    @Test
    fun stringLiteral_withEscapes() {
        assertType(DString, infer("'line1\\nline2'"))
    }

    @Test
    fun stringLiteral_withQuotes() {
        assertType(DString, infer("'say \\'hi\\''"))
    }

    @Test
    fun boolLiteral_true() {
        assertType(DBool, infer("true"))
    }

    @Test
    fun boolLiteral_false() {
        assertType(DBool, infer("false"))
    }
}
