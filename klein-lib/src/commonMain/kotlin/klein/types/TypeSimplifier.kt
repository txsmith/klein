package klein.types

import klein.types.SimpleType.*

/**
 * Type simplification following the SimpleSub algorithm.
 *
 * The goal is to convert verbose inferred types with many type variables
 * into clean, readable types by:
 * 1. Eliminating polar variables (those appearing only positively or negatively)
 * 2. Replacing variables with their concrete bounds when possible
 * 3. Keeping polymorphic variables that truly represent parametric types
 *
 * Reference: https://lptk.github.io/programming/2020/03/26/demystifying-mlsub.html
 */
object TypeSimplifier {

    /**
     * Simplify a type for display.
     * This is the main entry point for type simplification.
     */
    fun simplify(type: SimpleType): SimpleType {
        val ctx = SimplificationContext()
        return ctx.simplify(type, positive = true)
    }

    private class SimplificationContext {
        // Track which variables we've seen at each polarity
        private val seenPositive = mutableSetOf<TVar>()
        private val seenNegative = mutableSetOf<TVar>()

        // Track which variables we're currently expanding to detect cycles
        private val expanding = mutableSetOf<Pair<TVar, Boolean>>()

        // Cache for already simplified types
        private val cache = mutableMapOf<Pair<TVar, Boolean>, SimpleType>()

        /**
         * First pass: collect all variable occurrences to determine polarity.
         */
        fun collectOccurrences(type: SimpleType, positive: Boolean, visited: MutableSet<Pair<TVar, Boolean>> = mutableSetOf()) {
            when (type) {
                is TVar -> {
                    val key = type to positive
                    if (key in visited) return
                    visited.add(key)

                    if (positive) seenPositive.add(type) else seenNegative.add(type)

                    // Traverse bounds
                    val bounds = if (positive) type.lowerBounds else type.upperBounds
                    for (bound in bounds) {
                        collectOccurrences(bound, positive, visited)
                    }
                }
                is TFun -> {
                    type.params.forEach { collectOccurrences(it, !positive, visited) }
                    collectOccurrences(type.result, positive, visited)
                }
                is TRecord -> {
                    type.fields.values.forEach { collectOccurrences(it, positive, visited) }
                }
                else -> {} // primitives
            }
        }

        /**
         * Check if a variable is polar (appears in only one polarity).
         */
        fun isPolar(v: TVar): Boolean {
            val pos = v in seenPositive
            val neg = v in seenNegative
            return pos xor neg
        }

        /**
         * Check if a variable appears only positively.
         */
        fun isPositiveOnly(v: TVar): Boolean = v in seenPositive && v !in seenNegative

        /**
         * Check if a variable appears only negatively.
         */
        fun isNegativeOnly(v: TVar): Boolean = v !in seenPositive && v in seenNegative

        /**
         * Simplify a type at a given polarity.
         */
        fun simplify(type: SimpleType, positive: Boolean): SimpleType {
            // First collect all occurrences
            collectOccurrences(type, positive)

            // Then simplify
            return doSimplify(type, positive)
        }

        private fun doSimplify(type: SimpleType, positive: Boolean): SimpleType = when (type) {
            TNum, TString, TBool, TUnit, TTop, TBottom -> type

            is TVar -> simplifyVar(type, positive)

            is TFun -> {
                val simplifiedParams = type.params.map { doSimplify(it, !positive) }
                val simplifiedResult = doSimplify(type.result, positive)
                TFun(simplifiedParams, simplifiedResult)
            }

            is TRecord -> {
                val simplifiedFields = type.fields.mapValues { (_, v) -> doSimplify(v, positive) }
                TRecord(simplifiedFields)
            }
        }

        private fun simplifyVar(v: TVar, positive: Boolean): SimpleType {
            val key = v to positive
            cache[key]?.let { return it }

            // Detect cycles
            if (key in expanding) {
                return v // Keep the variable for recursive types
            }
            expanding.add(key)

            try {
                // Get relevant bounds based on polarity
                val bounds = if (positive) v.lowerBounds else v.upperBounds

                // If polar variable (only appears in one polarity), try to eliminate it
                if (isPolar(v)) {
                    val result = if (positive && isPositiveOnly(v)) {
                        // Positive-only variable: expand to its lower bounds (union)
                        expandBounds(bounds.toList(), positive)
                    } else if (!positive && isNegativeOnly(v)) {
                        // Negative-only variable: expand to its upper bounds (intersection)
                        expandBounds(bounds.toList(), positive)
                    } else {
                        v // Keep if polarity doesn't match current context
                    }
                    cache[key] = result
                    return result
                }

                // Non-polar variable: check if all bounds lead to the same concrete type
                val concreteBounds = bounds.mapNotNull { extractConcreteBound(it, positive) }.toSet()
                if (concreteBounds.size == 1) {
                    val concrete = concreteBounds.single()
                    cache[key] = concrete
                    return concrete
                }

                // Keep the variable - it's truly polymorphic
                cache[key] = v
                return v
            } finally {
                expanding.remove(key)
            }
        }

        /**
         * Expand a list of bounds into a single type.
         * For positive polarity: union (prefer any concrete type)
         * For negative polarity: intersection (prefer any concrete type)
         */
        private fun expandBounds(bounds: List<SimpleType>, positive: Boolean): SimpleType {
            if (bounds.isEmpty()) {
                return if (positive) TBottom else TTop
            }

            // Simplify all bounds
            val simplified = bounds.map { doSimplify(it, positive) }

            // Prefer concrete types over variables
            val concrete = simplified.filterNot { it is TVar }
            val vars = simplified.filterIsInstance<TVar>()

            return when {
                concrete.isEmpty() && vars.isEmpty() -> if (positive) TBottom else TTop
                concrete.size == 1 -> concrete.first()
                concrete.isNotEmpty() -> pickBestConcrete(concrete, positive)
                else -> vars.first() // Fall back to first variable
            }
        }

        /**
         * Extract a concrete type from a bound by following variable chains.
         */
        private fun extractConcreteBound(bound: SimpleType, positive: Boolean): SimpleType? = when (bound) {
            is TNum, is TString, is TBool, is TUnit -> bound
            is TVar -> {
                val innerBounds = if (positive) bound.lowerBounds else bound.upperBounds
                innerBounds.firstNotNullOfOrNull { extractConcreteBound(it, positive) }
            }
            is TFun, is TRecord -> bound // These are "concrete" compound types
            TTop, TBottom -> null
        }

        /**
         * Pick the best concrete type when there are multiple options.
         */
        private fun pickBestConcrete(types: List<SimpleType>, positive: Boolean): SimpleType {
            // Prefer primitives over compound types
            val prims = types.filter { it is TNum || it is TString || it is TBool || it is TUnit }
            if (prims.isNotEmpty()) {
                return prims.first()
            }
            return types.first()
        }
    }
}
