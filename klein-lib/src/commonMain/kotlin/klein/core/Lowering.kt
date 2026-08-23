package klein.core

import klein.SourceSpan
import klein.surface.*
import klein.surface.Lambda as SurfaceLambda
import klein.surface.Apply as SurfaceApply
import klein.surface.Match as SurfaceMatch

private const val IMPLICIT_PARAM = "."

internal fun lower(program: Program): CoreExpr = lowerScope(program.stmts, LowerEnv.empty, program.span)

internal fun lowerWithPrelude(
    program: Program,
    prelude: List<PreludeBinding>,
): CoreExpr {
    val sorted = prelude.sortedBy { it.name }
    val env = LowerEnv.empty.childScope(sorted.map { it.name }, emptyList())
    val binds =
        sorted.map { binding ->
            Bind(slotOf(binding.name, env), binding.name, lowerPreludeBinding(binding, program.span), program.span)
        }
    return EnterScope(binds, lowerScope(program.stmts, env, program.span), program.span)
}

private fun lowerPreludeBinding(
    binding: PreludeBinding,
    span: SourceSpan,
): CoreExpr =
    when (binding) {
        is PreludeBinding.Ctor -> lowerConstructor(binding.name, binding.fieldNames, span)
        is PreludeBinding.Function ->
            Lambda(
                binding.arity,
                HostCall(binding.name, List(binding.arity) { i -> Var(0, i, "_$i", span) }, span),
                binding.name,
                span,
            )
        is PreludeBinding.Value -> HostCall(binding.name, emptyList(), span)
    }

private fun lowerScope(
    stmts: List<Stmt>,
    parent: LowerEnv,
    span: SourceSpan,
): CoreExpr {
    val trailing = stmts.lastOrNull() as? Expr
    val leading = if (trailing != null) stmts.dropLast(1) else stmts

    if (leading.isEmpty() && trailing != null) return lowerExpr(trailing, parent)

    val hoistedNames = mutableListOf<String>()
    val sequentialNames = mutableListOf<String>()
    leading.forEachIndexed { i, stmt ->
        when (stmt) {
            is Val -> sequentialNames.add(stmt.name)
            is FunDef -> hoistedNames.add(stmt.name)
            is TypeDefStmt -> stmt.typeDef.constructors.forEach { hoistedNames.add(it.name) }
            is PatternVal -> sequentialNames.addAll(patternValNames(stmt, i))
            is Expr -> {}
        }
    }
    var env = parent.childScope(hoistedNames, sequentialNames)

    val hoisted = mutableListOf<ScopeStmt>()
    val ordered = mutableListOf<ScopeStmt>()
    leading.forEachIndexed { i, stmt ->
        when (stmt) {
            is Val -> {
                ordered.add(Bind(slotOf(stmt.name, env), stmt.name, lowerBinding(stmt.value, stmt.name, env), stmt.span))
                env = env.reveal(listOf(stmt.name))
            }
            is FunDef ->
                hoisted.add(Bind(slotOf(stmt.name, env), stmt.name, lowerFunDef(stmt, env), stmt.span))
            is TypeDefStmt ->
                stmt.typeDef.constructors.forEach { ctor ->
                    hoisted.add(
                        Bind(
                            slotOf(ctor.name, env),
                            ctor.name,
                            lowerConstructor(ctor.name, ctor.fields.map { it.name }, ctor.span),
                            ctor.span,
                        ),
                    )
                }
            is PatternVal -> {
                ordered.addAll(lowerPatternVal(stmt, env, i))
                env = env.reveal(patternValNames(stmt, i))
            }
            is Expr -> ordered.add(Run(lowerExpr(stmt, env), stmt.span))
        }
    }
    val result = if (trailing != null) lowerExpr(trailing, env) else Literal(Constant.CUnit, span)
    return EnterScope(hoisted + ordered, result, span)
}

