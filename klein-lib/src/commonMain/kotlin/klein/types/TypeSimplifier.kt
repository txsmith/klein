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

        // Depth limit to prevent stack overflow on complex types
        private var depth = 0
        private val maxDepth = 100

        // Union-find for equivalent type variables (co-occurrence analysis)
        private val parent = mutableMapOf<TVar, TVar>()

        /**
         * Find the representative of a variable's equivalence class.
         */
        private fun find(v: TVar): TVar {
            val p = parent[v] ?: return v
            val root = find(p)
            if (p !== root) parent[v] = root // Path compression
            return root
        }

        /**
         * Union two variables into the same equivalence class.
         */
        private fun union(v1: TVar, v2: TVar) {
            val r1 = find(v1)
            val r2 = find(v2)
            if (r1 !== r2) {
                parent[r2] = r1
            }
        }

        /**
         * First pass: collect all variable occurrences to determine polarity,
         * and detect equivalent variables via co-occurrence analysis.
         */
        fun collectOccurrences(type: SimpleType, positive: Boolean, visited: MutableSet<Pair<TVar, Boolean>> = mutableSetOf()) {
            // Depth protection
            if (depth >= maxDepth) return
            depth++
            try {
                when (type) {
                    is TVar -> {
                        val key = type to positive
                        if (key in visited) return
                        visited.add(key)

                        if (positive) seenPositive.add(type) else seenNegative.add(type)

                        // Copy bounds to list before iterating to avoid iterator nesting issues
                        val bounds = (if (positive) type.lowerBounds else type.upperBounds).toList()

                        // Co-occurrence analysis: detect equivalent variables
                        // Two vars are equivalent if they mutually constrain each other (α <: β AND β <: α)
                        // This means each appears in both bounds of the other
                        for (bound in type.lowerBounds.toList()) {
                            if (bound is TVar && type in bound.lowerBounds) {
                                union(type, bound)
                            }
                        }
                        for (bound in type.upperBounds.toList()) {
                            if (bound is TVar && type in bound.upperBounds) {
                                union(type, bound)
                            }
                        }

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
            } finally {
                depth--
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
         * Propagate polarity info to representatives after co-occurrence analysis.
         * If α and β are equivalent, and α appears positively and β negatively,
         * then the representative should appear at both polarities.
         */
        private fun propagatePolarity() {
            val allVars = seenPositive + seenNegative
            for (v in allVars) {
                val rep = find(v)
                if (v in seenPositive) seenPositive.add(rep)
                if (v in seenNegative) seenNegative.add(rep)
            }
        }

        /**
         * Simplify a type at a given polarity.
         */
        fun simplify(type: SimpleType, positive: Boolean): SimpleType {
            // First collect all occurrences and detect equivalences
            collectOccurrences(type, positive)

            // Propagate polarity to representatives
            propagatePolarity()

            // Then simplify
            return doSimplify(type, positive)
        }

        private fun doSimplify(type: SimpleType, positive: Boolean): SimpleType {
            // Depth limit to prevent stack overflow
            if (depth >= maxDepth) {
                return type // Give up simplifying at extreme depths
            }
            depth++
            try {
                return when (type) {
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
            } finally {
                depth--
            }
        }

        private fun simplifyVar(v: TVar, positive: Boolean): SimpleType {
            // Use the representative variable from co-occurrence analysis
            val rep = find(v)
            val key = rep to positive
            cache[key]?.let { return it }

            // Detect cycles - return the representative for recursive types
            if (key in expanding) {
                return rep
            }
            expanding.add(key)

            try {
                // Copy bounds to a list upfront to avoid iterator nesting issues
                val bounds = if (positive) rep.lowerBounds.toList() else rep.upperBounds.toList()

                // If polar variable (only appears in one polarity), try to eliminate it
                if (isPolar(rep)) {
                    val result = if (positive && isPositiveOnly(rep)) {
                        // Positive-only variable: expand to its lower bounds (union)
                        expandBounds(bounds, positive)
                    } else if (!positive && isNegativeOnly(rep)) {
                        // Negative-only variable: expand to its upper bounds (intersection)
                        expandBounds(bounds, positive)
                    } else {
                        rep // Keep if polarity doesn't match current context
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

                // Keep the representative variable - it's truly polymorphic
                cache[key] = rep
                return rep
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
        private fun extractConcreteBound(
            bound: SimpleType,
            positive: Boolean,
            visited: MutableSet<TVar> = mutableSetOf()
        ): SimpleType? = when (bound) {
            is TNum, is TString, is TBool, is TUnit -> bound
            is TVar -> {
                if (bound in visited) {
                    null // Cycle detected, no concrete bound
                } else {
                    visited.add(bound)
                    val innerBounds = if (positive) bound.lowerBounds else bound.upperBounds
                    innerBounds.firstNotNullOfOrNull { extractConcreteBound(it, positive, visited) }
                }
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

            // Handle multiple records: compute proper intersection/union
            val records = types.filterIsInstance<TRecord>()
            if (records.size > 1) {
                return combineRecords(records, positive)
            }

            // Handle multiple functions: for now just take first (could improve later)
            return types.first()
        }

        /**
         * Combine multiple record types based on polarity.
         *
         * For positive polarity (union/output): only keep fields common to ALL records.
         * This is sound because the value could be any of the record types at runtime.
         *
         * For negative polarity (intersection/input): keep ALL fields from all records.
         * This is sound because we need to accept any of the record types.
         */
        private fun combineRecords(records: List<TRecord>, positive: Boolean): TRecord {
            if (records.isEmpty()) return TRecord(emptyMap())
            if (records.size == 1) return records.first()

            return if (positive) {
                // Union (output): intersect field sets, keeping only common fields
                val commonFields = records
                    .map { it.fields.keys }
                    .reduce { acc, keys -> acc.intersect(keys) }

                val combinedFields = commonFields.associateWith { fieldName ->
                    // For each common field, combine the field types (they form a union)
                    val fieldTypes = records.map { it.fields[fieldName]!! }
                    if (fieldTypes.toSet().size == 1) {
                        fieldTypes.first()
                    } else {
                        // Multiple different field types - recursively combine
                        pickBestConcrete(fieldTypes, positive)
                    }
                }
                TRecord(combinedFields)
            } else {
                // Intersection (input): union field sets, keeping all fields
                val allFields = mutableMapOf<String, SimpleType>()
                for (record in records) {
                    for ((name, type) in record.fields) {
                        if (name in allFields) {
                            // Field appears in multiple records - combine types
                            val existing = allFields[name]!!
                            if (existing != type) {
                                allFields[name] = pickBestConcrete(listOf(existing, type), positive)
                            }
                        } else {
                            allFields[name] = type
                        }
                    }
                }
                TRecord(allFields)
            }
        }
    }
}
