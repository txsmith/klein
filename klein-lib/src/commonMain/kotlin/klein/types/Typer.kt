package klein.types

import klein.*
import klein.types.SimpleType.*

sealed class TypedStmt {
    data class TypedVal(
        val name: String,
        val type: SimpleType,
    ) : TypedStmt()

    data class TypedFunDef(
        val name: String,
        val type: SimpleType,
    ) : TypedStmt()

    data class TypedExpr(
        val type: SimpleType,
        val span: SourceSpan,
    ) : TypedStmt()
}

data class ProgramResult(
    val stmts: List<TypedStmt>,
    val env: TypeEnv,
    val errors: List<TypeError>,
) {
    val type: SimpleType
        get() = (stmts.lastOrNull() as? TypedStmt.TypedExpr)?.type ?: TUnit

    val hasErrors: Boolean
        get() = errors.isNotEmpty()
}

class Typer {
    private val errors = mutableListOf<TypeError>()
    private val subtyping = Subtyping()

    fun getErrors(): List<TypeError> = errors + subtyping.getErrors()

    fun infer(
        program: Program,
        env: TypeEnv = TypeEnv.empty(),
    ): ProgramResult {
        val typedStmts = mutableListOf<TypedStmt>()

        for (stmt in program.stmts) {
            when (stmt) {
                is Val -> {
                    if (env.contains(stmt.name)) {
                        errors.add(TypeError.DuplicateBinding(stmt.name, stmt.span))
                    }
                    val type = infer(stmt.value, env)
                    env.bind(stmt.name, type, isPolymorphic = true)
                    typedStmts.add(TypedStmt.TypedVal(stmt.name, type))
                }
                is FunDef -> {
                    if (env.contains(stmt.name)) {
                        errors.add(TypeError.DuplicateBinding(stmt.name, stmt.span))
                    }
                    val type = inferFunction(stmt.params, stmt.body, stmt.span, env)
                    env.bind(stmt.name, type, isPolymorphic = true)
                    typedStmts.add(TypedStmt.TypedFunDef(stmt.name, type))
                }
                is Expr -> {
                    val type = infer(stmt, env)
                    typedStmts.add(TypedStmt.TypedExpr(type, stmt.span))
                }
            }
        }

        return ProgramResult(typedStmts, env, getErrors())
    }

    fun infer(
        expr: Expr,
        env: TypeEnv,
    ): SimpleType =
        when (expr) {
            is IntLiteral -> TNum
            is DoubleLiteral -> TNum
            is StringLiteral -> TString
            is BoolLiteral -> TBool
            is Ident -> inferIdent(expr, env)
            is BinaryOp -> inferBinaryOp(expr, env)
            is UnaryOp -> inferUnaryOp(expr, env)
            is Lambda -> inferLambda(expr, env)
            is Apply -> inferApply(expr, env)
            is RecordLiteral -> inferRecordLiteral(expr, env)
            is FieldAccess -> inferFieldAccess(expr, env)
            is IfThenElse -> inferIfThenElse(expr, env)
            is ImplicitParam -> TODO("Phase 6: Functions")
            is Block -> TODO("Phase 8: Blocks")
        }

    private fun inferIdent(
        expr: Ident,
        env: TypeEnv,
    ): SimpleType =
        env.lookup(expr.name) ?: run {
            errors.add(TypeError.UnboundVariable(expr.name, expr.span))
            TVar()
        }

    private fun inferLambda(
        expr: Lambda,
        env: TypeEnv,
    ): SimpleType = inferFunction(expr.params, expr.body, expr.span, env)

    private fun inferFunction(
        params: List<String>,
        body: Expr,
        span: SourceSpan,
        env: TypeEnv,
    ): SimpleType {
        val seen = mutableSetOf<String>()
        for (name in params) {
            if (name in seen) {
                errors.add(TypeError.DuplicateParameter(name, span))
            }
            seen.add(name)
        }

        val paramTypes = params.map { TVar() }
        val childEnv = env.child()
        params.zip(paramTypes).forEach { (name, type) ->
            childEnv.bind(name, type)
        }
        val bodyType = infer(body, childEnv)
        return TFun(paramTypes, bodyType)
    }

    private fun inferApply(
        expr: Apply,
        env: TypeEnv,
    ): SimpleType {
        val calleeType = infer(expr.callee, env)
        val argTypes = expr.args.map { infer(it, env) }
        val resultType = TVar()

        subtyping.constrain(
            calleeType,
            TFun(argTypes, resultType),
            expr.span,
        )

        return resultType
    }

    private fun inferBinaryOp(
        expr: BinaryOp,
        env: TypeEnv,
    ): SimpleType {
        val leftType = infer(expr.left, env)
        val rightType = infer(expr.right, env)

        return when (expr.op) {
            Operator.Add, Operator.Sub, Operator.Mul, Operator.Div, Operator.Mod -> {
                subtyping.constrain(leftType, TNum, expr.left.span)
                subtyping.constrain(rightType, TNum, expr.right.span)
                TNum
            }
            Operator.Lt, Operator.LtEq, Operator.Gt, Operator.GtEq -> {
                subtyping.constrain(leftType, TNum, expr.left.span)
                subtyping.constrain(rightType, TNum, expr.right.span)
                TBool
            }
            Operator.Eq, Operator.NotEq -> {
                TBool
            }
            Operator.And, Operator.Or -> {
                subtyping.constrain(leftType, TBool, expr.left.span)
                subtyping.constrain(rightType, TBool, expr.right.span)
                TBool
            }
        }
    }

    private fun inferUnaryOp(
        expr: UnaryOp,
        env: TypeEnv,
    ): SimpleType {
        val operandType = infer(expr.operand, env)

        return when (expr.op) {
            UnaryOperator.Neg -> {
                subtyping.constrain(operandType, TNum, expr.operand.span)
                TNum
            }
            UnaryOperator.Not -> {
                subtyping.constrain(operandType, TBool, expr.operand.span)
                TBool
            }
        }
    }

    private fun inferRecordLiteral(
        expr: RecordLiteral,
        env: TypeEnv,
    ): SimpleType {
        val fieldTypes = mutableMapOf<String, SimpleType>()
        for ((name, value) in expr.fields) {
            if (name in fieldTypes) {
                errors.add(TypeError.DuplicateField(name, expr.span))
            }
            fieldTypes[name] = infer(value, env)
        }
        return TRecord(fieldTypes)
    }

    private fun inferFieldAccess(
        expr: FieldAccess,
        env: TypeEnv,
    ): SimpleType {
        val targetType = infer(expr.target, env)
        val fieldType = TVar()
        subtyping.constrain(targetType, TRecord(mapOf(expr.field to fieldType)), expr.span)
        return fieldType
    }

    private fun inferIfThenElse(
        expr: IfThenElse,
        env: TypeEnv,
    ): SimpleType {
        val condType = infer(expr.condition, env)
        subtyping.constrain(condType, TBool, expr.condition.span)

        val thenType = infer(expr.thenBranch, env)

        return if (expr.elseBranch != null) {
            val elseType = infer(expr.elseBranch, env)
            val resultType = TVar()
            subtyping.constrain(thenType, resultType, expr.thenBranch.span)
            subtyping.constrain(elseType, resultType, expr.elseBranch.span)
            resultType
        } else {
            TUnit
        }
    }

    companion object {
        fun infer(
            program: Program,
            env: TypeEnv = TypeEnv.empty(),
        ): ProgramResult = Typer().infer(program, env)
    }
}
