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

    fun constrain(
        lhs: SimpleType,
        rhs: SimpleType,
        span: SourceSpan,
        context: List<ConstraintContext> = emptyList(),
    ) {
        errors.addAll(constrainReturningErrs(lhs, rhs, span, context))
    }
    fun constrainEqual(
        lhs: SimpleType,
        rhs: SimpleType,
        span: SourceSpan,
        context: List<ConstraintContext> = emptyList(),
    ) {
        errors.addAll(constrainReturningErrs(lhs, rhs, span, context))
        errors.addAll(constrainReturningErrs(rhs, lhs, span, context))
    }

    /**
     * Constrain lhs to be a subtype of rhs.
     * Accumulates bounds on type variables.
     *
     * Level checking: We maintain the invariant that a variable's bounds never
     * contain types with a higher level than the variable itself. When this would
     * be violated, we use extrusion to create a proxy at the appropriate level.
     */
    private fun constrainReturningErrs(
        lhs: SimpleType,
        rhs: SimpleType,
        span: SourceSpan,
        context: List<ConstraintContext> = emptyList(),
    ): List<TypeError> {
        val lhsFlexible = lhs is TVar && !lhs.isRigid
        val rhsFlexible = rhs is TVar && !rhs.isRigid
        if (lhsFlexible || rhsFlexible) {
            val pair = lhs to rhs
            if (pair in cache) return emptyList()
            cache.add(pair)
        }

        when {
            lhs === rhs -> return emptyList()

            lhs is TNum && rhs is TNum -> return emptyList()
            lhs is TString && rhs is TString -> return emptyList()
            lhs is TBool && rhs is TBool -> return emptyList()
            lhs is TNull && rhs is TNull -> return emptyList()
            lhs is TUnit && rhs is TUnit -> return emptyList()

            // Rigid-vs-rigid identity: same user-declared skolem.
            lhs is TVar && lhs.isRigid && rhs is TVar && rhs.isRigid && lhs.uid == rhs.uid -> return emptyList()

            // Top and Bottom: universal super/sub type (covers rigid vs Top/Bottom too).
            rhs is TTop -> return emptyList()
            lhs is TBottom -> return emptyList()

            // Flexible TVars on one side is and rigid and the other, the flexible side records the rigid as a bound without mutating the rigid.
            lhs is TVar && !lhs.isRigid -> {
                if (rhs.level <= lhs.level) {
                    lhs.upperBounds.add(rhs)
                    return lhs.lowerBounds.flatMap { constrainReturningErrs(it, rhs, span, context) }
                } else {
                    val extruded = extrude(rhs, positive = false, targetLevel = lhs.level)
                    return constrainReturningErrs(lhs, extruded, span, context)
                }
            }

            rhs is TVar && !rhs.isRigid -> {
                if (lhs.level <= rhs.level) {
                    rhs.lowerBounds.add(lhs)
                    return rhs.upperBounds.flatMap { constrainReturningErrs(lhs, it, span, context) }
                } else {
                    val extruded = extrude(lhs, positive = true, targetLevel = rhs.level)
                    return constrainReturningErrs(extruded, rhs, span, context)
                }
            }

            lhs is TVar && lhs.isRigid && lhs.lowerBounds.isNotEmpty() -> {
                val failures = lhs.lowerBounds.flatMap { constrainReturningErrs(it, rhs, span, context) }
                return collapseFailures(failures, lhs, rhs, span, context)
            }
            rhs is TVar && rhs.isRigid && rhs.upperBounds.isNotEmpty() -> {
                val failures = rhs.upperBounds.flatMap { constrainReturningErrs(lhs, it, span, context) }
                return collapseFailures(failures, lhs, rhs, span, context)
            }
            // Intersection consumed: the value offers every member's capabilities, so SOME member
            // must satisfy the demand (OR-trial). A multi-field record demand is decomposed first
            // (∀ field), so each field can be supplied by a different member.
            lhs is TVar && lhs.isRigid && lhs.upperBounds.isNotEmpty() -> {
                if (rhs is TRecord && rhs.fields.size > 1) {
                    return rhs.fields.flatMap { (name, fieldType) ->
                        constrainReturningErrs(lhs, TRecord(mapOf(name to fieldType)), span, context)
                    }
                }
                val failures = mutableListOf<TypeError>()
                for (ub in lhs.upperBounds) {
                    val branchErrors = constrainReturningErrs(ub, rhs, span, context)
                    if (branchErrors.isEmpty()) return emptyList()
                    failures.addAll(branchErrors)
                }
                return collapseFailures(failures, lhs, rhs, span, context)
            }

            // Bare rigid skolem against a record demand: an opaque skolem exposes no fields, so
            // every demanded field is missing. (Skolems with bounds are handled by the branches
            // above; only bound-less skolems reach here.)
            lhs is TVar && lhs.isRigid && rhs is TRecord -> {
                return rhs.fields.keys.map { name ->
                    TypeError.MissingField(name, simplifyCanonical(lhs, env), span, context)
                }
            }

            // Union demanded: the value must match SOME member as a whole (OR-trial, no field split —
            // union members are whole-value alternatives, not capability fragments).
            rhs is TVar && rhs.isRigid && rhs.lowerBounds.isNotEmpty() -> {
                val failures = mutableListOf<TypeError>()
                for (lb in rhs.lowerBounds) {
                    val branchErrors = constrainReturningErrs(lhs, lb, span, context)
                    if (branchErrors.isEmpty()) return emptyList()
                    failures.addAll(branchErrors)
                }
                return collapseFailures(failures, lhs, rhs, span, context)
            }

            // TODO:the below is wong:
            // // Rigid vs non-flexible (the flexible cases fell through above). Rigid TVars
            // // never accumulate bounds - the constraint must be discharged via the fixed
            // // upper/lower bounds, or it's a type error.
            // lhs is TVar && lhs.isRigid -> {
            //     val errors = mutableListOf<TypeError>()
            //     if (lhs.upperBounds.isEmpty()) {
            //         errors.add(
            //             TypeError.TypeMismatch(
            //                 simplifyCanonical(lhs, env, pol = true),
            //                 simplifyCanonical(rhs, env, pol = false),
            //                 span,
            //                 context,
            //             ),
            //         )
            //     } else {
            //         for (ub in lhs.upperBounds.toList()) errors.addAll(constrainReturningErrs(ub, rhs, span, context))
            //     }
            //     return errors
            // }
            //
            // rhs is TVar && rhs.isRigid -> {
            //     val errors = mutableListOf<TypeError>()
            //     if (rhs.lowerBounds.isEmpty()) {
            //         errors.add(
            //             TypeError.TypeMismatch(
            //                 simplifyCanonical(lhs, env, pol = true),
            //                 simplifyCanonical(rhs, env, pol = false),
            //                 span,
            //                 context,
            //             ),
            //         )
            //     } else {
            //         for (lb in rhs.lowerBounds.toList()) errors.addAll(constrainReturningErrs(lhs, lb, span, context))
            //     }
            //     return errors
            // }
            //
            lhs is TFun && rhs is TFun -> {
                if (lhs.params.size != rhs.params.size) {
                    return listOf(TypeError.CallArityMismatch(lhs.params.size, rhs.params.size, span, context))
                }
                val funCall = context.lastOrNull() as? ConstraintContext.FunctionCall
                val errors = mutableListOf<TypeError>()
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
                    errors.addAll(constrainReturningErrs(rhs.params[i], lhs.params[i], argSpan ?: span, argContext))
                }
                val resultContext =
                    context +
                        ConstraintContext.FunctionResult(
                            subtype = lhs.result.clone(),
                            supertype = rhs.result.clone(),
                        )
                errors.addAll(constrainReturningErrs(lhs.result, rhs.result, span, resultContext))
                return errors
            }

            lhs is TRecord && rhs is TRecord -> {
                var errors = mutableListOf<TypeError>()
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
                        errors.addAll(constrainReturningErrs(lhsType, rhsType, span, context))
                    }
                }
                return errors
            }

            // Null <: T? (null injection into any optional)
            lhs is TNull && rhs is TOptional -> return emptyList()

            // T <: T? (embedding) and T? <: U? if T <: U (covariance)
            rhs is TOptional -> {
                when (lhs) {
                    is TOptional -> return constrainReturningErrs(lhs.inner, rhs.inner, span, context)
                    else -> return constrainReturningErrs(lhs, rhs.inner, span, context)
                }
            }

            // Null NOT <: T for non-optional T (null safety)
            lhs is TNull -> {
                return listOf(TypeError.NullNotAllowed(simplifyCanonical(rhs, env), span, context))
            }

            // Structural subtyping
            lhs is TRef && rhs is TRecord -> {
                val typeDef = env.getTypeDef(lhs.name)
                val (structuralType, replacedVars) = typeDef.iface.instantiate(env.level)
                val freshVars = typeDef.typeParams.map { it.copy(tvar = replacedVars[it.tvar] ?: it.tvar) }
                val errors = mutableListOf<TypeError>()

                freshVars.zip(lhs.typeArgs) { typeParam, typeArg ->
                    when (typeParam.variance) {
                        Variance.Covariant -> errors.addAll(constrainReturningErrs(typeArg, typeParam.tvar, span, context))
                        Variance.Contravariant -> errors.addAll(constrainReturningErrs(typeParam.tvar, typeArg, span, context))
                        Variance.Invariant, Variance.Bivariant -> errors.addAll(constrainEqualReturningErrs(typeParam.tvar, typeArg, span, context))
                    }
                }

                errors.addAll(constrainReturningErrs(structuralType, rhs, span, context + ConstraintContext.NominalToStructural(lhs.name)))
                return errors
            }

            // Nominal subtyping
            lhs is TRef && rhs is TRef -> {
                val updatedContext: List<ConstraintContext>
                val lhsTypeDef: TypeDefInfo
                val errors = mutableListOf<TypeError>()

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
                        Variance.Covariant -> errors.addAll(constrainReturningErrs(lhsArg, rhsArg, span, varianceContext))
                        Variance.Contravariant -> errors.addAll(constrainReturningErrs(rhsArg, lhsArg, span, varianceContext))
                        Variance.Invariant, Variance.Bivariant -> errors.addAll(constrainEqualReturningErrs(lhsArg, rhsArg, span, varianceContext))
                    }
                }
                return errors
            }

            else -> {
                return listOf(
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
     * Collapse the errors from iterating a rigid var's bounds into a concise diagnosis. A
     * union/intersection failure otherwise reports the full cartesian product of member
     * mismatches (e.g. `Num|String <: Bool|Char` → 4 errors); fold those into a single
     * [TypeError.TypeMismatch] of the whole types. Missing fields are kept (deduplicated by field)
     * since a record demand against an intersection legitimately reports each absent field.
     */
    private fun collapseFailures(
        failures: List<TypeError>,
        lhs: SimpleType,
        rhs: SimpleType,
        span: SourceSpan,
        context: List<ConstraintContext>,
    ): List<TypeError> {
        if (failures.isEmpty()) return emptyList()
        val missing = failures.filterIsInstance<TypeError.MissingField>().distinctBy { it.field }
        return missing.ifEmpty {
            listOf(
                TypeError.TypeMismatch(
                    renderForError(lhs, defaultPol = true),
                    renderForError(rhs, defaultPol = false),
                    span,
                    context,
                ),
            )
        }
    }

    /**
     * Render a type for an error message. A rigid union/intersection skolem only has meaning at a
     * single polarity — a union (lower bounds) at positive, an intersection (upper bounds) at
     * negative — so it is always rendered there, regardless of which side of the constraint it sat
     * on. Other types use the side's [defaultPol] (subtype positive, supertype negative).
     */
    private fun renderForError(
        ty: SimpleType,
        defaultPol: Boolean,
    ): Type {
        val pol =
            when {
                ty is TVar && ty.isRigid && ty.lowerBounds.isNotEmpty() -> true
                ty is TVar && ty.isRigid && ty.upperBounds.isNotEmpty() -> false
                else -> defaultPol
            }
        return simplifyCanonical(ty, env, pol)
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

    fun constrainEqualReturningErrs(
        lhs: SimpleType,
        rhs: SimpleType,
        span: SourceSpan,
        context: List<ConstraintContext> = emptyList(),
    ): List<TypeError> = constrainReturningErrs(lhs, rhs, span, context) + constrainReturningErrs(rhs, lhs, span, context)
}
