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
                val parts = mutableListOf<String>()
                if (t.vars.isNotEmpty()) parts.add("vars=[${t.vars.joinToString(", ") { "$it" }}]")
                if (t.prims.isNotEmpty()) parts.add("prims=[${t.prims.joinToString(", ")}]")
                if (t.rec != null) parts.add("rec={${t.rec.entries.joinToString(", ") { (k, v) -> "$k: ${formatCompactType(v)}" }}}")
                if (t.func !=
                    null
                ) {
                    parts.add(
                        "func=([${t.func.first.joinToString(", ") { formatCompactType(it) }}] -> ${formatCompactType(t.func.second)})",
                    )
                }
                if (t.refs.isNotEmpty()) {
                    parts.add(
                        "refs=[${t.refs.joinToString(", ") { ref ->
                            "TRef(\"${ref.name}\", [${ref.args.joinToString(", ") { arg ->
                                "Bounded(lower=${formatCompactType(arg.lower)}, upper=${formatCompactType(arg.upper)})"
                            }}])"
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
                trace { "fromSimpleType(${formatSimpleType(ty)}, $pol)" }
                indent++
                val result =
                    when (ty) {
                        is TNum -> CompactType.prim(PrimType.Num)
                        is TString -> CompactType.prim(PrimType.String)
                        is TBool -> CompactType.prim(PrimType.Bool)
                        is TNull -> CompactType.prim(PrimType.Null)
                        is TUnit -> CompactType.prim(PrimType.Unit)
                        is TOptional -> CompactType.optional(fromSimpleType(ty.inner, pol))
                        is TFun -> CompactType.function(ty.params.map { fromSimpleType(it, pol.flip()) }, fromSimpleType(ty.result, pol))
                        is TRecord -> CompactType.record(ty.fields.mapValues { (_, v) -> fromSimpleType(v, pol) })
                        is TRef -> {
                            val typeDef = env.getTypeDef(ty.name)
                            trace { "typeParams for ${ty.name}: ${typeDef.typeParams.map { it.variance }}" }
                            val args =
                                ty.typeArgs.zip(typeDef.typeParams) { arg, paramInfo ->
                                    val composedPol = pol.compose(paramInfo.variance)
                                    BoundedCompactType(
                                        lower = if (composedPol.isPositive) fromSimpleType(arg, Variance.Covariant) else CompactType.empty,
                                        upper =
                                            if (composedPol.isNegative) {
                                                fromSimpleType(
                                                    arg,
                                                    Variance.Contravariant,
                                                )
                                            } else {
                                                CompactType.empty
                                            },
                                    )
                                }
                            CompactType(refs = setOf(RefType(ty.name, args)))
                        }
                        is TVar -> CompactType(vars = closeOver(ty, pol))
                    }
                indent--
                trace { "  => ${formatCompactType(result)}" }
                return result
            }

            // Traverse the CompactType,
            fun mergeBounds(
                ty: CompactType,
                pol: Variance,
                inProgress: Set<Pair<CompactType, Variance>>,
            ): CompactType {
                trace { "mergeBounds(${formatCompactType(ty)}, $pol)" }
                indent++
                if (ty.isEmpty()) {
                    indent--
                    trace { "  => empty" }
                    return ty
                }

                // Short-circuit when a loop in bounds is detected
                val key = ty to pol
                if (key in inProgress) {
                    val recVar = recursive.getOrPut(key) { TVar() }
                    indent--
                    trace { "  => CYCLE, returning $recVar" }
                    return CompactType.variable(recVar)
                }
                val newInProgress = inProgress + key

                // Merge the concrete bounds of all TVars into a single type (e.g. combine record types)
                val bounds =
                    ty.vars
                        .flatMap { tv ->
                            trace { "converting concrete bounds for $tv ($pol)" }
                            indent++
                            val lower =
                                tv.lowerBounds.filter { pol.isPositive && it !is TVar }.map {
                                    trace { "$tv has lower bound $it" }
                                    indent++
                                    val r = fromSimpleType(it, Variance.Covariant)
                                    indent--
                                    r
                                }
                            val upper =
                                tv.upperBounds.filter { pol.isNegative && it !is TVar }.map {
                                    trace { "$tv has upper bound $it" }
                                    indent++
                                    val r = fromSimpleType(it, Variance.Contravariant)
                                    indent--
                                    r
                                }

                            indent--
                            lower + upper
                        }.fold(ty) { acc, t -> acc.merge(t, pol) }
                trace { "merged concrete bounds: ${formatCompactType(bounds)}" }

                val result =
                    CompactType(
                        vars = bounds.vars,
                        prims = bounds.prims,
                        rec =
                            bounds.rec?.mapValues { (_, v) ->
                                trace { "recursing into ${bounds.rec}" }
                                mergeBounds(v, pol, newInProgress)
                            },
                        func =
                            bounds.func?.let { (params, result) ->
                                params.map { mergeBounds(it, pol.flip(), newInProgress) } to mergeBounds(result, pol, newInProgress)
                            },
                        optional = bounds.optional?.let { mergeBounds(it, pol, newInProgress) },
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
                indent--
                return if (recVar != null) {
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
                        this.rec[k]!!.merge(other.rec[k]!!, pol)
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
                            else -> t1.merge(t2, pol)
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
                            p1.merge(p2, pol.flip())
                        }
                    val mergedResult = result1.merge(result2, pol)
                    mergedParams to mergedResult
                }
            }

        val mergedOptional =
            when {
                this.optional == null -> other.optional
                other.optional == null -> this.optional
                else -> this.optional.merge(other.optional, pol)
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
