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
    private var currentLineIndent: Int = 0

    fun parseProgram(): Program {
        val start = peek().span
        val stmts = mutableListOf<Stmt>()
        while (peek().kind != EOF) {
            val stmt = if (peek().kind == FUN) parseFunDef() else parseStmt()
            stmts.add(stmt)
        }
        val end = if (stmts.isNotEmpty()) stmts.last().span else start
        return Program(stmts, start + end)
    }

    fun parseStmt(): Stmt {
        val stmt = if (isBinding()) parseBinding() else parseExpr()

        if (!canEndStatement()) {
            throw ParseError("Expected newline but got ${peek()}", peek().span)
        }

        return stmt
    }

    private fun canEndStatement(): Boolean {
        val token = peek()
        return token.isNewline || token.kind in setOf(PIPE, RPAREN, RBRACE, RBRACKET, ELSE, EOF)
    }

    private fun parseFunDef(): FunDef {
        val funToken = advance()
        val name = expectAndAdvance(IDENT, message = "Expected function name")
        expectAndAdvance(LPAREN, message = "Expected '('")
        val params = parseFunParams()
        expectAndAdvance(RPAREN, message = "Expected ')'")
        expectAndAdvance(EQ, message = "Expected '='")
        val body = parseBlockOrExpr()
        return FunDef(name.text!!, params, body, funToken.span + body.span)
    }

    private fun parseFunParams(): List<String> {
        if (peek().kind == RPAREN) return emptyList()

        val params = mutableListOf<String>()
        params.add(expectAndAdvance(IDENT, message = "Expected parameter name").text!!)

        while (peek().kind == COMMA) {
            advance()
            params.add(expectAndAdvance(IDENT, message = "Expected parameter name").text!!)
        }

        return params
    }

    private fun parseBinding(): Val {
        val name = expectAndAdvance(IDENT, message = "Expected identifier")
        expectAndAdvance(EQ, message = "Expected =")
        val value = parseBlockOrExpr()
        return Val(name.text!!, value, name.span + value.span)
    }

    private fun parseBlockOrExpr(): Expr = if (isBlockStart()) parseBlock() else parseExpr()

    private fun parseBlock(): Block {
        val stmts = mutableListOf<Stmt>()

        val blockIndent = peek().indent ?: currentLineIndent
        val blockStartSpan = peek().span
        var blockEndSpan = blockStartSpan

        while (!isBlockEnd()) {
            val stmt = parseStmt()
            blockEndSpan = stmt.span
            stmts.add(stmt)
            currentLineIndent = blockIndent
        }

        return Block(stmts, blockStartSpan + blockEndSpan)
    }

    fun parseExpr(): Expr = parseExprAtPrecedence(0)

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

    private fun parseApply(): Expr {
        val exprIndent = currentLineIndent
        var expr = parseAtom()

        while (true) {
            if (peek().startsLineAtOrBefore(exprIndent)) break

            when (peek().kind) {
                LPAREN -> {
                    expr = parseFunctionCallOn(expr)
                }

                DOT -> {
                    expr = parseFieldAccessOn(expr)
                }

                else -> break
            }
        }

        return expr
    }

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

            MINUS, MINUS_TIGHT -> {
                advance()
                val operand = parseAtom()
                UnaryOp(UnaryOperator.Neg, operand, token.span + operand.span)
            }

            IF -> parseIfThenElse(token)

            LPAREN -> {
                advance()
                val expr = parseExpr()
                expectAndAdvance(RPAREN, message = "Expected ')'")
                expr
            }

            PIPE -> {
                advance()
                parseLambda(token)
            }

            DOT -> parseImplicitParam(token)

            LBRACE -> parseRecordLiteral(token)

            FUN -> throw ParseError("Function definitions are only allowed at the top level", token.span)

            else -> throw ParseError("Expected expression, got $token", token.span)
        }
    }

    private fun parseIfThenElse(ifToken: Token): IfThenElse {
        val ifIndent = ifToken.indent ?: currentLineIndent
        advance()
        val condition = parseExpr()
        expectAndAdvance(THEN, message = "Expected 'then'")
        val thenBranch = parseBlockOrExpr()
        val elseBranch = tryParseElse(ifIndent)
        val endSpan = elseBranch?.span ?: thenBranch.span
        return IfThenElse(condition, thenBranch, elseBranch, ifToken.span + endSpan)
    }

    private fun tryParseElse(minIndent: Int): Expr? {
        val token = peek()
        if (token.kind != ELSE) return null
        if (token.startsLineBefore(minIndent)) return null
        advance()
        return parseBlockOrExpr()
    }

    private fun parseLambda(open: Token): Lambda {
        val params = parseLambdaParams()
        val body = parseBlockOrExpr()
        val close = expectAndAdvance(PIPE, message = "Expected '|'")
        return Lambda(params, body, open.span + close.span)
    }

    private fun parseLambdaParams(): List<String> {
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

        expectAndAdvance(ARROW, message = "Expected '->'")
        return params
    }

    private fun parseRecordLiteral(open: Token): RecordLiteral {
        advance()
        val fields = mutableListOf<Pair<String, Expr>>()

        while (peek().kind != RBRACE && peek().kind != EOF) {
            val nameToken = expectAndAdvance(IDENT, message = "Expected field name")
            val name = nameToken.text!!

            val value =
                if (peek().kind == EQ) {
                    advance()
                    parseExpr()
                } else {
                    Ident(name, nameToken.span)
                }

            fields.add(name to value)

            if (peek().kind == COMMA) {
                advance()
            } else {
                break
            }
        }

        val close = expectAndAdvance(RBRACE, message = "Expected '}'")
        return RecordLiteral(fields, open.span + close.span)
    }

    private fun parseImplicitParam(dotToken: Token): Expr {
        advance()
        val implicitParam = ImplicitParam(dotToken.span)
        val field = peek()
        return if (field.kind == IDENT) {
            advance()
            FieldAccess(implicitParam, field.text!!, dotToken.span + field.span)
        } else {
            implicitParam
        }
    }

    private fun parseFunctionCallOn(callee: Expr): Apply {
        advance()
        val args = parseArgs()
        val close = expectAndAdvance(RPAREN, message = "Expected ')'")
        return Apply(callee, args, callee.span + close.span)
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

    private fun parseFieldAccessOn(target: Expr): FieldAccess {
        advance()
        val field = expectAndAdvance(IDENT, message = "Expected field name after '.'")
        return FieldAccess(target, field.text!!, target.span + field.span)
    }

    private fun isBinding(): Boolean = peek().kind == IDENT && peekAt(1).kind == EQ

    private fun endsWithBlock(stmt: Stmt): Boolean =
        when (stmt) {
            is Val -> endsWithBlockExpr(stmt.value)
            is FunDef -> endsWithBlockExpr(stmt.body)
            is Expr -> endsWithBlockExpr(stmt)
        }

    private fun endsWithBlockExpr(expr: Expr): Boolean =
        when (expr) {
            is Block -> true
            is IfThenElse -> endsWithBlockExpr(expr.elseBranch ?: expr.thenBranch)
            else -> false
        }

    private fun peekBinaryOp(): Operator? {
        val token = peek()
        if (token.kind == MINUS_TIGHT && token.startsLineAtOrBefore(currentLineIndent)) {
            return null
        }
        return Operator.fromTokenKind(token.kind)
    }

    private fun isBlockStart() = peek().startsLineAfter(currentLineIndent)

    private fun isBlockEnd(): Boolean {
        val next = peek()
        if (next.kind in setOf(RPAREN, RBRACE, RBRACKET, ELSE, EOF)) return true
        // PIPE ends block unless it's indented further than the block (starting a nested lambda)
        if (next.kind == PIPE && !next.startsLineAfter(currentLineIndent)) return true
        return next.startsLineBefore(currentLineIndent)
    }

    private fun peek(): Token = tokens[pos]

    private fun peekAt(offset: Int): Token = tokens[pos + offset]

    private fun advance(): Token {
        val token = tokens[pos++]
        token.indent?.let { currentLineIndent = it }
        return token
    }

    private fun expectAndAdvance(
        vararg kinds: TokenKind,
        message: String,
    ): Token {
        val token = peek()
        if (token.kind !in kinds) {
            throw ParseError("$message, got $token", token.span)
        }
        return if (token.kind == EOF) token else advance()
    }
}
