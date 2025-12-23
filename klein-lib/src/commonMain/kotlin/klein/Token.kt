package klein

import klein.TokenKind.*

data class Token(
    val kind: TokenKind,
    val span: SourceSpan,
    val text: String? = null,
    val indent: Int? = null,
) {
    val isNewline: Boolean get() = indent != null

    fun startsLineBefore(column: Int) = indent != null && indent < column

    fun startsLineAtOrBefore(column: Int) = indent != null && indent <= column

    fun startsLineAfter(column: Int) = indent != null && indent > column

    override fun toString(): String =
        when {
            kind == IDENT -> "Ident($text)"
            kind == INT || kind == DOUBLE -> "Number($text)"
            kind == STRING -> "String($text)"
            kind == EOF -> "Eof"
            kind.keyword != null -> "Keyword(${kind.name})"
            kind.symbol != null -> "'${kind.symbol}'"
            else -> "Token($kind)"
        }
}

enum class TokenKind(
    val symbol: String? = null,
    val keyword: String? = null,
    val continuesExpression: Boolean = false,
) {
    // Literals
    IDENT,
    INT,
    DOUBLE,
    STRING,

    // Keywords
    IF(keyword = "if"),
    THEN(keyword = "then", continuesExpression = true),
    ELSE(keyword = "else", continuesExpression = true),
    FUN(keyword = "fun"),
    TRUE(keyword = "true"),
    FALSE(keyword = "false"),
    AND(keyword = "and"),
    OR(keyword = "or"),
    NOT(keyword = "not"),

    // Symbols
    PLUS(symbol = "+"),
    MINUS(symbol = "-"),
    MINUS_TIGHT(symbol = "-"), // No space after - indicates unary
    STAR(symbol = "*"),
    SLASH(symbol = "/"),
    PERCENT(symbol = "%"),
    LPAREN(symbol = "("),
    RPAREN(symbol = ")"),
    LBRACKET(symbol = "["),
    RBRACKET(symbol = "]"),
    LBRACE(symbol = "{"),
    RBRACE(symbol = "}"),
    EQ(symbol = "="),
    EQEQ(symbol = "=="),
    NEQ(symbol = "!="),
    LT(symbol = "<"),
    LTEQ(symbol = "<="),
    GT(symbol = ">"),
    GTEQ(symbol = ">="),
    ARROW(symbol = "->"),
    DOT(symbol = "."),
    DOTDOT(symbol = ".."),
    COMMA(symbol = ","),
    COLON(symbol = ":"),
    SEMICOLON(symbol = ";"),
    BANG(symbol = "!"),
    AT(symbol = "@"),
    AMP(symbol = "&"),

    // Structure
    PIPE,
    EOF,
    ;

    companion object {
        private val byKeyword = entries.filter { it.keyword != null }.associateBy { it.keyword }
        private val bySymbol = entries.filter { it.symbol != null }.sortedByDescending { it.symbol!!.length }.map { it.symbol!! to it }

        fun fromKeyword(text: String): TokenKind? = byKeyword[text]

        fun matchSymbol(
            source: String,
            pos: Int,
        ): Pair<TokenKind, Int>? {
            for ((sym, kind) in bySymbol) {
                if (source.startsWith(sym, pos)) {
                    return kind to sym.length
                }
            }
            return null
        }
    }
}
