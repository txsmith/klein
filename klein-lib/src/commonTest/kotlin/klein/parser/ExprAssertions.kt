package klein.parser

import klein.ReleaseNumber
import klein.RevisionNumber
import klein.surface.AppliedTypeExpr
import klein.surface.Apply
import klein.surface.Ascription
import klein.surface.SafeApply
import klein.surface.BinaryOp
import klein.surface.Block
import klein.surface.BoolLiteral
import klein.surface.Constructor
import klein.surface.DataPattern
import klein.surface.DoubleLiteral
import klein.surface.Expr
import klein.surface.FieldAccess
import klein.surface.FieldDecl
import klein.surface.FieldPattern
import klein.surface.ContractExpr
import klein.surface.CapabilityDeclaration
import klein.surface.FunDecl
import klein.surface.FunDef
import klein.surface.ValDecl
import klein.surface.FunctionTypeExpr
import klein.surface.Ident
import klein.surface.IfThenElse
import klein.surface.ImplicitParam
import klein.surface.IntLiteral
import klein.surface.Lambda
import klein.surface.Lexer
import klein.surface.LiteralPattern
import klein.surface.Match
import klein.surface.MatchArm
import klein.surface.NullLiteral
import klein.surface.Operator
import klein.surface.OptionalTypeExpr
import klein.surface.Param
import klein.surface.ParseError
import klein.surface.Parser
import klein.surface.Pattern
import klein.surface.PatternVal
import klein.surface.Program
import klein.surface.RecordField
import klein.surface.RecordLiteral
import klein.surface.RecordTypeExpr
import klein.surface.ReleaseBlock
import klein.surface.ReleaseEntry
import klein.surface.SafeFieldAccess
import klein.SourceSpan
import klein.surface.Stmt
import klein.surface.StringLiteral
import klein.surface.TupleTypeExpr
import klein.surface.TypeDef
import klein.surface.TypeDefStmt
import klein.surface.TypeExpr
import klein.surface.TypeName
import klein.surface.TypeVar
import klein.surface.UnaryOp
import klein.surface.UnaryOperator
import klein.surface.Val
import klein.surface.VariablePattern
import klein.surface.WildcardPattern

private val noSpan = SourceSpan.zero

fun int(
    value: Long,
    text: String = value.toString(),
) = IntLiteral(value, noSpan, text)

fun int(
    value: Int,
    text: String = value.toString(),
) = IntLiteral(value.toLong(), noSpan, text)

fun double(
    value: Double,
    text: String = if (value % 1.0 == 0.0) "${value.toLong()}.0" else value.toString(),
) = DoubleLiteral(value, noSpan, text)

fun string(value: String) = StringLiteral(value, noSpan)

fun bool(value: Boolean) = BoolLiteral(value, noSpan)

fun nullLit() = NullLiteral(noSpan)

fun id(name: String) = Ident(name, noSpan)

fun neg(operand: Expr) = UnaryOp(UnaryOperator.Neg, operand, noSpan)

fun not(operand: Expr) = UnaryOp(UnaryOperator.Not, operand, noSpan)

fun <R : RevisionNumber?> param(name: String, type: TypeExpr<R>) = Param(name, type)

fun param(name: String) = Param<Nothing?>(name, null)

fun lambda(
    vararg params: String,
    body: Expr,
) = Lambda(params.map { Param(it) }, body, noSpan)

fun lambda(
    params: List<Param<Nothing?>>,
    body: Expr,
) = Lambda(params, body, noSpan)

fun call(
    callee: Expr,
    vararg args: Expr,
) = Apply(callee, args.toList(), noSpan)

fun block(vararg stmts: Stmt) = Block(stmts.toList(), noSpan)

fun add(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.Add, right, noSpan)

fun sub(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.Sub, right, noSpan)

fun mul(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.Mul, right, noSpan)

fun div(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.Div, right, noSpan)

fun mod(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.Mod, right, noSpan)

fun eq(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.Eq, right, noSpan)

fun neq(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.NotEq, right, noSpan)

fun lt(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.Lt, right, noSpan)

fun lte(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.LtEq, right, noSpan)

fun gt(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.Gt, right, noSpan)

fun gte(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.GtEq, right, noSpan)

fun and(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.And, right, noSpan)

fun or(
    left: Expr,
    right: Expr,
) = BinaryOp(left, Operator.Or, right, noSpan)

fun ifThenElse(
    condition: Expr,
    thenBranch: Expr,
    elseBranch: Expr? = null,
) = IfThenElse(condition, thenBranch, elseBranch, noSpan)

fun fieldAccess(
    target: Expr,
    field: String,
) = FieldAccess(target, field, noSpan)

