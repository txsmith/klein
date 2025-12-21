package klein.parser

import klein.Apply
import klein.BinaryOp
import klein.Block
import klein.BoolLiteral
import klein.DoubleLiteral
import klein.Expr
import klein.FieldAccess
import klein.FunDef
import klein.Ident
import klein.IfThenElse
import klein.ImplicitParam
import klein.IntLiteral
import klein.Lambda
import klein.Lexer
import klein.Operator
import klein.Parser
import klein.RecordLiteral
import klein.SourceSpan
import klein.Stmt
import klein.StringLiteral
import klein.UnaryOp
import klein.UnaryOperator
import klein.Val
import kotlin.test.assertEquals

private val noSpan = SourceSpan.zero

fun int(value: Long) = IntLiteral(value, noSpan)

fun int(value: Int) = IntLiteral(value.toLong(), noSpan)

fun double(value: Double) = DoubleLiteral(value, noSpan)

fun string(value: String) = StringLiteral(value, noSpan)

fun bool(value: Boolean) = BoolLiteral(value, noSpan)

fun id(name: String) = Ident(name, noSpan)

fun neg(operand: Expr) = UnaryOp(UnaryOperator.Neg, operand, noSpan)

fun not(operand: Expr) = UnaryOp(UnaryOperator.Not, operand, noSpan)

fun lambda(
    vararg params: String,
    body: Expr,
) = Lambda(params.toList(), body, noSpan)

fun call(
    callee: Expr,
    vararg args: Expr,
) = Apply(callee, args.toList(), noSpan)

fun block(
    vararg stmts: Stmt,
    expr: Expr,
) = Block(stmts.toList(), expr, noSpan)

fun block(expr: Expr) = Block(emptyList(), expr, noSpan)

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

fun implicitParam() = ImplicitParam(noSpan)

fun record(vararg fields: Pair<String, Expr>) = RecordLiteral(fields.toList(), noSpan)

fun Expr.stripSpans(): Expr =
    when (this) {
        is IntLiteral -> IntLiteral(value, noSpan)
        is DoubleLiteral -> DoubleLiteral(value, noSpan)
        is StringLiteral -> StringLiteral(value, noSpan)
        is BoolLiteral -> BoolLiteral(value, noSpan)
        is Ident -> Ident(name, noSpan)
        is UnaryOp -> UnaryOp(op, operand.stripSpans(), noSpan)
        is BinaryOp -> BinaryOp(left.stripSpans(), op, right.stripSpans(), noSpan)
        is Lambda -> Lambda(params, body.stripSpans(), noSpan)
        is Apply -> Apply(callee.stripSpans(), args.map { it.stripSpans() }, noSpan)
        is Block -> Block(stmts.map { it.stripSpan() }, expr.stripSpans(), noSpan)
        is IfThenElse -> IfThenElse(condition.stripSpans(), thenBranch.stripSpans(), elseBranch?.stripSpans(), noSpan)
        is FieldAccess -> FieldAccess(target.stripSpans(), field, noSpan)
        is ImplicitParam -> ImplicitParam(noSpan)
        is RecordLiteral -> RecordLiteral(fields.map { (name, value) -> name to value.stripSpans() }, noSpan)
    }

fun parse(source: String): Expr {
    val tokens = Lexer(source).tokenize().toList()
    return Parser(tokens).parseExpr()
}

fun assertExprEquals(
    actual: Expr,
    expected: Expr,
) {
    assertEquals(expected, actual.stripSpans())
}

fun valStmt(
    name: String,
    value: Expr,
) = Val(name, value, noSpan)

fun funDef(
    name: String,
    vararg params: String,
    body: Expr,
) = FunDef(name, params.toList(), body, noSpan)

fun parseStmt(source: String): Stmt {
    val tokens = Lexer(source).tokenize().toList()
    return Parser(tokens).parseStmt()
}

fun Stmt.stripSpan(): Stmt =
    when (this) {
        is Val -> Val(name, value.stripSpans(), noSpan)
        is FunDef -> FunDef(name, params, body.stripSpans(), noSpan)
        is Expr -> stripSpans()
    }

fun assertStmtEquals(
    actual: Stmt,
    expected: Stmt,
) {
    assertEquals(expected, actual.stripSpan())
}

fun parseProgram(source: String): List<Stmt> {
    val tokens = Lexer(source).tokenize().toList()
    return Parser(tokens).parseProgram()
}

fun assertProgramEquals(
    actual: List<Stmt>,
    expected: List<Stmt>,
) {
    assertEquals(expected, actual.map { it.stripSpan() })
}
