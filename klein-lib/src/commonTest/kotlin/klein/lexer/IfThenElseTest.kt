@file:Suppress("ktlint")

package klein.lexer

import klein.TokenKind.*
import kotlin.test.Test

class IfThenElseTest {
    @Test
    fun singleLineIfThenElse() {
        assertTokens(
            "if x then y else z",
            kw(IF), ident("x"), kw(THEN), ident("y"), kw(ELSE), ident("z"), eof,
        )
    }

    @Test
    fun ifThenElseWithElseOnNewLine() {
        val program = """
            if x then y
            else z
        """.trimIndent()
        assertTokens(
            program,
            kw(IF), ident("x"), kw(THEN), ident("y"),
            kw(ELSE), ident("z"), eof,
        )
    }

    @Test
    fun ifThenElseWithBothBranchesOnNewLines() {
        val program = """
            if x
            then y
            else z
        """.trimIndent()
        assertTokens(
            program,
            kw(IF), ident("x"),
            kw(THEN), ident("y"),
            kw(ELSE), ident("z"), eof,
        )
    }

    @Test
    fun ifThenElseInsideLambdaInsideParens() {
        val program = """
            filter(items, |x ->
                if x > 0 then
                    x + 1
                else
                    x - 1
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("filter"), sym('('), ident("items"), sym(','), pipeOpen, ident("x"), sym("->"), blockStart,
            kw(IF), ident("x"), sym('>'), num("0"), kw(THEN), blockStart,
            ident("x"), sym('+'), num("1"), blockEnd,
            kw(ELSE), blockStart, ident("x"), sym('-'), num("1"), blockEnd, blockEnd,
            pipeClose, sym(')'), eof,
        )
    }

    @Test
    fun thenFollowedByPipeWithIndentInsideParens() {
        val program = """
            foo(if x then
                |
                    y = 1
                    y
                |
            else z)
        """.trimIndent()
        assertTokens(
            program,
            ident("foo"), sym('('),
            kw(IF), ident("x"), kw(THEN),
            pipeOpen, blockStart,
            ident("y"), sym('='), num("1"), stmtEnd,
            ident("y"), blockEnd, pipeClose,
            kw(ELSE), ident("z"), sym(')'), eof,
        )
    }

    @Test
    fun elseFollowedByPipeWithIndentInsideParens() {
        val program = """
            foo(if x then y else
                |
                    z = 1
                    z
                |
            )
        """.trimIndent()
        assertTokens(
            program,
            ident("foo"), sym('('),
            kw(IF), ident("x"), kw(THEN), ident("y"),
            kw(ELSE),
            pipeOpen, blockStart,
            ident("z"), sym('='), num("1"), stmtEnd,
            ident("z"), blockEnd, pipeClose, sym(')'), eof,
        )
    }

    @Test
    fun nestedIfThenElseInsidePipeInsideParens() {
        val program = """
            foo(
                |
                    if x then
                        a
                    else
                        b
                |
            )
        """.trimIndent()
        assertTokens(
            program,
            ident("foo"), sym('('), pipeOpen, blockStart,
            kw(IF), ident("x"), kw(THEN), blockStart,
            ident("a"), blockEnd,
            kw(ELSE), blockStart, ident("b"), blockEnd,
            blockEnd, pipeClose, sym(')'), eof,
        )
    }

    @Test
    fun pipeAfterThenOnSameLineInsideParens() {
        val program = """
            foo(if x then |y| else |z|)
        """.trimIndent()
        assertTokens(
            program,
            ident("foo"), sym('('),
            kw(IF), ident("x"), kw(THEN),
            pipeOpen, ident("y"), pipeClose,
            kw(ELSE),
            pipeOpen, ident("z"), pipeClose,
            sym(')'), eof,
        )
    }

    @Test
    fun deeplyNestedPipesAndBlocksInsideParens() {
        val program = """
            foo(
                |x ->
                    if x then
                        |
                            a = 1
                            a
                        |
                    else
                        b
                |
            )
        """.trimIndent()
        assertTokens(
            program,
            ident("foo"), sym('('), pipeOpen, ident("x"), sym("->"), blockStart,
            kw(IF), ident("x"), kw(THEN), blockStart,
            pipeOpen, blockStart,
            ident("a"), sym('='), num("1"), stmtEnd,
            ident("a"), blockEnd, pipeClose, blockEnd,
            kw(ELSE), blockStart, ident("b"), blockEnd,
            blockEnd, pipeClose, sym(')'), eof,
        )
    }
}