fun safeFieldAccess(
    target: Expr,
    field: String,
) = SafeFieldAccess(target, field, noSpan)

fun safeApply(
    target: Expr,
    method: String,
    vararg args: Expr,
) = SafeApply(target, method, args.toList(), noSpan)

fun implicitParam() = ImplicitParam(noSpan)

fun record(vararg fields: Pair<String, Expr>) =
    RecordLiteral(fields.map { (name, value) -> RecordField(name, value) }, noSpan)

fun annotatedRecord(vararg fields: RecordField) =
    RecordLiteral(fields.toList(), noSpan)

fun recordField(name: String, value: Expr, typeAnnotation: TypeExpr<Nothing?>? = null) =
    RecordField(name, value, typeAnnotation)

fun matchExpr(
    scrutinee: Expr,
    vararg arms: MatchArm,
) = Match(scrutinee, arms.toList(), noSpan)

fun arm(
    pattern: Pattern,
    body: Expr,
    guard: Expr? = null,
) = MatchArm(pattern, guard, body, noSpan)

fun wildcardP() = WildcardPattern(noSpan)

fun litP(literal: Expr) = LiteralPattern(literal, noSpan)

fun varP(name: String) = VariablePattern(name, noSpan)

fun ctorP(name: String) = DataPattern(name, null, emptyList(), noSpan)

fun ctorBindP(
    name: String,
    binder: String,
    vararg fields: FieldPattern,
) = DataPattern(name, binder, fields.toList(), noSpan)

fun ctorP(
    name: String,
    vararg fields: FieldPattern,
) = DataPattern(name, null, fields.toList(), noSpan)

fun recordP(vararg fields: FieldPattern) = DataPattern(null, null, fields.toList(), noSpan)

fun recordBindP(
    binder: String,
    vararg fields: FieldPattern,
) = DataPattern(null, binder, fields.toList(), noSpan)

fun fieldP(
    field: String,
    binder: String? = field,
) = FieldPattern(field, binder, noSpan)

fun Pattern.stripSpan(): Pattern =
    when (this) {
        is WildcardPattern -> WildcardPattern(noSpan)
        is LiteralPattern -> LiteralPattern(literal.stripSpans(), noSpan)
        is VariablePattern -> VariablePattern(name, noSpan)
        is DataPattern -> DataPattern(tag, binder, fields.map { FieldPattern(it.field, it.binder, noSpan) }, noSpan)
    }

fun Expr.stripSpans(): Expr =
    when (this) {
        is IntLiteral -> IntLiteral(value, noSpan, text)
        is DoubleLiteral -> DoubleLiteral(value, noSpan, text)
        is StringLiteral -> StringLiteral(value, noSpan)
        is BoolLiteral -> BoolLiteral(value, noSpan)
        is NullLiteral -> NullLiteral(noSpan)
        is Ident -> Ident(name, noSpan)
        is UnaryOp -> UnaryOp(op, operand.stripSpans(), noSpan)
        is BinaryOp -> BinaryOp(left.stripSpans(), op, right.stripSpans(), noSpan)
        is Lambda -> Lambda(params.map { it.stripSpan() }, body.stripSpans(), noSpan)
        is Apply -> Apply(callee.stripSpans(), args.map { it.stripSpans() }, noSpan)
        is Block -> Block(stmts.map { it.stripSpan() }, noSpan)
        is IfThenElse -> IfThenElse(condition.stripSpans(), thenBranch.stripSpans(), elseBranch?.stripSpans(), noSpan)
        is FieldAccess -> FieldAccess(target.stripSpans(), field, noSpan)
        is SafeFieldAccess -> SafeFieldAccess(target.stripSpans(), field, noSpan)
        is SafeApply -> SafeApply(target.stripSpans(), method, args.map { it.stripSpans() }, noSpan)
        is ImplicitParam -> ImplicitParam(noSpan)
        is RecordLiteral -> RecordLiteral(fields.map { RecordField(it.name, it.value.stripSpans(), it.typeAnnotation?.stripSpan()) }, noSpan)
        is Ascription -> Ascription(expr.stripSpans(), type.stripSpan(), noSpan)
        is Match ->
            Match(
                scrutinee.stripSpans(),
                arms.map { MatchArm(it.pattern.stripSpan(), it.guard?.stripSpans(), it.body.stripSpans(), noSpan) },
                noSpan,
            )
    }

fun parse(source: String): Expr {
    val tokens = Lexer(source).tokenize().toList()
    return Parser(tokens).parseExpr()
}

fun assertExprEquals(
    actual: Expr,
    expected: Expr,
) {
    assertEqualsPretty(expected, actual.stripSpans())
}

