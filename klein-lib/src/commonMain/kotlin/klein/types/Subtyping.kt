package klein.types

import klein.SourceSpan
import klein.Type
import klein.types.SimpleType.*
import klein.types.TypeSimplifier.simplifyCanonical

class Subtyping(
    private val env: TypeEnv,
) {
    private fun typeLookup(name: String): TypeDefInfo = env.getTypeDef(name)

    private fun ctorLookup(name: String): ConstructorInfo? = env.lookupConstructor(name)

    private val cache = mutableSetOf<Pair<SimpleType, SimpleType>>()
    private val errors = linkedSetOf<TypeError>()

    fun getErrors(): List<TypeError> = errors.toList()

    /**
     * Constrain lhs to be a subtype of rhs.
     * Accumulates bounds on type variables.
     *
     * Level checking: We maintain the invariant that a variable's bounds never
     * contain types with a higher level than the variable itself. When this would
     * be violated, we use extrusion to create a proxy at the appropriate level.
     */
    fun constrain(
        lhs: SimpleType,
        rhs: SimpleType,
        span: SourceSpan,
        context: List<ConstraintContext> = emptyList(),
    ) {
        if (lhs is TVar || rhs is TVar) {
            val pair = lhs to rhs
            if (pair in cache) return
            cache.add(pair)
        }

        when {
            lhs === rhs -> return

            lhs is TNum && rhs is TNum -> return
            lhs is TString && rhs is TString -> return
            lhs is TBool && rhs is TBool -> return
            lhs is TNull && rhs is TNull -> return
            lhs is TUnit && rhs is TUnit -> return

            lhs is TSkolem && rhs is TSkolem && lhs.uid == rhs.uid -> return

            // Top and Bottom: universal super/sub type
            rhs is TTop -> return
            lhs is TBottom -> return

            lhs is TVar -> {
                if (rhs.level <= lhs.level) {
                    lhs.upperBounds.add(rhs)
                    for (lb in lhs.lowerBounds.toList()) {
                        constrain(lb, rhs, span, context)
                    }
                } else {
                    val extruded = extrude(rhs, positive = false, targetLevel = lhs.level)
                    constrain(lhs, extruded, span, context)
                }
            }

            rhs is TVar -> {
                if (lhs.level <= rhs.level) {
                    rhs.lowerBounds.add(lhs)
                    for (ub in rhs.upperBounds.toList()) {
                        constrain(lhs, ub, span, context)
                    }
                } else {
                    val extruded = extrude(lhs, positive = true, targetLevel = rhs.level)
                    constrain(extruded, rhs, span, context)
                }
            }

            lhs is TFun && rhs is TFun -> {
                if (lhs.params.size != rhs.params.size) {
                    errors.add(TypeError.CallArityMismatch(lhs.params.size, rhs.params.size, span, context))
                    return
                }
                val funCall = context.lastOrNull() as? ConstraintContext.FunctionCall
                for (i in lhs.params.indices) {
                    val argContext =
                        context +
                            ConstraintContext.Argument(
                                paramIndex = i,
                                paramName = lhs.paramNames.getOrNull(i),
                                subtype = rhs.params[i].clone(),
                                supertype = lhs.params[i].clone(),
                            )
                    val argSpan = funCall?.argSpans?.getOrNull(i)
                    constrain(rhs.params[i], lhs.params[i], argSpan ?: span, argContext)
                }
                val resultContext =
                    context +
                        ConstraintContext.FunctionResult(
                            subtype = lhs.result.clone(),
                            supertype = rhs.result.clone(),
                        )
                constrain(lhs.result, rhs.result, span, resultContext)
            }

            lhs is TRecord && rhs is TRecord -> {
                for ((name, rhsType) in rhs.fields) {
                    val lhsType = lhs.fields[name]
                    if (lhsType == null) {
                        val nominalType = context.filterIsInstance<ConstraintContext.NominalToStructural>().lastOrNull()
                        val recordType =
                            if (nominalType != null) {
                                Type.Ref(nominalType.typeName, emptyList())
                            } else {
                                simplifyCanonical(lhs, env)
                            }
                        errors.add(TypeError.MissingField(name, recordType, span, context))
                    } else {
                        constrain(lhsType, rhsType, span, context)
                    }
                }
            }

            // Null <: T? (null injection into any optional)
            lhs is TNull && rhs is TOptional -> return

            // T <: T? (embedding) and T? <: U? if T <: U (covariance)
            rhs is TOptional -> {
                when (lhs) {
                    is TOptional -> constrain(lhs.inner, rhs.inner, span, context)
                    else -> constrain(lhs, rhs.inner, span, context)
                }
            }

            // Null NOT <: T for non-optional T (null safety)
            lhs is TNull -> {
                errors.add(TypeError.NullNotAllowed(simplifyCanonical(rhs, env), span, context))
            }

            // Structural subtyping
            lhs is TRef && rhs is TRecord -> {
                val typeDef = env.getTypeDef(lhs.name)
                val (structuralType, replacedVars) = typeDef.iface.instantiate(env.level)
                val freshVars = typeDef.typeParams.map { it.copy(tvar = replacedVars[it.tvar] ?: it.tvar) }

                freshVars.zip(lhs.typeArgs) { typeParam, typeArg ->
                    when (typeParam.variance) {
                        Variance.Covariant -> constrain(typeArg, typeParam.tvar, span, context)
                        Variance.Contravariant -> constrain(typeParam.tvar, typeArg, span, context)
                        Variance.Invariant, Variance.Bivariant -> constrainEqual(typeParam.tvar, typeArg, span, context)
                    }
                }

                constrain(structuralType, rhs, span, context + ConstraintContext.NominalToStructural(lhs.name))
            }

            // Nominal subtyping
            lhs is TRef && rhs is TRef -> {
                val updatedContext: List<ConstraintContext>
                val lhsTypeDef: TypeDefInfo

                if (lhs.name == rhs.name) {
                    updatedContext = context
                    lhsTypeDef = typeLookup(lhs.name)
                } else {
                    val lhsCtor = ctorLookup(lhs.name)
                    if (lhsCtor == null || lhsCtor.parentType != rhs.name) {
                        errors.add(
                            TypeError.TypeMismatch(
                                simplifyCanonical(lhs, env, pol = true),
                                simplifyCanonical(rhs, env, pol = false),
                                span,
                                context,
                            ),
                        )
                        return
                    }
                    updatedContext = context + ConstraintContext.ConstructorToParent(lhs.name, rhs.name)
                    lhsTypeDef = typeLookup(lhs.name)
                }

                val rhsTypeDef = typeLookup(rhs.name)

                val lhsApplied =
                    lhsTypeDef.typeParams
                        .map { it.name }
                        .zip(lhs.typeArgs)
                        .toMap()
                val rhsApplied = rhsTypeDef.typeParams.zip(rhs.typeArgs).associate { (p, arg) -> p.name to (p.variance to arg) }

                for ((name, lhsArg) in lhsApplied) {
                    val (variance, rhsArg) = rhsApplied.getValue(name)
                    val varianceContext =
                        updatedContext +
                            ConstraintContext.VarianceCheck(
                                typeName = rhs.name,
                                typeParams = rhsTypeDef.typeParams.map { it.name },
                                paramName = name,
                                variance = variance,
                            )
                    when (variance) {
                        Variance.Covariant -> constrain(lhsArg, rhsArg, span, varianceContext)
                        Variance.Contravariant -> constrain(rhsArg, lhsArg, span, varianceContext)
                        Variance.Invariant, Variance.Bivariant -> constrainEqual(lhsArg, rhsArg, span, varianceContext)
                    }
                }
            }

            else -> {
                errors.add(
                    TypeError.TypeMismatch(
                        simplifyCanonical(lhs, env, pol = true),
                        simplifyCanonical(rhs, env, pol = false),
                        span,
                        context,
                    ),
                )
            }
        }
    }

    /**
     * Extrude a type to a target level.
     *
     * Creates a copy of the type where all type variables above the target level
     * are replaced with fresh proxies at the target level. The proxies are connected
     * to the original variables via bounds.
     *
     * @param ty The type to extrude
     * @param positive True if in positive position (covariant), false if negative (contravariant)
     * @param targetLevel The level to extrude to
     * @param cache Cache of already-extruded polar variables to handle cycles
     */
    private fun extrude(
        ty: SimpleType,
        positive: Boolean,
        targetLevel: Int,
        cache: MutableMap<Pair<TVar, Boolean>, TVar> = mutableMapOf(),
    ): SimpleType {
        // If type is already at or below target level, no extrusion needed
        if (ty.level <= targetLevel) return ty

        return when (ty) {
            is TFun ->
                TFun(
                    ty.params.map { extrude(it, !positive, targetLevel, cache) },
                    extrude(ty.result, positive, targetLevel, cache),
                    ty.paramNames,
                )

            is TRecord ->
                TRecord(ty.fields.mapValues { (_, v) -> extrude(v, positive, targetLevel, cache) })

            is TOptional ->
                TOptional(extrude(ty.inner, positive, targetLevel, cache))

            is TVar -> {
                val key = ty to positive
                cache[key]?.let { return it }

                val proxy = TVar(targetLevel)
                cache[key] = proxy

                // Connect proxy to original via bounds
                if (positive) {
                    // In positive position: proxy is an upper bound approximation
                    // Original flows into proxy (original <: proxy)
                    ty.upperBounds.add(proxy)
                    // Copy lower bounds (freshened)
                    for (lb in ty.lowerBounds.toList()) {
                        proxy.lowerBounds.add(extrude(lb, positive, targetLevel, cache))
                    }
                } else {
                    // In negative position: proxy is a lower bound approximation
                    // Proxy flows into original (proxy <: original)
                    ty.lowerBounds.add(proxy)
                    // Copy upper bounds (freshened)
                    for (ub in ty.upperBounds.toList()) {
                        proxy.upperBounds.add(extrude(ub, positive, targetLevel, cache))
                    }
                }

                proxy
            }
            else -> ty
        }
    }

    fun constrainEqual(
        lhs: SimpleType,
        rhs: SimpleType,
        span: SourceSpan,
        context: List<ConstraintContext> = emptyList(),
    ) {
        constrain(lhs, rhs, span, context)
        constrain(rhs, lhs, span, context)
    }
}
