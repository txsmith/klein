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
) {
    enum class PrimType { Num, String, Bool, Unit }

    companion object {
        val empty = CompactType()

        fun prim(p: PrimType) = CompactType(prims = setOf(p))

        fun variable(v: TVar) = CompactType(vars = setOf(v))

        fun function(
            params: List<CompactType>,
            result: CompactType,
        ) = CompactType(func = params to result)

        fun record(fields: Map<String, CompactType>) = CompactType(rec = fields)

        fun fromSimpleType(ty: SimpleType): CompactType {
            fun go(
                ty: SimpleType,
                positive: Boolean,
                parents: Set<TVar>,
                inProgress: Set<Pair<TVar, Boolean>>,
            ): CompactType =
                when (ty) {
                    TNum -> CompactType.prim(PrimType.Num)
                    TString -> CompactType.prim(PrimType.String)
                    TBool -> CompactType.prim(PrimType.Bool)
                    TUnit -> CompactType.prim(PrimType.Unit)
                    is TFun ->
                        CompactType.function(
                            ty.params.map { go(it, !positive, parents, inProgress) },
                            go(ty.result, positive, parents, inProgress),
                        )
                    is TRecord -> CompactType.record(ty.fields.mapValues { (_, v) -> go(v, positive, parents, inProgress) })
                    is TVar -> {
                        val key = ty to positive

                        if (key in inProgress) {
                            if (parents.contains(ty)) {
                                CompactType.empty
                            } else {
                                CompactType.variable(ty)
                            }
                        } else {
                            val bounds = if (positive) ty.lowerBounds.toList() else ty.upperBounds.toList()

                            if (bounds.isEmpty()) {
                                CompactType.variable(ty)
                            } else {
                                val boundTypes = bounds.map { go(it, positive, parents, inProgress + key) }
                                val merged = boundTypes.fold(CompactType.variable(ty)) { acc, t -> acc.merge(t, positive) }
                                merged
                            }
                        }
                    }
                }

            return go(ty, positive = true, parents = setOf(), inProgress = setOf())
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

        return CompactType(
            vars = mergedVars,
            prims = mergedPrims,
            rec = mergedRec,
            func = mergedFun,
        )
    }

    fun isEmpty(): Boolean = vars.isEmpty() && prims.isEmpty() && rec == null && func == null
}

data class CompactTypeScheme(
    val term: CompactType,
    val recVars: Map<TVar, CompactType> = emptyMap(),
)
