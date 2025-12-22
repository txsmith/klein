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
            ident("filter", indent = 0), sym('('), ident("items"), sym(','), pipe, ident("x"), sym("->"),
            kw(IF, indent = 4), ident("x"), sym('>'), num("0"), kw(THEN),
            ident("x", indent = 8), sym('+'), num("1"),
            kw(ELSE, indent = 4),
            ident("x", indent = 8), sym('-'), num("1"),
            pipe(0), sym(')'), eof,
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
            ident("foo", indent = 0), sym('('),
            kw(IF), ident("x"), kw(THEN),
            pipe(4),
            ident("y", indent = 8), sym('='), num("1"),
            ident("y", indent = 8),
            pipe(4),
            kw(ELSE, indent = 0), ident("z"), sym(')'), eof,
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
            ident("foo", indent = 0), sym('('),
            kw(IF), ident("x"), kw(THEN), ident("y"),
            kw(ELSE),
            pipe(4),
            ident("z", indent = 8), sym('='), num("1"),
            ident("z", indent = 8),
            pipe(4), sym(')'), eof,
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
            ident("foo", indent = 0), sym('('),
            pipe(4),
            kw(IF, indent = 8), ident("x"), kw(THEN),
            ident("a", indent = 12),
            kw(ELSE, indent = 8),
            ident("b", indent = 12),
            pipe(4), sym(')'), eof,
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
            pipe, ident("y"), pipe,
            kw(ELSE),
            pipe, ident("z"), pipe,
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
            ident("foo", indent = 0), sym('('),
            pipe(4), ident("x"), sym("->"),
            kw(IF, indent = 8), ident("x"), kw(THEN),
            pipe(12),
            ident("a", indent = 16), sym('='), num("1"),
            ident("a", indent = 16),
            pipe(12),
            kw(ELSE, indent = 8),
            ident("b", indent = 12),
            pipe(4), sym(')', indent = 0), eof,
        )
    }
}
