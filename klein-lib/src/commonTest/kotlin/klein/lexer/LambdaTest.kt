@file:Suppress("ktlint")

package klein.lexer

import klein.surface.TokenKind.*
import kotlin.test.Test

class LambdaTest {
    @Test
    fun pipeWithContent() {
        assertTokens("|x|", pipe, ident("x"), pipe, eof)
    }

    @Test
    fun emptyPipePair() {
        assertTokens("||", pipe, pipe, eof)
    }

    @Test
    fun newlineInsidePipeLambda() {
        val program = """
            |
                x = 1
                y
            |
        """.trimIndent()
        assertTokens(
            program,
            pipe(0),
            ident("x", indent = 4), sym('='), num("1"),
            ident("y", indent = 4),
            pipe(0), eof)
    }

    @Test
    fun nestedLambdaWithIndentedBlock() {
        val program = """
            ||
                x = 42
                x
            ||
        """.trimIndent()
        assertTokens(
            program,
            pipe(0), pipe,
            ident("x", indent = 4), sym('='), num("42"),
            ident("x", indent = 4),
            pipe(0), pipe, eof,
        )
    }

    @Test
    fun nestedLambdaWithIndentedBlock2() {
        val program = """
            |
              |
                x = 42
                x
              |
            |
        """.trimIndent()
        assertTokens(
            program,
            pipe(0),
            pipe(2),
            ident("x", indent = 4), sym('='), num("42"),
            ident("x", indent = 4),
            pipe(2),
            pipe(0), eof,
        )
    }

    @Test
    fun tripleNestedLambdaWithIndentedBlock() {
        val program = """
            |||
                x = 42
                x
            |||
        """.trimIndent()
        assertTokens(
            program,
            pipe(0), pipe, pipe,
            ident("x", indent = 4), sym('='), num("42"),
            ident("x", indent = 4),
            pipe(0), pipe, pipe, eof,
        )
    }

    @Test
    fun nestedLambdaInParens() {
        assertTokens("foo(|.x|)", ident("foo"), sym('('), pipe, sym('.'), ident("x"), pipe, sym(')'), eof)
    }

    @Test
    fun bareImplicitParamLambda() {
        assertTokens("|.|", pipe, sym('.'), pipe, eof)
    }

    @Test
    fun nestedLambdas() {
        assertTokens(
            "filter(items, |.orders.any(|.price > 100|)|)",
            ident("filter"), sym('('), ident("items"), sym(','),
            pipe, sym('.'), ident("orders"), sym('.'), ident("any"), sym('('),
            pipe, sym('.'), ident("price"), sym('>'), num("100"), pipe,
            sym(')'), pipe, sym(')'), eof,
        )
    }

    @Test
    fun nestedMultilineLambda() {
        val program = """
            filter(items,
                |.orders.any(
                    1
                )|
            )
        """.trimIndent()
        assertTokens(
            program,
            ident("filter"), sym('('), ident("items"), sym(','),
            pipe, sym('.'), ident("orders"), sym('.'), ident("any"), sym('('),
            num("1"), sym(')'), pipe, sym(')'), eof,
        )
    }

    @Test
    fun nestedMultilineLambdaWithStatement() {
        val program = """
            filter(items,
                |
                    x = 1
                    .orders.any(
                        1
                    )
                |
            )
        """.trimIndent()
        assertTokens(
            program,
            ident("filter", indent = 0), sym('('), ident("items"), sym(','),
            pipe(4),
            ident("x", indent = 8), sym('='), num("1"),
            sym('.', indent = 8), ident("orders"), sym('.'), ident("any"), sym('('),
            num("1", indent = 12),
            sym(')', indent = 8),
            pipe(4),
            sym(')', indent = 0),
            eof,
        )
    }

