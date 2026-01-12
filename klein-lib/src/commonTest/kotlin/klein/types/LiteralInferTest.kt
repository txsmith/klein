package klein.types

import kotlin.test.Test

class LiteralInferTest {
    @Test
    fun intLiteral_zero() {
        assertType("Num", infer("0"))
    }

    @Test
    fun intLiteral_positive() {
        assertType("Num", infer("42"))
    }

    @Test
    fun intLiteral_negative() {
        assertType("Num", infer("-17"))
    }

    @Test
    fun intLiteral_large() {
        assertType("Num", infer("9999999999"))
    }

    @Test
    fun doubleLiteral_zero() {
        assertType("Num", infer("0.0"))
    }

    @Test
    fun doubleLiteral_positive() {
        assertType("Num", infer("3.14"))
    }

    @Test
    fun doubleLiteral_negative() {
        assertType("Num", infer("-2.718"))
    }

    @Test
    fun doubleLiteral_noFraction() {
        assertType("Num", infer("1.0"))
    }

    @Test
    fun doubleLiteral_smallFraction() {
        assertType("Num", infer("0.001"))
    }

    @Test
    fun stringLiteral_empty() {
        assertType("String", infer("''"))
    }

    @Test
    fun stringLiteral_simple() {
        assertType("String", infer("'hello'"))
    }

    @Test
    fun stringLiteral_withSpaces() {
        assertType("String", infer("'hello world'"))
    }

    @Test
    fun stringLiteral_withEscapes() {
        assertType("String", infer("'line1\\nline2'"))
    }

    @Test
    fun stringLiteral_withQuotes() {
        assertType("String", infer("'say \\'hi\\''"))
    }

    @Test
    fun boolLiteral_true() {
        assertType("Bool", infer("true"))
    }

    @Test
    fun boolLiteral_false() {
        assertType("Bool", infer("false"))
    }
}
