package klein.interp

import klein.interp.Value
import klein.interp.Value.VBool
import klein.interp.Value.VNull
import klein.interp.Value.VNum
import klein.interp.Value.VStr
import kotlin.test.Test
import kotlin.test.assertEquals

class LiteralEvalTest {
    @Test
    fun numbers() {
        assertEvaluatesTo(VNum(42.0), "42")
        assertEvaluatesTo(VNum(2.5), "2.5")
    }

    @Test
    fun strings() = assertEvaluatesTo(VStr("hello"), "\"hello\"")

    @Test
    fun booleans() {
        assertEvaluatesTo(VBool(true), "true")
        assertEvaluatesTo(VBool(false), "false")
    }

    @Test
    fun nullLiteral() = assertEvaluatesTo(VNull, "null")

    @Test
    fun ascriptionIsTransparent() = assertEvaluatesTo(VNum(1.0), "(1 : Num)")

    @Test
    fun stringEscapesDecode() {
        assertEvaluatesTo(VStr("line1\nline2"), "\"line1\\nline2\"")
        assertEvaluatesTo(VStr("say \"hi\""), "\"say \\\"hi\\\"\"")
    }

    @Test
    fun emptyString() = assertEvaluatesTo(VStr(""), "\"\"")

    @Test
    fun edgeNumbers() {
        assertEvaluatesTo(VNum(9999999999.0), "9999999999")
        assertEvaluatesTo(VNum(0.001), "0.001")
        assertEvaluatesTo(VNum(-17.0), "-17")
    }

    @Test
    fun valuePrinting() {
        assertEquals("42", Value.print(runSource("42")))
        assertEquals("2.5", Value.print(runSource("2.5")))
        assertEquals("\"hi\"", Value.print(runSource("\"hi\"")))
        assertEquals("{ x = 1 }", Value.print(runSource("{ x = 1 }")))
    }
}
