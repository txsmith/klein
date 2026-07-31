package klein.core

/**
 * Canonical, deterministic printer for the core IR — the anchor for golden lowering tests.
 * Every property a lowering test cares about is rendered explicitly: variable slots as
 * `name[depth;slot]`, lambda arity and inferred name, data tags, and prim operators. Field
 * order follows the node's own lists (never a Map), so output is stable. Blocks (`scope`,
 * `match`) render multi-line at two-space indent; every other node is inline.
 */
object CorePrinter {
    fun print(expr: CoreExpr): String = render(expr, 0)

    private fun indent(n: Int): String = "  ".repeat(n)

    private fun render(
        expr: CoreExpr,
        depth: Int,
    ): String =
        when (expr) {
            is Literal -> constant(expr.value)
            is Var -> "${expr.name}[${expr.depth};${expr.slot}]"
            is Lambda -> {
                val name = expr.name?.let { " $it" } ?: ""
                "fun$name/${expr.arity} -> ${render(expr.body, depth)}"
            }
            is Apply -> {
                val callee = render(expr.callee, depth)
                val calleeText = if (expr.callee is Lambda) "($callee)" else callee
                "$calleeText(${expr.args.joinToString(", ") { render(it, depth) }})"
            }
            is PrimApp ->
                if (expr.args.size == 2) {
                    "(${render(expr.args[0], depth)} ${primSym(expr.prim)} ${render(expr.args[1], depth)})"
                } else {
                    "(${primSym(expr.prim)}${expr.args.joinToString("") { " " + render(it, depth) }})"
                }
            is MakeData -> {
                val fields = expr.fieldNames.zip(expr.args).joinToString(", ") { (f, a) -> "$f: ${render(a, depth)}" }
                "${expr.tag ?: ""}{$fields}"
            }
            is FieldGet -> "${render(expr.target, depth)}.${expr.field}"
            is HostCall -> "host ${expr.name}(${expr.args.joinToString(", ") { render(it, depth) }})"
            is EnterScope -> renderScope(expr, depth)
            is Match -> renderMatch(expr, depth)
        }

    private fun renderScope(
        scope: EnterScope,
        depth: Int,
    ): String {
        val inner = depth + 1
        val sb = StringBuilder("scope")
        for (stmt in scope.stmts) {
            sb.append("\n").append(indent(inner))
            when (stmt) {
                is Bind -> sb.append("bind ${stmt.name}#${stmt.slotIdx} = ${render(stmt.body, inner)}")
                is Run -> sb.append("run ${render(stmt.body, inner)}")
            }
        }
        sb.append("\n").append(indent(inner)).append(render(scope.result, inner))
        return sb.toString()
    }

    private fun renderMatch(
        match: Match,
        depth: Int,
    ): String {
        val inner = depth + 1
        val sb = StringBuilder("match ${render(match.scrutinee, depth)}")
        for (arm in match.arms) {
            sb.append("\n").append(indent(inner)).append(renderArm(arm, inner))
        }
        return sb.toString()
    }

    private fun renderArm(
        arm: Match.Arm,
        depth: Int,
    ): String {
        val pattern =
            when (arm) {
                is Match.DataArm -> "${arm.tag ?: ""}{${arm.fields.joinToString(", ")}}"
                is Match.LitArm -> "lit ${constant(arm.lit)}"
                is Match.Default -> "_"
            }
        val guard = arm.guard?.let { " if ${render(it, depth)}" } ?: ""
        return "$pattern$guard -> ${render(arm.body, depth)}"
    }

    private fun constant(c: Constant): String =
        when (c) {
            is Constant.CNum -> {
                val v = c.value
                if (v.isFinite() && v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
            }
            is Constant.CStr -> "\"${c.value}\""
            is Constant.CBool -> c.value.toString()
            Constant.CNull -> "null"
            Constant.CUnit -> "unit"
        }

    private fun primSym(p: PrimOp): String =
        when (p) {
            PrimOp.Add -> "+"
            PrimOp.Sub -> "-"
            PrimOp.Mul -> "*"
            PrimOp.Div -> "/"
            PrimOp.Mod -> "%"
            PrimOp.Neg -> "neg"
            PrimOp.Lt -> "<"
            PrimOp.LtEq -> "<="
            PrimOp.Gt -> ">"
            PrimOp.GtEq -> ">="
            PrimOp.Eq -> "=="
            PrimOp.NotEq -> "!="
            PrimOp.Not -> "not"
        }
}
