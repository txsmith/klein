package klein

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LexerStringTest {
    @Test
    fun simpleString() {
        val tokens = Lexer("'hello'").tokenize()
        assertEquals("hello", (tokens[0] as Token.Str).value)
    }

    @Test
    fun emptyString() {
        val tokens = Lexer("''").tokenize()
        assertEquals("", (tokens[0] as Token.Str).value)
    }

    @Test
    fun stringWithSpaces() {
        val tokens = Lexer("'hello world'").tokenize()
        assertEquals("hello world", (tokens[0] as Token.Str).value)
    }

    @Test
    fun escapedQuote() {
        val tokens = Lexer("""'hello\'world'""").tokenize()
        assertEquals("hello'world", (tokens[0] as Token.Str).value)
    }

    @Test
    fun escapedNewline() {
        val tokens = Lexer("""'line1\nline2'""").tokenize()
        assertEquals("line1\nline2", (tokens[0] as Token.Str).value)
    }

    @Test
    fun escapedTab() {
        val tokens = Lexer("""'col1\tcol2'""").tokenize()
        assertEquals("col1\tcol2", (tokens[0] as Token.Str).value)
    }

    @Test
    fun escapedBackslash() {
        val tokens = Lexer("""'a\\b'""").tokenize()
        assertEquals("a\\b", (tokens[0] as Token.Str).value)
    }

    @Test
    fun doubleBackslash() {
        val tokens = Lexer("""'a\\\\b'""").tokenize()
        assertEquals("a\\\\b", (tokens[0] as Token.Str).value)
    }

    @Test
    fun backslashBeforeQuote() {
        val tokens = Lexer("""'a\\\'b'""").tokenize()
        assertEquals("a\\'b", (tokens[0] as Token.Str).value)
    }

    @Test
    fun multipleEscapes() {
        val tokens = Lexer("""'a\nb\tc'""").tokenize()
        assertEquals("a\nb\tc", (tokens[0] as Token.Str).value)
    }

    @Test
    fun unterminatedString() {
        val error = assertFailsWith<LexerError> { Lexer("'hello").tokenize() }
        assertEquals("Unterminated string", error.message)
    }

    @Test
    fun unterminatedStringWithContent() {
        val error = assertFailsWith<LexerError> { Lexer("let x = '").tokenize() }
        assertEquals("Unterminated string", error.message)
        assertEquals(SourceSpan(8, 9), error.span)
    }

    @Test
    fun unknownEscapeSequence() {
        val error = assertFailsWith<LexerError> { Lexer("""'a\b'""").tokenize() }
        assertEquals("""Invalid escape sequence: \b""", error.message)
    }

    @Test
    fun spanForString() {
        val tokens = Lexer("'hello'").tokenize()
        assertEquals(SourceSpan(0, 7), tokens[0].span)
    }

    @Test
    fun spanForEmptyString() {
        val tokens = Lexer("''").tokenize()
        assertEquals(SourceSpan(0, 2), tokens[0].span)
    }

    @Test
    fun spanForStringWithEscapes() {
        val tokens = Lexer("""'a\nb'""").tokenize()
        assertEquals(SourceSpan(0, 6), tokens[0].span)
    }

    @Test
    fun incompleteEscapeSequence() {
        val error = assertFailsWith<LexerError> { Lexer("""'a\""").tokenize() }
        assertEquals("Invalid escape sequence", error.message)
    }
}
