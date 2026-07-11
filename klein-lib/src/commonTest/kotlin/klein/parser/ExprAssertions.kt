package klein.parser

import klein.AppliedTypeExpr
import klein.Apply
import klein.Ascription
import klein.BinaryOp
import klein.Block
import klein.BoolLiteral
import klein.Constructor
import klein.DoubleLiteral
import klein.Expr
import klein.FieldAccess
import klein.FieldDecl
import klein.FunDef
import klein.FunctionTypeExpr
import klein.Ident
import klein.IfThenElse
import klein.ImplicitParam
import klein.IntLiteral
import klein.IntersectionTypeExpr
import klein.Lambda
import klein.Lexer
import klein.NullLiteral
import klein.Operator
import klein.Param
import klein.ParseError
import klein.Parser
import klein.OptionalTypeExpr
import klein.Program
import klein.RecordField
import klein.RecordLiteral
import klein.RecordTypeExpr
import klein.SafeFieldAccess
import klein.SourceSpan
import klein.Stmt
import klein.StringLiteral
import klein.TupleTypeExpr
import klein.TypeDef
import klein.TypeExpr
import klein.TypeName
import klein.TypeVar
import klein.UnaryOp
import klein.UnaryOperator
import klein.UnionTypeExpr
import klein.Val

private val noSpan = SourceSpan.zero

fun int(value: Long) = IntLiteral(value, noSpan)

fun int(value: Int) = IntLiteral(value.toLong(), noSpan)

fun double(value: Double) = DoubleLiteral(value, noSpan)

fun string(value: String) = StringLiteral(value, noSpan)

fun bool(value: Boolean) = BoolLiteral(value, noSpan)

fun nullLit() = NullLiteral(noSpan)

fun id(name: String) = Ident(name, noSpan)

fun neg(operand: Expr) = UnaryOp(UnaryOperator.Neg, operand, noSpan)

fun not(operand: Expr) = UnaryOp(UnaryOperator.Not, operand, noSpan)

fun param(name: String, type: TypeExpr? = null) = Param(name, type)

fun lambda(
    vararg params: String,
    body: Expr,
) = Lambda(params.map { Param(it) }, body, noSpan)

fun lambda(
    params: List<Param>,
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

fun implicitParam() = ImplicitParam(noSpan)

fun record(vararg fields: Pair<String, Expr>) =
    RecordLiteral(fields.map { (name, value) -> RecordField(name, value) }, noSpan)

fun annotatedRecord(vararg fields: RecordField) =
    RecordLiteral(fields.toList(), noSpan)

fun recordField(name: String, value: Expr, typeAnnotation: TypeExpr? = null) =
    RecordField(name, value, typeAnnotation)

fun Expr.stripSpans(): Expr =
    when (this) {
        is IntLiteral -> IntLiteral(value, noSpan)
        is DoubleLiteral -> DoubleLiteral(value, noSpan)
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
        is ImplicitParam -> ImplicitParam(noSpan)
        is RecordLiteral -> RecordLiteral(fields.map { RecordField(it.name, it.value.stripSpans(), it.typeAnnotation?.stripSpan()) }, noSpan)
        is Ascription -> Ascription(expr.stripSpans(), type.stripSpan(), noSpan)
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
    typeAnnotation: TypeExpr? = null,
) = Val(name, value, noSpan, typeAnnotation)

fun funDef(
    name: String,
    vararg params: String,
    body: Expr,
) = FunDef(name, params.map { Param(it) }, body, noSpan)

fun funDef(
    name: String,
    params: List<Param>,
    body: Expr,
    returnType: TypeExpr? = null,
) = FunDef(name, params, body, noSpan, returnType)

fun ascription(
    expr: Expr,
    type: TypeExpr,
) = Ascription(expr, type, noSpan)

fun typeDef(
    name: String,
    typeParams: List<String> = emptyList(),
    vararg constructors: Constructor,
) = TypeDef(name, typeParams, constructors.toList(), noSpan)

fun constructor(
    name: String,
    vararg fields: FieldDecl,
) = Constructor(name, fields.toList(), noSpan)

fun field(
    name: String,
    type: TypeExpr,
) = FieldDecl(name, type, noSpan)

fun typeName(name: String) = TypeName(name, noSpan)

fun typeVar(name: String) = TypeVar(name, noSpan)

fun appliedType(
    name: String,
    vararg args: TypeExpr,
) = AppliedTypeExpr(name, args.toList(), noSpan)

fun functionType(
    paramType: TypeExpr,
    returnType: TypeExpr,
) = FunctionTypeExpr(listOf(paramType), returnType, noSpan)

fun functionType(returnType: TypeExpr) = FunctionTypeExpr(emptyList(), returnType, noSpan)

fun tupleType(vararg elements: TypeExpr) = TupleTypeExpr(elements.toList(), noSpan)

fun recordType(vararg fields: Pair<String, TypeExpr>) = RecordTypeExpr(fields.toList(), noSpan)

fun optionalType(inner: TypeExpr) = OptionalTypeExpr(inner, noSpan)

fun unionType(left: TypeExpr, right: TypeExpr) = UnionTypeExpr(left, right, noSpan)

fun intersectionType(left: TypeExpr, right: TypeExpr) = IntersectionTypeExpr(left, right, noSpan)

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
        is FunDef -> FunDef(name, params.map { it.stripSpan() }, body.stripSpans(), noSpan, returnType?.stripSpan())
        is TypeDef -> TypeDef(name, typeParams, constructors.map { it.stripSpan() }, noSpan)
        is Expr -> stripSpans()
    }

fun Param.stripSpan(): Param = Param(name, typeAnnotation?.stripSpan())

fun Constructor.stripSpan(): Constructor = Constructor(name, fields.map { it.stripSpan() }, noSpan)

fun FieldDecl.stripSpan(): FieldDecl = FieldDecl(name, type.stripSpan(), noSpan)

fun TypeExpr.stripSpan(): TypeExpr =
    when (this) {
        is TypeName -> TypeName(name, noSpan)
        is TypeVar -> TypeVar(name, noSpan)
        is AppliedTypeExpr -> AppliedTypeExpr(name, args.map { it.stripSpan() }, noSpan)
        is FunctionTypeExpr -> FunctionTypeExpr(paramTypes.map { it.stripSpan() }, returnType.stripSpan(), noSpan)
        is TupleTypeExpr -> TupleTypeExpr(elements.map { it.stripSpan() }, noSpan)
        is RecordTypeExpr -> RecordTypeExpr(fields.map { (name, type) -> name to type.stripSpan() }, noSpan)
        is OptionalTypeExpr -> OptionalTypeExpr(inner.stripSpan(), noSpan)
        is UnionTypeExpr -> UnionTypeExpr(left.stripSpan(), right.stripSpan(), noSpan)
        is IntersectionTypeExpr -> IntersectionTypeExpr(left.stripSpan(), right.stripSpan(), noSpan)
    }

fun parseTypeDef(source: String): TypeDef {
    val tokens = Lexer(source).tokenize().toList()
    val stmt = Parser(tokens).parseStmt()
    if (stmt !is TypeDef) {
        throw ParseError("Expected type definition", stmt.span)
    }
    return stmt
}

fun assertTypeDefEquals(
    actual: TypeDef,
    expected: TypeDef,
) {
    assertEqualsPretty(expected, actual.stripSpan() as TypeDef)
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
