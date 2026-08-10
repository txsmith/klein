package klein.core

import klein.SourceSpan
import klein.surface.Lexer
import klein.surface.Parser
import kotlin.test.assertEquals

private val Z = SourceSpan.zero

internal fun num(value: Double) = Literal(Constant.CNum(value), Z)

internal fun str(value: String) = Literal(Constant.CStr(value), Z)

internal fun bool(value: Boolean) = Literal(Constant.CBool(value), Z)

internal fun nul() = Literal(Constant.CNull, Z)

internal fun unit() = Literal(Constant.CUnit, Z)

internal fun v(
    depth: Int,
    slot: Int,
    name: String = "v_${depth}_$slot",
) = Var(depth, slot, name, Z)

internal fun lam(
    arity: Int,
    body: CoreExpr,
    name: String? = null,
) = Lambda(arity, body, name, Z)

internal fun app(
    callee: CoreExpr,
    vararg args: CoreExpr,
) = Apply(callee, args.toList(), Z)

internal fun prim(
    op: PrimOp,
    vararg args: CoreExpr,
) = PrimApp(op, args.toList(), Z)

internal fun mk(
    tag: String?,
    vararg fields: Pair<String, CoreExpr>,
) = MakeData(tag, fields.map { it.first }, fields.map { it.second }, Z)

internal fun get(
    target: CoreExpr,
    field: String,
) = FieldGet(target, field, Z)

internal fun host(
    name: String,
    vararg args: CoreExpr,
) = HostCall(name, args.toList(), Z)

internal fun bind(
    slot: Int,
    body: CoreExpr,
    name: String = "_",
) = Bind(slot, name, body, Z)

internal fun stmt(body: CoreExpr) = Run(body, Z)

internal fun scope(
    vararg stmts: ScopeStmt,
    result: CoreExpr,
) = EnterScope(stmts.toList(), result, Z)

internal fun match(
    scrutinee: CoreExpr,
    vararg arms: Match.Arm,
) = Match(scrutinee, arms.toList(), Z)

internal fun litArm(
    lit: Constant,
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.LitArm(lit, guard, body, Z)

internal fun ctorArm(
    tag: String,
    fields: List<String>,
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.DataArm(tag, fields, guard, body, Z)

internal fun recordArm(
    fields: List<String>,
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.DataArm(null, fields, guard, body, Z)

internal fun default(
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.Default(guard, body, Z)

/** Parse Klein [source] to a surface program (lex + parse, no checking). */
internal fun parseProgram(source: String) = Parser(Lexer(source).tokenize().toList()).parseProgram()

/**
 * Golden lowering assertion: lower [source] and compare the printed IR to [expected]. Both
 * are `trimIndent().trim()`-normalized so tests can use multi-line string literals; Klein is
 * indentation-sensitive, so the source's common leading indentation is stripped (relative
 * indentation of nested blocks is preserved). Inputs are assumed well-typed — the lowerer's
 * domain is checked programs.
 */
internal fun assertLowersTo(
    source: String,
    expected: String,
) {
    val core = Lowering().lower(parseProgram(source.trimIndent().trim()))
    assertEquals(expected.trimIndent().trim(), CorePrinter.print(core))
}