private fun slotOf(
    name: String,
    env: LowerEnv,
): Int = env.declaredSlot(name) ?: throw InvariantViolation("bind '$name' not in its own scope at lowering")

private fun lowerFunDef(
    fn: FunDef,
    env: LowerEnv,
): Lambda = Lambda(fn.params.size, lowerExpr(fn.body, env.child(fn.params.map { it.name })), fn.name, fn.span)

private fun lowerBinding(
    value: Expr,
    name: String,
    env: LowerEnv,
): CoreExpr = if (value is SurfaceLambda) lowerLambda(value, name, env) else lowerExpr(value, env)

private fun lowerLambda(
    expr: SurfaceLambda,
    name: String?,
    env: LowerEnv,
): Lambda {
    val paramNames =
        if (expr.params.isEmpty() && expr.body.usesImplicitParam) {
            listOf(IMPLICIT_PARAM)
        } else {
            expr.params.map { it.name }
        }
    return Lambda(paramNames.size, lowerExpr(expr.body, env.child(paramNames)), name, expr.span)
}

private fun lowerConstructor(
    name: String,
    fieldNames: List<String>,
    span: SourceSpan,
): CoreExpr {
    if (fieldNames.isEmpty()) return MakeData(name, emptyList(), emptyList(), span)
    val body = MakeData(name, fieldNames, fieldNames.mapIndexed { i, field -> Var(0, i, field, span) }, span)
    return Lambda(fieldNames.size, body, name, span)
}

private fun patternValNames(
    pv: PatternVal,
    stmtIdx: Int,
): List<String> {
    val pat = pv.pattern as DataPattern
    val fieldBinders = pat.fields.mapNotNull { it.binder }
    return when {
        pat.binder != null -> listOf(pat.binder) + fieldBinders
        fieldBinders.size > 1 -> listOf(tempName(stmtIdx)) + fieldBinders
        else -> fieldBinders
    }
}

private fun tempName(stmtIdx: Int): String = "_rhs$stmtIdx"

private fun lowerPatternVal(
    pv: PatternVal,
    env: LowerEnv,
    stmtIdx: Int,
): List<ScopeStmt> {
    val pat = pv.pattern as DataPattern
    val projections = pat.fields.filter { it.binder != null }
    val binds = mutableListOf<ScopeStmt>()
    val receiver: CoreExpr =
        when {
            pat.binder != null -> {
                val slot = slotOf(pat.binder, env)
                binds.add(Bind(slot, pat.binder, lowerExpr(pv.value, env), pv.span))
                Var(0, slot, pat.binder, pv.span)
            }
            projections.size > 1 -> {
                val temp = tempName(stmtIdx)
                val tempSlot = slotOf(temp, env)
                binds.add(Bind(tempSlot, temp, lowerExpr(pv.value, env), pv.span))
                Var(0, tempSlot, temp, pv.span)
            }
            projections.size == 1 -> lowerExpr(pv.value, env)
            else -> return listOf(Run(lowerExpr(pv.value, env), pv.span))
        }
    for (fp in projections) {
        binds.add(Bind(slotOf(fp.binder!!, env), fp.binder, FieldGet(receiver, fp.field, fp.span), fp.span))
    }
    return binds
}

