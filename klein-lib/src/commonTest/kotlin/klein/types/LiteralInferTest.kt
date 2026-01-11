package klein.types

import klein.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals

class LiteralInferTest {
    @Test
    fun intLiteral_zero() {
        val type = infer("0")
        assertEquals(TInt, type)
    }

    @Test
    fun intLiteral_positive() {
        val type = infer("42")
        assertEquals(TInt, type)
    }

    // Note: -17 parses as UnaryOp(Neg, IntLiteral(17))
    // This will be handled in Phase 5 (Operators)
    // @Test
    // fun intLiteral_negative() {
    //     val type = inferExpr("-17")
    //     assertEquals(TInt, type)
    // }

    @Test
    fun intLiteral_large() {
        val type = infer("9999999999")
        assertEquals(TInt, type)
    }

    @Test
    fun doubleLiteral_zero() {
        val type = infer("0.0")
        assertEquals(TDouble, type)
    }

    @Test
    fun doubleLiteral_positive() {
        val type = infer("3.14")
        assertEquals(TDouble, type)
    }

    // Note: -2.718 parses as UnaryOp(Neg, DoubleLiteral(2.718))
    // This will be handled in Phase 5 (Operators)
    // @Test
    // fun doubleLiteral_negative() {
    //     val type = inferExpr("-2.718")
    //     assertEquals(TDouble, type)
    // }

    @Test
    fun doubleLiteral_noFraction() {
        val type = infer("1.0")
        assertEquals(TDouble, type)
    }

    @Test
    fun doubleLiteral_smallFraction() {
        val type = infer("0.001")
        assertEquals(TDouble, type)
    }

    @Test
    fun stringLiteral_empty() {
        val type = infer("''")
        assertEquals(TString, type)
    }

    @Test
    fun stringLiteral_simple() {
        val type = infer("'hello'")
        assertEquals(TString, type)
    }

    @Test
    fun stringLiteral_withSpaces() {
        val type = infer("'hello world'")
        assertEquals(TString, type)
    }

    @Test
    fun stringLiteral_withEscapes() {
        val type = infer("'line1\\nline2'")
        assertEquals(TString, type)
    }

    @Test
    fun stringLiteral_withQuotes() {
        val type = infer("'say \\'hi\\''")
        assertEquals(TString, type)
    }

    @Test
    fun boolLiteral_true() {
        val type = infer("true")
        assertEquals(TBool, type)
    }

    @Test
    fun boolLiteral_false() {
        val type = infer("false")
        assertEquals(TBool, type)
    }
}
