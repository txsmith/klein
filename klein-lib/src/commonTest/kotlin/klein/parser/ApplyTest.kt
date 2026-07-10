package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApplyTest {
    @Test
    fun simpleCall() {
        val expr = parse("f()")
        assertExprEquals(expr, call(id("f")))
    }

    @Test
    fun callWithOneArg() {
        val expr = parse("f(1)")
        assertExprEquals(expr, call(id("f"), int(1)))
    }

    @Test
    fun callWithTwoArgs() {
        val expr = parse("f(1, 2)")
        assertExprEquals(expr, call(id("f"), int(1), int(2)))
    }

    @Test
    fun callWithThreeArgs() {
        val expr = parse("f(1, 2, 3)")
        assertExprEquals(expr, call(id("f"), int(1), int(2), int(3)))
    }

    @Test
    fun callWithExpressionArg() {
        val expr = parse("f(1 + 2)")
        assertExprEquals(expr, call(id("f"), add(int(1), int(2))))
    }

    @Test
    fun callWithMixedArgs() {
        val expr = parse("f(x, 1 + 2, true)")
        assertExprEquals(expr, call(id("f"), id("x"), add(int(1), int(2)), bool(true)))
    }

    @Test
    fun chainedCalls() {
        val expr = parse("f()()")
        assertExprEquals(expr, call(call(id("f"))))
    }

    @Test
    fun chainedCallsWithArgs() {
        val expr = parse("f(1)(2)")
        assertExprEquals(expr, call(call(id("f"), int(1)), int(2)))
    }

    @Test
    fun callOnParenthesizedExpr() {
        val expr = parse("(f)()")
        assertExprEquals(expr, call(id("f")))
    }

    @Test
    fun callOnLambda() {
        val expr = parse("|x -> x|(1)")
        assertExprEquals(expr, call(lambda("x", body = id("x")), int(1)))
    }

    @Test
    fun callOnThunk() {
        val expr = parse("|42|()")
        assertExprEquals(expr, call(lambda(body = int(42))))
    }

    @Test
    fun callInBinaryExpr() {
        val expr = parse("f(1) + g(2)")
        assertExprEquals(expr, add(call(id("f"), int(1)), call(id("g"), int(2))))
    }

    @Test
    fun callWithLambdaArg() {
        val expr = parse("f(|x -> x|)")
        assertExprEquals(expr, call(id("f"), lambda("x", body = id("x"))))
    }

    @Test
    fun callWithNestedCall() {
        val expr = parse("f(g(1))")
        assertExprEquals(expr, call(id("f"), call(id("g"), int(1))))
    }

    @Test
    fun deeplyNestedCalls() {
        val expr = parse("f(g(h(x)))")
        assertExprEquals(expr, call(id("f"), call(id("g"), call(id("h"), id("x")))))
    }

    @Test
    fun callWithNegativeArg() {
        val expr = parse("f(-1)")
        assertExprEquals(expr, call(id("f"), neg(int(1))))
    }

    @Test
    fun callWithNotArg() {
        val expr = parse("f(not true)")
        assertExprEquals(expr, call(id("f"), not(bool(true))))
    }

    @Test
    fun unclosedCall() {
        val error = assertFailsWith<ParseError> { parse("f(1") }
        assertEquals("Expected ')', got Eof", error.message)
    }

    @Test
    fun unclosedCallWithMultipleArgs() {
        val error = assertFailsWith<ParseError> { parse("f(1, 2") }
        assertEquals("Expected ')', got Eof", error.message)
    }

    @Test
    fun trailingCommaInArgs() {
        val error = assertFailsWith<ParseError> { parse("f(1,)") }
        assertEquals("Expected expression, got ')'", error.message)
    }

    @Test
    fun doubleCommaInArgs() {
        val error = assertFailsWith<ParseError> { parse("f(1,,2)") }
        assertEquals("Expected expression, got ','", error.message)
    }

    @Test
    fun applyInComparison() {
        val expr = parse("f(1) < g(2)")
        assertExprEquals(expr, lt(call(id("f"), int(1)), call(id("g"), int(2))))
    }

    @Test
    fun applyInLogicalExpr() {
        val expr = parse("f() and g()")
        assertExprEquals(expr, and(call(id("f")), call(id("g"))))
    }

    @Test
    fun applyAsCondition() {
        val expr = parse("f(x) or g(y) and h(z)")
        assertExprEquals(expr, or(call(id("f"), id("x")), and(call(id("g"), id("y")), call(id("h"), id("z")))))
    }

    @Test
    fun intLiteralAsCallee() {
        val expr = parse("1(2)")
        assertExprEquals(expr, call(int(1), int(2)))
    }

    @Test
    fun boolLiteralAsCallee() {
        val expr = parse("true(1)")
        assertExprEquals(expr, call(bool(true), int(1)))
    }

    @Test
    fun stringLiteralAsCallee() {
        val expr = parse("\"hello\"(1)")
        assertExprEquals(expr, call(string("hello"), int(1)))
    }

    @Test
    fun binaryExprAsCallee() {
        val expr = parse("(1 + 2)(3)")
        assertExprEquals(expr, call(add(int(1), int(2)), int(3)))
    }

    @Test
    fun negatedExprAsCallee() {
        val expr = parse("(-f)(1)")
        assertExprEquals(expr, call(neg(id("f")), int(1)))
    }

    @Test
    fun curriedApplication() {
        val expr = parse("f(1)(2)(3)")
        assertExprEquals(expr, call(call(call(id("f"), int(1)), int(2)), int(3)))
    }

    @Test
    fun keywordAndAsCallee() {
        val error = assertFailsWith<ParseError> { parse("and(1)") }
        assertEquals("Expected expression, got Keyword(AND)", error.message)
    }

    @Test
    fun keywordOrAsCallee() {
        val error = assertFailsWith<ParseError> { parse("or(1)") }
        assertEquals("Expected expression, got Keyword(OR)", error.message)
    }

    @Test
    fun keywordNotAsCallee() {
        val expr = parse("not(1)")
        assertExprEquals(expr, not(int(1)))
    }

    @Test
    fun callWithDoubleArg() {
        val expr = parse("f(3.14)")
        assertExprEquals(expr, call(id("f"), double(3.14)))
    }

    @Test
    fun callWithStringArg() {
        val expr = parse("f(\"hello\")")
        assertExprEquals(expr, call(id("f"), string("hello")))
    }

    @Test
    fun callWithMultipleLambdaArgs() {
        val expr = parse("f(|x -> x|, |y -> y|)")
        assertExprEquals(expr, call(id("f"), lambda("x", body = id("x")), lambda("y", body = id("y"))))
    }

    @Test
    fun callWithComparisonArg() {
        val expr = parse("f(x > 0)")
        assertExprEquals(expr, call(id("f"), gt(id("x"), int(0))))
    }

    @Test
    fun callWithLogicalArg() {
        val expr = parse("f(a and b)")
        assertExprEquals(expr, call(id("f"), and(id("a"), id("b"))))
    }

    @Test
    fun veryLongChain() {
        val expr = parse("f()()()()")
        assertExprEquals(expr, call(call(call(call(id("f"))))))
    }

    @Test
    fun nestedLambdaCall() {
        val expr = parse("|f -> f(1)|(|x -> x + 1|)")
        assertExprEquals(
            expr,
            call(lambda("f", body = call(id("f"), int(1))), lambda("x", body = add(id("x"), int(1)))),
        )
    }

    @Test
    fun spaceBeforeParens() {
        val expr = parse("f (1)")
        assertExprEquals(expr, call(id("f"), int(1)))
    }

    @Test
    fun leadingCommaInArgs() {
        val error = assertFailsWith<ParseError> { parse("f(,1)") }
        assertEquals("Expected expression, got ','", error.message)
    }

    @Test
    fun missingCommaBetweenArgs() {
        val error = assertFailsWith<ParseError> { parse("f(1 2)") }
        assertEquals("Expected ')', got Number(2)", error.message)
    }

    @Test
    fun nestedUnclosedCall() {
        val error = assertFailsWith<ParseError> { parse("f(g(1)") }
        assertEquals("Expected ')', got Eof", error.message)
    }

    @Test
    fun callWithUnclosedLambdaArg() {
        val error = assertFailsWith<ParseError> { parse("f(|x -> x)") }
        assertEquals("Expected '|', got ')'", error.message)
    }

    @Test
    fun emptyParensAsCallee() {
        val error = assertFailsWith<ParseError> { parse("()(1)") }
        assertEquals("Expected expression, got ')'", error.message)
    }

    @Test
    fun justCommaInArgs() {
        val error = assertFailsWith<ParseError> { parse("f(,)") }
        assertEquals("Expected expression, got ','", error.message)
    }

    @Test
    fun plusNotUnary() {
        val error = assertFailsWith<ParseError> { parse("f(+)") }
        assertEquals("Expected expression, got '+'", error.message)
    }

    @Test
    fun multipleTrailingCommas() {
        val error = assertFailsWith<ParseError> { parse("f(1,,)") }
        assertEquals("Expected expression, got ','", error.message)
    }

    @Test
    fun openParenAtEof() {
        val error = assertFailsWith<ParseError> { parse("f(") }
        assertEquals("Expected expression, got Eof", error.message)
    }

    @Test
    fun applyMultiLineLambda() {
        val program =
            """
            |x ->
              y = x + 1
              y
            |(5)
            """.trimIndent()
        assertExprEquals(
            parse(program),
            call(lambda("x", body = block(valStmt("y", add(id("x"), int(1))), id("y"))), int(5)),
        )
    }

    @Test
    fun applyLambdaWithIfThenElse() {
        val program =
            """
            |x ->
              if x > 0 then
                x
              else
                0
            |(5)
            """.trimIndent()
        assertExprEquals(
            parse(program),
            call(
                lambda(
                    "x",
                    body = block(ifThenElse(gt(id("x"), int(0)), block(id("x")), block(int(0)))),
                ),
                int(5),
            ),
        )
    }

    @Test
    fun callWithMultiLineLambdaArg() {
        val program =
            """
            foo(|x ->
              y = x + 1
              y
            |)
            """.trimIndent()
        assertExprEquals(
            parse(program),
            call(id("foo"), lambda("x", body = block(valStmt("y", add(id("x"), int(1))), id("y")))),
        )
    }

    // A parenthesized expression that *starts a new statement line* (at or before the block indent)
    // must not be attached to the previous line as a call. Regression: the postfix loop used to
    // anchor to the stale enclosing indent, swallowing `(bar)` as `foo(bar)`.
    @Test
    fun parenExprOnNextLineIsSeparateStatement() {
        val program =
            """
            |
              foo
              (bar)
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(body = block(id("foo"), id("bar"))),
        )
    }

    // The concrete case that had this parser bug filed against it: two ascriptions of the same
    // variable on adjacent lines are two statements, not `(x: Cat)(x: Dog)`.
    @Test
    fun consecutiveAscriptionsOnAdjacentLinesAreSeparate() {
        val program =
            """
            |x ->
              (x: Cat)
              (x: Dog)
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body = block(ascription(id("x"), typeName("Cat")), ascription(id("x"), typeName("Dog"))),
            ),
        )
    }
}
