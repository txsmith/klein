package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IfThenElseTest {
    @Test
    fun simpleIfThenElse() {
        val expr = parse("if x then y else z")
        assertExprEquals(expr, ifThenElse(id("x"), id("y"), id("z")))
    }

    @Test
    fun ifWithComparisonCondition() {
        val expr = parse("if x > 0 then 1 else 2")
        assertExprEquals(expr, ifThenElse(gt(id("x"), int(0)), int(1), int(2)))
    }

    @Test
    fun ifWithArithmeticBranches() {
        val expr = parse("if x then y + 1 else z - 1")
        assertExprEquals(
            expr,
            ifThenElse(id("x"), add(id("y"), int(1)), sub(id("z"), int(1))),
        )
    }

    @Test
    fun nestedIfInThenBranch() {
        val expr = parse("if a then if b then c else d else e")
        assertExprEquals(
            expr,
            ifThenElse(id("a"), ifThenElse(id("b"), id("c"), id("d")), id("e")),
        )
    }

    @Test
    fun nestedIfInElseBranch() {
        val expr = parse("if a then b else if c then d else e")
        assertExprEquals(
            expr,
            ifThenElse(id("a"), id("b"), ifThenElse(id("c"), id("d"), id("e"))),
        )
    }

    @Test
    fun nestedIfThenInElseBranch() {
        val expr = parse("if a then b else if c then d")
        assertExprEquals(
            expr,
            ifThenElse(id("a"), id("b"), ifThenElse(id("c"), id("d"))),
        )
    }

    @Test
    fun elseBindsToNearestIf() {
        val expr = parse("if a then if b then c else d")
        assertExprEquals(
            expr,
            ifThenElse(id("a"), ifThenElse(id("b"), id("c"), id("d"))),
        )
    }

    @Test
    fun chainedIfThen() {
        val expr = parse("if a then if b then c")
        assertExprEquals(
            expr,
            ifThenElse(id("a"), ifThenElse(id("b"), id("c"))),
        )
    }

    @Test
    fun ifWithBooleanCondition() {
        val expr = parse("if true then 1 else 0")
        assertExprEquals(expr, ifThenElse(bool(true), int(1), int(0)))
    }

    @Test
    fun ifWithAndCondition() {
        val expr = parse("if x and y then 1 else 0")
        assertExprEquals(expr, ifThenElse(and(id("x"), id("y")), int(1), int(0)))
    }

    @Test
    fun ifWithOrCondition() {
        val expr = parse("if x or y then 1 else 0")
        assertExprEquals(expr, ifThenElse(or(id("x"), id("y")), int(1), int(0)))
    }

    @Test
    fun ifInsideLambda() {
        val expr = parse("|x -> if x then 1 else 0|")
        assertExprEquals(
            expr,
            lambda("x", body = ifThenElse(id("x"), int(1), int(0))),
        )
    }

    @Test
    fun ifInsideParameterlessLambda() {
        val expr = parse("|if x then y else z|")
        assertExprEquals(
            expr,
            lambda(body = ifThenElse(id("x"), id("y"), id("z"))),
        )
    }

    @Test
    fun ifWithLambdaBranches() {
        val expr = parse("if x then |y| else |z|")
        assertExprEquals(
            expr,
            ifThenElse(id("x"), lambda(body = id("y")), lambda(body = id("z"))),
        )
    }

    @Test
    fun ifWithFunctionCallCondition() {
        val expr = parse("if isValid(x) then y else z")
        assertExprEquals(
            expr,
            ifThenElse(call(id("isValid"), id("x")), id("y"), id("z")),
        )
    }

    @Test
    fun ifAsArgument() {
        val expr = parse("foo(if x then 1 else 2)")
        assertExprEquals(
            expr,
            call(id("foo"), ifThenElse(id("x"), int(1), int(2))),
        )
    }

    @Test
    fun missingThen() {
        val error = assertFailsWith<ParseError> { parse("if x else y") }
        assertEquals("Expected 'then', got Keyword(ELSE, span=SourceSpan(start=5, end=9))", error.message)
    }

    @Test
    fun ifThenWithoutElse() {
        val expr = parse("if x then y")
        assertExprEquals(expr, ifThenElse(id("x"), id("y")))
    }

    @Test
    fun ifThenWithoutElseWithComparison() {
        val expr = parse("if x > 0 then doSomething()")
        assertExprEquals(
            expr,
            ifThenElse(gt(id("x"), int(0)), call(id("doSomething"))),
        )
    }

    @Test
    fun ifThenWithoutElseInsideLambda() {
        val expr = parse("|x -> if x then print(x)|")
        assertExprEquals(
            expr,
            lambda("x", body = ifThenElse(id("x"), call(id("print"), id("x")))),
        )
    }

    @Test
    fun ifWithBlockInThenBranch() {
        val program =
            """
            |x ->
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
                "x",
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
    fun ifWithBlockInElseBranch() {
        val program =
            """
            |x ->
              if x > 0 then
                1
              else
                y = x - 1
                y
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    ifThenElse(
                        gt(id("x"), int(0)),
                        int(1),
                        block(valStmt("y", sub(id("x"), int(1))), expr = id("y")),
                    ),
            ),
        )
    }

    @Test
    fun ifWithBlocksInBothBranches() {
        val program =
            """
            |x ->
              if x > 0 then
                a = x + 1
                a
              else
                b = x - 1
                b
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    ifThenElse(
                        gt(id("x"), int(0)),
                        block(valStmt("a", add(id("x"), int(1))), expr = id("a")),
                        block(valStmt("b", sub(id("x"), int(1))), expr = id("b")),
                    ),
            ),
        )
    }

    @Test
    fun ifThenWithBlockNoElse() {
        val program =
            """
            |x ->
              if x > 0 then
                y = x * 2
                print(y)
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    ifThenElse(
                        gt(id("x"), int(0)),
                        block(valStmt("y", mul(id("x"), int(2))), expr = call(id("print"), id("y"))),
                    ),
            ),
        )
    }

    @Test
    fun nestedIfWithBlocks() {
        val program =
            """
            |x ->
              if x > 0 then
                if x > 10 then
                  y = x
                  y
                else
                  0
              else
                -1
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    ifThenElse(
                        gt(id("x"), int(0)),
                        ifThenElse(
                            gt(id("x"), int(10)),
                            block(valStmt("y", id("x")), expr = id("y")),
                            int(0),
                        ),
                        neg(int(1)),
                    ),
            ),
        )
    }

    @Test
    fun ifWithLambdaInBlock() {
        val program =
            """
            |x ->
              if x then
                f = |y -> y + 1|
                f(x)
              else
                0
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    ifThenElse(
                        id("x"),
                        block(
                            valStmt("f", lambda("y", body = add(id("y"), int(1)))),
                            expr = call(id("f"), id("x")),
                        ),
                        int(0),
                    ),
            ),
        )
    }

    @Test
    fun missingCondition() {
        val error = assertFailsWith<ParseError> { parse("if then y else z") }
        assertEquals("Expected expression, got Keyword(THEN, span=SourceSpan(start=3, end=7))", error.message)
    }

    @Test
    fun missingThenBranch() {
        val error = assertFailsWith<ParseError> { parse("if x then else z") }
        assertEquals("Expected expression, got Keyword(ELSE, span=SourceSpan(start=10, end=14))", error.message)
    }

    @Test
    fun missingElseBranch() {
        val error = assertFailsWith<ParseError> { parse("if x then y else") }
        assertEquals("Expected expression, got Eof(span=SourceSpan(start=16, end=16))", error.message)
    }

    @Test
    fun ifThenNoElseFollowedByStatement() {
        val program =
            """
            |x ->
              if x > 0 then
                print(x)
              doSomethingElse()
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    block(
                        ifThenElse(gt(id("x"), int(0)), call(id("print"), id("x"))),
                        expr = call(id("doSomethingElse")),
                    ),
            ),
        )
    }

    @Test
    fun ifThenNoElseWithBlockFollowedByStatement() {
        val program =
            """
            |x ->
              if x > 0 then
                y = x + 1
                print(y)
              doSomethingElse()
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    block(
                        ifThenElse(
                            gt(id("x"), int(0)),
                            block(valStmt("y", add(id("x"), int(1))), expr = call(id("print"), id("y"))),
                        ),
                        expr = call(id("doSomethingElse")),
                    ),
            ),
        )
    }

    @Test
    fun nestedIfThenNoElseWithBlocks() {
        val program =
            """
            |x ->
              if x > 0 then
                if x > 10 then
                  y = x
                  print(y)
              done()
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    block(
                        ifThenElse(
                            gt(id("x"), int(0)),
                            ifThenElse(
                                gt(id("x"), int(10)),
                                block(valStmt("y", id("x")), expr = call(id("print"), id("y"))),
                            ),
                        ),
                        expr = call(id("done")),
                    ),
            ),
        )
    }

    @Test
    fun multipleIfThenNoElseInSequence() {
        val program =
            """
            |x ->
              if x > 0 then
                print(1)
              if x > 10 then
                print(2)
              done()
            |
            """.trimIndent()
        assertExprEquals(
            parse(program),
            lambda(
                "x",
                body =
                    block(
                        ifThenElse(gt(id("x"), int(0)), call(id("print"), int(1))),
                        ifThenElse(gt(id("x"), int(10)), call(id("print"), int(2))),
                        expr = call(id("done")),
                    ),
            ),
        )
    }
}
