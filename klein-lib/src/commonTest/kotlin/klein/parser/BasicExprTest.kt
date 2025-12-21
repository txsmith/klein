package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BasicExprTest {
    @Test
    fun intLiteral() {
        val expr = parse("42")
        assertExprEquals(expr, int(42))
    }

    @Test
    fun doubleLiteral() {
        val expr = parse("3.14")
        assertExprEquals(expr, double(3.14))
    }

    @Test
    fun stringLiteral() {
        val expr = parse("'hello'")
        assertExprEquals(expr, string("hello"))
    }

    @Test
    fun trueLiteral() {
        val expr = parse("true")
        assertExprEquals(expr, bool(true))
    }

    @Test
    fun falseLiteral() {
        val expr = parse("false")
        assertExprEquals(expr, bool(false))
    }

    @Test
    fun identifier() {
        val expr = parse("foo")
        assertExprEquals(expr, id("foo"))
    }

    @Test
    fun identifierWithUnderscore() {
        val expr = parse("foo_bar")
        assertExprEquals(expr, id("foo_bar"))
    }

    @Test
    fun simpleAddition() {
        val expr = parse("1 + 2")
        assertExprEquals(expr, add(int(1), int(2)))
    }

    @Test
    fun chainedAddition() {
        val expr = parse("1 + 2 + 3")
        assertExprEquals(expr, add(add(int(1), int(2)), int(3)))
    }

    @Test
    fun precedenceMulOverAdd() {
        val expr = parse("1 + 2 * 3")
        assertExprEquals(expr, add(int(1), mul(int(2), int(3))))
    }

    @Test
    fun precedenceAddOverComparison() {
        val expr = parse("a + b < c")
        assertExprEquals(expr, lt(add(id("a"), id("b")), id("c")))
    }

    @Test
    fun precedenceComparisonOverAnd() {
        val expr = parse("a < b and c < d")
        assertExprEquals(expr, and(lt(id("a"), id("b")), lt(id("c"), id("d"))))
    }

    @Test
    fun precedenceAndOverOr() {
        val expr = parse("a or b and c")
        assertExprEquals(expr, or(id("a"), and(id("b"), id("c"))))
    }

    @Test
    fun allComparisonOps() {
        assertExprEquals(parse("a == b"), eq(id("a"), id("b")))
        assertExprEquals(parse("a != b"), neq(id("a"), id("b")))
        assertExprEquals(parse("a < b"), lt(id("a"), id("b")))
        assertExprEquals(parse("a <= b"), lte(id("a"), id("b")))
        assertExprEquals(parse("a > b"), gt(id("a"), id("b")))
        assertExprEquals(parse("a >= b"), gte(id("a"), id("b")))
    }

    @Test
    fun allArithmeticOps() {
        assertExprEquals(parse("a + b"), add(id("a"), id("b")))
        assertExprEquals(parse("a - b"), sub(id("a"), id("b")))
        assertExprEquals(parse("a * b"), mul(id("a"), id("b")))
        assertExprEquals(parse("a / b"), div(id("a"), id("b")))
        assertExprEquals(parse("a % b"), mod(id("a"), id("b")))
    }

    @Test
    fun unaryNegation() {
        val expr = parse("-42")
        assertExprEquals(expr, neg(int(42)))
    }

    @Test
    fun unaryNot() {
        val expr = parse("not true")
        assertExprEquals(expr, not(bool(true)))
    }

    @Test
    fun unaryNegationOfIdentifier() {
        val expr = parse("-x")
        assertExprEquals(expr, neg(id("x")))
    }

    @Test
    fun unaryNotOfIdentifier() {
        val expr = parse("not x")
        assertExprEquals(expr, not(id("x")))
    }

    @Test
    fun doubleNegation() {
        val expr = parse("--1")
        assertExprEquals(expr, neg(neg(int(1))))
    }

    @Test
    fun doubleNot() {
        val expr = parse("not not x")
        assertExprEquals(expr, not(not(id("x"))))
    }

    @Test
    fun negationBindsTighterThanMultiplication() {
        val expr = parse("-2 * 3")
        assertExprEquals(expr, mul(neg(int(2)), int(3)))
    }

    @Test
    fun negationBindsTighterThanAddition() {
        val expr = parse("1 + -2")
        assertExprEquals(expr, add(int(1), neg(int(2))))
    }

    @Test
    fun notBindsTighterThanAnd() {
        val expr = parse("not a and b")
        assertExprEquals(expr, and(not(id("a")), id("b")))
    }

    @Test
    fun parenthesizedExpression() {
        val expr = parse("(42)")
        assertExprEquals(expr, int(42))
    }

    @Test
    fun parenthesesOverridePrecedence() {
        assertExprEquals(parse("(1 + 2) * 3"), mul(add(int(1), int(2)), int(3)))
        assertExprEquals(parse("(a or b) and true"), and(or(id("a"), id("b")), bool(true)))
    }

    @Test
    fun nestedParentheses() {
        val expr = parse("((1))")
        assertExprEquals(expr, int(1))
    }

    @Test
    fun negationOfParenthesizedExpression() {
        val expr = parse("-(1 + 2)")
        assertExprEquals(expr, neg(add(int(1), int(2))))
    }

    @Test
    fun notOfParenthesizedExpression() {
        val expr = parse("not (a and b)")
        assertExprEquals(expr, not(and(id("a"), id("b"))))
    }

    @Test
    fun unexpectedOperatorAtStart() {
        val error = assertFailsWith<ParseError> { parse("* 2") }
        assertEquals("Expected expression, got Symbol(text=*, span=SourceSpan(start=0, end=1))", error.message)
    }

    @Test
    fun unexpectedEof() {
        val error = assertFailsWith<ParseError> { parse("") }
        assertEquals("Expected expression, got Eof(span=SourceSpan(start=0, end=0))", error.message)
    }

    @Test
    fun unclosedParen() {
        val error = assertFailsWith<ParseError> { parse("(1 + 2") }
        assertEquals("Expected ')', got Eof(span=SourceSpan(start=6, end=6))", error.message)
    }

    @Test
    fun unclosedNestedParen() {
        val error = assertFailsWith<ParseError> { parse("((1 + 2)") }
        assertEquals("Expected ')', got Eof(span=SourceSpan(start=8, end=8))", error.message)
    }

    @Test
    fun incompleteIfExpression() {
        val error = assertFailsWith<ParseError> { parse("if") }
        assertEquals("Expected expression, got Eof(span=SourceSpan(start=2, end=2))", error.message)
    }

    @Test
    fun missingRightOperand() {
        val error = assertFailsWith<ParseError> { parse("1 +") }
        assertEquals("Expected expression, got Eof(span=SourceSpan(start=3, end=3))", error.message)
    }

    @Test
    fun missingOperandAfterNot() {
        val error = assertFailsWith<ParseError> { parse("not") }
        assertEquals("Expected expression, got Eof(span=SourceSpan(start=3, end=3))", error.message)
    }

    @Test
    fun missingOperandAfterNegation() {
        val error = assertFailsWith<ParseError> { parse("-") }
        assertEquals("Expected expression, got Eof(span=SourceSpan(start=1, end=1))", error.message)
    }

    @Test
    fun emptyParens() {
        val error = assertFailsWith<ParseError> { parse("()") }
        assertEquals("Expected expression, got Symbol(text=), span=SourceSpan(start=1, end=2))", error.message)
    }

    @Test
    fun errorSpanForUnclosedParen() {
        val error = assertFailsWith<ParseError> { parse("(42") }
        assertEquals(3, error.span.start)
    }

    @Test
    fun errorSpanForUnexpectedOperator() {
        val error = assertFailsWith<ParseError> { parse("1 + * 2") }
        assertEquals(4, error.span.start)
    }
}
