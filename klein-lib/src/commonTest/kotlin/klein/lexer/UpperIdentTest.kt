@file:Suppress("ktlint")

package klein.lexer

import kotlin.test.Test

class UpperIdentTest {
    @Test
    fun uppercaseIdent() {
        assertTokens("Foo", upperIdent("Foo"), eof)
    }

    @Test
    fun lowercaseIdent() {
        assertTokens("foo", ident("foo"), eof)
    }

    @Test
    fun upperIdentWithUnderscore() {
        assertTokens("Foo_Bar", upperIdent("Foo_Bar"), eof)
    }

    @Test
    fun upperIdentWithDigits() {
        assertTokens("Option2", upperIdent("Option2"), eof)
    }

    @Test
    fun mixedIdentifiers() {
        assertTokens(
            "foo Bar baz",
            ident("foo"),
            upperIdent("Bar"),
            ident("baz"),
            eof
        )
    }

    @Test
    fun typeDefinitionIdentifiers() {
        assertTokens(
            "type Option = Some",
            kw(klein.surface.TokenKind.TYPE),
            upperIdent("Option"),
            sym("="),
            upperIdent("Some"),
            eof
        )
    }

    @Test
    fun underscoreStartsLowercaseIdent() {
        assertTokens("_foo", ident("_foo"), eof)
    }

    @Test
    fun underscoreAloneIsLowercaseIdent() {
        assertTokens("_", ident("_"), eof)
    }

    @Test
    fun upperIdentInExpression() {
        assertTokens(
            "Person { name = \"Alice\" }",
            upperIdent("Person"),
            sym("{"),
            ident("name"),
            sym("="),
            str("Alice"),
            sym("}"),
            eof
        )
    }
}
