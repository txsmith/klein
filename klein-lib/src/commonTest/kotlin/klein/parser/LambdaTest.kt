package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LambdaTest {
    @Test
    fun simpleLambdaNoParam() {
        val expr = parse("|42|")
        assertExprEquals(expr, lambda(body = int(42)))
    }

    @Test
    fun lambdaWithParam() {
        val expr = parse("|x -> x|")
        assertExprEquals(expr, lambda("x", body = id("x")))
    }

    @Test
    fun lambdaWithExpressionBody() {
        val expr = parse("|n -> n + 1|")
        assertExprEquals(expr, lambda("n", body = add(id("n"), int(1))))
    }

    @Test
    fun lambdaNoParamWithExpression() {
        val expr = parse("|1 + 2|")
        assertExprEquals(expr, lambda(body = add(int(1), int(2))))
    }

    @Test
    fun lambdaWithComplexBody() {
        val expr = parse("|x -> x * x|")
        assertExprEquals(expr, lambda("x", body = mul(id("x"), id("x"))))
    }

    @Test
    fun nestedLambda() {
        val expr = parse("|x -> |y -> x + y||")
        assertExprEquals(expr, lambda("x", body = lambda("y", body = add(id("x"), id("y")))))
    }

    @Test
    fun lambdaInBinaryExpr() {
        val expr = parse("|1| + |2|")
        assertExprEquals(expr, add(lambda(body = int(1)), lambda(body = int(2))))
    }

    @Test
    fun lambdaWithBooleanBody() {
        val expr = parse("|x -> x > 0|")
        assertExprEquals(expr, lambda("x", body = gt(id("x"), int(0))))
    }

    @Test
    fun lambdaWithLogicalBody() {
        val expr = parse("|x -> x > 0 and x < 100|")
        assertExprEquals(expr, lambda("x", body = and(gt(id("x"), int(0)), lt(id("x"), int(100)))))
    }

    @Test
    fun lambdaWithMultipleParams() {
        val expr = parse("|x, y -> x + y|")
        assertExprEquals(expr, lambda("x", "y", body = add(id("x"), id("y"))))
    }

    @Test
    fun lambdaWithThreeParams() {
        val expr = parse("|a, b, c -> a + b + c|")
        assertExprEquals(expr, lambda("a", "b", "c", body = add(add(id("a"), id("b")), id("c"))))
    }

    @Test
    fun lambdaWithNegationBody() {
        val expr = parse("|x -> -x|")
        assertExprEquals(expr, lambda("x", body = neg(id("x"))))
    }

    @Test
    fun lambdaWithNotBody() {
        val expr = parse("|x -> not x|")
        assertExprEquals(expr, lambda("x", body = not(id("x"))))
    }

    @Test
    fun lambdaWithDoubleNegation() {
        val expr = parse("|x -> --x|")
        assertExprEquals(expr, lambda("x", body = neg(neg(id("x")))))
    }

    @Test
    fun lambdaNoParamWithNegation() {
        val expr = parse("|-1|")
        assertExprEquals(expr, lambda(body = neg(int(1))))
    }

    @Test
    fun lambdaNoParamWithNot() {
        val expr = parse("|not true|")
        assertExprEquals(expr, lambda(body = not(bool(true))))
    }

    @Test
    fun thunkReturningLiteral() {
        val expr = parse("|42|")
        assertExprEquals(expr, lambda(body = int(42)))
    }

    @Test
    fun thunkReturningIdentifier() {
        val expr = parse("|x|")
        assertExprEquals(expr, lambda(body = id("x")))
    }

    @Test
    fun thunkReturningExpression() {
        val expr = parse("|1 + 2 * 3|")
        assertExprEquals(expr, lambda(body = add(int(1), mul(int(2), int(3)))))
    }

    @Test
    fun thunkReturningLambda() {
        val expr = parse("||x -> x||")
        assertExprEquals(expr, lambda(body = lambda("x", body = id("x"))))
    }

    @Test
    fun thunkReturningThunk() {
        val expr = parse("||42||")
        assertExprEquals(expr, lambda(body = lambda(body = int(42))))
    }

    @Test
    fun thunkWithParenthesizedBody() {
        val expr = parse("|(1 + 2)|")
        assertExprEquals(expr, lambda(body = add(int(1), int(2))))
    }

    @Test
    fun unclosedLambda() {
        val error = assertFailsWith<ParseError> { parse("|42") }
        assertEquals("Expected '|', got Eof(span=SourceSpan(start=3, end=3))", error.message)
    }

    @Test
    fun unclosedLambdaWithParam() {
        val error = assertFailsWith<ParseError> { parse("|x -> x") }
        assertEquals("Expected '|', got Eof(span=SourceSpan(start=7, end=7))", error.message)
    }

    @Test
    fun emptyLambda() {
        val error = assertFailsWith<ParseError> { parse("||") }
        assertEquals("Expected expression, got Eof(span=SourceSpan(start=2, end=2))", error.message)
    }

    @Test
    fun missingArrowAfterParams() {
        val error = assertFailsWith<ParseError> { parse("|x, y x + y|") }
        assertEquals("Expected '->', got Ident(name=x, span=SourceSpan(start=6, end=7))", error.message)
    }

    @Test
    fun trailingCommaInParams() {
        val error = assertFailsWith<ParseError> { parse("|x, y, -> x|") }
        assertEquals("Expected '->', got Symbol(text=,, span=SourceSpan(start=5, end=6))", error.message)
    }

    @Test
    fun missingBodyAfterArrow() {
        val error = assertFailsWith<ParseError> { parse("|x -> |") }
        assertEquals("Expected expression, got Eof(span=SourceSpan(start=7, end=7))", error.message)
    }

    @Test
    fun lambdaWithOnlyArrow() {
        val error = assertFailsWith<ParseError> { parse("| -> 1|") }
        assertEquals("Expected expression, got Symbol(text=->, span=SourceSpan(start=2, end=4))", error.message)
    }

    @Test
    fun keywordTrueAsParam() {
        val error = assertFailsWith<ParseError> { parse("|true -> 1|") }
        assertEquals("Expected '|', got Symbol(text=->, span=SourceSpan(start=6, end=8))", error.message)
    }

    @Test
    fun keywordIfAsParam() {
        val error = assertFailsWith<ParseError> { parse("|if -> 1|") }
        assertEquals("Expected expression, got Symbol(text=->, span=SourceSpan(start=4, end=6))", error.message)
    }

    @Test
    fun keywordAndAsParam() {
        val error = assertFailsWith<ParseError> { parse("|and -> 1|") }
        assertEquals("Expected expression, got Keyword(AND, span=SourceSpan(start=1, end=4))", error.message)
    }

    @Test
    fun parensAroundSingleParam() {
        val error = assertFailsWith<ParseError> { parse("|(x) -> x|") }
        assertEquals("Expected '|', got Symbol(text=->, span=SourceSpan(start=5, end=7))", error.message)
    }

    @Test
    fun parensAroundMultipleParams() {
        val error = assertFailsWith<ParseError> { parse("|(x, y) -> x + y|") }
        assertEquals("Expected ')', got Symbol(text=,, span=SourceSpan(start=3, end=4))", error.message)
    }

    @Test
    fun lambdaBodyWithApplication() {
        val expr = parse("|x -> x(1)|")
        assertExprEquals(expr, lambda("x", body = call(id("x"), int(1))))
    }

    @Test
    fun lambdaBodyWithChainedApplication() {
        val expr = parse("|f -> f(1)(2)|")
        assertExprEquals(expr, lambda("f", body = call(call(id("f"), int(1)), int(2))))
    }

    @Test
    fun lambdaBodyWithNestedApplication() {
        val expr = parse("|f -> f(g(1))|")
        assertExprEquals(expr, lambda("f", body = call(id("f"), call(id("g"), int(1)))))
    }

    @Test
    fun curriedLambda() {
        val expr = parse("|x -> |y -> x + y||")
        assertExprEquals(expr, lambda("x", body = lambda("y", body = add(id("x"), id("y")))))
    }

    @Test
    fun curriedLambdaApplied() {
        val expr = parse("|x -> |y -> x + y||(1)")
        assertExprEquals(expr, call(lambda("x", body = lambda("y", body = add(id("x"), id("y")))), int(1)))
    }

    @Test
    fun curriedLambdaFullyApplied() {
        val expr = parse("|x -> |y -> x + y||(1)(2)")
        assertExprEquals(expr, call(call(lambda("x", body = lambda("y", body = add(id("x"), id("y")))), int(1)), int(2)))
    }

    @Test
    fun triplyCurriedLambda() {
        val expr = parse("|x -> |y -> |z -> x + y + z|||")
        assertExprEquals(
            expr,
            lambda("x", body = lambda("y", body = lambda("z", body = add(add(id("x"), id("y")), id("z"))))),
        )
    }

    @Test
    fun lambdaWithSingleStatement() {
        val program =
            """
            |x ->
              y = x + 1
              y * 2
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda("x", body = block(valStmt("y", add(id("x"), int(1))), expr = mul(id("y"), int(2)))),
        )
    }

    @Test
    fun lambdaWithMultipleStatements() {
        val program =
            """
            |x ->
              a = x + 1
              b = a * 2
              a + b
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body = block(valStmt("a", add(id("x"), int(1))), valStmt("b", mul(id("a"), int(2))), expr = add(id("a"), id("b"))),
            ),
        )
    }

    @Test
    fun lambdaNoParamWithStatements() {
        val program =
            """
            |
              x = 1
              y = 2
              x + y
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(body = block(valStmt("x", int(1)), valStmt("y", int(2)), expr = add(id("x"), id("y")))),
        )
    }

    @Test
    fun lambdaWithCompactStatements() {
        val program =
            """
            |x ->
              y = x
              y
            |
            """.trimIndent()
        assertExprEquals(parse(program), lambda("x", body = block(valStmt("y", id("x")), expr = id("y"))))
    }

    @Test
    fun nestedLambdaWithStatements() {
        val program =
            """
            |x ->
              f = |y ->
                z = y + 1
                z
              |
              f(x)
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    block(
                        valStmt("f", lambda("y", body = block(valStmt("z", add(id("y"), int(1))), expr = id("z")))),
                        expr = call(id("f"), id("x")),
                    ),
            ),
        )
    }

    @Test
    fun lambdaWithMultilineStatement() {
        val program =
            """
            |x ->
              y = x +
                1
              y
            |
            """.trimIndent()
        assertExprEquals(parse(program), lambda("x", body = block(valStmt("y", add(id("x"), int(1))), expr = id("y"))))
    }

    @Test
    fun lambdaWithArrowNoParamsIsError() {
        val error = assertFailsWith<ParseError> { parse("|-> 42|") }
        assertEquals("Expected expression, got Symbol(text=->, span=SourceSpan(start=1, end=3))", error.message)
    }

    @Test
    fun multipleExpressionsInBlock() {
        val program =
            """
            |x ->
              1
              2
            |
            """.trimIndent()
        assertExprEquals(parse(program), lambda("x", body = block(int(1), expr = int(2))))
    }

    @Test
    fun tripleNestedNoParamLambda() {
        val expr = parse("|||42|||")
        assertExprEquals(expr, lambda(body = lambda(body = lambda(body = int(42)))))
    }

    @Test
    fun nestedNoParamLambdaInParens() {
        val expr = parse("(||42||)")
        assertExprEquals(expr, lambda(body = lambda(body = int(42))))
    }

    @Test
    fun quadrupleNestedNoParamLambda() {
        val expr = parse("(|| || 42 || ||)")
        assertExprEquals(expr, lambda(body = lambda(body = lambda(body = lambda(body = int(42))))))
    }

    @Test
    fun lambdaAsArgumentToCall() {
        val expr = parse("map(items, |x -> x + 1|)")
        assertExprEquals(expr, call(id("map"), id("items"), lambda("x", body = add(id("x"), int(1)))))
    }

    @Test
    fun noParamLambdaAsArgument() {
        val expr = parse("foo(|42|)")
        assertExprEquals(expr, call(id("foo"), lambda(body = int(42))))
    }

    @Test
    fun lambdaWithBlockAsArgument() {
        val program =
            """
            foo(|
              x = 1
              x
            |)
            """.trimIndent()
        assertExprEquals(parse(program), call(id("foo"), lambda(body = block(valStmt("x", int(1)), expr = id("x")))))
    }

    @Test
    fun lambdaWithNestedCallAsArgument() {
        val program =
            """
            filter(items, |
              x = foo(42)
              x
            |)
            """.trimIndent()
        assertExprEquals(
            parse(program),
            call(id("filter"), id("items"), lambda(body = block(valStmt("x", call(id("foo"), int(42))), expr = id("x")))),
        )
    }

    @Test
    fun lambdaArgumentWithParamAndBlock() {
        val program =
            """
            foo(|x ->
              y = x + 1
              y
            |)
            """.trimIndent()
        assertExprEquals(
            parse(program),
            call(id("foo"), lambda("x", body = block(valStmt("y", add(id("x"), int(1))), expr = id("y")))),
        )
    }

    @Test
    fun multipleDedentsAtOnce() {
        val program =
            """
            |
              a = |
                b = |
                  1
                |
                b
              |
              a
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                body =
                    block(
                        valStmt(
                            "a",
                            lambda(
                                body =
                                    block(
                                        valStmt("b", lambda(body = int(1))),
                                        expr = id("b"),
                                    ),
                            ),
                        ),
                        expr = id("a"),
                    ),
            ),
        )
    }

    @Test
    fun expressionContinuationInBlock() {
        val program =
            """
            |x ->
              y = 1 +
                2
              y
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda("x", body = block(valStmt("y", add(int(1), int(2))), expr = id("y"))),
        )
    }

    @Test
    fun expressionContinuationWithDeeperIndent() {
        val program =
            """
            |x ->
              y = 1
                + 2
                + 3
              y
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda("x", body = block(valStmt("y", add(add(int(1), int(2)), int(3))), expr = id("y"))),
        )
    }

    @Test
    fun deeplyNestedBlocks() {
        val program =
            """
            |
              a = |
                b = |
                  c = |
                    42
                  |
                  c
                |
                b
              |
              a
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                body =
                    block(
                        valStmt(
                            "a",
                            lambda(
                                body =
                                    block(
                                        valStmt(
                                            "b",
                                            lambda(
                                                body =
                                                    block(
                                                        valStmt("c", lambda(body = int(42))),
                                                        expr = id("c"),
                                                    ),
                                            ),
                                        ),
                                        expr = id("b"),
                                    ),
                            ),
                        ),
                        expr = id("a"),
                    ),
            ),
        )
    }

    @Test
    fun blockWithCallsInsideParens() {
        val program =
            """
            |x ->
              result = foo(
                a,
                b
              )
              result
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    block(
                        valStmt("result", call(id("foo"), id("a"), id("b"))),
                        expr = id("result"),
                    ),
            ),
        )
    }

    @Test
    fun noHeadWithIfThenElse() {
        assertExprEquals(
            parse("|if x then y else z|"),
            lambda(body = ifThenElse(id("x"), id("y"), id("z"))),
        )
    }

    @Test
    fun noHeadWithIfThenNoElse() {
        assertExprEquals(
            parse("|if x then y|"),
            lambda(body = ifThenElse(id("x"), id("y"))),
        )
    }

    @Test
    fun noHeadWithIfAndBlock() {
        val program =
            """
            |
              if x > 0 then
                y = x + 1
                y
              else
                0
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                body =
                    ifThenElse(
                        gt(id("x"), int(0)),
                        block(valStmt("y", add(id("x"), int(1))), expr = id("y")),
                        int(0),
                    ),
            ),
        )
    }

    @Test
    fun noHeadWithIfThenNoElseAndBlock() {
        val program =
            """
            |
              if x > 0 then
                y = x + 1
                print(y)
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                body =
                    ifThenElse(
                        gt(id("x"), int(0)),
                        block(valStmt("y", add(id("x"), int(1))), expr = call(id("print"), id("y"))),
                    ),
            ),
        )
    }

    @Test
    fun noHeadWithNestedIfThen() {
        val program =
            """
            |
              if a then
                if b then
                  print(1)
              done()
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                body =
                    block(
                        ifThenElse(
                            id("a"),
                            ifThenElse(id("b"), call(id("print"), int(1))),
                        ),
                        expr = call(id("done")),
                    ),
            ),
        )
    }

    @Test
    fun noHeadWithMultipleIfThen() {
        val program =
            """
            |
              if a then
                print(1)
              if b then
                print(2)
              done()
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                body =
                    block(
                        ifThenElse(id("a"), call(id("print"), int(1))),
                        ifThenElse(id("b"), call(id("print"), int(2))),
                        expr = call(id("done")),
                    ),
            ),
        )
    }

    @Test
    fun noHeadIfThenWithValAndIf() {
        val program =
            """
            |
              x = getValue()
              if x > 0 then
                print(x)
              finish()
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                body =
                    block(
                        valStmt("x", call(id("getValue"))),
                        ifThenElse(gt(id("x"), int(0)), call(id("print"), id("x"))),
                        expr = call(id("finish")),
                    ),
            ),
        )
    }

    @Test
    fun inlineBindingInLambdaIsError() {
        val error = assertFailsWith<ParseError> { parse("|x -> y = 1 y|") }
        assertEquals("Expected '|', got Symbol(text==, span=SourceSpan(start=8, end=9))", error.message)
    }

    @Test
    fun multipleInlineBindingsInLambdaIsError() {
        val error = assertFailsWith<ParseError> { parse("|x -> a = 1 b = 2 a + b|") }
        assertEquals("Expected '|', got Symbol(text==, span=SourceSpan(start=8, end=9))", error.message)
    }

    @Test
    fun inlineBindingInHeadlessLambdaIsError() {
        val error = assertFailsWith<ParseError> { parse("|y = 1 y|") }
        assertEquals("Expected '|', got Symbol(text==, span=SourceSpan(start=3, end=4))", error.message)
    }
}
