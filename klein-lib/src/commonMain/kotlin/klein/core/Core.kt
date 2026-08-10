package klein.core

import klein.SourceSpan

sealed interface Control

/**
 * The compiled program a host holds between [klein.Klein.lower] and [klein.Klein.execute]: opaque
 * by design, so only its shape (this sealed spine) is public — the node kinds beneath it are
 * lowering/machine internals.
 */
sealed class CoreExpr : Control {
    abstract val span: SourceSpan
}

internal data class Literal(
    val value: Constant,
    override val span: SourceSpan,
) : CoreExpr()

internal data class Var(
    val depth: Int,
    val slot: Int,
    val name: String,
    override val span: SourceSpan,
) : CoreExpr()

internal data class Lambda(
    val arity: Int,
    val body: CoreExpr,
    val name: String?,
    override val span: SourceSpan,
) : CoreExpr()

internal data class Apply(
    val callee: CoreExpr,
    val args: List<CoreExpr>,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands = listOf(callee) + args
    val arity: Int = args.size
}

internal data class PrimApp(
    val prim: PrimOp,
    val args: List<CoreExpr>,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands get() = args
}

internal enum class PrimOp {
    Add,
    Sub,
    Mul,
    Div,
    Mod,
    Neg,
    Lt,
    LtEq,
    Gt,
    GtEq,
    Eq,
    NotEq,
    Not,
}

internal data class MakeData(
    val tag: String?,
    val fieldNames: List<String>,
    val args: List<CoreExpr>,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands get() = args
}

internal data class FieldGet(
    val target: CoreExpr,
    val field: String,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands = listOf(target)
}

internal data class HostCall(
    val name: String,
    val args: List<CoreExpr>,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands get() = args
}

internal data class EnterScope(
    val stmts: List<ScopeStmt>,
    val result: CoreExpr,
    override val span: SourceSpan,
) : CoreExpr() {
    val bindingCount: Int = stmts.count { it is Bind }
}

internal sealed class ScopeStmt {
    abstract val body: CoreExpr
    abstract val span: SourceSpan
}

internal data class Bind(
    val slotIdx: Int,
    val name: String,
    override val body: CoreExpr,
    override val span: SourceSpan,
) : ScopeStmt()

internal data class Run(
    override val body: CoreExpr,
    override val span: SourceSpan,
) : ScopeStmt()

internal data class Match(
    val scrutinee: CoreExpr,
    val arms: List<Match.Arm>,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands = listOf(scrutinee)

    sealed class Arm : Control {
        abstract val guard: CoreExpr?
        abstract val body: CoreExpr
        abstract val span: SourceSpan
    }

    data class DataArm(
        val tag: String?,
        val fields: List<String>,
        override val guard: CoreExpr?,
        override val body: CoreExpr,
        override val span: SourceSpan,
    ) : Arm()

    data class LitArm(
        val lit: Constant,
        override val guard: CoreExpr?,
        override val body: CoreExpr,
        override val span: SourceSpan,
    ) : Arm()

    data class Default(
        override val guard: CoreExpr?,
        override val body: CoreExpr,
        override val span: SourceSpan,
    ) : Arm()
}

internal sealed class Constant {
    // IEEE for now until we settle on a plan for rationals
    data class CNum(
        val value: Double,
    ) : Constant()

    data class CStr(
        val value: String,
    ) : Constant()

    data class CBool(
        val value: Boolean,
    ) : Constant()

    data object CNull: Constant()
    data object CUnit: Constant()

}

internal interface HasOperands {
    val operands: List<CoreExpr>
}
