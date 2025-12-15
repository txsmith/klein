package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValTest {
    @Test
    fun simpleBinding() {
        val stmt = parseStmt("x = 1")
        assertStmtEquals(stmt, valStmt("x", int(1)))
    }

    @Test
    fun bindingWithExpression() {
        val stmt = parseStmt("sum = 1 + 2")
        assertStmtEquals(stmt, valStmt("sum", add(int(1), int(2))))
    }

    @Test
    fun bindingWithComplexExpression() {
        val stmt = parseStmt("result = a * b + c")
        assertStmtEquals(stmt, valStmt("result", add(mul(id("a"), id("b")), id("c"))))
    }

    @Test
    fun bindingWithLambda() {
        val stmt = parseStmt("f = |x -> x + 1|")
        assertStmtEquals(stmt, valStmt("f", lambda("x", body = add(id("x"), int(1)))))
    }

    @Test
    fun bindingWithThunk() {
        val stmt = parseStmt("lazy = |42|")
        assertStmtEquals(stmt, valStmt("lazy", lambda(body = int(42))))
    }

    @Test
    fun bindingWithCall() {
        val stmt = parseStmt("y = f(x)")
        assertStmtEquals(stmt, valStmt("y", call(id("f"), id("x"))))
    }

    @Test
    fun bindingWithBoolean() {
        val stmt = parseStmt("flag = true")
        assertStmtEquals(stmt, valStmt("flag", bool(true)))
    }

    @Test
    fun bindingWithLogicalExpr() {
        val stmt = parseStmt("check = a and b or c")
        assertStmtEquals(stmt, valStmt("check", or(and(id("a"), id("b")), id("c"))))
    }

    @Test
    fun bindingWithComparison() {
        val stmt = parseStmt("isPositive = x > 0")
        assertStmtEquals(stmt, valStmt("isPositive", gt(id("x"), int(0))))
    }

    @Test
    fun bindingWithString() {
        val stmt = parseStmt("name = 'hello'")
        assertStmtEquals(stmt, valStmt("name", string("hello")))
    }

    @Test
    fun bindingWithDouble() {
        val stmt = parseStmt("pi = 3.14")
        assertStmtEquals(stmt, valStmt("pi", double(3.14)))
    }

    @Test
    fun bindingWithNegation() {
        val stmt = parseStmt("neg = -x")
        assertStmtEquals(stmt, valStmt("neg", neg(id("x"))))
    }

    @Test
    fun bindingWithNot() {
        val stmt = parseStmt("inverted = not flag")
        assertStmtEquals(stmt, valStmt("inverted", not(id("flag"))))
    }

    @Test
    fun bindingWithParenthesized() {
        val stmt = parseStmt("grouped = (1 + 2) * 3")
        assertStmtEquals(stmt, valStmt("grouped", mul(add(int(1), int(2)), int(3))))
    }

    @Test
    fun bindingWithNestedCalls() {
        val stmt = parseStmt("composed = f(g(x))")
        assertStmtEquals(stmt, valStmt("composed", call(id("f"), call(id("g"), id("x")))))
    }

    @Test
    fun bindingWithCurriedLambda() {
        val stmt = parseStmt("add = |x -> |y -> x + y||")
        assertStmtEquals(stmt, valStmt("add", lambda("x", body = lambda("y", body = add(id("x"), id("y"))))))
    }

    @Test
    fun underscoreInName() {
        val stmt = parseStmt("my_var = 1")
        assertStmtEquals(stmt, valStmt("my_var", int(1)))
    }

    @Test
    fun missingValue() {
        val error = assertFailsWith<ParseError> { parseStmt("x =") }
        assertEquals("Expected expression, got Eof(span=SourceSpan(start=3, end=3))", error.message)
    }

    @Test
    fun keywordAsName() {
        val error = assertFailsWith<ParseError> { parseStmt("true = 1") }
        assertEquals("Expected newline or end of input, got Symbol(text==, span=SourceSpan(start=5, end=6))", error.message)
    }

    @Test
    fun numberAsName() {
        val error = assertFailsWith<ParseError> { parseStmt("123 = 1") }
        assertEquals("Expected newline or end of input, got Symbol(text==, span=SourceSpan(start=4, end=5))", error.message)
    }
}