private fun lowerExpr(
    expr: Expr,
    env: LowerEnv,
): CoreExpr =
    when (expr) {
        is IntLiteral -> Literal(Constant.CNum(expr.value.toDouble()), expr.span)
        is DoubleLiteral -> Literal(Constant.CNum(expr.value), expr.span)
        is StringLiteral -> Literal(Constant.CStr(expr.value), expr.span)
        is BoolLiteral -> Literal(Constant.CBool(expr.value), expr.span)
        is NullLiteral -> Literal(Constant.CNull, expr.span)
        is Ident -> {
            val ref =
                env.resolve(expr.name)
                    ?: throw InvariantViolation("unbound name '${expr.name}' at lowering", expr.span)
            Var(ref.depth, ref.slot, ref.name ?: expr.name, expr.span)
        }
        is BinaryOp -> lowerBinaryOp(expr, env)
        is UnaryOp ->
            PrimApp(
                when (expr.op) {
                    UnaryOperator.Neg -> PrimOp.Neg
                    UnaryOperator.Not -> PrimOp.Not
                },
                listOf(lowerExpr(expr.operand, env)),
                expr.span,
            )
        is SurfaceLambda -> lowerLambda(expr, null, env)
        is RecordLiteral ->
            MakeData(null, expr.fields.map { it.name }, expr.fields.map { lowerExpr(it.value, env) }, expr.span)
        is Ascription -> lowerExpr(expr.expr, env)
        is FieldAccess -> FieldGet(lowerExpr(expr.target, env), expr.field, expr.span)
        is ImplicitParam -> {
            val ref =
                env.resolve(IMPLICIT_PARAM)
                    ?: throw InvariantViolation("implicit parameter outside an implicit lambda", expr.span)
            Var(ref.depth, ref.slot, "param", expr.span)
        }
        is SurfaceApply -> Apply(lowerExpr(expr.callee, env), expr.args.map { lowerExpr(it, env) }, expr.span)
        is Block -> lowerScope(expr.stmts, env, expr.span)
        is IfThenElse -> {
            val armEnv = env.child(emptyList())
            Match(
                lowerExpr(expr.condition, env),
                listOf(
                    Match.LitArm(Constant.CBool(true), null, lowerExpr(expr.thenBranch, armEnv), expr.span),
                    Match.Default(
                        null,
                        expr.elseBranch?.let { lowerExpr(it, armEnv) } ?: Literal(Constant.CNull, expr.span),
                        expr.span,
                    ),
                ),
                expr.span,
            )
        }
        is SurfaceMatch -> lowerMatch(expr, env)
        is SafeFieldAccess -> lowerSafeFieldAccess(expr, env)
        is SafeApply -> lowerSafeApply(expr, env)
    }

private fun lowerSafeFieldAccess(
    expr: SafeFieldAccess,
    env: LowerEnv,
): CoreExpr {
    val inlineRecv = lowerExpr(expr.target, env)
    if (inlineRecv is Var) return safeMatch(inlineRecv, expr.field, expr.span)
    val hoistEnv = env.child(listOf("_recv"))
    val recvVar = Var(0, slotOf("_recv", hoistEnv), "_recv", expr.target.span)
    val bind = Bind(recvVar.slot, "_recv", lowerExpr(expr.target, hoistEnv), expr.target.span)
    return EnterScope(listOf(bind), safeMatch(recvVar, expr.field, expr.span), expr.span)
}

private fun lowerSafeApply(
    expr: SafeApply,
    env: LowerEnv,
): CoreExpr {
    val inlineRecv = lowerExpr(expr.target, env)
    if (inlineRecv is Var) return safeCallMatch(inlineRecv, expr, env)
    val hoistEnv = env.child(listOf("_recv"))
    val recvVar = Var(0, slotOf("_recv", hoistEnv), "_recv", expr.target.span)
    val bind = Bind(recvVar.slot, "_recv", lowerExpr(expr.target, hoistEnv), expr.target.span)
    return EnterScope(listOf(bind), safeCallMatch(recvVar, expr, hoistEnv), expr.span)
}

private fun safeCallMatch(
    recv: Var,
    expr: SafeApply,
    env: LowerEnv,
): Match {
    val nullArm = Match.LitArm(Constant.CNull, null, Literal(Constant.CNull, expr.span), expr.span)
    val armEnv = env.child(emptyList())
    val deeper = Var(recv.depth + 1, recv.slot, recv.name, expr.span)
    val call = Apply(FieldGet(deeper, expr.method, expr.span), expr.args.map { lowerExpr(it, armEnv) }, expr.span)
    return Match(recv, listOf(nullArm, Match.Default(null, call, expr.span)), expr.span)
}

