package klein.core

import klein.SourceSpan

sealed class CoreExpr {
    abstract val span: SourceSpan
}

data class Literal(
    val value: Constant,
    override val span: SourceSpan,
) : CoreExpr()

data class Var(
    val depth: Int,
    val slot: Int,
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
) : CoreExpr()

data class PrimApp(
    val prim: Prim,
    val args: List<CoreExpr>,
    override val span: SourceSpan,
) : CoreExpr()

enum class Prim {
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
) : CoreExpr()

data class FieldGet(
    val target: CoreExpr,
    val field: String,
    override val span: SourceSpan,
) : CoreExpr()

data class Suspend(
    val name: String,
    val args: List<CoreExpr>,
    override val span: SourceSpan,
) : CoreExpr()

data class Scope(
    val stmts: List<Scope.Stmt>,
    val result: CoreExpr,
    override val span: SourceSpan,
) : CoreExpr() {
    sealed class Stmt {
        abstract val span: SourceSpan
    }

    data class Bind(
        val body: CoreExpr,
        override val span: SourceSpan,
    ) : Stmt()

    data class Run(
        val body: CoreExpr,
        override val span: SourceSpan,
    ) : Stmt()
}

data class Match(
    val scrutinee: CoreExpr,
    val arms: List<Match.Arm>,
    override val span: SourceSpan,
) : CoreExpr() {
    sealed class Arm {
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

sealed class Constant

// IEEE for now until we settle on a plan for rationals
data class NumConst(
    val value: Double,
) : Constant()

data class StrConst(
    val value: String,
) : Constant()

data class BoolConst(
    val value: Boolean,
) : Constant()

data object NullConst : Constant()
