package klein.core

import klein.SourceSpan
import klein.surface.Lexer
import klein.surface.Parser
import kotlin.test.assertEquals

private val Z = SourceSpan.zero

fun num(value: Double) = Literal(Constant.CNum(value), Z)

fun str(value: String) = Literal(Constant.CStr(value), Z)

fun bool(value: Boolean) = Literal(Constant.CBool(value), Z)

fun nul() = Literal(Constant.CNull, Z)

fun unit() = Literal(Constant.CUnit, Z)

fun v(
    depth: Int,
    slot: Int,
    name: String = "v_${depth}_$slot",
) = Var(depth, slot, name, Z)

fun lam(
    arity: Int,
    body: CoreExpr,
    name: String? = null,
) = Lambda(arity, body, name, Z)

fun app(
    callee: CoreExpr,
    vararg args: CoreExpr,
) = Apply(callee, args.toList(), Z)

fun prim(
    op: PrimOp,
    vararg args: CoreExpr,
) = PrimApp(op, args.toList(), Z)

fun mk(
    tag: String?,
    vararg fields: Pair<String, CoreExpr>,
) = MakeData(tag, fields.map { it.first }, fields.map { it.second }, Z)

fun get(
    target: CoreExpr,
    field: String,
) = FieldGet(target, field, Z)

fun host(
    name: String,
    vararg args: CoreExpr,
) = HostCall(name, args.toList(), Z)

fun bind(
    slot: Int,
    body: CoreExpr,
    name: String = "_",
) = Bind(slot, name, body, Z)

fun stmt(body: CoreExpr) = Run(body, Z)

fun scope(
    vararg stmts: ScopeStmt,
    result: CoreExpr,
) = EnterScope(stmts.toList(), result, Z)

fun match(
    scrutinee: CoreExpr,
    vararg arms: Match.Arm,
) = Match(scrutinee, arms.toList(), Z)

fun litArm(
    lit: Constant,
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.LitArm(lit, guard, body, Z)

fun ctorArm(
    tag: String,
    fields: List<String>,
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.DataArm(tag, fields, guard, body, Z)

fun recordArm(
    fields: List<String>,
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.DataArm(null, fields, guard, body, Z)

fun default(
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.Default(guard, body, Z)

/** Parse Klein [source] to a surface program (lex + parse, no checking). */
fun parseProgram(source: String) = Parser(Lexer(source).tokenize().toList()).parseProgram()

/**
 * Golden lowering assertion: lower [source] and compare the printed IR to [expected]. Both
 * are `trimIndent().trim()`-normalized so tests can use multi-line string literals; Klein is
 * indentation-sensitive, so the source's common leading indentation is stripped (relative
 * indentation of nested blocks is preserved). Inputs are assumed well-typed — the lowerer's
 * domain is checked programs.
 */
fun assertLowersTo(
    source: String,
    expected: String,
) {
    val core = Lowering().lower(parseProgram(source.trimIndent().trim()))
    assertEquals(expected.trimIndent().trim(), CorePrinter.print(core))
}