private fun safeMatch(
    recv: Var,
    field: String,
    span: SourceSpan,
): Match {
    val nullArm = Match.LitArm(Constant.CNull, null, Literal(Constant.CNull, span), span)
    val deeper = Var(recv.depth + 1, recv.slot, recv.name, span)
    val presentArm = Match.Default(null, FieldGet(deeper, field, span), span)
    return Match(recv, listOf(nullArm, presentArm), span)
}

private fun lowerMatch(
    expr: SurfaceMatch,
    env: LowerEnv,
): CoreExpr {
    val needsScrutSlot =
        expr.arms.any { arm ->
            val p = arm.pattern
            p is VariablePattern || (p is DataPattern && p.binder != null)
        }
    val inlineScrut = lowerExpr(expr.scrutinee, env)
    if (!needsScrutSlot || inlineScrut is Var) {
        val arms = expr.arms.map { lowerArm(it, env, inlineScrut as? Var) }
        return Match(inlineScrut, arms, expr.span)
    }
    val hoistEnv = env.child(listOf("_scrut"))
    val scrutVar = Var(0, slotOf("_scrut", hoistEnv), "_scrut", expr.scrutinee.span)
    val match = Match(scrutVar, expr.arms.map { lowerArm(it, hoistEnv, scrutVar) }, expr.span)
    val bind = Bind(scrutVar.slot, "_scrut", lowerExpr(expr.scrutinee, hoistEnv), expr.scrutinee.span)
    return EnterScope(listOf(bind), match, expr.span)
}

private fun lowerArm(
    arm: MatchArm,
    matchEnv: LowerEnv,
    scrutVar: Var?,
): Match.Arm =
    when (val p = arm.pattern) {
        is LiteralPattern -> {
            val armEnv = matchEnv.child(emptyList())
            Match.LitArm(constantOf(p.literal), lowerGuard(arm, armEnv), lowerExpr(arm.body, armEnv), arm.span)
        }
        is WildcardPattern -> {
            val armEnv = matchEnv.child(emptyList())
            Match.Default(lowerGuard(arm, armEnv), lowerExpr(arm.body, armEnv), arm.span)
        }
        is VariablePattern -> {
            val armEnv = matchEnv.childWithAliases(emptyList(), mapOf(p.name to aliasOf(scrutVar!!)))
            Match.Default(lowerGuard(arm, armEnv), lowerExpr(arm.body, armEnv), arm.span)
        }
        is DataPattern -> {
            val projections = p.fields.filter { it.binder != null }
            val aliases = if (p.binder != null) mapOf(p.binder to aliasOf(scrutVar!!)) else emptyMap()
            val armEnv = matchEnv.childWithAliases(projections.map { it.binder!! }, aliases)
            Match.DataArm(
                p.tag,
                projections.map { it.field },
                lowerGuard(arm, armEnv),
                lowerExpr(arm.body, armEnv),
                arm.span,
            )
        }
    }

private fun lowerGuard(
    arm: MatchArm,
    armEnv: LowerEnv,
): CoreExpr? = arm.guard?.let { lowerExpr(it, armEnv) }

private fun aliasOf(scrutVar: Var): LowerEnv.Resolved =
    LowerEnv.Resolved(scrutVar.depth, scrutVar.slot, scrutVar.name)

private fun constantOf(literal: Expr): Constant =
    when (literal) {
        is IntLiteral -> Constant.CNum(literal.value.toDouble())
        is DoubleLiteral -> Constant.CNum(literal.value)
        is StringLiteral -> Constant.CStr(literal.value)
        is BoolLiteral -> Constant.CBool(literal.value)
        is NullLiteral -> Constant.CNull
        else -> throw InvariantViolation("non-literal in literal pattern at lowering", literal.span)
    }

