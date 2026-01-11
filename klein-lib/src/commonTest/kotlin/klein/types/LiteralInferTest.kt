package klein.types

import kotlin.test.Test

class LiteralInferTest {
    @Test
    fun intLiteral_zero() {
        assertType("Int", infer("0"))
    }

    @Test
    fun intLiteral_positive() {
        assertType("Int", infer("42"))
    }

    // Note: -17 parses as UnaryOp(Neg, IntLiteral(17))
    // This will be handled in Phase 5 (Operators)

    @Test
    fun intLiteral_large() {
        assertType("Int", infer("9999999999"))
    }

    @Test
    fun doubleLiteral_zero() {
        assertType("Double", infer("0.0"))
    }

    @Test
    fun doubleLiteral_positive() {
        assertType("Double", infer("3.14"))
    }

    // Note: -2.718 parses as UnaryOp(Neg, DoubleLiteral(2.718))
    // This will be handled in Phase 5 (Operators)

    @Test
    fun doubleLiteral_noFraction() {
        assertType("Double", infer("1.0"))
    }

    @Test
    fun doubleLiteral_smallFraction() {
        assertType("Double", infer("0.001"))
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
