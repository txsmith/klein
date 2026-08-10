package klein.check

import klein.Revision
import klein.SourceSpan
import klein.check.Type.*
import klein.surface.*

/**
 * Surface type syntax to checker types — the one thing the program checker and the contract checker
 * share, and the reason `contractMode` is gone. There is no mode to consult: an unrevisioned
 * `TypeExpr<Nothing?>` has nothing to reject, and a revision could only have come from a contract.
 *
 * This is the seam where the parser's proof becomes the checker's. The surface and the checked tree
 * carry *different* witnesses: a contract's surface may have written a revision or not, so it is
 * taken at `TypeExpr<*>`, while its checked form is [ContractType], because resolution defaults an
 * absent revision to revision 1. So the resolver takes a [revisionOf] normalizer — the same shape
 * the parser already uses for `contractRevision` / `programRevision` — rather than assuming the two
 * witnesses coincide.
 *
 * Diagnostics accumulate into the [errors] list the owning checker passes in, so a resolver and its
 * checker report into one place.
 */
internal class TypeResolver<R : Revision?>(
    private val errors: MutableList<TypeError>,
    val revisionOf: (Revision?) -> R,
) {
    private var skolemCounter = 0

    fun freshSkolem(name: String): TSkolem = TSkolem(name, skolemCounter++)

    fun resolve(
        typeExpr: TypeExpr<*>,
        env: TypeEnv<R>,
    ): Type<R> =
        when (typeExpr) {
            is TypeName -> {
                val revision = revisionOf(typeExpr.revision)
                val primitive = primitiveType(typeExpr.name)
                val display = revisionedName(typeExpr.name, typeExpr.revision)
                val def = if (primitive == null) env.lookupTypeDef(typeExpr.name, revision) else null
                when {
                    primitive != null ->
                        rejectRevisionOnPrimitive(typeExpr.name, typeExpr.revision, typeExpr.span) ?: primitive
                    def == null -> recordError(TypeError.UnboundVariable(display, typeExpr.span))
                    def.typeParams.isNotEmpty() -> {
                        recordError(TypeError.TypeArityMismatch(display, def.typeParams.size, 0, typeExpr.span))
                        TRef(typeExpr.name, emptyList(), revision)
                    }
                    else -> TRef(typeExpr.name, emptyList(), revision)
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
                val revision = revisionOf(typeExpr.revision)
                val display = revisionedName(typeExpr.name, typeExpr.revision)
                val info = env.lookupTypeDef(typeExpr.name, revision)
                val args = typeExpr.args.map { resolve(it, env) }
                val primitiveRejection = rejectRevisionOnPrimitive(typeExpr.name, typeExpr.revision, typeExpr.span)
                when {
                    primitiveRejection != null -> primitiveRejection
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
        sigEnv: TypeEnv<R>,
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
        env: TypeEnv<R>,
    ): Pair<TypeEnv<R>, List<Type<R>>> {
        val sigEnv = env.child(ImplicitParamContext.BlockedByNamedFunction)
        reportDuplicateParams(params)
        introduceTypeVars(params.mapNotNull { it.typeAnnotation } + listOfNotNull(returnType), sigEnv)
        val paramTypes =
            params.map { param ->
                val annotation = param.typeAnnotation
                if (annotation != null) {
                    resolve(annotation, sigEnv)
                } else {
                    recordError(TypeError.MissingParamAnnotation(param.name, param.span))
                }
            }
        return sigEnv to paramTypes
    }

    /**
     * A written revision on a built-in type, at any number. Takes the *unresolved* revision, since
     * the point is to tell `Num` from `Num/1` — everywhere else they mean the same thing, and here
     * only one of them was written by a person.
     */
    private fun rejectRevisionOnPrimitive(
        name: String,
        revision: Revision?,
        span: SourceSpan,
    ): Type<R>? =
        if (revision != null && primitiveType(name) != null) {
            recordError(TypeError.RevisionOnPrimitive(name, revision, span))
        } else {
            null
        }

    private fun primitiveType(name: String): Type<R>? =
        when (name) {
            "Num" -> TNum
            "String" -> TStr
            "Bool" -> TBool
            "Unit" -> TUnit
            "Any" -> TTop
            "Nothing" -> TBottom
            else -> null
        }

    private fun recordError(err: TypeError): Type<R> {
        errors.add(err)
        return TBottom
    }
}

/** `∀params. body`, or just `body` when there's nothing to quantify. */
internal fun <R : Revision?> quantify(
    params: Set<TSkolem>,
    body: Type<R>,
): Type<R> = if (params.isEmpty()) body else TForall(params, body)
