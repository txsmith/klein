package klein.surface

import klein.KleinError
import klein.Revision
import klein.SourceSpan

import klein.surface.TokenKind.*

class ParseError(
    override val message: String,
    override val span: SourceSpan,
) : Exception(message),
    KleinError

class Parser(
    private val tokens: List<Token>,
) {
    private var pos: Int = 0

    /**
     * Indent of the line of the last consumed token; updates on consumption, so it lags [peek] at a
     * line boundary. For the line a specific token is on, use [lineIndentOf(peek())] / [lineIndentOf].
     */
    private var lastTokenIndent: Int = 0

    private fun lineIndentOf(token: Token): Int = token.indent ?: lastTokenIndent

    /** The rule language: type definitions, function definitions, and statements that run. */
    fun parseProgram(): Program {
        val start = peek().span
        val stmts = mutableListOf<Stmt>()
        while (peek().kind != EOF) {
            val stmt =
                when (peek().kind) {
                    FUN -> parseFunDef()
                    TYPE -> TypeDefStmt(parseTypeDef(::expectNoRevision))
                    else -> parseStmt()
                }
            stmts.add(stmt)
        }
        val end = if (stmts.isNotEmpty()) stmts.last().span else start
        return Program(stmts, start + end)
    }

    /**
     * The contract language: type definitions and declarations, and nothing that computes. The
     * sub-parsers below `parseType` are shared with [parseProgram] verbatim; only how a revision is
     * read differs, and that is the argument threaded through them.
     */
    fun parseContract(): ContractExpr {
        val start = peek().span
        val types = mutableListOf<TypeDef<*>>()
        val declarations = mutableListOf<Declaration>()
        var end = start
        while (peek().kind != EOF) {
            end =
                when {
                    peek().kind == TYPE -> parseTypeDef(::contractRevision).also { types.add(it) }.span
                    peek().kind == FUN -> parseFunDecl().also { declarations.add(it) }.span
                    isDestructuringBinding() ->
                        throw ParseError(definitionInContract(parsePattern().boundNames.firstOrNull()), peek().span)
                    isBinding() -> parseValDecl().also { declarations.add(it) }.span
                    else ->
                        throw ParseError(
                            "A capability contract has nothing to evaluate; it only declares what the host provides",
                            peek().span,
                        )
                }
            if (!canEndStatement()) {
                throw ParseError("Expected newline but got ${peek()}", peek().span)
            }
        }
        return ContractExpr(types, declarations, start + end)
    }

    fun parseStmt(allowTypeDef: Boolean = true): Stmt {
        // Check for keyword used as variable name in binding
        val token = peek()
        if (token.kind.keyword != null && peekAt(1).kind == EQ) {
            throw ParseError("Expected identifier, got keyword '${token.kind.keyword}'", token.span)
        }

        val stmt =
            when {
                peek().kind == TYPE && allowTypeDef -> TypeDefStmt(parseTypeDef(::expectNoRevision))
                isDestructuringBinding() -> parseDestructuringBinding()
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

    private fun parseFunDef(): Stmt {
        val funToken = advance()
        val name = expectName("Expected function name")
        expectNoRevision(name.text!!)
        expectAndAdvance(LPAREN, message = "Expected '('")
        val params = parseFunParams(::expectNoRevision)
        expectAndAdvance(RPAREN, message = "Expected ')'")
        val returnType = parseOptionalTypeAnnotation { parseTypeExpr(::expectNoRevision) }
        if (returnType != null && peek().kind != EQ) {
            throw ParseError(declarationWithoutBody(name.text), funToken.span + returnType.span)
        }
        expectAndAdvance(EQ, message = "Expected '='")
        val body = parseBlockOrExpr()
        return FunDef(name.text, params, body, funToken.span + body.span, returnType)
    }

    private fun parseFunDecl(): FunDecl {
        val funToken = advance()
        val name = expectName("Expected function name")
        val revision = contractRevision(name.text!!)
        expectAndAdvance(LPAREN, message = "Expected '('")
        val params = parseFunParams(::contractRevision)
        expectAndAdvance(RPAREN, message = "Expected ')'")
        val returnType =
            parseOptionalTypeAnnotation { parseTypeExpr(::contractRevision) }
                ?: throw ParseError("Expected ':' and a return type, got ${peek()}", peek().span)
        if (peek().kind == EQ) {
            throw ParseError(definitionInContract(name.text), peek().span)
        }
        return FunDecl(name.text, params, returnType, funToken.span + returnType.span, revision)
    }

    private fun parseValDecl(): ValDecl {
        val name = expectName("Expected identifier")
        val revision = contractRevision(name.text!!)
        val type =
            parseOptionalTypeAnnotation { parseTypeExpr(::contractRevision) }
                ?: throw ParseError(definitionInContract(name.text), peek().span)
        if (peek().kind == EQ) {
            throw ParseError(definitionInContract(name.text), peek().span)
        }
        return ValDecl(name.text, type, name.span + type.span, revision)
    }

    private fun declarationWithoutBody(name: String): String =
        "'$name' has no body; declarations without a body are only allowed in a capability contract"

    private fun definitionInContract(name: String?): String =
        if (name == null) {
            "A capability contract only declares what the host provides; it defines nothing itself"
        } else {
            "'$name' is defined here; a capability contract only declares what the host provides"
        }

    /** How a contract reads a revision: `/N` where one is written, absent otherwise. */
    private fun contractRevision(name: String): Revision? = parseRevisionSuffix()

    /**
     * How a rule reads a revision: it does not. `Nothing?` has one inhabitant, so every type
     * expression the rule parser builds is provably unrevisioned; writing one is refused here, at
     * the position it was written.
     */
    private fun expectNoRevision(name: String): Nothing? {
        if (peek().kind == SLASH && peekAt(1).kind == INT) {
            throw ParseError(
                "'$name/${peekAt(1).text}' names a revision; revision syntax is only allowed in a capability contract",
                peek().span + peekAt(1).span,
            )
        }
        return null
    }

    private fun parseRevisionSuffix(): Revision? {
        if (peek().kind != SLASH) return null
        val slash = advance()
        val number = peek()
        if (number.kind != INT) {
            throw ParseError("Expected a revision number after '/', got $number", slash.span + number.span)
        }
        advance()
        val revision = number.text!!.toIntOrNull()
        if (revision == null || revision < 1) {
            throw ParseError("Revision must be a positive integer, got '${number.text}'", number.span)
        }
        return Revision(revision)
    }

    private fun <R : Revision?> parseTypeDef(revision: (name: String) -> R): TypeDef<R> {
        val typeToken = advance()
        val typeDefIndent = lineIndentOf(typeToken)

        val nameToken = expectUpperIdent("Expected type name")
        validateNotReserved(nameToken)

        val typeRevision = revision(nameToken.text!!)
        val typeParams = if (peek().kind == LT) parseTypeParams() else emptyList()

        val constructors =
            if (peek().kind == EQ && !peek().startsLineBefore(typeDefIndent)) {
                advance()
                parseConstructors(typeDefIndent, revision)
            } else {
                emptyList()
            }

        val endSpan = constructors.lastOrNull()?.span ?: typeParams.lastOrNull()?.let { nameToken.span } ?: nameToken.span
        return TypeDef(nameToken.text, typeParams, constructors, typeToken.span + endSpan, typeRevision)
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

    private fun <R : Revision?> parseConstructors(
        typeDefIndent: Int,
        revision: (name: String) -> R,
    ): List<Constructor<R>> {
        val constructors = mutableListOf<Constructor<R>>()
        val seenNames = mutableSetOf<String>()

        constructors.add(parseConstructor(seenNames, revision))

        while (peek().kind == PIPE && !peek().startsLineAtOrBefore(typeDefIndent)) {
            advance()
            constructors.add(parseConstructor(seenNames, revision))
        }

        return constructors
    }

    private fun <R : Revision?> parseConstructor(
        seenNames: MutableSet<String>,
        revision: (name: String) -> R,
    ): Constructor<R> {
        val nameToken = expectUpperIdent("Expected constructor name")
        validateNotReserved(nameToken)

        if (!seenNames.add(nameToken.text!!)) {
            throw ParseError("Duplicate constructor name: '${nameToken.text}'", nameToken.span)
        }

        val fields =
            if (peek().kind == LBRACE) {
                parseConstructorFields(revision)
            } else {
                emptyList()
            }

        val endSpan = fields.lastOrNull()?.span ?: nameToken.span
        return Constructor(nameToken.text, fields, nameToken.span + endSpan)
    }

    private fun <R : Revision?> parseConstructorFields(revision: (name: String) -> R): List<FieldDecl<R>> {
        advance()
        if (peek().kind == RBRACE) {
            throw ParseError("Constructor fields cannot be empty", peek().span)
        }

        val fields = mutableListOf<FieldDecl<R>>()
        val seenFields = mutableSetOf<String>()

        while (true) {
            val field = parseFieldDecl(seenFields, revision)
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

    private fun <R : Revision?> parseFieldDecl(
        seenFields: MutableSet<String>,
        revision: (name: String) -> R,
    ): FieldDecl<R> {
        val nameToken = expectName("Expected field name")

        if (!seenFields.add(nameToken.text!!)) {
            throw ParseError("Duplicate field name: '${nameToken.text}'", nameToken.span)
        }

        expectAndAdvance(COLON, message = "Expected ':'")
        val type = parseTypeExpr(revision)

        return FieldDecl(nameToken.text, type, nameToken.span + type.span)
    }

    private fun <R : Revision?> parseTypeExpr(revision: (name: String) -> R): TypeExpr<R> {
        val left = parseTypePostfix(revision)

        if (peek().kind == ARROW) {
            advance()
            val right = parseTypeExpr(revision)
            val paramTypes = if (left is TupleTypeExpr<R>) left.elements else listOf(left)
            return FunctionTypeExpr(paramTypes, right, left.span + right.span)
        }

        return left
    }

    /** Postfix `?` (nullable) binds tighter than `->`: `Num -> Num?` is a function returning `Num?`,
     *  while `(Num -> Num)?` is an optional function. Repeated `?` parse and are collapsed to a
     *  single optional during type resolution (`T?? = T?`). */
    private fun <R : Revision?> parseTypePostfix(revision: (name: String) -> R): TypeExpr<R> {
        var type = parseTypeAtom(revision)
        while (peek().kind == QUESTION) {
            val question = advance()
            type = OptionalTypeExpr(type, type.span + question.span)
        }
        return type
    }

    private fun <R : Revision?> parseTypeArgs(revision: (name: String) -> R): List<TypeExpr<R>> {
        advance()
        val args = mutableListOf<TypeExpr<R>>()
        args.add(parseTypeExpr(revision))
        while (peek().kind == COMMA) {
            advance()
            args.add(parseTypeExpr(revision))
        }
        expectAndAdvance(GT, message = "Expected '>'")
        return args
    }

    private fun <R : Revision?> parseTypeAtom(revision: (name: String) -> R): TypeExpr<R> {
        val token = peek()
        return when (token.kind) {
            UPPER_IDENT -> {
                advance()
                val typeRevision = revision(token.text!!)
                if (peek().kind == LT) {
                    val args = parseTypeArgs(revision)
                    AppliedTypeExpr(token.text, args, token.span + args.last().span, typeRevision)
                } else {
                    TypeName(token.text, token.span, typeRevision)
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
                    val innerType = parseTypeExpr(revision)
                    if (peek().kind == COMMA) {
                        val elements = mutableListOf(innerType)
                        while (peek().kind == COMMA) {
                            advance()
                            elements.add(parseTypeExpr(revision))
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
                val fields = mutableListOf<Pair<String, TypeExpr<R>>>()
                while (peek().kind != RBRACE && peek().kind != EOF) {
                    val fieldName = expectName("Expected field name")
                    expectAndAdvance(COLON, message = "Expected ':'")
                    val fieldType = parseTypeExpr(revision)
                    fields.add(fieldName.text!! to fieldType)
                    if (peek().kind == COMMA) {
                        advance()
                    } else {
                        break
                    }
                }
                val close = expectAndAdvance(RBRACE, message = "Expected '}'")
                if (fields.isEmpty()) {
                    throw ParseError("Empty record type '{}' is not allowed; use 'Any' instead", token.span + close.span)
                }
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
        val reserved = setOf("Type", "If", "Then", "Else", "Fun", "And", "Or", "Not", "Match")
        if (token.text in reserved) {
            throw ParseError("'${token.text}' is a reserved word", token.span)
        }
    }

    private fun expectName(message: String): Token {
        val token = expectIdentifier(message)
        if (token.text == "_") {
            throw ParseError("_ (underscore) cannot be used as a name", token.span)
        }
        return token
    }

    private fun <R : Revision?> parseFunParams(revision: (name: String) -> R): List<Param<R>> {
        if (peek().kind == RPAREN) return emptyList()

        val params = mutableListOf<Param<R>>()
        params.add(parseAnnotatedParam(revision))

        while (peek().kind == COMMA) {
            advance()
            params.add(parseAnnotatedParam(revision))
        }

        return params
    }

    private fun <T> parseOptionalTypeAnnotation(typeParser: () -> T): T? =
        if (peek().kind == COLON) {
            advance()
            typeParser()
        } else {
            null
        }

    private fun <R : Revision?> parseAnnotatedParam(revision: (name: String) -> R): Param<R> {
        val name = expectIdentifier("Expected parameter name")
        val typeAnnotation = parseOptionalTypeAnnotation { parseTypeExpr(revision) }
        val span = name.span + (typeAnnotation?.span ?: name.span)
        return Param(name.text!!, typeAnnotation, span)
    }

    private fun parseBinding(): Stmt {
        val name = expectName("Expected identifier")
        expectNoRevision(name.text!!)
        val typeAnnotation = parseOptionalTypeAnnotation { parseTypeExpr(::expectNoRevision) }
        if (typeAnnotation != null && peek().kind != EQ) {
            throw ParseError(declarationWithoutBody(name.text), name.span + typeAnnotation.span)
        }
        expectAndAdvance(EQ, message = "Expected =")
        val value = parseBlockOrExpr()
        return Val(name.text, value, name.span + value.span, typeAnnotation)
    }

    private fun parseBlockOrExpr(): Expr = if (isBlockStart()) parseBlock() else parseExpr()

    private fun parseBlock(): Block {
        val stmts = mutableListOf<Stmt>()

        val blockStartSpan = peek().span
        var blockEndSpan = blockStartSpan

        val blockIndent = lineIndentOf(peek())
        var boundaryIndent = lastTokenIndent

        while (!isBlockEnd(boundaryIndent)) {
            val stmt = parseStmt(allowTypeDef = false)
            blockEndSpan = stmt.span
            stmts.add(stmt)
            boundaryIndent = blockIndent
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
        val exprIndent = lineIndentOf(peek())
        var expr = parseAtom()

        while (true) {
            // A postfix `(`/`.`/`?.` only continues this expression while it stays more indented than
            // the line the expression began on; at or below that indent it belongs to a new statement.
            if (peek().startsLineAtOrBefore(exprIndent)) break

            when (peek().kind) {
                LPAREN -> {
                    expr = parseFunctionCallOn(expr)
                }

                DOT -> {
                    expr = parseFieldAccessOn(expr)
                }

                QUESTION_DOT -> {
                    val access = parseSafeFieldAccessOn(expr)
                    expr =
                        if (peek().kind == LPAREN && !peek().startsLineAtOrBefore(exprIndent)) {
                            val call = parseFunctionCallOn(access)
                            SafeApply(access.target, access.field, call.args, call.span)
                        } else {
                            access
                        }
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
                IntLiteral(value, token.span, token.text)
            }

            DOUBLE -> {
                advance()
                val value = token.text!!.toDoubleOrNull() ?: throw ParseError("Invalid number: ${token.text}", token.span)
                DoubleLiteral(value, token.span, token.text)
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

            MATCH -> parseMatch(token)

            LPAREN -> {
                advance()
                val expr = parseExpr()
                if (peek().kind == COLON) {
                    advance()
                    val type = parseTypeExpr(::expectNoRevision)
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
        val ifIndent = lineIndentOf(ifToken)
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

    private fun parseMatch(matchToken: Token): Match {
        advance()
        val scrutinee = parseExpr()

        val first = peek()
        if (!first.startsLineAfter(lineIndentOf(matchToken))) {
            throw ParseError("Expected indented match arms after scrutinee", first.span)
        }
        val armIndent = lineIndentOf(first)

        val arms = mutableListOf<MatchArm>()
        var boundaryIndent = lineIndentOf(matchToken)
        while (!isBlockEnd(boundaryIndent)) {
            arms.add(parseMatchArm())
            boundaryIndent = armIndent
        }
        if (arms.isEmpty()) {
            throw ParseError("Expected at least one match arm", first.span)
        }

        return Match(scrutinee, arms, matchToken.span + arms.last().span)
    }

    private fun parseMatchArm(): MatchArm {
        val pattern = parsePattern()
        val guard =
            if (peek().kind == IF) {
                advance()
                parseExpr()
            } else {
                null
            }
        expectAndAdvance(ARROW, message = "Expected '->' after pattern")
        val body = parseBlockOrExpr()
        return MatchArm(pattern, guard, body, pattern.span + body.span)
    }

    private fun parsePattern(): Pattern {
        val token = peek()
        return when (token.kind) {
            INT -> {
                advance()
                val value = token.text!!.toLongOrNull() ?: throw ParseError("Invalid number: ${token.text}", token.span)
                LiteralPattern(IntLiteral(value, token.span, token.text), token.span)
            }

            DOUBLE -> {
                advance()
                val value = token.text!!.toDoubleOrNull() ?: throw ParseError("Invalid number: ${token.text}", token.span)
                LiteralPattern(DoubleLiteral(value, token.span, token.text), token.span)
            }

            MINUS, MINUS_TIGHT -> {
                advance()
                val number = expectAndAdvance(INT, DOUBLE, message = "Expected number after '-' in pattern")
                val span = token.span + number.span
                val literal =
                    when (number.kind) {
                        INT -> IntLiteral(-number.text!!.toLong(), span, "-${number.text}")
                        else -> DoubleLiteral(-number.text!!.toDouble(), span, "-${number.text}")
                    }
                LiteralPattern(literal, span)
            }

            STRING -> {
                advance()
                LiteralPattern(StringLiteral(token.text!!, token.span), token.span)
            }

            TRUE -> {
                advance()
                LiteralPattern(BoolLiteral(true, token.span), token.span)
            }

            FALSE -> {
                advance()
                LiteralPattern(BoolLiteral(false, token.span), token.span)
            }

            NULL -> {
                advance()
                LiteralPattern(NullLiteral(token.span), token.span)
            }

            IDENT -> {
                advance()
                when {
                    token.text == "_" -> WildcardPattern(token.span)
                    peek().kind == LBRACE -> {
                        val (fields, fieldsSpan) = parseRecordFields()
                        DataPattern(null, token.text, fields, token.span + fieldsSpan)
                    }
                    else -> VariablePattern(token.text!!, token.span)
                }
            }

            UPPER_IDENT -> {
                advance()
                var span = token.span
                val binder =
                    if (peek().kind == IDENT) {
                        val b = advance()
                        span += b.span
                        b.text!!.takeUnless { it == "_" }
                    } else {
                        null
                    }
                val fields =
                    if (peek().kind == LBRACE) {
                        val (fs, fieldsSpan) = parseRecordFields()
                        span += fieldsSpan
                        fs
                    } else {
                        emptyList()
                    }
                DataPattern(token.text, binder, fields, span)
            }

            LBRACE -> {
                val (fields, fieldsSpan) = parseRecordFields()
                DataPattern(null, null, fields, fieldsSpan)
            }

            else -> throw ParseError("Expected pattern, got $token", token.span)
        }
    }

    private fun parseRecordFields(): Pair<List<FieldPattern>, SourceSpan> {
        val open = expectAndAdvance(LBRACE, message = "Expected '{'")
        if (peek().kind == RBRACE) {
            throw ParseError("Record pattern must name at least one field", open.span + peek().span)
        }

        val fields = mutableListOf<FieldPattern>()
        val seenFields = mutableSetOf<String>()

        while (true) {
            val fieldToken = expectName("Expected field name")
            if (!seenFields.add(fieldToken.text!!)) {
                throw ParseError("Duplicate field in pattern: '${fieldToken.text}'", fieldToken.span)
            }

            val binder =
                if (peek().kind == EQ) {
                    advance()
                    expectIdentifier("Expected binder name or '_' after '='")
                } else {
                    fieldToken
                }
            val binderName = binder.text!!.takeUnless { it == "_" }
            fields.add(FieldPattern(fieldToken.text, binderName, fieldToken.span + binder.span))

            when (peek().kind) {
                RBRACE -> break
                COMMA -> {
                    advance()
                    if (peek().kind == RBRACE) break
                }
                else -> throw ParseError("Expected ',' or '}'", peek().span)
            }
        }

        val close = advance()
        return fields to (open.span + close.span)
    }

    private fun parseLambda(open: Token): Lambda {
        val params = parseLambdaParams()
        val body = parseBlockOrExpr()
        val close = expectAndAdvance(PIPE, message = "Expected '|'")
        return Lambda(params, body, open.span + close.span)
    }

    private fun parseLambdaParams(): List<Param<Nothing?>> {
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

        val params = mutableListOf<Param<Nothing?>>()
        while (peek().kind == IDENT) {
            val ident = advance()
            // Parse an atom plus any postfix `?`, but not a `->` function type — the arrow belongs to
            // the lambda (`|x: Num? -> x|`), so a nullable param annotation must still work here.
            val typeAnnotation = parseOptionalTypeAnnotation { parseTypePostfix(::expectNoRevision) }
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
        val fields = mutableListOf<RecordField>()

        while (peek().kind != RBRACE && peek().kind != EOF) {
            val nameToken = expectName("Expected field name")
            val name = nameToken.text!!

            val typeAnnotation = parseOptionalTypeAnnotation { parseTypeExpr(::expectNoRevision) }
            val value =
                if (peek().kind == EQ) {
                    advance()
                    parseExpr()
                } else if (typeAnnotation != null) {
                    throw ParseError("Expected '=' after type annotation", peek().span)
                } else {
                    Ident(name, nameToken.span)
                }

            fields.add(RecordField(name, value, typeAnnotation))

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
        return if (peek().kind == IDENT) {
            val field = expectName("Expected field name")
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
        val field = expectName("Expected field name after '.'")
        return FieldAccess(target, field.text!!, target.span + field.span)
    }

    private fun parseSafeFieldAccessOn(target: Expr): SafeFieldAccess {
        advance()
        val field = expectName("Expected field name after '?.'")
        return SafeFieldAccess(target, field.text!!, target.span + field.span)
    }

    private fun isBinding(): Boolean =
        peek().kind == IDENT && (peekAt(1).kind == EQ || peekAt(1).kind == COLON || isRevisionedBinding())

    private fun isRevisionedBinding(): Boolean =
        peekAt(1).kind == SLASH && peekAt(2).kind == INT && (peekAt(3).kind == COLON || peekAt(3).kind == EQ)

    private fun isDestructuringBinding(): Boolean {
        var i =
            when {
                peek().kind == LBRACE -> 0
                peek().kind == IDENT && peekAt(1).kind == LBRACE -> 1
                peek().kind == UPPER_IDENT && peekAt(1).kind == LBRACE -> 1
                peek().kind == UPPER_IDENT && peekAt(1).kind == IDENT ->
                    if (peekAt(2).kind == LBRACE) 2 else return peekAt(2).kind == EQ
                else -> return false
            }
        var depth = 0
        while (true) {
            when (peekAt(i).kind) {
                LBRACE -> depth++
                RBRACE -> {
                    depth--
                    if (depth == 0) return peekAt(i + 1).kind == EQ
                }
                EOF -> return false
                else -> {}
            }
            i++
        }
    }

    private fun parseDestructuringBinding(): PatternVal {
        val pattern = parsePattern()
        expectAndAdvance(EQ, message = "Expected '='")
        val value = parseBlockOrExpr()
        return PatternVal(pattern, value, pattern.span + value.span)
    }

    private fun endsWithBlock(stmt: Stmt): Boolean =
        when (stmt) {
            is Val -> endsWithBlockExpr(stmt.value)
            is PatternVal -> endsWithBlockExpr(stmt.value)
            is FunDef -> endsWithBlockExpr(stmt.body)
            is TypeDefStmt -> false
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
        if (token.kind == MINUS_TIGHT && token.startsLineAtOrBefore(lastTokenIndent)) {
            return null
        }
        return Operator.fromTokenKind(token.kind)
    }

    private fun isBlockStart() = peek().startsLineAfter(lastTokenIndent)

    // Can the next token end a block?
    private fun isBlockEnd(boundaryIndent: Int): Boolean {
        val next = peek()
        if (next.kind in setOf(RPAREN, RBRACE, RBRACKET, ELSE, EOF)) return true
        // PIPE ends block unless it's indented further than the block (starting a nested lambda)
        if (next.kind == PIPE && !next.startsLineAfter(boundaryIndent)) return true
        return next.startsLineBefore(boundaryIndent)
    }

    private fun peek(): Token = tokens[pos]

    private fun peekAt(offset: Int): Token {
        val index = pos + offset
        return if (index < tokens.size) tokens[index] else tokens.last()
    }

    private fun advance(): Token {
        val token = tokens[pos++]
        token.indent?.let { lastTokenIndent = it }
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
