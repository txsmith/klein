package klein.types

import klein.Type
import kotlin.test.Test
import kotlin.test.assertEquals

class LiteralInferTest {
    // ========== Integer Literals ==========

    @Test
    fun intLiteral_zero() {
        val type = inferExpr("0")
        assertEquals(Type.TInt, type)
    }

    @Test
    fun intLiteral_positive() {
        val type = inferExpr("42")
        assertEquals(Type.TInt, type)
    }

    // Note: -17 parses as UnaryOp(Neg, IntLiteral(17))
    // This will be handled in Phase 5 (Operators)
    // @Test
    // fun intLiteral_negative() {
    //     val type = inferExpr("-17")
    //     assertEquals(Type.TInt, type)
    // }

    @Test
    fun intLiteral_large() {
        val type = inferExpr("9999999999")
        assertEquals(Type.TInt, type)
    }

    // ========== Double Literals ==========

    @Test
    fun doubleLiteral_zero() {
        val type = inferExpr("0.0")
        assertEquals(Type.TDouble, type)
    }

    @Test
    fun doubleLiteral_positive() {
        val type = inferExpr("3.14")
        assertEquals(Type.TDouble, type)
    }

    // Note: -2.718 parses as UnaryOp(Neg, DoubleLiteral(2.718))
    // This will be handled in Phase 5 (Operators)
    // @Test
    // fun doubleLiteral_negative() {
    //     val type = inferExpr("-2.718")
    //     assertEquals(Type.TDouble, type)
    // }

    @Test
    fun doubleLiteral_noFraction() {
        val type = inferExpr("1.0")
        assertEquals(Type.TDouble, type)
    }

    @Test
    fun doubleLiteral_smallFraction() {
        val type = inferExpr("0.001")
        assertEquals(Type.TDouble, type)
    }

    // ========== String Literals ==========

    @Test
    fun stringLiteral_empty() {
        val type = inferExpr("''")
        assertEquals(Type.TString, type)
    }

    @Test
    fun stringLiteral_simple() {
        val type = inferExpr("'hello'")
        assertEquals(Type.TString, type)
    }

    @Test
    fun stringLiteral_withSpaces() {
        val type = inferExpr("'hello world'")
        assertEquals(Type.TString, type)
    }

    @Test
    fun stringLiteral_withEscapes() {
        val type = inferExpr("'line1\\nline2'")
        assertEquals(Type.TString, type)
    }

    @Test
    fun stringLiteral_withQuotes() {
        val type = inferExpr("'say \\'hi\\''")
        assertEquals(Type.TString, type)
    }

    // ========== Boolean Literals ==========

    @Test
    fun boolLiteral_true() {
        val type = inferExpr("true")
        assertEquals(Type.TBool, type)
    }

    @Test
    fun boolLiteral_false() {
        val type = inferExpr("false")
        assertEquals(Type.TBool, type)
    }
}