fun valStmt(
    name: String,
    value: Expr,
    typeAnnotation: TypeExpr<Nothing?>? = null,
) = Val(name, value, noSpan, typeAnnotation)

fun patternVal(
    pattern: Pattern,
    value: Expr,
) = PatternVal(pattern, value, noSpan)

fun funDef(
    name: String,
    vararg params: String,
    body: Expr,
) = FunDef(name, params.map { Param(it) }, body, noSpan)

fun funDef(
    name: String,
    params: List<Param<Nothing?>>,
    body: Expr,
    returnType: TypeExpr<Nothing?>? = null,
) = FunDef(name, params, body, noSpan, returnType)

fun funDecl(
    name: String,
    params: List<Param<*>>,
    returnType: TypeExpr<*>,
    revision: RevisionNumber? = null,
) = FunDecl(name, params, returnType, noSpan, revision)

fun valDecl(
    name: String,
    type: TypeExpr<*>,
    revision: RevisionNumber? = null,
) = ValDecl(name, type, noSpan, revision)

fun releaseBlock(
    number: Int,
    vararg entries: ReleaseEntry,
) = ReleaseBlock(ReleaseNumber(number), entries.toList(), noSpan)

fun releaseEntry(
    name: String,
    revision: RevisionNumber? = null,
    remove: Boolean = false,
) = ReleaseEntry(name, revision, remove, noSpan)

fun ascription(
    expr: Expr,
    type: TypeExpr<Nothing?>,
) = Ascription(expr, type, noSpan)

@Suppress("UNCHECKED_CAST")
fun <R : RevisionNumber?> typeDef(
    name: String,
    typeParams: List<String> = emptyList(),
    vararg constructors: Constructor<R>,
    revision: RevisionNumber? = null,
): TypeDef<R> = TypeDef(name, typeParams, constructors.toList(), noSpan, revision as R)

/** A type definition as a rule writes it: wrapped, and unrevisioned because [TypeDefStmt] holds
 *  nothing else. */
fun typeDefStmt(
    name: String,
    typeParams: List<String> = emptyList(),
    vararg constructors: Constructor<Nothing?>,
) = TypeDefStmt(TypeDef(name, typeParams, constructors.toList(), noSpan, null))

fun <R : RevisionNumber?> constructor(
    name: String,
    vararg fields: FieldDecl<R>,
) = Constructor(name, fields.toList(), noSpan)

fun <R : RevisionNumber?> field(
    name: String,
    type: TypeExpr<R>,
) = FieldDecl(name, type, noSpan)

fun typeName(name: String) = TypeName<Nothing?>(name, noSpan, null)

fun typeName(
    name: String,
    revision: RevisionNumber?,
) = TypeName(name, noSpan, revision)

fun typeVar(name: String) = TypeVar(name, noSpan)

@Suppress("UNCHECKED_CAST")
fun <R : RevisionNumber?> appliedType(
    name: String,
    vararg args: TypeExpr<R>,
    revision: RevisionNumber? = null,
): AppliedTypeExpr<R> = AppliedTypeExpr(name, args.toList(), noSpan, revision as R)

fun <R : RevisionNumber?> functionType(
    paramType: TypeExpr<R>,
    returnType: TypeExpr<R>,
) = FunctionTypeExpr(listOf(paramType), returnType, noSpan)

fun <R : RevisionNumber?> functionType(
    paramTypes: List<TypeExpr<R>>,
    returnType: TypeExpr<R>,
) = FunctionTypeExpr(paramTypes, returnType, noSpan)

fun <R : RevisionNumber?> functionType(returnType: TypeExpr<R>) = FunctionTypeExpr(emptyList(), returnType, noSpan)

fun <R : RevisionNumber?> tupleType(vararg elements: TypeExpr<R>) = TupleTypeExpr(elements.toList(), noSpan)

fun <R : RevisionNumber?> recordType(vararg fields: Pair<String, TypeExpr<R>>) = RecordTypeExpr(fields.toList(), noSpan)

fun <R : RevisionNumber?> optionalType(inner: TypeExpr<R>) = OptionalTypeExpr(inner, noSpan)

fun parseStmt(source: String): Stmt {
    val tokens = Lexer(source).tokenize().toList()
    return Parser(tokens).parseStmt()
}

fun parseTopLevel(source: String): Stmt {
    val tokens = Lexer(source).tokenize().toList()
    return Parser(tokens).parseProgram().stmts.first()
}

