package klein

/**
 * SimpleSub-style subtyping with constraint propagation.
 *
 * When checking lhs <: rhs:
 * - If both are concrete, check structural compatibility
 * - If lhs is a TVar, add rhs to its upper bounds
 * - If rhs is a TVar, add lhs to its lower bounds
 * - For functions: contravariant in params, covariant in result
 * - For records: width subtyping (more fields = subtype)
 */
class Subtyping(
    private val onError: (TypeError) -> Unit,
) {
    // Track pairs we've already seen to handle recursive types
    private val seen = mutableSetOf<Pair<Type, Type>>()

    /**
     * Constrain lhs to be a subtype of rhs.
     * This is the core operation - it accumulates bounds on type variables.
     */
    fun constrain(lhs: Type, rhs: Type, span: SourceSpan) {
        // Skip if we've seen this pair (handles recursive types)
        val pair = lhs to rhs
        if (pair in seen) return
        seen.add(pair)

        when {
            // Same type or trivial cases
            lhs === rhs -> return
            rhs is Type.TTop -> return // Everything is subtype of Top
            lhs is Type.TBottom -> return // Bottom is subtype of everything

            // Type variable on left: add rhs as upper bound
            lhs is Type.TVar -> {
                if (rhs !is Type.TVar || lhs.id != rhs.id) {
                    lhs.upperBounds.add(rhs)
                    // Propagate: existing lower bounds must also be <: rhs
                    for (lb in lhs.lowerBounds.toList()) {
                        constrain(lb, rhs, span)
                    }
                }
            }

            // Type variable on right: add lhs as lower bound
            rhs is Type.TVar -> {
                rhs.lowerBounds.add(lhs)
                // Propagate: lhs must be <: existing upper bounds
                for (ub in rhs.upperBounds.toList()) {
                    constrain(lhs, ub, span)
                }
            }

            // Function subtyping: contravariant params, covariant result
            lhs is Type.TFun && rhs is Type.TFun -> {
                if (lhs.params.size != rhs.params.size) {
                    onError(TypeError.ArityMismatch(rhs.params.size, lhs.params.size, span))
                    return
                }
                // Contravariant: rhs params <: lhs params
                for (i in lhs.params.indices) {
                    constrain(rhs.params[i], lhs.params[i], span)
                }
                // Covariant: lhs result <: rhs result
                constrain(lhs.result, rhs.result, span)
            }

            // Record subtyping: width subtyping (more fields = subtype)
            lhs is Type.TRecord && rhs is Type.TRecord -> {
                // lhs must have all fields that rhs has
                for ((name, rhsType) in rhs.fields) {
                    val lhsType = lhs.fields[name]
                    if (lhsType == null) {
                        onError(TypeError.MissingField(name, lhs, span))
                    } else {
                        constrain(lhsType, rhsType, span)
                    }
                }
            }

            // Primitive types: must be equal
            lhs is Type.TInt && rhs is Type.TInt -> return
            lhs is Type.TDouble && rhs is Type.TDouble -> return
            lhs is Type.TString && rhs is Type.TString -> return
            lhs is Type.TBool && rhs is Type.TBool -> return
            lhs is Type.TUnit && rhs is Type.TUnit -> return

            // Otherwise: type mismatch
            else -> {
                onError(TypeError.TypeMismatch(rhs, lhs, span))
            }
        }
    }

    /**
     * Check if we can unify two types for equality contexts (like ==).
     * Both directions must work.
     */
    fun constrainEqual(lhs: Type, rhs: Type, span: SourceSpan) {
        constrain(lhs, rhs, span)
        constrain(rhs, lhs, span)
    }
}
