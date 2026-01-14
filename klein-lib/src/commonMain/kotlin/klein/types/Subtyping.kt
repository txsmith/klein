package klein.types

import klein.SourceSpan
import klein.types.SimpleType.*
import klein.types.TypeSimplifier.simplifyCanonical

class Subtyping {
    private val cache = mutableSetOf<Pair<SimpleType, SimpleType>>()
    private val errors = mutableListOf<TypeError>()

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
    ) {
        if (lhs is TVar || rhs is TVar) {
            val pair = lhs to rhs
            if (pair in cache) return
            cache.add(pair)
        }

        when {
            lhs === rhs -> return

            lhs is TVar -> {
                if (rhs.level <= lhs.level) {
                    lhs.upperBounds.add(rhs)
                    for (lb in lhs.lowerBounds.toList()) {
                        constrain(lb, rhs, span)
                    }
                } else {
                    val extruded = extrude(rhs, positive = false, targetLevel = lhs.level)
                    constrain(lhs, extruded, span)
                }
            }

            rhs is TVar -> {
                if (lhs.level <= rhs.level) {
                    rhs.lowerBounds.add(lhs)
                    for (ub in rhs.upperBounds.toList()) {
                        constrain(lhs, ub, span)
                    }
                } else {
                    val extruded = extrude(lhs, positive = true, targetLevel = rhs.level)
                    constrain(extruded, rhs, span)
                }
            }

            lhs is TFun && rhs is TFun -> {
                if (lhs.params.size != rhs.params.size) {
                    errors.add(TypeError.ArityMismatch(rhs.params.size, lhs.params.size, span))
                    return
                }
                for (i in lhs.params.indices) {
                    constrain(rhs.params[i], lhs.params[i], span)
                }
                constrain(lhs.result, rhs.result, span)
            }

            lhs is TRecord && rhs is TRecord -> {
                for ((name, rhsType) in rhs.fields) {
                    val lhsType = lhs.fields[name]
                    if (lhsType == null) {
                        errors.add(TypeError.MissingField(name, simplifyCanonical(lhs), span))
                    } else {
                        constrain(lhsType, rhsType, span)
                    }
                }
            }

            lhs is TNum && rhs is TNum -> return
            lhs is TString && rhs is TString -> return
            lhs is TBool && rhs is TBool -> return
            lhs is TUnit && rhs is TUnit -> return

            else -> {
                errors.add(TypeError.TypeMismatch(simplifyCanonical(rhs), simplifyCanonical(lhs), span))
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
                )

            is TRecord ->
                TRecord(ty.fields.mapValues { (_, v) -> extrude(v, positive, targetLevel, cache) })

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
    ) {
        constrain(lhs, rhs, span)
        constrain(rhs, lhs, span)
    }
}