fun Stmt.stripSpan(): Stmt =
    when (this) {
        is Val -> Val(name, value.stripSpans(), noSpan, typeAnnotation?.stripSpan())
        is PatternVal -> PatternVal(pattern.stripSpan(), value.stripSpans(), noSpan)
        is FunDef -> FunDef(name, params.map { it.stripSpan() }, body.stripSpans(), noSpan, returnType?.stripSpan())
        is TypeDefStmt -> TypeDefStmt(typeDef.stripSpan())
        is Expr -> stripSpans()
    }

fun CapabilityDeclaration.stripSpan(): CapabilityDeclaration =
    when (this) {
        is FunDecl -> FunDecl(name, params.map { it.stripSpan() }, returnType.stripSpan(), noSpan, revision)
        is ValDecl -> ValDecl(name, type.stripSpan(), noSpan, revision)
    }

fun ReleaseBlock.stripSpan(): ReleaseBlock =
    ReleaseBlock(number, entries.map { ReleaseEntry(it.name, it.revision, it.remove, noSpan) }, noSpan)

fun <R : RevisionNumber?> TypeDef<R>.stripSpan(): TypeDef<R> =
    TypeDef(name, typeParams, constructors.map { it.stripSpan() }, noSpan, revision)

fun <R : RevisionNumber?> Param<R>.stripSpan(): Param<R> = Param(name, typeAnnotation?.stripSpan())

fun <R : RevisionNumber?> Constructor<R>.stripSpan(): Constructor<R> = Constructor(name, fields.map { it.stripSpan() }, noSpan)

fun <R : RevisionNumber?> FieldDecl<R>.stripSpan(): FieldDecl<R> = FieldDecl(name, type.stripSpan(), noSpan)

@Suppress("UNCHECKED_CAST")
fun <R : RevisionNumber?> TypeExpr<R>.stripSpan(): TypeExpr<R> =
    when (this) {
        is TypeName -> TypeName(name, noSpan, revision)
        is TypeVar -> TypeVar(name, noSpan) as TypeExpr<R>
        is AppliedTypeExpr -> AppliedTypeExpr(name, args.map { it.stripSpan() }, noSpan, revision)
        is FunctionTypeExpr -> FunctionTypeExpr(paramTypes.map { it.stripSpan() }, returnType.stripSpan(), noSpan)
        is TupleTypeExpr -> TupleTypeExpr(elements.map { it.stripSpan() }, noSpan)
        is RecordTypeExpr -> RecordTypeExpr(fields.map { (name, type) -> name to type.stripSpan() }, noSpan)
        is OptionalTypeExpr -> OptionalTypeExpr(inner.stripSpan(), noSpan)
    }

fun parseTypeDef(source: String): TypeDef<Nothing?> {
    val tokens = Lexer(source).tokenize().toList()
    val stmt = Parser(tokens).parseStmt()
    if (stmt !is TypeDefStmt) {
        throw ParseError("Expected type definition", stmt.span)
    }
    return stmt.typeDef
}

fun assertTypeDefEquals(
    actual: TypeDef<Nothing?>,
    expected: TypeDef<Nothing?>,
) {
    assertEqualsPretty(expected, actual.stripSpan())
}

fun assertStmtEquals(
    actual: Stmt,
    expected: Stmt,
) {
    assertEqualsPretty(expected, actual.stripSpan())
}

fun parseProgram(source: String): Program {
    val tokens = Lexer(source).tokenize().toList()
    return Parser(tokens).parseProgram()
}

fun assertProgramEquals(
    actual: Program,
    expected: List<Stmt>,
) {
    assertEqualsPretty(expected, actual.stmts.map { it.stripSpan() })
}

fun parseContract(source: String): ContractExpr {
    val tokens = Lexer(source).tokenize().toList()
    return Parser(tokens).parseContract()
}

fun assertContractEquals(
    actual: ContractExpr,
    types: List<TypeDef<*>> = emptyList(),
    declarations: List<CapabilityDeclaration> = emptyList(),
    releases: List<ReleaseBlock> = emptyList(),
) {
    assertEqualsPretty(types, actual.types.map { it.stripSpan() })
    assertEqualsPretty(declarations, actual.declarations.map { it.stripSpan() })
    assertEqualsPretty(releases, actual.releases.map { it.stripSpan() })
}

private fun <T> assertEqualsPretty(
    expected: T,
    actual: T,
) {
    if (expected != actual) {
        val message =
            buildString {
                appendLine()
                appendLine("Expected: ${stripSpanNoise(expected.toString())}")
                appendLine("Actual:   ${stripSpanNoise(actual.toString())}")
            }
        throw AssertionError(message)
    }
}

private fun stripSpanNoise(s: String): String = s.replace(Regex(""", span=SourceSpan\(start=\d+, end=\d+\)"""), "")
