package klein.types

import klein.types.SimpleType.*

/**
 * Compact type representation for the simplification phase.
 *
 * Unlike SimpleType (which has mutable bounds for inference), CompactType is an
 * immutable representation that explicitly represents unions and intersections.
 *
 * In positive position (output), this represents a union of components.
 * In negative position (input), this represents an intersection of components.
 *
 * Reference: SimpleSub paper - https://lptk.github.io/programming/2020/03/26/demystifying-mlsub.html
 */
data class RefType(
    val name: String,
    val args: List<CompactType>,
    val negArgs: List<CompactType>? = null,
)

data class CompactType(
    val vars: Set<TVar> = emptySet(),
    val prims: Set<PrimType> = emptySet(),
    val rec: Map<String, CompactType>? = null,
    val func: Pair<List<CompactType>, CompactType>? = null,
    val optional: CompactType? = null,
    val refs: Set<RefType> = emptySet(),
) {
    enum class PrimType { Num, String, Bool, Null, Unit }

    companion object {
        val empty = CompactType()

        fun prim(p: PrimType) = CompactType(prims = setOf(p))

        fun variable(v: TVar) = CompactType(vars = setOf(v))

        fun function(
            params: List<CompactType>,
            result: CompactType,
        ) = CompactType(func = params to result)

        fun record(fields: Map<String, CompactType>) = CompactType(rec = fields)

        fun optional(inner: CompactType) = CompactType(optional = inner)

        fun ref(
            name: String,
            args: List<CompactType>,
        ) = CompactType(refs = setOf(RefType(name, args)))

        /**
         * Convert SimpleType to CompactType using canonicalization.
         *
         * Tracks (CompactType, pol) pairs to detect recursion, allowing merging
         * co-occurring recursive types with different cycle lengths by finding their LCD.
         *
         * The algorithm uses two phases:
         * - go0: Convert outermost layer without expanding variable bounds
         * - go1: Merge bounds of all variables and recursively process
         */
        fun canonicalizeType(
            ty: SimpleType,
            positive: Boolean = true,
            env: TypeEnv,
        ): CompactTypeScheme {
            val recursive = mutableMapOf<Pair<CompactType, Boolean>, TVar>()
            val recVars = mutableMapOf<TVar, CompactType>()

            // Transitively close over variables reachable through bounds
            fun closeOver(
                initial: TVar,
                positive: Boolean,
            ): Set<TVar> {
                val result = mutableSetOf(initial)
                val worklist = mutableListOf(initial)
                while (worklist.isNotEmpty()) {
                    val tv = worklist.removeLast()
                    val bounds = if (positive) tv.lowerBounds else tv.upperBounds
                    for (bound in bounds) {
                        if (bound is TVar && bound !in result) {
                            result.add(bound)
                            worklist.add(bound)
                        }
                    }
                }
                return result
            }

            // Convert outermost layer of SimpleType to CompactType,
            // leaving type variables untransformed (just close over them)
            fun go0(
                ty: SimpleType,
                positive: Boolean,
            ): CompactType =
                when (ty) {
                    TNum -> CompactType.prim(PrimType.Num)
                    TString -> CompactType.prim(PrimType.String)
                    TBool -> CompactType.prim(PrimType.Bool)
                    TNull -> CompactType.prim(PrimType.Null)
                    TUnit -> CompactType.prim(PrimType.Unit)
                    is TOptional -> CompactType.optional(go0(ty.inner, positive))
                    is TFun -> CompactType.function(ty.params.map { go0(it, !positive) }, go0(ty.result, positive))
                    is TRecord -> CompactType.record(ty.fields.mapValues { (_, v) -> go0(v, positive) })
                    is TRef -> {
                        val typeDef = env.getTypeDef(ty.name)
                        val hasInvariant = typeDef.typeParams.any { it.variance == Variance.Invariant }
                        val args =
                            ty.typeArgs.zip(typeDef.typeParams) { arg, paramInfo ->
                                go0(arg, if (paramInfo.variance.isPositive) positive else !positive)
                            }
                        val negArgs =
                            if (hasInvariant) {
                                ty.typeArgs.zip(typeDef.typeParams) { arg, paramInfo ->
                                    go0(arg, if (paramInfo.variance.isPositive) !positive else positive)
                                }
                            } else {
                                null
                            }
                        CompactType(refs = setOf(RefType(ty.name, args, negArgs)))
                    }
                    is TVar -> CompactType(vars = closeOver(ty, positive))
                }

            // Merge bounds of all variables in a CompactType and recursively traverse
            fun go1(
                ty: CompactType,
                positive: Boolean,
                inProgress: Set<Pair<CompactType, Boolean>>,
            ): CompactType {
                if (ty.isEmpty()) return ty

                // Short-circuit when a loop in bounds is detected
                val key = ty to positive
                if (key in inProgress) {
                    val recVar = recursive.getOrPut(key) { TVar() }
                    return CompactType.variable(recVar)
                }
                val newInProgress = inProgress + key

                // Merge bounds of all variables (excluding variable-to-variable bounds)
                val merged =
                    ty.vars
                        .flatMap { tv ->
                            val bounds = if (positive) tv.lowerBounds else tv.upperBounds
                            bounds.filter { it !is TVar }.map { go0(it, positive) }
                        }.fold(ty) { acc, t -> acc.merge(t, positive) }

                val adapted =
                    CompactType(
                        vars = merged.vars,
                        prims = merged.prims,
                        rec = merged.rec?.mapValues { (_, v) -> go1(v, positive, newInProgress) },
                        func =
                            merged.func?.let { (params, result) ->
                                params.map { go1(it, !positive, newInProgress) } to go1(result, positive, newInProgress)
                            },
                        optional = merged.optional?.let { go1(it, positive, newInProgress) },
                        refs =
                            merged.refs
                                .map { ref ->
                                    val typeDef = env.getTypeDef(ref.name)
                                    RefType(
                                        ref.name,
                                        ref.args.zip(typeDef.typeParams) { arg, paramInfo ->
                                            go1(arg, if (paramInfo.variance.isPositive) positive else !positive, newInProgress)
                                        },
                                        ref.negArgs?.zip(typeDef.typeParams) { arg, paramInfo ->
                                            go1(arg, if (paramInfo.variance.isPositive) !positive else positive, newInProgress)
                                        },
                                    )
                                }.toSet(),
                    )

                val recVar = recursive[key]
                return if (recVar != null) {
                    recVars[recVar] = adapted
                    CompactType.variable(recVar)
                } else {
                    adapted
                }
            }

            val term = go1(go0(ty, positive), positive, inProgress = emptySet())
            return CompactTypeScheme(term, recVars)
        }
    }

    fun merge(
        other: CompactType,
        positive: Boolean,
    ): CompactType {
        val mergedVars = this.vars + other.vars
        val mergedPrims = this.prims + other.prims
        val mergedRefs = this.refs + other.refs

        val mergedRec =
            when {
                this.rec == null -> other.rec
                other.rec == null -> this.rec
                positive -> {
                    val commonKeys = this.rec.keys.intersect(other.rec.keys)
                    commonKeys.associateWith { k ->
                        this.rec[k]!!.merge(other.rec[k]!!, positive)
                    }
                }
                else -> {
                    val allKeys = this.rec.keys.union(other.rec.keys)
                    allKeys.associateWith { k ->
                        val t1 = this.rec[k]
                        val t2 = other.rec[k]
                        when {
                            t1 == null -> t2!!
                            t2 == null -> t1
                            else -> t1.merge(t2, positive)
                        }
                    }
                }
            }

        val mergedFun =
            when {
                this.func == null -> other.func
                other.func == null -> this.func
                else -> {
                    val (params1, result1) = this.func
                    val (params2, result2) = other.func
                    val mergedParams =
                        params1.zip(params2).map { (p1, p2) ->
                            p1.merge(p2, !positive)
                        }
                    val mergedResult = result1.merge(result2, positive)
                    mergedParams to mergedResult
                }
            }

        val mergedOptional =
            when {
                this.optional == null -> other.optional
                other.optional == null -> this.optional
                else -> this.optional.merge(other.optional, positive)
            }

        return CompactType(
            vars = mergedVars,
            prims = mergedPrims,
            rec = mergedRec,
            func = mergedFun,
            optional = mergedOptional,
            refs = mergedRefs,
        )
    }

    fun isEmpty(): Boolean = vars.isEmpty() && prims.isEmpty() && rec == null && func == null && optional == null && refs.isEmpty()
}

data class CompactTypeScheme(
    val term: CompactType,
    val recVars: Map<TVar, CompactType> = emptyMap(),
)
