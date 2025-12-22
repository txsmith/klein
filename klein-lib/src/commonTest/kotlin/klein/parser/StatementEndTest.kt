package klein.parser

import kotlin.test.Test

class StatementEndTest {
    @Test
    fun newlineAfterOperatorContinuesExpression() {
        val program =
            """
            x = 1 +
            2
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", add(int(1), int(2)))))
    }

    @Test
    fun newlineAfterEqualsContinuesExpression() {
        val program =
            """
            x =
            1
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", int(1))))
    }

    @Test
    fun newlineAfterCommaContinuesExpression() {
        val program =
            """
            x = foo(a,
            b)
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", call(id("foo"), id("a"), id("b")))))
    }

    @Test
    fun newlineAfterOpenParenContinuesExpression() {
        val program =
            """
            x = foo(
            a, b)
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", call(id("foo"), id("a"), id("b")))))
    }

    @Test
    fun newlineInsideParensContinuesExpression() {
        val program =
            """
            x = (1 +
            2)
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", add(int(1), int(2)))))
    }

    @Test
    fun newlineBeforeOperatorContinuesExpression() {
        val program =
            """
            x = 1
            + 2
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", add(int(1), int(2)))))
    }

    @Test
    fun complexMultilineExpression() {
        val program =
            """
            result = foo(
                a + b,
                c * d
            )
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("result", call(id("foo"), add(id("a"), id("b")), mul(id("c"), id("d"))))))
    }

    @Test
    fun chainedOperatorsAcrossLines() {
        val program =
            """
            x = 1 +
            2 +
            3
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", add(add(int(1), int(2)), int(3)))))
    }

    @Test
    fun booleanOperatorsAcrossLines() {
        val program =
            """
            x = a and
            b or
            c
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", or(and(id("a"), id("b")), id("c")))))
    }

    @Test
    fun newlineAfterLambdaArrowContinues() {
        val program =
            """
            f = |x ->
            x + 1|
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("f", lambda("x", body = add(id("x"), int(1))))))
    }

    @Test
    fun newlineAfterComparisonOperatorContinues() {
        val program =
            """
            x = a ==
            b
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", eq(id("a"), id("b")))))
    }

    @Test
    fun newlineAfterComparisonGteContinues() {
        val program =
            """
            x = a >=
            b
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", gte(id("a"), id("b")))))
    }

    @Test
    fun newlineAfterUnaryNotContinues() {
        val program =
            """
            x = not
            true
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", not(bool(true)))))
    }

    @Test
    fun multipleBlankLinesInContinuation() {
        val program =
            """
            x = 1 +

            2
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", add(int(1), int(2)))))
    }

    @Test
    fun newlineAfterLambdaOpenPipeContinues() {
        val program =
            """
            f = |
            x -> x|
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("f", lambda("x", body = id("x")))))
    }

    @Test
    fun newlineAfterCloseParenEndsThenNextStatement() {
        val program =
            """
            x = foo(1)
            y = bar(2)
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", call(id("foo"), int(1))), valStmt("y", call(id("bar"), int(2)))))
    }

    @Test
    fun newlineAfterCloseParenDoesNotChainCalls() {
        val program =
            """
            x = foo()
            (1)
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", call(id("foo"))), int(1)))
    }

    @Test
    fun newlineAfterLambdaClosePipeEndsStatement() {
        val program =
            """
            f = |x -> x|
            g = |y -> y|
            """.trimIndent()
        assertProgramEquals(
            parseProgram(program),
            listOf(valStmt("f", lambda("x", body = id("x"))), valStmt("g", lambda("y", body = id("y")))),
        )
    }

    @Test
    fun newlineAfterIdentifierEndsStatement() {
        val program =
            """
            x = a
            y = b
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", id("a")), valStmt("y", id("b"))))
    }

    @Test
    fun newlineAfterIntLiteralEndsStatement() {
        val program =
            """
            x = 1
            y = 2
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", int(1)), valStmt("y", int(2))))
    }

    @Test
    fun newlineAfterBoolLiteralEndsStatement() {
        val program =
            """
            x = true
            y = false
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", bool(true)), valStmt("y", bool(false))))
    }

    @Test
    fun newlineAfterStringLiteralEndsStatement() {
        val program =
            """
            x = 'hello'
            y = 'world'
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", string("hello")), valStmt("y", string("world"))))
    }

    @Test
    fun lambdaFollowedByParensOnNextLineDoesNotApply() {
        val program =
            """
            f = |x -> x|
            (1 + 2)
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("f", lambda("x", body = id("x"))), add(int(1), int(2))))
    }

    @Test
    fun chainedCallsBrokenByNewline() {
        val program =
            """
            x = foo(1)
            (2)
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", call(id("foo"), int(1))), int(2)))
    }

    @Test
    fun chainedCallsContinueWithIndent() {
        val program =
            """
            x = foo()
              (1)
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", call(call(id("foo")), int(1)))))
    }

    @Test
    fun minusAfterNewlineIsUnaryNotBinary() {
        val program =
            """
            x = 1
            -2
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", int(1)), neg(int(2))))
    }

    @Test
    fun commentDoesNotAffectContinuation() {
        val program =
            """
            x = 1 + # adding
            2
            """.trimIndent()
        assertProgramEquals(parseProgram(program), listOf(valStmt("x", add(int(1), int(2)))))
    }
}
