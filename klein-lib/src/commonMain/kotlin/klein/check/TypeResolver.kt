package klein.check

import klein.Revision
import klein.check.Type.*
import klein.surface.*

/**
 * Surface type syntax to checker types — the one thing the program checker and the contract checker
 * share, and the reason `contractMode` is gone. There is no mode to consult: an unrevisioned
 * `TypeExpr<Nothing?>` has nothing to reject, and a revision could only have come from a contract.
 *
 * Diagnostics accumulate into the [errors] list the owning checker passes in, so a resolver and its
 * checker report into one place.
 */
class TypeResolver(
    private val errors: MutableList<TypeError>,
) {
    private var skolemCounter = 0

    fun freshSkolem(name: String): TSkolem = TSkolem(name, skolemCounter++)

    fun resolve(
        typeExpr: TypeExpr<*>,
        env: TypeEnv,
    ): Type =
        when (typeExpr) {
            is TypeName -> {
                val revision = typeExpr.revision ?: Revision(1)
                primitiveType(typeExpr.name).takeIf { revision.value == 1 } ?: run {
                    val display = revisionedName(typeExpr.name, typeExpr.revision)
                    val def = env.lookupTypeDef(typeExpr.name, revision)
                    when {
                        def == null -> recordError(TypeError.UnboundVariable(display, typeExpr.span))
                        def.typeParams.isNotEmpty() -> {
                            recordError(TypeError.TypeArityMismatch(display, def.typeParams.size, 0, typeExpr.span))
                            TRef(typeExpr.name, emptyList(), revision)
                        }
                        else -> TRef(typeExpr.name, emptyList(), revision)
                    }
                }
            }
            is FunctionTypeExpr ->
                TFun(typeExpr.paramTypes.map { resolve(it, env) }, resolve(typeExpr.returnType, env))
            is RecordTypeExpr ->
                recordOf(typeExpr.fields.associate { (name, t) -> name to resolve(t, env) })
            is OptionalTypeExpr ->
                optionalOf(resolve(typeExpr.inner, env))
            is TupleTypeExpr ->
                if (typeExpr.elements.isEmpty()) {
                    TUnit
                } else {
                    TRecord(typeExpr.elements.mapIndexed { i, t -> "_${i + 1}" to resolve(t, env) }.toMap())
                }
            is TypeVar ->
                env.lookupTypeVar(typeExpr.name)
                    ?: recordError(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
            is AppliedTypeExpr -> {
                val revision = typeExpr.revision ?: Revision(1)
                val display = revisionedName(typeExpr.name, typeExpr.revision)
                val info = env.lookupTypeDef(typeExpr.name, revision)
                val args = typeExpr.args.map { resolve(it, env) }
                when {
                    info == null -> recordError(TypeError.UnboundVariable(display, typeExpr.span))
                    info.typeParams.size != args.size -> {
                        recordError(TypeError.TypeArityMismatch(display, info.typeParams.size, args.size, typeExpr.span))
                        TRef(typeExpr.name, args, revision)
                    }
                    else -> TRef(typeExpr.name, args, revision)
                }
            }
        }

    /** Introduce each not-yet-in-scope type variable in [annotations] as a fresh skolem at [sigEnv] —
     *  the binder owns where its `'T`s are quantified; [resolve] only ever *references* them. */
    fun introduceTypeVars(
        annotations: List<TypeExpr<*>>,
        sigEnv: TypeEnv,
    ) {
        annotations.forEach { annotation ->
            collectTypeVarNames(annotation).forEach { name ->
                if (sigEnv.lookupTypeVar(name) == null) sigEnv.bindTypeVar(name, freshSkolem(name))
            }
        }
    }

    fun reportDuplicateParams(params: List<Param<*>>) {
        val seen = mutableSetOf<String>()
        params.forEach { param ->
            if (!param.isDiscard && !seen.add(param.name)) recordError(TypeError.DuplicateParameter(param.name, param.span))
        }
    }

    /** Open a signature's own scope: its type variables introduced, its parameter types resolved. */
    fun openSignature(
        params: List<Param<*>>,
        returnType: TypeExpr<*>?,
        env: TypeEnv,
    ): Pair<TypeEnv, List<Type>> {
        val sigEnv = env.child(ImplicitParamContext.BlockedByNamedFunction)
        reportDuplicateParams(params)
        introduceTypeVars(params.mapNotNull { it.typeAnnotation } + listOfNotNull(returnType), sigEnv)
        val paramTypes =
            params.map { param ->
                if (param.typeAnnotation != null) {
                    resolve(param.typeAnnotation, sigEnv)
                } else {
                    recordError(TypeError.MissingParamAnnotation(param.name, param.span))
                }
            }
        return sigEnv to paramTypes
    }

    private fun primitiveType(name: String): Type? =
        when (name) {
            "Num" -> TNum
            "String" -> TStr
            "Bool" -> TBool
            "Unit" -> TUnit
            "Any" -> TTop
            "Nothing" -> TBottom
            else -> null
        }

    private fun recordError(err: TypeError): Type {
        errors.add(err)
        return TBottom
    }
}

/** `∀params. body`, or just `body` when there's nothing to quantify. */
internal fun quantify(
    params: Set<TSkolem>,
    body: Type,
): Type = if (params.isEmpty()) body else TForall(params, body)
