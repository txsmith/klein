package klein.types

import klein.types.SimpleType.*

// Compact type representation for the simplification phase.
//
// Unlike SimpleType (which has mutable bounds for inference), CompactType is an
// immutable representation that explicitly represents unions and intersections.
//
// In positive position (output), this represents a union of components.
// In negative position (input), this represents an intersection of components.
//
// Reference: SimpleSub paper - https://lptk.github.io/programming/2020/03/26/demystifying-mlsub.html

/**
 * Represents a type with explicit lower and upper bounds.
 * Used for invariant type positions where we need to track both.
 *
 * - lower: covariant components (what values can flow out / be produced)
 * - upper: contravariant components (what values can flow in / be consumed)
 */
data class BoundedCompactType(
    val lower: CompactType = CompactType.empty,
    val upper: CompactType = CompactType.empty,
)

data class RefType(
    val name: String,
    val args: List<BoundedCompactType>,
)

data class CompactType(
    val vars: Set<TVar> = emptySet(),
    val prims: Set<PrimType> = emptySet(),
    val rec: Map<String, BoundedCompactType>? = null,
    val func: Pair<List<BoundedCompactType>, BoundedCompactType>? = null,
    val optional: BoundedCompactType? = null,
    val refs: Set<RefType> = emptySet(),
) {
    enum class PrimType { Num, String, Bool, Null, Unit }

    companion object {
        val empty = CompactType()

        fun prim(p: PrimType) = CompactType(prims = setOf(p))

        fun variable(v: TVar) = CompactType(vars = setOf(v))

        fun function(
            params: List<BoundedCompactType>,
            result: BoundedCompactType,
        ) = CompactType(func = params to result)

        fun record(fields: Map<String, BoundedCompactType>) = CompactType(rec = fields)

        fun optional(inner: BoundedCompactType) = CompactType(optional = inner)

        fun ref(
            name: String,
            args: List<BoundedCompactType>,
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
            pol: Variance = Variance.Covariant,
            env: TypeEnv,
            debug: Boolean = true,
        ): CompactTypeScheme {
            val recursive = mutableMapOf<Pair<CompactType, Variance>, TVar>()
            val recVars = mutableMapOf<TVar, CompactType>()
            var indent = 0

            fun pad() = "  ".repeat(indent)

            fun trace(msg: () -> String) {
                if (debug) println(pad() + msg())
            }

            fun <T> traced(
                msg: () -> String,
                block: () -> T,
            ): T {
                trace(msg)
                indent++
                return try {
                    block()
                } finally {
                    indent--
                }
            }

            fun formatSimpleType(t: SimpleType): String =
                when (t) {
                    is TNum -> "TNum"
                    is TString -> "TString"
                    is TBool -> "TBool"
                    is TNull -> "TNull"
                    is TUnit -> "TUnit"
                    is TVar -> "$t"
                    is TOptional -> "TOptional(${formatSimpleType(t.inner)})"
                    is TFun -> "TFun([${t.params.joinToString(", ") { formatSimpleType(it) }}], ${formatSimpleType(t.result)})"
                    is TRecord -> "TRecord(${t.fields.map { (k, v) -> "$k: ${formatSimpleType(v)}" }})"
                    is TRef -> "TRef(\"${t.name}\", [${t.typeArgs.joinToString(", ") { formatSimpleType(it) }}])"
                }

            fun formatCompactType(t: CompactType): String {
                fun formatBounded(b: BoundedCompactType): String =
                    "Bounded(lower=${formatCompactType(b.lower)}, upper=${formatCompactType(b.upper)})"

                val parts = mutableListOf<String>()
                if (t.vars.isNotEmpty()) parts.add("vars=[${t.vars.joinToString(", ") { "$it" }}]")
                if (t.prims.isNotEmpty()) parts.add("prims=[${t.prims.joinToString(", ")}]")
                if (t.rec != null) parts.add("rec={${t.rec.entries.joinToString(", ") { (k, v) -> "$k: ${formatBounded(v)}" }}}")
                if (t.func != null) {
                    parts.add(
                        "func=([${t.func.first.joinToString(", ") { formatBounded(it) }}] -> ${formatBounded(t.func.second)})",
                    )
                }
                if (t.optional != null) parts.add("optional=${formatBounded(t.optional)}")
                if (t.refs.isNotEmpty()) {
                    parts.add(
                        "refs=[${t.refs.joinToString(", ") { ref ->
                            "TRef(\"${ref.name}\", [${ref.args.joinToString(", ") { arg -> formatBounded(arg) }}])"
                        }}]",
                    )
                }
                return "CompactType(${parts.joinToString(", ")})"
            }

            // Transitively close over variables reachable through bounds
            fun closeOver(
                initial: TVar,
                pol: Variance,
            ): Set<TVar> {
                val result = mutableSetOf(initial)
                val worklist = mutableListOf(initial)
                while (worklist.isNotEmpty()) {
                    val tv = worklist.removeLast()
                    val bounds =
                        when (pol) {
                            Variance.Covariant -> tv.lowerBounds
                            Variance.Contravariant -> tv.upperBounds
                            Variance.Invariant, Variance.Bivariant -> tv.lowerBounds + tv.upperBounds
                        }
                    for (bound in bounds) {
                        if (bound is TVar && bound !in result) {
                            result.add(bound)
                            worklist.add(bound)
                        }
                    }
                }
                trace { "closeOver($initial, $pol)  => [${result.joinToString(", ") { "$it" }}]" }
                return result
            }

            // Convert SimpleType into CompactType up to TVars and flatten transitive bounds.
            // In a way this turns a single 'layer' of SimpleType into CompactType.
            // TVar bounds are left as SimpleType, as this allows us to deal with cycles in the next phase
            fun fromSimpleType(
                ty: SimpleType,
                pol: Variance,
            ): CompactType {
                fun toBounded(
                    inner: SimpleType,
                    composedPol: Variance,
                ): BoundedCompactType =
                    BoundedCompactType(
                        lower = if (composedPol.isPositive) fromSimpleType(inner, Variance.Covariant) else CompactType.empty,
                        upper = if (composedPol.isNegative) fromSimpleType(inner, Variance.Contravariant) else CompactType.empty,
                    )

                return traced({ "fromSimpleType(${formatSimpleType(ty)}, $pol)" }) {
                    when (ty) {
                        is TNum -> CompactType.prim(PrimType.Num)
                        is TString -> CompactType.prim(PrimType.String)
                        is TBool -> CompactType.prim(PrimType.Bool)
                        is TNull -> CompactType.prim(PrimType.Null)
                        is TUnit -> CompactType.prim(PrimType.Unit)
                        is TOptional -> CompactType.optional(toBounded(ty.inner, pol))
                        is TFun ->
                            CompactType.function(
                                ty.params.map { toBounded(it, pol.flip()) },
                                toBounded(ty.result, pol),
                            )
                        is TRecord -> CompactType.record(ty.fields.mapValues { (_, v) -> toBounded(v, pol) })
                        is TRef -> {
                            val typeDef = env.getTypeDef(ty.name)
                            trace { "typeParams for ${ty.name}: ${typeDef.typeParams.map { it.variance }}" }
                            val args =
                                ty.typeArgs.zip(typeDef.typeParams) { arg, paramInfo ->
                                    toBounded(
                                        arg,
                                        when (paramInfo.variance) {
                                            Variance.Contravariant -> pol.flip()
                                            Variance.Invariant -> Variance.Invariant
                                            else -> pol
                                        },
                                    )
                                }
                            CompactType(refs = setOf(RefType(ty.name, args)))
                        }
                        is TVar -> CompactType(vars = closeOver(ty, pol))
                    }
                }.also { trace { "  => ${formatCompactType(it)}" } }
            }

            fun mergeBounds(
                ty: CompactType,
                pol: Variance,
                inProgress: Set<Pair<CompactType, Variance>>,
            ): CompactType =
                traced({ "mergeBounds(${formatCompactType(ty)}, $pol)" }) {
                    if (ty.isEmpty()) {
                        trace { "  => empty" }
                        return@traced ty
                    }

                    val key = ty to pol
                    if (key in inProgress) {
                        val recVar = recursive.getOrPut(key) { TVar() }
                        trace { "  => CYCLE, returning $recVar" }
                        return@traced CompactType.variable(recVar)
                    }
                    val newInProgress = inProgress + key

                    fun mergeBounded(
                        b: BoundedCompactType,
                        composedPol: Variance,
                    ): BoundedCompactType =
                        BoundedCompactType(
                            lower =
                                if (composedPol.isPositive) {
                                    mergeBounds(
                                        b.lower,
                                        Variance.Covariant,
                                        newInProgress,
                                    )
                                } else {
                                    CompactType.empty
                                },
                            upper =
                                if (composedPol.isNegative) {
                                    mergeBounds(
                                        b.upper,
                                        Variance.Contravariant,
                                        newInProgress,
                                    )
                                } else {
                                    CompactType.empty
                                },
                        )

                    val bounds =
                        ty.vars
                            .flatMap { tv ->
                                traced({ "converting concrete bounds for $tv ($pol)" }) {
                                    val lower =
                                        tv.lowerBounds.filter { pol.isPositive && it !is TVar }.map {
                                            traced({ "$tv has lower bound $it" }) {
                                                fromSimpleType(it, Variance.Covariant)
                                            }
                                        }
                                    val upper =
                                        tv.upperBounds.filter { pol.isNegative && it !is TVar }.map {
                                            traced({ "$tv has upper bound $it" }) {
                                                fromSimpleType(it, Variance.Contravariant)
                                            }
                                        }
                                    lower + upper
                                }
                            }.fold(ty) { acc, t -> acc.merge(t, pol) }
                    trace { "merged concrete bounds: ${formatCompactType(bounds)}" }

                    val result =
                        CompactType(
                            vars = bounds.vars,
                            prims = bounds.prims,
                            rec =
                                bounds.rec?.mapValues { (_, v) ->
                                    trace { "recursing into ${bounds.rec}" }
                                    mergeBounded(v, pol)
                                },
                            func =
                                bounds.func?.let { (params, result) ->
                                    params.map { mergeBounded(it, pol.flip()) } to mergeBounded(result, pol)
                                },
                            optional = bounds.optional?.let { mergeBounded(it, pol) },
                            refs =
                                bounds.refs
                                    .map { ref ->
                                        trace { "recursing into $ref" }
                                        RefType(
                                            ref.name,
                                            ref.args.map { arg ->
                                                BoundedCompactType(
                                                    lower = mergeBounds(arg.lower, Variance.Covariant, newInProgress),
                                                    upper = mergeBounds(arg.upper, Variance.Contravariant, newInProgress),
                                                )
                                            },
                                        )
                                    }.toSet(),
                        )

                    val recVar = recursive[key]
                    if (recVar != null) {
                        recVars[recVar] = result
                        trace { "  => recursive $recVar" }
                        CompactType.variable(recVar)
                    } else {
                        trace { "  => ${formatCompactType(result)}" }
                        result
                    }
                }

            val term = mergeBounds(fromSimpleType(ty, pol), pol, inProgress = emptySet())
            return CompactTypeScheme(term, recVars)
        }
    }

    fun merge(
        other: CompactType,
        pol: Variance,
    ): CompactType {
        fun mergeBounded(
            b1: BoundedCompactType,
            b2: BoundedCompactType,
        ) = BoundedCompactType(
            lower = b1.lower.merge(b2.lower, Variance.Covariant),
            upper = b1.upper.merge(b2.upper, Variance.Contravariant),
        )

        val positive = pol.isPositive
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
                        mergeBounded(this.rec[k]!!, other.rec[k]!!)
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
                            else -> mergeBounded(t1, t2)
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
                            mergeBounded(p1, p2)
                        }
                    val mergedResult = mergeBounded(result1, result2)
                    mergedParams to mergedResult
                }
            }

        val mergedOptional =
            when {
                this.optional == null -> other.optional
                other.optional == null -> this.optional
                else -> mergeBounded(this.optional, other.optional)
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
