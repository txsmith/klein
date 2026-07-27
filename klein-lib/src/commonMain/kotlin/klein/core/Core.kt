package klein.core

import klein.SourceSpan

sealed interface Control

sealed class CoreExpr : Control {
    abstract val span: SourceSpan
}

data class Literal(
    val value: Constant,
    override val span: SourceSpan,
) : CoreExpr()

data class Var(
    val depth: Int,
    val slot: Int,
    val name: String,
    override val span: SourceSpan,
) : CoreExpr()

data class Lambda(
    val arity: Int,
    val body: CoreExpr,
    val name: String?,
    override val span: SourceSpan,
) : CoreExpr()

data class Apply(
    val callee: CoreExpr,
    val args: List<CoreExpr>,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands = listOf(callee) + args
    val arity: Int = args.size
}

data class PrimApp(
    val prim: PrimOp,
    val args: List<CoreExpr>,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands get() = args
}

enum class PrimOp {
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

data class MakeData(
    val tag: String?,
    val fieldNames: List<String>,
    val args: List<CoreExpr>,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands get() = args
}

data class FieldGet(
    val target: CoreExpr,
    val field: String,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands = listOf(target)
}

data class HostCall(
    val name: String,
    val args: List<CoreExpr>,
    override val span: SourceSpan,
) : CoreExpr(), HasOperands {
    override val operands get() = args
}

data class EnterScope(
    val stmts: List<EnterScope.Stmt>,
    val result: CoreExpr,
    override val span: SourceSpan,
) : CoreExpr() {
    val bindingCount: Int = stmts.count { it is Bind }

    sealed class Stmt {
        abstract val body: CoreExpr
        abstract val span: SourceSpan
    }

    data class Bind(
        val slotIdx: Int,
        override val body: CoreExpr,
        override val span: SourceSpan,
    ) : Stmt()

    data class Run(
        override val body: CoreExpr,
        override val span: SourceSpan,
    ) : Stmt()
}

data class Match(
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

    data class ConstructorArm(
        val tag: String,
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

sealed class Constant {
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

interface HasOperands {
    val operands: List<CoreExpr>
}
