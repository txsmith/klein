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
            val stmt =
                when (peek().kind) {
                    FUN -> parseFunDef()
                    TYPE -> parseTypeDef()
                    else -> parseStmt()
                }
            stmts.add(stmt)
        }
        val end = if (stmts.isNotEmpty()) stmts.last().span else start
        return Program(stmts, start + end)
    }

    fun parseStmt(allowTypeDef: Boolean = true): Stmt {
        // Check for keyword used as variable name in binding
        val token = peek()
        if (token.kind.keyword != null && peekAt(1).kind == EQ) {
            throw ParseError("Expected identifier, got keyword '${token.kind.keyword}'", token.span)
        }

        val stmt =
            when {
                peek().kind == TYPE && allowTypeDef -> parseTypeDef()
                isBinding() -> parseBinding()
                else -> parseExpr()
            }

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
        val name = expectIdentifier("Expected function name")
        expectAndAdvance(LPAREN, message = "Expected '('")
        val params = parseFunParams()
        expectAndAdvance(RPAREN, message = "Expected ')'")
        val returnType = parseOptionalTypeAnnotation()
        expectAndAdvance(EQ, message = "Expected '='")
        val body = parseBlockOrExpr()
        return FunDef(name.text!!, params, body, funToken.span + body.span, returnType)
    }

    private fun parseTypeDef(): TypeDef {
        val typeToken = advance()
        val typeDefIndent = typeToken.indent ?: currentLineIndent

        val nameToken = expectUpperIdent("Expected type name")
        validateNotReserved(nameToken)

        val typeParams = if (peek().kind == LT) parseTypeParams() else emptyList()

        val constructors =
            if (peek().kind == EQ && !peek().startsLineBefore(typeDefIndent)) {
                advance()
                parseConstructors(typeDefIndent)
            } else {
                emptyList()
            }

        val endSpan = constructors.lastOrNull()?.span ?: typeParams.lastOrNull()?.let { nameToken.span } ?: nameToken.span
        return TypeDef(nameToken.text!!, typeParams, constructors, typeToken.span + endSpan)
    }

    private fun parseTypeParams(): List<String> {
        advance()
        if (peek().kind == GT) {
            throw ParseError("Type parameter list cannot be empty", peek().span)
        }

        val params = mutableListOf<String>()
        val seenParams = mutableSetOf<String>()

        while (true) {
            val paramToken = expectAndAdvance(TYPE_VAR, message = "Expected type variable (e.g., 'A)")
            val paramName = paramToken.text!!
            if (!seenParams.add(paramName)) {
                throw ParseError("Duplicate type parameter: '$paramName", paramToken.span)
            }
            params.add(paramName)

            when (peek().kind) {
                GT -> {
                    advance()
                    break
                }
                COMMA -> {
                    advance()
                    if (peek().kind == GT) {
                        advance()
                        break
                    }
                }
                else -> throw ParseError("Expected ',' or '>'", peek().span)
            }
        }

        return params
    }

    private fun parseConstructors(typeDefIndent: Int): List<Constructor> {
        val constructors = mutableListOf<Constructor>()
        val seenNames = mutableSetOf<String>()

        constructors.add(parseConstructor(seenNames))

        while (peek().kind == PIPE && !peek().startsLineAtOrBefore(typeDefIndent)) {
            advance()
            constructors.add(parseConstructor(seenNames))
        }

        return constructors
    }

    private fun parseConstructor(seenNames: MutableSet<String>): Constructor {
        val nameToken = expectUpperIdent("Expected constructor name")
        validateNotReserved(nameToken)

        if (!seenNames.add(nameToken.text!!)) {
            throw ParseError("Duplicate constructor name: '${nameToken.text}'", nameToken.span)
        }

        val fields =
            if (peek().kind == LBRACE) {
                parseConstructorFields()
            } else {
                emptyList()
            }

        val endSpan = fields.lastOrNull()?.span ?: nameToken.span
        return Constructor(nameToken.text, fields, nameToken.span + endSpan)
    }

    private fun parseConstructorFields(): List<FieldDecl> {
        advance()
        if (peek().kind == RBRACE) {
            throw ParseError("Constructor fields cannot be empty", peek().span)
        }

        val fields = mutableListOf<FieldDecl>()
        val seenFields = mutableSetOf<String>()

        while (true) {
            val field = parseFieldDecl(seenFields)
            fields.add(field)

            when (peek().kind) {
                RBRACE -> {
                    advance()
                    break
                }
                COMMA -> {
                    advance()
                    if (peek().kind == RBRACE) {
                        advance()
                        break
                    }
                }
                else -> throw ParseError("Expected ',' or '}'", peek().span)
            }
        }

        return fields
    }

    private fun parseFieldDecl(seenFields: MutableSet<String>): FieldDecl {
        val nameToken = expectAndAdvance(IDENT, message = "Expected field name")
        validateNotKeyword(nameToken)

        if (!seenFields.add(nameToken.text!!)) {
            throw ParseError("Duplicate field name: '${nameToken.text}'", nameToken.span)
        }

        expectAndAdvance(COLON, message = "Expected ':'")
        val type = parseTypeExpr()

        return FieldDecl(nameToken.text, type, nameToken.span + type.span)
    }

    private fun parseTypeExpr(): TypeExpr {
        val left = parseTypeAtom()

        if (peek().kind == ARROW) {
            advance()
            val right = parseTypeExpr()
            val paramTypes =
                if (left is TupleTypeExpr && left.elements.isEmpty()) {
                    emptyList()
                } else {
                    listOf(left)
                }
            return FunctionTypeExpr(paramTypes, right, left.span + right.span)
        }

        return left
    }

    private fun parseTypeArgs(): List<TypeExpr> {
        advance()
        val args = mutableListOf<TypeExpr>()
        args.add(parseTypeExpr())
        while (peek().kind == COMMA) {
            advance()
            args.add(parseTypeExpr())
        }
        expectAndAdvance(GT, message = "Expected '>'")
        return args
    }

    private fun parseTypeAtom(): TypeExpr {
        val token = peek()
        return when (token.kind) {
            UPPER_IDENT -> {
                advance()
                if (peek().kind == LT) {
                    val args = parseTypeArgs()
                    AppliedTypeExpr(token.text!!, args, token.span + args.last().span)
                } else {
                    TypeName(token.text!!, token.span)
                }
            }

            TYPE_VAR -> {
                advance()
                TypeVar(token.text!!, token.span)
            }

            LPAREN -> {
                advance()
                if (peek().kind == RPAREN) {
                    val close = advance()
                    TupleTypeExpr(emptyList(), token.span + close.span)
                } else {
                    val innerType = parseTypeExpr()
                    if (peek().kind == COMMA) {
                        val elements = mutableListOf(innerType)
                        while (peek().kind == COMMA) {
                            advance()
                            elements.add(parseTypeExpr())
                        }
                        val close = expectAndAdvance(RPAREN, message = "Expected ')'")
                        TupleTypeExpr(elements, token.span + close.span)
                    } else {
                        expectAndAdvance(RPAREN, message = "Expected ')'")
                        innerType
                    }
                }
            }

            LBRACE -> {
                advance()
                val fields = mutableListOf<Pair<String, TypeExpr>>()
                while (peek().kind != RBRACE && peek().kind != EOF) {
                    val fieldName = expectAndAdvance(IDENT, message = "Expected field name")
                    expectAndAdvance(COLON, message = "Expected ':'")
                    val fieldType = parseTypeExpr()
                    fields.add(fieldName.text!! to fieldType)
                    if (peek().kind == COMMA) {
                        advance()
                    } else {
                        break
                    }
                }
                val close = expectAndAdvance(RBRACE, message = "Expected '}'")
                RecordTypeExpr(fields, token.span + close.span)
            }

            else -> throw ParseError("Expected type", token.span)
        }
    }

    private fun expectUpperIdent(message: String): Token {
        val token = peek()
        if (token.kind != UPPER_IDENT) {
            val errorMsg =
                if (token.kind == IDENT) {
                    "$message (must start with uppercase letter)"
                } else {
                    message
                }
            throw ParseError(errorMsg, token.span)
        }
        return advance()
    }

    private fun validateNotReserved(token: Token) {
        val reserved = setOf("Type", "If", "Then", "Else", "Fun", "And", "Or", "Not")
        if (token.text in reserved) {
            throw ParseError("'${token.text}' is a reserved word", token.span)
        }
    }

    private fun validateNotKeyword(token: Token) {
        if (TokenKind.fromKeyword(token.text!!) != null) {
            throw ParseError("'${token.text}' is a keyword and cannot be used as a field name", token.span)
        }
    }

    private fun parseFunParams(): List<Param> {
        if (peek().kind == RPAREN) return emptyList()

        val params = mutableListOf<Param>()
        params.add(parseAnnotatedParam())

        while (peek().kind == COMMA) {
            advance()
            params.add(parseAnnotatedParam())
        }

        return params
    }

    private fun parseOptionalTypeAnnotation(typeParser: () -> TypeExpr = ::parseTypeExpr): TypeExpr? =
        if (peek().kind == COLON) {
            advance()
            typeParser()
        } else {
            null
        }

    private fun parseAnnotatedParam(): Param {
        val name = expectIdentifier("Expected parameter name")
        val typeAnnotation = parseOptionalTypeAnnotation()
        val span = name.span + (typeAnnotation?.span ?: name.span)
        return Param(name.text!!, typeAnnotation, span)
    }

    private fun parseBinding(): Val {
        val name = expectIdentifier("Expected identifier")
        val typeAnnotation = parseOptionalTypeAnnotation()
        expectAndAdvance(EQ, message = "Expected =")
        val value = parseBlockOrExpr()
        return Val(name.text!!, value, name.span + value.span, typeAnnotation)
    }

    private fun parseBlockOrExpr(): Expr = if (isBlockStart()) parseBlock() else parseExpr()

    private fun parseBlock(): Block {
        val stmts = mutableListOf<Stmt>()

        val blockIndent = peek().indent ?: currentLineIndent
        val blockStartSpan = peek().span
        var blockEndSpan = blockStartSpan

        while (!isBlockEnd()) {
            val stmt = parseStmt(allowTypeDef = false)
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

                QUESTION_DOT -> {
                    expr = parseSafeFieldAccessOn(expr)
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

            IDENT, UPPER_IDENT -> {
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

            NULL -> {
                advance()
                NullLiteral(token.span)
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
                if (peek().kind == COLON) {
                    advance()
                    val type = parseTypeExpr()
                    val close = expectAndAdvance(RPAREN, message = "Expected ')'")
                    Ascription(expr, type, token.span + close.span)
                } else {
                    expectAndAdvance(RPAREN, message = "Expected ')'")
                    expr
                }
            }

            PIPE -> {
                advance()
                parseLambda(token)
            }

            DOT -> parseImplicitParam(token)

            LBRACE -> parseRecordLiteral(token)

            FUN -> throw ParseError("Function definitions are only allowed at the top level", token.span)

            TYPE -> throw ParseError("Type definitions are only allowed at the top level", token.span)

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

    private fun parseLambdaParams(): List<Param> {
        val token = peek()

        // Check if a keyword is being used as a parameter name
        if (token.kind.keyword != null) {
            val next = peekAt(1)
            if (next.kind == ARROW || next.kind == COMMA || next.kind == COLON) {
                throw ParseError("Expected parameter name, got keyword '${token.kind.keyword}'", token.span)
            }
        }

        if (token.kind != IDENT) return emptyList()

        val next = peekAt(1)
        if (next.kind != ARROW && next.kind != COMMA && next.kind != COLON) return emptyList()

        val params = mutableListOf<Param>()
        while (peek().kind == IDENT) {
            val ident = advance()
            // Use typeAtom to avoid consuming '->' as function type arrow
            val typeAnnotation = parseOptionalTypeAnnotation(::parseTypeAtom)
            val span = ident.span + (typeAnnotation?.span ?: ident.span)
            params.add(Param(ident.text!!, typeAnnotation, span))

            if (peek().kind == COMMA) {
                advance()
                // Check for keyword after comma
                val afterComma = peek()
                if (afterComma.kind.keyword != null && (peekAt(1).kind == ARROW || peekAt(1).kind == COMMA || peekAt(1).kind == COLON)) {
                    throw ParseError("Expected parameter name, got keyword '${afterComma.kind.keyword}'", afterComma.span)
                }
                if (afterComma.kind != IDENT) {
                    throw ParseError("Expected parameter name, got $afterComma", afterComma.span)
                }
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
            val nameToken = expectIdentifier("Expected field name")
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
        val field = expectIdentifier("Expected field name after '.'")
        return FieldAccess(target, field.text!!, target.span + field.span)
    }

    private fun parseSafeFieldAccessOn(target: Expr): SafeFieldAccess {
        advance()
        val field = expectIdentifier("Expected field name after '?.'")
        return SafeFieldAccess(target, field.text!!, target.span + field.span)
    }

    private fun isBinding(): Boolean =
        peek().kind == IDENT && (peekAt(1).kind == EQ || peekAt(1).kind == COLON)

    private fun endsWithBlock(stmt: Stmt): Boolean =
        when (stmt) {
            is Val -> endsWithBlockExpr(stmt.value)
            is FunDef -> endsWithBlockExpr(stmt.body)
            is TypeDef -> false
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

    private fun peekAt(offset: Int): Token {
        val index = pos + offset
        return if (index < tokens.size) tokens[index] else tokens.last()
    }

    private fun advance(): Token {
        val token = tokens[pos++]
        token.indent?.let { currentLineIndent = it }
        return token
    }

    private fun expectIdentifier(message: String): Token {
        val token = peek()
        if (token.kind != IDENT) {
            val errorMsg =
                if (token.kind.keyword != null) {
                    "$message, got keyword '${token.kind.keyword}'"
                } else {
                    "$message, got $token"
                }
            throw ParseError(errorMsg, token.span)
        }
        return advance()
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
