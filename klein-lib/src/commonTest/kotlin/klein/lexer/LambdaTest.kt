@file:Suppress("ktlint")

package klein.lexer

import klein.TokenKind.*
import kotlin.test.Test

class LambdaTest {
    @Test
    fun pipeWithContent() {
        assertTokens("|x|", pipeOpen, ident("x"), pipeClose, eof)
    }

    @Test
    fun emptyPipePair() {
        assertTokens("||", pipeOpen, pipeOpen, eof)
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
            pipeOpen,
            blockStart, ident("x"), sym('='), num("1"), stmtEnd,
            ident("y"),
            blockEnd, pipeClose, eof)
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
            pipeOpen, pipeOpen, blockStart,
            ident("x"), sym('='), num("42"), stmtEnd,
            ident("x"),
            blockEnd, pipeClose, pipeClose, eof,
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
            pipeOpen,
            blockStart, pipeOpen,
            blockStart, ident("x"), sym('='), num("42"), stmtEnd,
            ident("x"),
            blockEnd, pipeClose,
            blockEnd, pipeClose, eof,
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
            pipeOpen, pipeOpen, pipeOpen, blockStart,
            ident("x"), sym('='), num("42"), stmtEnd,
            ident("x"),
            blockEnd, pipeClose, pipeClose, pipeClose, eof,
        )
    }

    @Test
    fun nestedLambdaInParens() {
        assertTokens("foo(|.x|)", ident("foo"), sym('('), pipeOpen, sym('.'), ident("x"), pipeClose, sym(')'), eof)
    }

    @Test
    fun bareImplicitParamLambda() {
        assertTokens("|.|", pipeOpen, sym('.'), pipeClose, eof)
    }

    @Test
    fun nestedLambdas() {
        assertTokens(
            "filter(items, |.orders.any(|.price > 100|)|)",
            ident("filter"), sym('('), ident("items"), sym(','),
            pipeOpen, sym('.'), ident("orders"), sym('.'), ident("any"), sym('('),
            pipeOpen, sym('.'), ident("price"), sym('>'), num("100"), pipeClose,
            sym(')'), pipeClose, sym(')'), eof,
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
            pipeOpen, sym('.'), ident("orders"), sym('.'), ident("any"), sym('('),
            num("1"), sym(')'), pipeClose, sym(')'), eof,
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
            ident("filter"), sym('('), ident("items"), sym(','),
            pipeOpen, blockStart,
            ident("x"), sym('='), num("1"), stmtEnd,
            sym('.'), ident("orders"), sym('.'), ident("any"), sym('('),
            num("1"), sym(')'),
            blockEnd, pipeClose, sym(')'), eof,
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
            ident("filter"), sym('('), ident("items"), sym(','),
            pipeOpen, sym('.'), ident("orders"), sym('.'), ident("any"), sym('('),
            num("1"), sym(')'), pipeClose, sym(')'), eof,
        )
    }

    @Test
    fun nestedNoParamLambdas() {
        assertTokens(
            "||42||",
            pipeOpen, pipeOpen, num("42"), pipeClose, pipeClose, eof,
        )
    }

    @Test
    fun nestedNoParamLambdasInParens() {
        assertTokens(
            "(||42||)",
            sym('('), pipeOpen, pipeOpen, num("42"), pipeClose, pipeClose, sym(')'), eof,
        )
    }

    @Test
    fun doubleNestedNoParamLambdas() {
        assertTokens(
            "(|| || 42 || ||)",
            sym('('),
            pipeOpen, pipeOpen, pipeOpen, pipeOpen,
            num("42"),
            pipeClose, pipeClose, pipeClose, pipeClose,
            sym(')'), eof,
        )
    }

    @Test
    fun unclosedPipeAfterExpression() {
        assertTokens(
            "x = 42|",
            ident("x"), sym('='), num("42"), pipeOpen, eof,
        )
    }

    @Test
    fun pipeAfterIdentIsOpen() {
        assertTokens(
            "blah|x -> 42|",
            ident("blah"), pipeOpen, ident("x"), sym("->"), num("42"), pipeClose, eof,
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
            ident("f"), sym('='), pipeOpen, ident("x"), sym("->"), blockStart,
            ident("y"), sym('='), ident("x"), sym('+'), num("1"), stmtEnd,
            ident("y"), blockEnd, pipeClose, eof,
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
            ident("foo"), sym('('), pipeOpen,
            blockStart, num("42"),
            blockEnd, pipeClose, sym(')'), eof,
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
            ident("foo"), sym('('), pipeOpen,
            blockStart, ident("x"), sym('='), num("1"), stmtEnd,
            ident("x"),
            blockEnd, pipeClose, sym(')'), eof,
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
            ident("filter"), sym('('), ident("items"), sym(','), pipeOpen,
            blockStart, ident("x"), sym('='), ident("foo"), sym('('),
            num("42"),
            sym(')'), stmtEnd,
            ident("x"),
            blockEnd, pipeClose, sym(')'), eof,
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
            ident("filter"), sym('('), ident("items"), sym(','), pipeOpen,
            blockStart, ident("x"), sym('='), ident("foo"), sym('('),
            ident("x"), sym('>'), num("100"),
            blockEnd, pipeClose, sym(')'), eof,
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
            ident("filter"), sym('('), ident("items"), sym(','), pipeOpen,
            blockStart, ident("x"), sym('='), ident("foo"), sym('('),
            ident("x"), sym('>'), num("100"),
            sym(')'), blockEnd, pipeClose, eof,
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
            ident("map"), sym('('), ident("items"), sym(','), pipeOpen,
            ident("x"), sym("->"),
            blockStart, ident("arr"), sym('['), ident("x"),
            blockEnd, pipeClose, sym(')'), eof,
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
            ident("f"), sym('('), pipeOpen,
            blockStart, ident("a"), sym('('), ident("b"), sym('['), ident("c"), sym('('),
            ident("x"),
            blockEnd, pipeClose, sym(')'), eof,
        )
    }

    @Test
    fun pipeClosedThroughMultipleBlocks() {
        val program = """
            f(|
              x =
                y =
                  z
            |)
        """.trimIndent()
        assertTokens(
            program,
            ident("f"), sym('('), pipeOpen,
            blockStart, ident("x"), sym('='),
            blockStart, ident("y"), sym('='),
            blockStart, ident("z"),
            blockEnd, blockEnd, blockEnd, pipeClose, sym(')'), eof,
        )
    }

    @Test
    fun singleLineUnclosedParenInLambda() {
        assertTokens(
            "f(|x -> g(x|)",
            ident("f"), sym('('), pipeOpen,
            ident("x"), sym("->"), ident("g"), sym('('), ident("x"),
            pipeClose, sym(')'), eof,
        )
    }

    @Test
    fun singleLineUnclosedBracketInLambda() {
        assertTokens(
            "f(|x -> a[x|)",
            ident("f"), sym('('), pipeOpen,
            ident("x"), sym("->"), ident("a"), sym('['), ident("x"),
            pipeClose, sym(')'), eof,
        )
    }

    @Test
    fun singleLineParenClosedThroughPipe() {
        assertTokens(
            "f(|x)",
            ident("f"), sym('('), pipeOpen, ident("x"), sym(')'), eof,
        )
    }

    @Test
    fun singleLineMultipleUnclosed() {
        assertTokens(
            "f(|a(b[c|)",
            ident("f"), sym('('), pipeOpen,
            ident("a"), sym('('), ident("b"), sym('['), ident("c"),
            pipeClose, sym(')'), eof,
        )
    }

    @Test
    fun singleLineNestedLambdasWithUnclosedParen() {
        assertTokens(
            "f(||g(x||)",
            ident("f"), sym('('),
            pipeOpen, pipeOpen, ident("g"), sym('('), ident("x"), pipeClose, pipeClose,
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
            ident("foo"), sym('('), pipeOpen, blockStart,
            ident("x"), sym('='), num("1"), stmtEnd,
            ident("y"), blockEnd, pipeClose, sym(')'), eof,
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
            ident("foo"), sym('('), pipeOpen, blockStart,
            ident("x"), sym('='), num("1"),
            blockEnd, pipeClose, sym(')'), eof,
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
            ident("foo"), sym('('), sym('['), pipeOpen, blockStart,
            ident("x"), sym('='), num("1"),
            blockEnd, pipeClose, sym(']'), sym(')'), eof,
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
            ident("foo"), sym('('), pipeOpen, ident("x"), sym("->"), blockStart,
            ident("y"), sym('='), ident("x"), sym('+'), num("1"), stmtEnd,
            ident("y"), blockEnd, pipeClose, sym(')'), eof,
        )
    }
}
