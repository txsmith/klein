package klein

import klein.TokenKind.*

class ParseError(
    message: String,
    val span: SourceSpan,
) : Exception(message)

class Parser(
    private val tokens: List<Token>,
) {
    private var pos: Int = 0

    fun parseExpr(): Expr = parseExprAtPrecedence(0)

    fun parseProgram(): List<Stmt> {
        val stmts = mutableListOf<Stmt>()
        skipLeadingStatementEnds()
        while (peek().kind != EOF) {
            stmts.add(parseStmt())
        }
        return stmts
    }

    private fun skipLeadingStatementEnds() {
        while (peek().kind == STMT_END) {
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
        when (next.kind) {
            STMT_END -> advance()
            EOF -> {}
            BLOCK_END -> {}
            RBRACE, PIPE_CLOSE -> {}
            else -> throw ParseError("Expected newline or end of input, got $next", next.span)
        }

        return stmt
    }

    private fun isBinding(): Boolean = peek().kind == IDENT && peekAt(1).kind == EQ

    private fun parseBinding(): Val {
        val name = advance()
        advance() // consume '='
        val value = parseExpr()
        return Val(name.text!!, value, name.span + value.span)
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

    private fun peekBinaryOp(): Operator? = Operator.fromTokenKind(peek().kind)

    private fun parseAtom(): Expr {
        val token = peek()
        return when (token.kind) {
            INT -> {
                advance()
                val value = token.text!!.toLongOrNull() ?: throw ParseError("Invalid number: ${token.text}", token.span)
                IntLiteral(value, token.span)
            }

            DOUBLE -> {
                advance()
                val value = token.text!!.toDoubleOrNull() ?: throw ParseError("Invalid number: ${token.text}", token.span)
                DoubleLiteral(value, token.span)
            }

            STRING -> {
                advance()
                StringLiteral(token.text!!, token.span)
            }

            IDENT -> {
                advance()
                Ident(token.text!!, token.span)
            }

            TRUE -> {
                advance()
                BoolLiteral(true, token.span)
            }

            FALSE -> {
                advance()
                BoolLiteral(false, token.span)
            }

            NOT -> {
                advance()
                val operand = parseAtom()
                UnaryOp(UnaryOperator.Not, operand, token.span + operand.span)
            }

            MINUS -> {
                advance()
                val operand = parseAtom()
                UnaryOp(UnaryOperator.Neg, operand, token.span + operand.span)
            }

            IF -> {
                advance()
                val condition = parseExpr()
                expectAndAdvance(THEN, "Expected 'then'")
                val thenBranch = parseBranchExpr()
                val elseBranch =
                    if (peek().kind == ELSE) {
                        advance()
                        parseBranchExpr()
                    } else {
                        null
                    }
                val endSpan = elseBranch?.span ?: thenBranch.span
                IfThenElse(condition, thenBranch, elseBranch, token.span + endSpan)
            }

            LPAREN -> {
                advance()
                val expr = parseExpr()
                val close = peek()
                if (close.kind != RPAREN) {
                    throw ParseError("Expected ')', got $close", close.span)
                }
                advance()
                expr
            }

            PIPE_OPEN -> {
                advance()
                parseLambda(token)
            }

            else -> throw ParseError("Expected expression, got $token", token.span)
        }
    }

    private fun parseApply(): Expr {
        var callee = parseAtom()

        while (peek().kind == LPAREN) {
            advance()
            val args = parseArgs()

            val close = peek()
            if (close.kind != RPAREN) {
                throw ParseError("Expected ')', got $close", close.span)
            }
            advance()

            callee = Apply(callee, args, callee.span + close.span)
        }

        return callee
    }

    private fun parseArgs(): List<Expr> {
        if (peek().kind == RPAREN) return emptyList()

        val args = mutableListOf<Expr>()
        args.add(parseExpr())

        while (peek().kind == COMMA) {
            advance()
            args.add(parseExpr())
        }

        return args
    }

    private fun parseLambda(open: Token): Lambda {
        val params = parseParamsIfPresent()
        val body = parseLambdaBody()

        val close = peek()
        if (close.kind != PIPE_CLOSE) {
            throw ParseError("Expected '|', got $close", close.span)
        }
        advance()

        return Lambda(params, body, open.span + close.span)
    }

    private fun parseLambdaBody(): Expr {
        skipLeadingStatementEnds()

        // Check for indented block
        if (peek().kind == BLOCK_START) {
            return parseBlock()
        }

        // Single-line lambda: stmt* expr
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

    private fun parseBlock(): Expr {
        val blockStart = advance()
        skipLeadingStatementEnds()

        val stmts = mutableListOf<Stmt>()

        // Parse stmt* expr BlockEnd
        while (peek().kind != BLOCK_END && peek().kind != EOF) {
            if (isBinding()) {
                stmts.add(parseStmt())
            } else {
                // This might be the final expression
                val expr = parseExpr()
                skipLeadingStatementEnds()

                // Check if this is the last item (followed by BlockEnd or EOF)
                if (peek().kind == BLOCK_END || peek().kind == EOF) {
                    val blockEnd = if (peek().kind == BLOCK_END) advance() else peek()
                    return if (stmts.isEmpty()) {
                        expr
                    } else {
                        Block(stmts, expr, blockStart.span + blockEnd.span)
                    }
                }

                // Not the last item, so it's an expression statement
                // (This handles cases like `print(x)` followed by more statements)
                stmts.add(expr)
            }
            skipLeadingStatementEnds()
        }

        // Should have a BlockEnd or EOF
        val blockEnd = peek()
        if (blockEnd.kind != BLOCK_END && blockEnd.kind != EOF) {
            throw ParseError("Expected block end, got $blockEnd", blockEnd.span)
        }
        if (blockEnd.kind == BLOCK_END) {
            advance()
        }

        // Empty block is an error
        throw ParseError("Block must contain at least one expression", blockStart.span)
    }

    private fun parseParamsIfPresent(): List<String> {
        if (peek().kind != IDENT) return emptyList()

        val next = peekAt(1)
        if (next.kind != ARROW && next.kind != COMMA) return emptyList()

        val params = mutableListOf<String>()
        while (peek().kind == IDENT) {
            val ident = advance()
            params.add(ident.text!!)

            if (peek().kind == COMMA && peekAt(1).kind == IDENT) {
                advance()
            } else {
                break
            }
        }

        val arrow = peek()
        if (arrow.kind != ARROW) {
            throw ParseError("Expected '->', got $arrow", arrow.span)
        }
        advance()

        return params
    }

    private fun peek(): Token = tokens[pos]

    private fun peekAt(offset: Int): Token = tokens[pos + offset]

    private fun advance(): Token = tokens[pos++]

    private fun expectAndAdvance(
        kind: TokenKind,
        message: String,
    ) {
        val token = peek()
        if (token.kind != kind) {
            throw ParseError("$message, got $token", token.span)
        }
        advance()
    }

    private fun parseBranchExpr(): Expr =
        if (peek().kind == BLOCK_START) {
            parseBlock()
        } else {
            parseExpr()
        }
}