    @Test
    fun weirdIndentInsideParens() {
        val program = """
            filter(items,
                |.orders.any(
        1
                )|
            )
        """.trimIndent()
        assertTokens(
            program,
            ident("filter", indent = 4), sym('('), ident("items"), sym(','),
            pipe(indent = 8), sym('.'), ident("orders"), sym('.'), ident("any"), sym('('),
            num("1", indent = 0), sym(')', indent = 8), pipe,
            sym(')', indent = 4),
            eof,
        )
    }

    @Test
    fun nestedNoParamLambdas() {
        assertTokens(
            "||42||",
            pipe, pipe, num("42"), pipe, pipe, eof,
        )
    }

    @Test
    fun nestedNoParamLambdasInParens() {
        assertTokens(
            "(||42||)",
            sym('('), pipe, pipe, num("42"), pipe, pipe, sym(')'), eof,
        )
    }

    @Test
    fun doubleNestedNoParamLambdas() {
        assertTokens(
            "(|| || 42 || ||)",
            sym('('),
            pipe, pipe, pipe, pipe,
            num("42"),
            pipe, pipe, pipe, pipe,
            sym(')'), eof,
        )
    }

    @Test
    fun unclosedPipeAfterExpression() {
        assertTokens(
            "x = 42|",
            ident("x"), sym('='), num("42"), pipe, eof,
        )
    }

    @Test
    fun pipeAfterIdentIsOpen() {
        assertTokens(
            "blah|x -> 42|",
            ident("blah"), pipe, ident("x"), sym("->"), num("42"), pipe, eof,
        )
    }

    @Test
    fun lambdaArrowWithIndentedBody() {
        val program = """
            f = |x ->
                y = x + 1
                y
            |
        """.trimIndent()
        assertTokens(
            program,
            ident("f", indent = 0), sym('='), pipe, ident("x"), sym("->"),
            ident("y", indent = 4), sym('='), ident("x"), sym('+'), num("1"),
            ident("y", indent = 4), pipe(0), eof,
        )
    }

    @Test
    fun lambdaInsideParensWithIndentedBody() {
        val program = """
            foo(|
              42
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("foo", indent = 0), sym('('), pipe,
            num("42", indent = 2),
            pipe(0), sym(')'), eof,
        )
    }

    @Test
    fun lambdaInsideParensWithBindingAndIndentedBody() {
        val program = """
            foo(|
              x = 1
              x
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("foo", indent = 0), sym('('), pipe,
            ident("x", indent = 2), sym('='), num("1"),
            ident("x", indent = 2),
            pipe(0), sym(')'), eof,
        )
    }

