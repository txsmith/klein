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
data class CompactType(
    val vars: Set<TVar> = emptySet(),
    val prims: Set<PrimType> = emptySet(),
    val rec: Map<String, CompactType>? = null,
    val func: Pair<List<CompactType>, CompactType>? = null,
    val optional: CompactType? = null,
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

        fun fromSimpleType(ty: SimpleType): CompactTypeScheme {
            val recursive = mutableMapOf<Pair<TVar, Boolean>, TVar>()
            val recVars = mutableMapOf<TVar, CompactType>()

            fun go(
                ty: SimpleType,
                pol: Boolean,
                parents: Set<TVar>,
                inProgress: Set<Pair<TVar, Boolean>>,
            ): CompactType =
                when (ty) {
                    TNum -> CompactType.prim(PrimType.Num)
                    TString -> CompactType.prim(PrimType.String)
                    TBool -> CompactType.prim(PrimType.Bool)
                    TNull -> CompactType.prim(PrimType.Null)
                    TUnit -> CompactType.prim(PrimType.Unit)
                    is TOptional -> CompactType.optional(go(ty.inner, pol, parents, inProgress))
                    is TFun ->
                        CompactType.function(
                            ty.params.map { go(it, !pol, emptySet(), inProgress) },
                            go(ty.result, pol, emptySet(), inProgress),
                        )
                    is TRecord -> CompactType.record(ty.fields.mapValues { (_, v) -> go(v, pol, emptySet(), inProgress) })
                    is TRef -> TODO("TRef not yet supported in CompactType conversion")
                    is TVar -> {
                        val key = ty to pol

                        if (key in inProgress) {
                            if (ty in parents) {
                                CompactType.empty
                            } else {
                                val recVar = recursive.getOrPut(key) { TVar() }
                                CompactType.variable(recVar)
                            }
                        } else {
                            val bounds = if (pol) ty.lowerBounds.toList() else ty.upperBounds.toList()
                            val newInProgress = inProgress + key
                            val bound =
                                if (bounds.isEmpty()) {
                                    CompactType.variable(ty)
                                } else {
                                    val boundTypes = bounds.map { go(it, pol, parents + ty, newInProgress) }
                                    boundTypes.fold(CompactType.variable(ty)) { acc, t -> acc.merge(t, pol) }
                                }

                            val recVar = recursive[key]
                            if (recVar != null) {
                                recVars[recVar] = bound
                                CompactType.variable(recVar)
                            } else {
                                bound
                            }
                        }
                    }
                }

            val term = go(ty, pol = true, parents = emptySet(), inProgress = emptySet())
            return CompactTypeScheme(term, recVars)
        }

        /**
         * Canonicalizing version of fromSimpleType.
         *
         * Unlike fromSimpleType which tracks (TVar, pol) pairs to detect recursion,
         * this tracks (CompactType, pol) pairs. This allows merging co-occurring
         * recursive types with different cycle lengths by finding their LCD.
         *
         * The algorithm uses two phases:
         * - go0: Convert outermost layer without expanding variable bounds
         * - go1: Merge bounds of all variables and recursively process
         *
         * This is akin to NFA-to-DFA powerset construction.
         */
        fun canonicalizeType(ty: SimpleType): CompactTypeScheme {
            val recursive = mutableMapOf<Pair<CompactType, Boolean>, TVar>()
            val recVars = mutableMapOf<TVar, CompactType>()

            // Transitively close over variables reachable through bounds
            fun closeOver(
                initial: Set<TVar>,
                pol: Boolean,
            ): Set<TVar> {
                val result = initial.toMutableSet()
                val worklist = initial.toMutableList()
                while (worklist.isNotEmpty()) {
                    val tv = worklist.removeLast()
                    val bounds = if (pol) tv.lowerBounds else tv.upperBounds
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
                pol: Boolean,
            ): CompactType =
                when (ty) {
                    TNum -> CompactType.prim(PrimType.Num)
                    TString -> CompactType.prim(PrimType.String)
                    TBool -> CompactType.prim(PrimType.Bool)
                    TNull -> CompactType.prim(PrimType.Null)
                    TUnit -> CompactType.prim(PrimType.Unit)
                    is TOptional -> CompactType.optional(go0(ty.inner, pol))
                    is TFun ->
                        CompactType.function(
                            ty.params.map { go0(it, !pol) },
                            go0(ty.result, pol),
                        )
                    is TRecord -> CompactType.record(ty.fields.mapValues { (_, v) -> go0(v, pol) })
                    is TRef -> TODO("TRef not yet supported in CompactType canonicalization")
                    is TVar -> CompactType(vars = closeOver(setOf(ty), pol))
                }

            // Merge bounds of all variables in a CompactType and recursively traverse
            fun go1(
                ty: CompactType,
                pol: Boolean,
                inProgress: Set<Pair<CompactType, Boolean>>,
            ): CompactType {
                if (ty.isEmpty()) return ty

                val key = ty to pol
                if (key in inProgress) {
                    val recVar = recursive.getOrPut(key) { TVar() }
                    return CompactType.variable(recVar)
                }

                // Merge bounds of all variables (excluding variable-to-variable bounds)
                val boundMerge =
                    ty.vars
                        .flatMap { tv ->
                            val bounds = if (pol) tv.lowerBounds else tv.upperBounds
                            bounds.filter { it !is TVar }.map { go0(it, pol) }
                        }.fold(CompactType.empty) { acc, t -> acc.merge(t, pol) }

                val merged = ty.merge(boundMerge, pol)
                val newInProgress = inProgress + key

                val adapted =
                    CompactType(
                        vars = merged.vars,
                        prims = merged.prims,
                        rec = merged.rec?.mapValues { (_, v) -> go1(v, pol, newInProgress) },
                        func =
                            merged.func?.let { (params, result) ->
                                params.map { go1(it, !pol, newInProgress) } to go1(result, pol, newInProgress)
                            },
                        optional = merged.optional?.let { go1(it, pol, newInProgress) },
                    )

                val recVar = recursive[key]
                return if (recVar != null) {
                    recVars[recVar] = adapted
                    CompactType.variable(recVar)
                } else {
                    adapted
                }
            }

            val term = go1(go0(ty, pol = true), pol = true, inProgress = emptySet())
            return CompactTypeScheme(term, recVars)
        }
    }

    fun merge(
        other: CompactType,
        positive: Boolean,
    ): CompactType {
        val mergedVars = this.vars + other.vars
        val mergedPrims = this.prims + other.prims

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
        )
    }

    fun isEmpty(): Boolean = vars.isEmpty() && prims.isEmpty() && rec == null && func == null && optional == null
}

data class CompactTypeScheme(
    val term: CompactType,
    val recVars: Map<TVar, CompactType> = emptyMap(),
)
