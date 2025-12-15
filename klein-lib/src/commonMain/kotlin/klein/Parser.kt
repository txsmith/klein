package klein

class Parser(
    private val tokens: List<Token>,
) {
    private var pos: Int = 0

    fun parseExpr(): Expr = parseExprAtPrecedence(0)

    fun parseProgram(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        skipLeadingStatementEnds()
        while (peek() !is Token.Eof) {
            stmts.add(parseStmt())
        }
        return stmts
    }

    private fun skipLeadingStatementEnds() {
        while (peek() is Token.StatementEnd) {
            advance()
        }
    }

    fun parseStmt(): Stmt {
        val stmt =
            if (isBinding()) {
                parseBinding()
            } else {
                parseExpr()
            }

        val next = peek()
        when {
            next is Token.StatementEnd -> advance()
            next is Token.Eof -> {}
            next.isSymbol("}") || next.isSymbol("|") -> {}
            else -> throw ParseError("Expected newline or end of input, got $next", next.span)
        }

        return stmt
    }

    private fun isBinding(): Boolean = peek() is Token.Ident && peekAt(1).isSymbol("=")

    private fun parseBinding(): Val {
        val name = advance() as Token.Ident
        advance() // consume '='
        val value = parseExpr()
        return Val(name.name, value, name.span + value.span)
    }

    private fun parseExprAtPrecedence(minPrecedence: Int): Expr {
        var left = parseApply()

        while (true) {
            val op = peekBinaryOp() ?: break
            if (op.precedence < minPrecedence) break

            advance()
            val right = parseExprAtPrecedence(op.precedence + 1)
            left = BinaryOp(left, op, right, left.span + right.span)
        }

        return left
    }

    private fun peekBinaryOp(): Operator? =
        when (val token = peek()) {
            is Token.Symbol -> Operator.fromSymbol(token.text)
            is Token.Keyword -> Operator.fromKeyword(token.kind)
            else -> null
        }

    private fun parseAtom(): Expr {
        val token = peek()
        return when (token) {
            is Token.Number -> {
                advance()
                if ('.' in token.text) {
                    val value = token.text.toDoubleOrNull() ?: throw ParseError("Invalid number: ${token.text}", token.span)
                    DoubleLiteral(value, token.span)
                } else {
                    val value = token.text.toLongOrNull() ?: throw ParseError("Invalid number: ${token.text}", token.span)
                    IntLiteral(value, token.span)
                }
            }

            is Token.Str -> {
                advance()
                StringLiteral(token.value, token.span)
            }

            is Token.Ident -> {
                advance()
                Ident(token.name, token.span)
            }

            is Token.Keyword -> {
                when (token.kind) {
                    KeywordKind.True -> {
                        advance()
                        BoolLiteral(true, token.span)
                    }
                    KeywordKind.False -> {
                        advance()
                        BoolLiteral(false, token.span)
                    }
                    KeywordKind.Not -> {
                        advance()
                        val operand = parseAtom()
                        UnaryOp(UnaryOperator.Not, operand, token.span + operand.span)
                    }
                    else -> throw ParseError("Unexpected keyword: ${token.kind}", token.span)
                }
            }

            is Token.Symbol -> {
                when (token.text) {
                    "-" -> {
                        advance()
                        val operand = parseAtom()
                        UnaryOp(UnaryOperator.Neg, operand, token.span + operand.span)
                    }
                    "(" -> {
                        advance()
                        val expr = parseExpr()
                        val close = peek()
                        if (!close.isSymbol(")")) {
                            throw ParseError("Expected ')', got $close", close.span)
                        }
                        advance()
                        expr
                    }
                    "|" -> {
                        advance()
                        parseLambda(token)
                    }
                    else -> throw ParseError("Expected expression, got $token", token.span)
                }
            }

            else -> {
                throw ParseError("Expected expression, got $token", token.span)
            }
        }
    }

    private fun parseApply(): Expr {
        var callee = parseAtom()

        while (peek().isSymbol("(")) {
            advance()
            val args = parseArgs()

            val close = peek()
            if (!close.isSymbol(")")) {
                throw ParseError("Expected ')', got $close", close.span)
            }
            advance()

            callee = Apply(callee, args, callee.span + close.span)
        }

        return callee
    }

    private fun parseArgs(): List<Expr> {
        if (peek().isSymbol(")")) return emptyList()

        val args = mutableListOf<Expr>()
        args.add(parseExpr())

        while (peek().isSymbol(",")) {
            advance()
            args.add(parseExpr())
        }

        return args
    }

    private fun parseLambda(open: Token.Symbol): Lambda {
        val params = parseParamsIfPresent()
        val body = parseLambdaBody()

        val close = peek()
        if (!close.isSymbol("|")) {
            throw ParseError("Expected '|', got $close", close.span)
        }
        advance()

        return Lambda(params, body, open.span + close.span)
    }

    private fun parseLambdaBody(): Expr {
        skipLeadingStatementEnds()

        val stmts = mutableListOf<Stmt>()
        while (isBinding()) {
            stmts.add(parseStmt())
            skipLeadingStatementEnds()
        }

        val expr = parseExpr()
        skipLeadingStatementEnds()

        return if (stmts.isEmpty()) {
            expr
        } else {
            Block(stmts, expr, stmts.first().span + expr.span)
        }
    }

    private fun parseParamsIfPresent(): List<String> {
        if (peek() !is Token.Ident) return emptyList()

        val next = peekAt(1)
        if (!next.isSymbol("->") && !next.isSymbol(",")) return emptyList()

        val params = mutableListOf<String>()
        while (peek() is Token.Ident) {
            val ident = advance() as Token.Ident
            params.add(ident.name)

            if (peek().isSymbol(",") && peekAt(1) is Token.Ident) {
                advance()
            } else {
                break
            }
        }

        val arrow = peek()
        if (!arrow.isSymbol("->")) {
            throw ParseError("Expected '->', got $arrow", arrow.span)
        }
        advance()

        return params
    }

    private fun peek(): Token = tokens[pos]

    private fun peekAt(offset: Int): Token = tokens[pos + offset]

    private fun advance(): Token = tokens[pos++]

    private fun Token.isSymbol(text: String): Boolean = this is Token.Symbol && this.text == text
}

class ParseError(
    message: String,
    val span: SourceSpan,
) : Exception(message)