    @Test
    fun nestedParensInsideLambdaInsideParens() {
        val program = """
            filter(items, |
              x = foo(
                42
              )
              x
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("filter", indent = 0), sym('('), ident("items"), sym(','), pipe,
            ident("x", indent = 2), sym('='), ident("foo"), sym('('),
            num("42", indent = 4),
            sym(')', indent = 2),
            ident("x", indent = 2),
            pipe(0), sym(')'), eof,
        )
    }

    @Test
    fun unclosedParenInsideLambdaClosedByPipe() {
        val program = """
            filter(items, |
              x = foo(
              x > 100
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("filter", indent = 0), sym('('), ident("items"), sym(','), pipe,
            ident("x", indent = 2), sym('='), ident("foo"), sym('('),
            ident("x", indent = 2), sym('>'), num("100"),
            pipe(0), sym(')'), eof,
        )
    }

    @Test
    fun parenClosedBeforePipe() {
        val program = """
            filter(items, |
              x = foo(
              x > 100
            )|
        """.trimIndent()
        assertTokens(
            program,
            ident("filter", indent = 0), sym('('), ident("items"), sym(','), pipe,
            ident("x", indent = 2), sym('='), ident("foo"), sym('('),
            ident("x", indent = 2), sym('>'), num("100"),
            sym(')', indent = 0), pipe, eof,
        )
    }

    @Test
    fun unclosedBracketInsideLambda() {
        val program = """
            map(items, |x ->
              arr[x
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("map", indent = 0), sym('('), ident("items"), sym(','), pipe,
            ident("x"), sym("->"),
            ident("arr", indent = 2), sym('['), ident("x"),
            pipe(0), sym(')'), eof,
        )
    }

    @Test
    fun nestedUnclosedDelimiters() {
        val program = """
            f(|
              a(b[c(
              x
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("f", indent = 0), sym('('), pipe,
            ident("a", indent = 2), sym('('), ident("b"), sym('['), ident("c"), sym('('),
            ident("x", indent = 2),
            pipe(0), sym(')'), eof,
        )
    }

    @Test
    fun pipedThroughMultipleBlocks() {
        val program = """
            f(|
              x =
                y =
                  z
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("f", indent = 0), sym('('), pipe,
            ident("x", indent = 2), sym('='),
            ident("y", indent = 4), sym('='),
            ident("z", indent = 6),
            pipe(0), sym(')'), eof,
        )
    }

    @Test
    fun singleLineUnclosedParenInLambda() {
        assertTokens(
            "f(|x -> g(x|)",
            ident("f"), sym('('), pipe,
            ident("x"), sym("->"), ident("g"), sym('('), ident("x"),
            pipe, sym(')'), eof,
        )
    }

    @Test
    fun singleLineUnclosedBracketInLambda() {
        assertTokens(
            "f(|x -> a[x|)",
            ident("f"), sym('('), pipe,
            ident("x"), sym("->"), ident("a"), sym('['), ident("x"),
            pipe, sym(')'), eof,
        )
    }

    @Test
    fun singleLineParenClosedThroughPipe() {
        assertTokens(
            "f(|x)",
            ident("f"), sym('('), pipe, ident("x"), sym(')'), eof,
        )
    }

    @Test
    fun singleLineMultipleUnclosed() {
        assertTokens(
            "f(|a(b[c|)",
            ident("f"), sym('('), pipe,
            ident("a"), sym('('), ident("b"), sym('['), ident("c"),
            pipe, sym(')'), eof,
        )
    }

    @Test
    fun singleLineNestedLambdasWithUnclosedParen() {
        assertTokens(
            "f(||g(x||)",
            ident("f"), sym('('),
            pipe, pipe, ident("g"), sym('('), ident("x"), pipe, pipe,
            sym(')'), eof,
        )
    }

    @Test
    fun pipeOnSeparateLineInsideParens() {
        val program = """
            foo(
                |
                    x = 1
                    y
                |
            )
        """.trimIndent()
        assertTokens(
            program,
            ident("foo", indent = 0), sym('('),
            pipe(4),
            ident("x", indent = 8), sym('='), num("1"),
            ident("y", indent = 8),
            pipe(4), sym(')'), eof,
        )
    }

    @Test
    fun closingPipeAbsorbsIndentMismatch() {
        val program = """
            foo(
              |
                x = 1
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("foo", indent = 0), sym('('),
            pipe(2),
            ident("x", indent = 4), sym('='), num("1"),
            pipe(0), sym(')'), eof,
        )
    }

    @Test
    fun closingBracketAbsorbsIndentMismatch() {
        val program = """
            foo([
              |
                x = 1
            |])
        """.trimIndent()
        assertTokens(
            program,
            ident("foo", indent = 0), sym('('), sym('['),
            pipe(2),
            ident("x", indent = 4), sym('='), num("1"),
            pipe(0), sym(']'), sym(')'), eof,
        )
    }

    @Test
    fun nestedLambdaWithIndentInsideParens() {
        val program = """
            foo(|x ->
                y = x + 1
                y
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("foo", indent = 0), sym('('), pipe, ident("x"), sym("->"),
            ident("y", indent = 4), sym('='), ident("x"), sym('+'), num("1"),
            ident("y", indent = 4),
            pipe(0), sym(')'), eof,
        )
    }
}
