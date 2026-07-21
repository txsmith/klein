@file:Suppress("ktlint")

package klein.lexer

import klein.surface.TokenKind.*
import kotlin.test.Test

class TypeVarTest {
    @Test
    fun simpleTypeVar() {
        assertTokens("'A", typeVar("A"), eof)
    }

    @Test
    fun multipleTypeVars() {
        assertTokens("'A, 'B", typeVar("A"), sym(','), typeVar("B"), eof)
    }

    @Test
    fun longerTypeVarName() {
        assertTokens("'Foo", typeVar("Foo"), eof)
    }

    @Test
    fun typeVarInAngleBrackets() {
        assertTokens("<'A>", sym('<'), typeVar("A"), sym('>'), eof)
    }

    @Test
    fun typeVarInTypeDefinition() {
        assertTokens(
            "type Option<'A> = None",
            kw(TYPE), upperIdent("Option"), sym('<'), typeVar("A"), sym('>'), sym('='), upperIdent("None"), eof,
        )
    }

    @Test
    fun multipleTypeVarsInAngleBrackets() {
        assertTokens(
            "<'A, 'B, 'C>",
            sym('<'), typeVar("A"), sym(','), typeVar("B"), sym(','), typeVar("C"), sym('>'), eof,
        )
    }

    @Test
    fun typeVarAsFieldType() {
        assertTokens(
            "value: 'A",
            ident("value"), sym(':'), typeVar("A"), eof,
        )
    }

    @Test
    fun lowercaseAfterQuoteIsError() {
        assertTokens("'a", error("Type variable must start with uppercase"))
    }

    @Test
    fun numberAfterQuoteIsError() {
        assertTokens("'123", error("Type variable must start with uppercase"))
    }

    @Test
    fun emptyQuoteIsError() {
        assertTokens("' ", error("Type variable must start with uppercase"))
    }

    @Test
    fun quoteAtEndOfInputIsError() {
        assertTokens("'", error("Type variable must start with uppercase"))
    }

    @Test
    fun typeVarWithUnderscore() {
        assertTokens("'A_B", typeVar("A_B"), eof)
    }

    @Test
    fun typeVarWithNumbers() {
        assertTokens("'T1", typeVar("T1"), eof)
    }
}