private fun lowerBinaryOp(
    expr: BinaryOp,
    env: LowerEnv,
): CoreExpr {
    val prim = binaryPrim(expr.op)
    if (prim != null) {
        return PrimApp(prim, listOf(lowerExpr(expr.left, env), lowerExpr(expr.right, env)), expr.span)
    }
    val armEnv = env.child(emptyList())
    return when (expr.op) {
        Operator.And ->
            Match(
                lowerExpr(expr.left, env),
                listOf(
                    Match.LitArm(Constant.CBool(true), null, lowerExpr(expr.right, armEnv), expr.span),
                    Match.Default(null, Literal(Constant.CBool(false), expr.span), expr.span),
                ),
                expr.span,
            )
        Operator.Or ->
            Match(
                lowerExpr(expr.left, env),
                listOf(
                    Match.LitArm(Constant.CBool(true), null, Literal(Constant.CBool(true), expr.span), expr.span),
                    Match.Default(null, lowerExpr(expr.right, armEnv), expr.span),
                ),
                expr.span,
            )
        else -> throw InvariantViolation("binaryPrim returned null for ${expr.op}")
    }
}

private fun binaryPrim(op: Operator): PrimOp? =
    when (op) {
        Operator.Add -> PrimOp.Add
        Operator.Sub -> PrimOp.Sub
        Operator.Mul -> PrimOp.Mul
        Operator.Div -> PrimOp.Div
        Operator.Mod -> PrimOp.Mod
        Operator.Lt -> PrimOp.Lt
        Operator.LtEq -> PrimOp.LtEq
        Operator.Gt -> PrimOp.Gt
        Operator.GtEq -> PrimOp.GtEq
        Operator.Eq -> PrimOp.Eq
        Operator.NotEq -> PrimOp.NotEq
        Operator.And, Operator.Or -> null
    }

internal class LowerEnv private constructor(
    private val names: List<String>,
    private val visible: Int,
    private val aliases: Map<String, Resolved>,
    private val parent: LowerEnv?,
) {
    fun child(names: List<String>): LowerEnv = LowerEnv(names, names.size, emptyMap(), this)

    fun childScope(
        hoisted: List<String>,
        sequential: List<String>,
    ): LowerEnv = LowerEnv(hoisted + sequential, hoisted.size, emptyMap(), this)

    fun childWithAliases(
        names: List<String>,
        aliases: Map<String, Resolved>,
    ): LowerEnv = LowerEnv(names, names.size, aliases, this)

    fun reveal(revealed: List<String>): LowerEnv {
        revealed.forEachIndexed { i, name ->
            if (names.indexOf(name) != visible + i) {
                throw InvariantViolation("reveal of '$name' out of layout order at lowering")
            }
        }
        return LowerEnv(names, visible + revealed.size, aliases, parent)
    }

    fun declaredSlot(name: String): Int? = names.indexOf(name).takeIf { it >= 0 }

    fun resolve(name: String): Resolved? {
        val slot = names.indexOf(name)
        if (slot in 0 until visible) return Resolved(0, slot)
        aliases[name]?.let { return Resolved(it.depth + 1, it.slot, it.name) }
        return parent?.resolve(name)?.let { Resolved(it.depth + 1, it.slot, it.name) }
    }

    data class Resolved(
        val depth: Int,
        val slot: Int,
        val name: String? = null,
    )

    companion object {
        val empty: LowerEnv = LowerEnv(emptyList(), 0, emptyMap(), null)
    }
}

/**
 * Declared here, in lowering's own vocabulary: it says nothing about revisions or contracts, so
 * the IR stays revision-free and `klein.core` stays ignorant of `klein.check.contract`.
 */
internal sealed interface PreludeBinding {
    val name: String

    data class Ctor(
        override val name: String,
        val fieldNames: List<String>,
    ) : PreludeBinding

    data class Function(
        override val name: String,
        val arity: Int,
    ) : PreludeBinding

    data class Value(
        override val name: String,
    ) : PreludeBinding
}
