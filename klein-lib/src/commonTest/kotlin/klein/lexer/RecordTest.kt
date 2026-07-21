@file:Suppress("ktlint")

package klein.lexer

import kotlin.test.Test

class RecordTest {
    @Test
    fun emptyRecord() {
        assertTokens("{}", sym('{'), sym('}'), eof)
    }

    @Test
    fun singleField() {
        assertTokens(
            "{ name = \"Alice\" }",
            sym('{'), ident("name"), sym('='), str("Alice"), sym('}'),
            eof
        )
    }

    @Test
    fun multipleFields() {
        assertTokens(
            "{ name = \"Alice\", age = 30 }",
            sym('{'), ident("name"), sym('='), str("Alice"), sym(','),
            ident("age"), sym('='), num("30"), sym('}'),
            eof
        )
    }

    @Test
    fun shorthandField() {
        assertTokens(
            "{ name, age }",
            sym('{'), ident("name"), sym(','), ident("age"), sym('}'),
            eof
        )
    }

    @Test
    fun mixedShorthandAndFull() {
        assertTokens(
            "{ name, age = 30 }",
            sym('{'), ident("name"), sym(','), ident("age"), sym('='), num("30"), sym('}'),
            eof
        )
    }

    @Test
    fun nestedRecord() {
        assertTokens(
            "{ person = { name = \"Bob\" } }",
            sym('{'), ident("person"), sym('='),
            sym('{'), ident("name"), sym('='), str("Bob"), sym('}'),
            sym('}'),
            eof
        )
    }

    @Test
    fun recordWithExpressionValue() {
        assertTokens(
            "{ total = price + tax }",
            sym('{'), ident("total"), sym('='), ident("price"), sym('+'), ident("tax"), sym('}'),
            eof
        )
    }

    @Test
    fun recordAssignment() {
        assertTokens(
            "person = { name = \"Alice\" }",
            ident("person"), sym('='),
            sym('{'), ident("name"), sym('='), str("Alice"), sym('}'),
            eof
        )
    }

    @Test
    fun recordInBlock() {
        val program = """
            result =
              { name = "Alice" }
        """.trimIndent() + "\n"
        assertTokens(
            program,
            ident("result", indent = 0), sym('='),
            sym('{', indent = 2), ident("name"), sym('='), str("Alice"), sym('}'),
            eof
        )
    }

    @Test
    fun recordAfterLambdaArrow() {
        val program = """
            f = |x ->
              { value = x }
            |
        """.trimIndent()
        assertTokens(
            program,
            ident("f", indent = 0), sym('='),
            pipe, ident("x"), sym("->"),
            sym('{', indent = 2), ident("value"), sym('='), ident("x"), sym('}'),
            pipe(indent = 0),
            eof
        )
    }

    @Test
    fun multilineRecordInBlock() {
        val program = """
            person =
              {
                name = "Alice",
                age = 30
              }
        """.trimIndent() + "\n"
        assertTokens(
            program,
            ident("person", indent = 0), sym('='),
            sym('{', indent = 2),
            ident("name", indent = 4), sym('='), str("Alice"), sym(','),
            ident("age", indent = 4), sym('='), num("30"),
            sym('}', indent = 2),
            eof
        )
    }

    @Test
    fun recordInIfThenElse() {
        val program = """
            if x then { a = 1 } else { b = 2 }
        """.trimIndent()
        assertTokens(
            program,
            kw(klein.surface.TokenKind.IF), ident("x"),
            kw(klein.surface.TokenKind.THEN), sym('{'), ident("a"), sym('='), num("1"), sym('}'),
            kw(klein.surface.TokenKind.ELSE), sym('{'), ident("b"), sym('='), num("2"), sym('}'),
            eof
        )
    }

    @Test
    fun recordDoesNotStartBlock() {
        assertTokens(
            "x = { a = 1 }",
            ident("x"), sym('='),
            sym('{'), ident("a"), sym('='), num("1"), sym('}'),
            eof
        )
    }

    @Test
    fun recordFollowedByStatement() {
        val program = """
            x = { a = 1 }
            y = 2
        """.trimIndent()
        assertTokens(
            program,
            ident("x", indent = 0), sym('='),
            sym('{'), ident("a"), sym('='), num("1"), sym('}'),
            ident("y", indent = 0), sym('='), num("2"),
            eof
        )
    }

    @Test
    fun unclosedBrace() {
        assertTokens(
            "{ a = 1",
            sym('{'), ident("a"), sym('='), num("1"),
            eof
        )
    }

    @Test
    fun unmatchedCloseBrace() {
        assertTokens(
            "x }",
            ident("x"), sym('}'),
            eof
        )
    }

    @Test
    fun numberAsKey() {
        assertTokens(
            "{ 123 = 1 }",
            sym('{'), num("123"), sym('='), num("1"), sym('}'),
            eof
        )
    }

    @Test
    fun stringAsKey() {
        assertTokens(
            "{ \"key\" = 1 }",
            sym('{'), str("key"), sym('='), num("1"), sym('}'),
            eof
        )
    }

    @Test
    fun randomSymbolsInBraces() {
        assertTokens(
            "{ + - * }",
            sym('{'), sym('+'), sym('-'), sym('*'), sym('}'),
            eof
        )
    }

    @Test
    fun keywordsInBraces() {
        assertTokens(
            "{ if then else }",
            sym('{'), kw(klein.surface.TokenKind.IF), kw(klein.surface.TokenKind.THEN), kw(klein.surface.TokenKind.ELSE), sym('}'),
            eof
        )
    }

    @Test
    fun missingValue() {
        assertTokens(
            "{ a = }",
            sym('{'), ident("a"), sym('='), sym('}'),
            eof
        )
    }

    @Test
    fun missingEquals() {
        assertTokens(
            "{ a 1 }",
            sym('{'), ident("a"), num("1"), sym('}'),
            eof
        )
    }

    @Test
    fun recordInLambdaClosesPipe() {
        assertTokens(
            "|x -> { a = x }|",
            pipe, ident("x"), sym("->"),
            sym('{'), ident("a"), sym('='), ident("x"), sym('}'),
            pipe,
            eof
        )
    }
}
