package klein

/**
 * Type inference for Klein expressions.
 *
 * Uses SimpleSub-style inference with subtyping and bounds on type variables.
 */
class TypeGen {
    private var nextVarId = 0
    private val errors = mutableListOf<TypeError>()
    private val subtyping = Subtyping { errors.add(it) }

    fun freshVar(): Type.TVar = Type.TVar(nextVarId++)

    fun getErrors(): List<TypeError> = errors.toList()

    fun hasErrors(): Boolean = errors.isNotEmpty()

    /**
     * Constrain lhs to be a subtype of rhs.
     */
    fun constrain(lhs: Type, rhs: Type, span: SourceSpan) {
        subtyping.constrain(lhs, rhs, span)
    }

    /**
     * Constrain two types to be equal (both directions).
     */
    fun constrainEqual(lhs: Type, rhs: Type, span: SourceSpan) {
        subtyping.constrainEqual(lhs, rhs, span)
    }

    /**
     * Infer the type of an expression in the given environment.
     *
     * Returns the inferred type. Errors are accumulated in the errors list.
     */
    fun infer(expr: Expr, env: TypeEnv): Type = when (expr) {
        is IntLiteral -> Type.TInt
        is DoubleLiteral -> Type.TDouble
        is StringLiteral -> Type.TString
        is BoolLiteral -> Type.TBool
        is Ident -> inferIdent(expr, env)
        is BinaryOp -> TODO("Phase 5: Operators")
        is UnaryOp -> TODO("Phase 5: Operators")
        is Lambda -> TODO("Phase 6: Functions")
        is Apply -> TODO("Phase 6: Functions")
        is Block -> TODO("Phase 8: Blocks")
        is IfThenElse -> TODO("Phase 8: Control flow")
        is FieldAccess -> TODO("Phase 7: Records")
        is ImplicitParam -> TODO("Phase 6: Functions")
        is RecordLiteral -> TODO("Phase 7: Records")
    }

    private fun inferIdent(expr: Ident, env: TypeEnv): Type {
        return env.lookup(expr.name) ?: run {
            errors.add(TypeError.UnboundVariable(expr.name, expr.span))
            freshVar() // Return fresh var to continue inference
        }
    }
}
