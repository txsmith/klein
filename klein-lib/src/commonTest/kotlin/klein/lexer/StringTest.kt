@file:Suppress("ktlint")

package klein.lexer

import klein.Lexer
import klein.LexerError
import klein.SourceSpan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StringTest {
    @Test
    fun simpleString() {
        val tokens = Lexer("\"hello\"").tokenize().toList()
        assertEquals("hello", tokens[0].text)
    }

    @Test
    fun emptyString() {
        val tokens = Lexer("\"\"").tokenize().toList()
        assertEquals("", tokens[0].text)
    }

    @Test
    fun stringWithSpaces() {
        val tokens = Lexer("\"hello world\"").tokenize().toList()
        assertEquals("hello world", tokens[0].text)
    }

    @Test
    fun escapedQuote() {
        val tokens = Lexer(""""hello\"world"""").tokenize().toList()
        assertEquals("hello\"world", tokens[0].text)
    }

    @Test
    fun escapedNewline() {
        val tokens = Lexer(""""line1\nline2"""").tokenize().toList()
        assertEquals("line1\nline2", tokens[0].text)
    }

    @Test
    fun escapedTab() {
        val tokens = Lexer(""""col1\tcol2"""").tokenize().toList()
        assertEquals("col1\tcol2", tokens[0].text)
    }

    @Test
    fun escapedBackslash() {
        val tokens = Lexer(""""a\\b"""").tokenize().toList()
        assertEquals("a\\b", tokens[0].text)
    }

    @Test
    fun doubleBackslash() {
        val tokens = Lexer(""""a\\\\b"""").tokenize().toList()
        assertEquals("a\\\\b", tokens[0].text)
    }

    @Test
    fun backslashBeforeQuote() {
        val tokens = Lexer(""""a\\\"b"""").tokenize().toList()
        assertEquals("a\\\"b", tokens[0].text)
    }

    @Test
    fun multipleEscapes() {
        val tokens = Lexer(""""a\nb\tc"""").tokenize().toList()
        assertEquals("a\nb\tc", tokens[0].text)
    }

    @Test
    fun unterminatedString() {
        val error = assertFailsWith<LexerError> { Lexer("\"hello").tokenize().toList() }
        assertEquals("Unterminated string", error.message)
    }

    @Test
    fun unterminatedStringWithContent() {
        val error = assertFailsWith<LexerError> { Lexer("let x = \"").tokenize().toList() }
        assertEquals("Unterminated string", error.message)
        assertEquals(SourceSpan(8, 9), error.span)
    }

    @Test
    fun unknownEscapeSequence() {
        val error = assertFailsWith<LexerError> { Lexer(""""a\b"""").tokenize().toList() }
        assertEquals("""Invalid escape sequence: \b""", error.message)
    }

    @Test
    fun spanForString() {
        val tokens = Lexer("\"hello\"").tokenize().toList()
        assertEquals(SourceSpan(0, 7), tokens[0].span)
    }

    @Test
    fun spanForEmptyString() {
        val tokens = Lexer("\"\"").tokenize().toList()
        assertEquals(SourceSpan(0, 2), tokens[0].span)
    }

    @Test
    fun spanForStringWithEscapes() {
        val tokens = Lexer(""""a\nb"""").tokenize().toList()
        assertEquals(SourceSpan(0, 6), tokens[0].span)
    }

    @Test
    fun incompleteEscapeSequence() {
        val error = assertFailsWith<LexerError> { Lexer(""""a\"""").tokenize().toList() }
        assertEquals("Unterminated string", error.message)
    }
}
