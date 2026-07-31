@file:Suppress("ktlint")

package klein.lexer

import klein.surface.TokenKind.*
import kotlin.test.Test

/**
 * Lexer tests for the `null` keyword.
 *
 * These tests verify that the lexer correctly tokenizes `null` as a keyword
 * and handles various edge cases involving null in expressions.
 */
class NullTest {

    // =========================================================================
    // SECTION 1: Basic Null Tokenization
    // =========================================================================

    @Test
    fun nullKeyword() {
        assertTokens("null", kw(NULL), eof)
    }

    @Test
    fun nullKeyword_withSpan() {
        assertTokens("null", kw(NULL, span(0, 4)), eof)
    }

    @Test
    fun nullKeyword_multipleTimes() {
        assertTokens("null null", kw(NULL), kw(NULL), eof)
    }

    // =========================================================================
    // SECTION 2: Null-like Identifiers (Not Keywords)
    // =========================================================================

    @Test
    fun nullLikePrefix_isIdent() {
        // Words that start with "null" but aren't "null" should be identifiers
        assertTokens("nullable", ident("nullable"), eof)
    }

    @Test
    fun nullLikeSuffix_isIdent() {
        // Words that contain "null" but aren't "null" should be identifiers
        assertTokens("notNull", ident("notNull"), eof)
    }

    @Test
    fun nullWithNumbers_isIdent() {
        assertTokens("null2", ident("null2"), eof)
    }

    @Test
    fun nullWithUnderscore_isIdent() {
        assertTokens("null_value", ident("null_value"), eof)
    }

    @Test
    fun underscoreNull_isIdent() {
        assertTokens("_null", ident("_null"), eof)
    }

    // =========================================================================
    // SECTION 3: Null in Expressions
    // =========================================================================

    @Test
    fun nullInAssignment() {
        assertTokens(
            "x = null",
            ident("x"), sym('='), kw(NULL), eof
        )
    }

    @Test
    fun nullInIfThenElse() {
        assertTokens(
            "if true then 42 else null",
            kw(IF), kw(TRUE), kw(THEN), num("42"), kw(ELSE), kw(NULL), eof
        )
    }

    @Test
    fun nullInThenBranch() {
        assertTokens(
            "if false then null else 42",
            kw(IF), kw(FALSE), kw(THEN), kw(NULL), kw(ELSE), num("42"), eof
        )
    }

    @Test
    fun nullInBothBranches() {
        assertTokens(
            "if b then null else null",
            kw(IF), ident("b"), kw(THEN), kw(NULL), kw(ELSE), kw(NULL), eof
        )
    }

    @Test
    fun nullAsRecordFieldValue() {
        assertTokens(
            "{ x = null }",
            sym('{'), ident("x"), sym('='), kw(NULL), sym('}'), eof
        )
    }

    @Test
    fun nullInParens() {
        assertTokens(
            "(null)",
            sym('('), kw(NULL), sym(')'), eof
        )
    }

    @Test
    fun nullAsFunctionArg() {
        assertTokens(
            "f(null)",
            ident("f"), sym('('), kw(NULL), sym(')'), eof
        )
    }

    @Test
    fun nullAsMultipleArgs() {
        assertTokens(
            "f(null, null)",
            ident("f"), sym('('), kw(NULL), sym(','), kw(NULL), sym(')'), eof
        )
    }

    @Test
    fun nullInLambdaBody() {
        assertTokens(
            "|x -> null|",
            pipe, ident("x"), sym("->"), kw(NULL), pipe, eof
        )
    }

    @Test
    fun nullComparedWithNull() {
        assertTokens(
            "null == null",
            kw(NULL), sym("=="), kw(NULL), eof
        )
    }

    @Test
    fun nullNotEqual() {
        assertTokens(
            "x != null",
            ident("x"), sym("!="), kw(NULL), eof
        )
    }

    // =========================================================================
    // SECTION 4: Null with Operators (These will be type errors, but should lex)
    // =========================================================================

    @Test
    fun nullWithArithmetic_lexes() {
        // These are type errors, but the lexer should still tokenize them
        assertTokens(
            "null + 1",
            kw(NULL), sym('+'), num("1"), eof
        )
    }

    @Test
    fun nullWithNegation_lexes() {
        // Unary minus before null
        assertTokens(
            "-null",
            sym("-", kind = MINUS_TIGHT), kw(NULL), eof
        )
    }

    // =========================================================================
    // SECTION 5: Null with Comments
    // =========================================================================

    @Test
    fun nullWithComment() {
        assertTokens(
            "null # this is null",
            kw(NULL), eof
        )
    }

    @Test
    fun nullAfterComment() {
        val code = """
            # comment
            null
        """.trimIndent()
        assertTokens(code, kw(NULL), eof)
    }

    // =========================================================================
    // SECTION 6: Null with Newlines and Indentation
    // =========================================================================

    @Test
    fun nullOnNewLine() {
        val code = """
            x =
                null
        """.trimIndent()
        assertTokens(
            code,
            ident("x", indent = 0), sym('='), kw(NULL, indent = 4), eof
        )
    }

    @Test
    fun nullInMultilineIfThenElse() {
        val code = """
            if true
            then 42
            else null
        """.trimIndent()
        assertTokens(
            code,
            kw(IF, indent = 0), kw(TRUE),
            kw(THEN, indent = 0), num("42"),
            kw(ELSE, indent = 0), kw(NULL),
            eof
        )
    }

    // =========================================================================
    // SECTION 7: Null Cannot Be Used as Identifier
    // =========================================================================
    // These tests verify that `null` is always tokenized as a keyword,
    // even in contexts where an identifier would normally appear.
    // The parser will reject these as syntax errors.

    @Test
    fun nullAsVariableName_lexesAsKeyword() {
        // "null = 42" lexes as: NULL '=' NUM, not IDENT '=' NUM
        assertTokens(
            "null = 42",
            kw(NULL), sym('='), num("42"), eof
        )
    }

    @Test
    fun nullAsFunctionName_lexesAsKeyword() {
        // "fun null(x) = x" lexes with NULL as keyword
        assertTokens(
            "fun null(x) = x",
            kw(FUN), kw(NULL), sym('('), ident("x"), sym(')'), sym('='), ident("x"), eof
        )
    }

    @Test
    fun nullAsParameterName_lexesAsKeyword() {
        // "|null -> null|" lexes with NULL as keyword in both positions
        assertTokens(
            "|null -> null|",
            pipe, kw(NULL), sym("->"), kw(NULL), pipe, eof
        )
    }

    @Test
    fun nullAsRecordFieldName_lexesAsKeyword() {
        // "{ null = 42 }" lexes with NULL as keyword
        assertTokens(
            "{ null = 42 }",
            sym('{'), kw(NULL), sym('='), num("42"), sym('}'), eof
        )
    }

    @Test
    fun nullInFieldAccess_lexesAsKeyword() {
        // "x.null" lexes with NULL as keyword
        assertTokens(
            "x.null",
            ident("x"), sym('.'), kw(NULL), eof
        )
    }

    @Test
    fun nullAsFunctionCallTarget_lexesAsKeyword() {
        // "null(42)" lexes with NULL as keyword (will be a type error)
        assertTokens(
            "null(42)",
            kw(NULL), sym('('), num("42"), sym(')'), eof
        )
    }
}
