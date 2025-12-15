package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertFailsWith

class StatementEndTest {
    @Test
    fun newlineAfterOperatorContinuesExpression() {
        val prog =
            parseProgram(
                """
                x = 1 +
                2
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("x", add(int(1), int(2)))))
    }

    @Test
    fun newlineAfterEqualsContinuesExpression() {
        val prog =
            parseProgram(
                """
                x =
                1
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("x", int(1))))
    }

    @Test
    fun newlineAfterCommaContinuesExpression() {
        val prog =
            parseProgram(
                """
                x = foo(a,
                b)
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(valStmt("x", call(id("foo"), id("a"), id("b")))),
        )
    }

    @Test
    fun newlineAfterOpenParenContinuesExpression() {
        val prog =
            parseProgram(
                """
                x = foo(
                a, b)
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(valStmt("x", call(id("foo"), id("a"), id("b")))),
        )
    }

    @Test
    fun newlineInsideParensContinuesExpression() {
        val prog =
            parseProgram(
                """
                x = (1 +
                2)
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("x", add(int(1), int(2)))))
    }

    @Test
    fun newlineBeforeOperatorEndsStatement() {
        assertFailsWith<ParseError> {
            parseProgram(
                """
                x = 1
                + 2
                """.trimIndent(),
            )
        }
    }

    @Test
    fun complexMultilineExpression() {
        val prog =
            parseProgram(
                """
                result = foo(
                    a + b,
                    c * d
                )
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt(
                    "result",
                    call(id("foo"), add(id("a"), id("b")), mul(id("c"), id("d"))),
                ),
            ),
        )
    }

    @Test
    fun chainedOperatorsAcrossLines() {
        val prog =
            parseProgram(
                """
                x = 1 +
                2 +
                3
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("x", add(add(int(1), int(2)), int(3)))))
    }

    @Test
    fun booleanOperatorsAcrossLines() {
        val prog =
            parseProgram(
                """
                x = a and
                b or
                c
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("x", or(and(id("a"), id("b")), id("c")))))
    }

    @Test
    fun newlineAfterLambdaArrowContinues() {
        val prog =
            parseProgram(
                """
                f = |x ->
                x + 1|
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("f", lambda("x", body = add(id("x"), int(1))))))
    }

    @Test
    fun newlineAfterComparisonOperatorContinues() {
        val prog =
            parseProgram(
                """
                x = a ==
                b
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("x", eq(id("a"), id("b")))))
    }

    @Test
    fun newlineAfterComparisonGteContinues() {
        val prog =
            parseProgram(
                """
                x = a >=
                b
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("x", gte(id("a"), id("b")))))
    }

    @Test
    fun newlineAfterUnaryNotContinues() {
        val prog =
            parseProgram(
                """
                x = not
                true
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("x", not(bool(true)))))
    }

    @Test
    fun multipleBlankLinesInContinuation() {
        val prog =
            parseProgram(
                """
                x = 1 +

                2
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("x", add(int(1), int(2)))))
    }

    @Test
    fun newlineAfterLambdaOpenPipeContinues() {
        val prog =
            parseProgram(
                """
                f = |
                x -> x|
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("f", lambda("x", body = id("x")))))
    }

    @Test
    fun newlineAfterCloseParenEndsThenNextStatement() {
        val prog =
            parseProgram(
                """
                x = foo(1)
                y = bar(2)
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("x", call(id("foo"), int(1))),
                valStmt("y", call(id("bar"), int(2))),
            ),
        )
    }

    @Test
    fun newlineAfterCloseParenDoesNotChainCalls() {
        val prog =
            parseProgram(
                """
                x = foo()
                (1)
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("x", call(id("foo"))),
                int(1),
            ),
        )
    }

    @Test
    fun newlineAfterLambdaClosePipeEndsStatement() {
        val prog =
            parseProgram(
                """
                f = |x -> x|
                g = |y -> y|
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("f", lambda("x", body = id("x"))),
                valStmt("g", lambda("y", body = id("y"))),
            ),
        )
    }

    @Test
    fun newlineAfterIdentifierEndsStatement() {
        val prog =
            parseProgram(
                """
                x = a
                y = b
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("x", id("a")),
                valStmt("y", id("b")),
            ),
        )
    }

    @Test
    fun newlineAfterIntLiteralEndsStatement() {
        val prog =
            parseProgram(
                """
                x = 1
                y = 2
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("x", int(1)),
                valStmt("y", int(2)),
            ),
        )
    }

    @Test
    fun newlineAfterBoolLiteralEndsStatement() {
        val prog =
            parseProgram(
                """
                x = true
                y = false
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("x", bool(true)),
                valStmt("y", bool(false)),
            ),
        )
    }

    @Test
    fun newlineAfterStringLiteralEndsStatement() {
        val prog =
            parseProgram(
                """
                x = 'hello'
                y = 'world'
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("x", string("hello")),
                valStmt("y", string("world")),
            ),
        )
    }

    @Test
    fun lambdaFollowedByParensOnNextLineDoesNotApply() {
        val prog =
            parseProgram(
                """
                f = |x -> x|
                (1 + 2)
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("f", lambda("x", body = id("x"))),
                add(int(1), int(2)),
            ),
        )
    }

    @Test
    fun chainedCallsBrokenByNewline() {
        val prog =
            parseProgram(
                """
                x = foo(1)
                (2)
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("x", call(id("foo"), int(1))),
                int(2),
            ),
        )
    }

    @Test
    fun minusAfterNewlineIsUnaryNotBinary() {
        val prog =
            parseProgram(
                """
                x = 1
                -2
                """.trimIndent(),
            )
        assertProgramEquals(
            prog,
            listOf(
                valStmt("x", int(1)),
                neg(int(2)),
            ),
        )
    }

    @Test
    fun commentDoesNotAffectContinuation() {
        val prog =
            parseProgram(
                """
                x = 1 + # adding
                2
                """.trimIndent(),
            )
        assertProgramEquals(prog, listOf(valStmt("x", add(int(1), int(2)))))
    }
}
