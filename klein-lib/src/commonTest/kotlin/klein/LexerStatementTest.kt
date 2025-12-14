package klein

import kotlin.test.Test

class LexerStatementTest {
    @Test
    fun simpleBinding() {
        Lexer("x = 1").assertTokens(ident("x"), sym('='), num("1"), eof)
    }

    @Test
    fun twoBindingsOnSeparateLines() {
        Lexer(
                """
            x = 1
            y = 2
        """
                    .trimIndent())
            .assertTokens(
                ident("x"),
                sym('='),
                num("1"),
                stmtEnd,
                ident("y"),
                sym('='),
                num("2"),
                eof,
            )
    }

    @Test
    fun noNewlineAfterOperator() {
        Lexer(
                """
            x = 1 +
            2
            """
                    .trimIndent())
            .assertTokens(ident("x"), sym('='), num("1"), sym('+'), num("2"), eof)
    }

    @Test
    fun noNewlineAfterOpenParen() {
        Lexer(
                """
            foo(
                a,
                b
            )
            """
                    .trimIndent())
            .assertTokens(ident("foo"), sym('('), ident("a"), sym(','), ident("b"), sym(')'), eof)
    }

    @Test
    fun newlineInsideBlock() {
        Lexer(
                """
            {
                x = 1
                y = 2
            }
            """
                    .trimIndent())
            .assertTokens(
                sym('{'),
                ident("x"),
                sym('='),
                num("1"),
                stmtEnd,
                ident("y"),
                sym('='),
                num("2"),
                stmtEnd,
                sym('}'),
                eof,
            )
    }

    @Test
    fun noNewlineInsideParensInsideBlock() {
        Lexer(
                """
            {
                foo(
                    a,
                    b
                )
            }
            """
                    .trimIndent())
            .assertTokens(
                sym('{'),
                ident("foo"),
                sym('('),
                ident("a"),
                sym(','),
                ident("b"),
                sym(')'),
                stmtEnd,
                sym('}'),
                eof,
            )
    }

    @Test
    fun newlineInsidePipeLambda() {
        Lexer(
                """
            |
                x = 1
                y
            |
            """
                    .trimIndent())
            .assertTokens(
                sym('|'),
                ident("x"),
                sym('='),
                num("1"),
                stmtEnd,
                ident("y"),
                stmtEnd,
                sym('|'),
                eof,
            )
    }

    @Test
    fun nestedLambdaInParens() {
        Lexer("foo(|.x|)")
            .assertTokens(
                ident("foo"),
                sym('('),
                sym('|'),
                sym('.'),
                ident("x"),
                sym('|'),
                sym(')'),
                eof,
            )
    }

    @Test
    fun nestedLambdas() {
        Lexer("filter(items, |.orders.any(|.price > 100|)|)")
            .assertTokens(
                ident("filter"),
                sym('('),
                ident("items"),
                sym(','),
                sym('|'),
                sym('.'),
                ident("orders"),
                sym('.'),
                ident("any"),
                sym('('),
                sym('|'),
                sym('.'),
                ident("price"),
                sym('>'),
                num("100"),
                sym('|'),
                sym(')'),
                sym('|'),
                sym(')'),
                eof,
            )
    }

    @Test
    fun nestedMultilineLambda() {
        Lexer(
                """
            filter(items,
              |.orders.any(|
                  x = .price
                  x > 100
              |)
            |)
            """
                    .trimIndent())
            .assertTokens(
                ident("filter"),
                sym('('),
                ident("items"),
                sym(','),
                sym('|'),
                sym('.'),
                ident("orders"),
                sym('.'),
                ident("any"),
                sym('('),
                sym('|'),
                ident("x"),
                sym('='),
                sym('.'),
                ident("price"),
                stmtEnd,
                ident("x"),
                sym('>'),
                num("100"),
                stmtEnd,
                sym('|'),
                sym(')'),
                stmtEnd,
                sym('|'),
                sym(')'),
                eof,
            )
    }

    @Test
    fun noNewlineAfterEquals() {
        Lexer(
                """
            x =
            1
            """
                    .trimIndent())
            .assertTokens(ident("x"), sym('='), num("1"), eof)
    }

    @Test
    fun noNewlineAfterComma() {
        Lexer(
                """
            x = foo(a,
                b)
            """
                    .trimIndent())
            .assertTokens(
                ident("x"),
                sym('='),
                ident("foo"),
                sym('('),
                ident("a"),
                sym(','),
                ident("b"),
                sym(')'),
                eof,
            )
    }

    @Test
    fun multipleNewlinesCollapsed() {
        Lexer(
                """
            x = 1


            y = 2
            """
                    .trimIndent())
            .assertTokens(
                ident("x"),
                sym('='),
                num("1"),
                stmtEnd,
                ident("y"),
                sym('='),
                num("2"),
                eof,
            )
    }
}
