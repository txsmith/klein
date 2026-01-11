package klein.types

import klein.SourceSpan

class Subtyping {
    private val cache = mutableSetOf<Pair<SimpleType, SimpleType>>()
    private val errors = mutableListOf<TypeError>()

    fun getErrors(): List<TypeError> = errors.toList()

    /**
     * Constrain lhs to be a subtype of rhs.
     * Accumulates bounds on type variables.
     */
    fun constrain(
        lhs: SimpleType,
        rhs: SimpleType,
        span: SourceSpan,
    ) {
        val pair = lhs to rhs
        if (pair in cache) return
        cache.add(pair)

        when {
            lhs === rhs -> return
            rhs is SimpleType.TTop -> return
            lhs is SimpleType.TBottom -> return

            lhs is SimpleType.TVar && rhs is SimpleType.TVar && lhs !== rhs -> {
                lhs.upperBounds.add(rhs)
                rhs.lowerBounds.add(lhs)
                for (lb in lhs.lowerBounds.toList()) {
                    constrain(lb, rhs, span)
                }
                for (ub in rhs.upperBounds.toList()) {
                    constrain(lhs, ub, span)
                }
            }

            lhs is SimpleType.TVar -> {
                lhs.upperBounds.add(rhs)
                for (lb in lhs.lowerBounds.toList()) {
                    constrain(lb, rhs, span)
                }
            }

            rhs is SimpleType.TVar -> {
                rhs.lowerBounds.add(lhs)
                for (ub in rhs.upperBounds.toList()) {
                    constrain(lhs, ub, span)
                }
            }

            lhs is SimpleType.TFun && rhs is SimpleType.TFun -> {
                if (lhs.params.size != rhs.params.size) {
                    errors.add(TypeError.ArityMismatch(rhs.params.size, lhs.params.size, span))
                    return
                }
                for (i in lhs.params.indices) {
                    constrain(rhs.params[i], lhs.params[i], span)
                }
                constrain(lhs.result, rhs.result, span)
            }

            lhs is SimpleType.TRecord && rhs is SimpleType.TRecord -> {
                for ((name, rhsType) in rhs.fields) {
                    val lhsType = lhs.fields[name]
                    if (lhsType == null) {
                        errors.add(TypeError.MissingField(name, lhs, span))
                    } else {
                        constrain(lhsType, rhsType, span)
                    }
                }
            }

            lhs is SimpleType.TInt && rhs is SimpleType.TInt -> return
            lhs is SimpleType.TDouble && rhs is SimpleType.TDouble -> return
            lhs is SimpleType.TString && rhs is SimpleType.TString -> return
            lhs is SimpleType.TBool && rhs is SimpleType.TBool -> return
            lhs is SimpleType.TUnit && rhs is SimpleType.TUnit -> return

            else -> {
                errors.add(TypeError.TypeMismatch(rhs, lhs, span))
            }
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
