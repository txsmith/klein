package klein.types

import klein.Type
import kotlin.test.Test

class LiteralInferTest {
    @Test
    fun intLiteral_zero() {
        assertType(Type.Num, infer("0"))
    }

    @Test
    fun intLiteral_positive() {
        assertType(Type.Num, infer("42"))
    }

    @Test
    fun intLiteral_negative() {
        assertType(Type.Num, infer("-17"))
    }

    @Test
    fun intLiteral_large() {
        assertType(Type.Num, infer("9999999999"))
    }

    @Test
    fun doubleLiteral_zero() {
        assertType(Type.Num, infer("0.0"))
    }

    @Test
    fun doubleLiteral_positive() {
        assertType(Type.Num, infer("3.14"))
    }

    @Test
    fun doubleLiteral_negative() {
        assertType(Type.Num, infer("-2.718"))
    }

    @Test
    fun doubleLiteral_noFraction() {
        assertType(Type.Num, infer("1.0"))
    }

    @Test
    fun doubleLiteral_smallFraction() {
        assertType(Type.Num, infer("0.001"))
    }

    @Test
    fun stringLiteral_empty() {
        assertType(Type.Str, infer("\"\""))
    }

    @Test
    fun stringLiteral_simple() {
        assertType(Type.Str, infer("\"hello\""))
    }

    @Test
    fun stringLiteral_withSpaces() {
        assertType(Type.Str, infer("\"hello world\""))
    }

    @Test
    fun stringLiteral_withEscapes() {
        assertType(Type.Str, infer("\"line1\\nline2\""))
    }

    @Test
    fun stringLiteral_withQuotes() {
        assertType(Type.Str, infer("\"say \\\"hi\\\"\""))
    }

    @Test
    fun boolLiteral_true() {
        assertType(Type.Bool, infer("true"))
    }

    @Test
    fun boolLiteral_false() {
        assertType(Type.Bool, infer("false"))
    }
}
