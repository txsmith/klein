package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals

class LiteralTypeCheckTest {
    @Test
    fun intLiteral_zero() = assertEquals(TNum, infer("0").type)

    @Test
    fun intLiteral_positive() = assertEquals(TNum, infer("42").type)

    @Test
    fun intLiteral_negative() = assertEquals(TNum, infer("-17").type)

    @Test
    fun intLiteral_large() = assertEquals(TNum, infer("9999999999").type)

    @Test
    fun doubleLiteral_zero() = assertEquals(TNum, infer("0.0").type)

    @Test
    fun doubleLiteral_positive() = assertEquals(TNum, infer("3.14").type)

    @Test
    fun doubleLiteral_negative() = assertEquals(TNum, infer("-2.718").type)

    @Test
    fun doubleLiteral_noFraction() = assertEquals(TNum, infer("1.0").type)

    @Test
    fun doubleLiteral_smallFraction() = assertEquals(TNum, infer("0.001").type)

    @Test
    fun stringLiteral_empty() = assertEquals(TStr, infer("\"\"").type)

    @Test
    fun stringLiteral_simple() = assertEquals(TStr, infer("\"hello\"").type)

    @Test
    fun stringLiteral_withSpaces() = assertEquals(TStr, infer("\"hello world\"").type)

    @Test
    fun stringLiteral_withEscapes() = assertEquals(TStr, infer("\"line1\\nline2\"").type)

    @Test
    fun stringLiteral_withQuotes() = assertEquals(TStr, infer("\"say \\\"hi\\\"\"").type)

    @Test
    fun boolLiteral_true() = assertEquals(TBool, infer("true").type)

    @Test
    fun boolLiteral_false() = assertEquals(TBool, infer("false").type)
}
