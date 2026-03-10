package klein.types

import klein.types.SimpleType.*

// Compact type representation for the simplification phase.
//
// Unlike SimpleType (which has mutable bounds for inference), TypeComponents is an
// immutable representation that explicitly represents unions and intersections.
//
// In positive position (output), this represents a union of components.
// In negative position (input), this represents an intersection of components.
//
// Reference: SimpleSub paper - https://lptk.github.io/programming/2020/03/26/demystifying-mlsub.html

data class TypeScheme(
    val term: TypeComponents,
    val recVars: Map<TVar, TypeComponents> = emptyMap(),
    val pol: Boolean = true,
)

/**
 * Represents a type argument for a ref type.
 *
 * Most type args are simply resolved to a TypeComponents. Invariant type args
 * on refs genuinely need both bounds (positive and negative), which is captured
 * by the Invariant variant.
 */
sealed class RefArg {
    data class Resolved(val components: TypeComponents, val pol: Boolean) : RefArg()

    data class Invariant(val tvar: TVar, val pos: TypeComponents, val neg: TypeComponents) : RefArg()
}

data class RefType(
    val name: String,
    val args: List<RefArg>,
)

data class RecordType(
    val fields: Map<String, TypeComponents>,
)

data class FunctionType(
    val params: List<TypeComponents>,
    val result: TypeComponents,
)

data class TypeComponents(
    val vars: Set<TVar> = emptySet(),
    val prims: Set<PrimType> = emptySet(),
    val rec: RecordType? = null,
    val func: FunctionType? = null,
    val optional: TypeComponents? = null,
    val refs: Set<RefType> = emptySet(),
) {
    enum class PrimType { Num, String, Bool, Null, Unit }

    companion object {
        val empty = TypeComponents()

        fun prim(p: PrimType) = TypeComponents(prims = setOf(p))

        fun variable(v: TVar) = TypeComponents(vars = setOf(v))

        fun function(
            params: List<TypeComponents>,
            result: TypeComponents,
        ) = TypeComponents(func = FunctionType(params, result))

        fun record(fields: Map<String, TypeComponents>) = TypeComponents(rec = RecordType(fields))

        fun optional(inner: TypeComponents) = TypeComponents(optional = inner)

        fun ref(
            name: String,
            args: List<RefArg>,
        ) = TypeComponents(refs = setOf(RefType(name, args)))

        /**
         * Convert SimpleType to TypeComponents using canonicalization.
         *
         * Tracks (TypeComponents, pol) pairs to detect recursion, allowing merging
         * co-occurring recursive types with different cycle lengths by finding their LCD.
         *
         * The algorithm uses two phases:
         * - go0: Convert outermost layer without expanding variable bounds
         * - go1: Merge bounds of all variables and recursively process
         */
        fun canonicalizeType(
            ty: SimpleType,
            pol: Boolean = true,
            env: TypeEnv,
            debug: Boolean = false,
        ): TypeScheme {
            val recursive = mutableMapOf<Pair<TypeComponents, Boolean>, TVar>()
            val recVars = mutableMapOf<TVar, TypeComponents>()
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

            fun formatTypeComponents(t: TypeComponents): String {
                val parts = mutableListOf<String>()
                if (t.vars.isNotEmpty()) parts.add("vars=[${t.vars.joinToString(", ") { "$it" }}]")
                if (t.prims.isNotEmpty()) parts.add("prims=[${t.prims.joinToString(", ")}]")
                if (t.rec != null) parts.add("rec={${t.rec.fields.entries.joinToString(", ") { (k, v) -> "$k: $v" }}}")
                if (t.func != null) {
                    parts.add(
                        "func=([${t.func.params.joinToString(", ")}] -> ${t.func.result})",
                    )
                }
                if (t.optional != null) parts.add("optional=${t.optional}")
                if (t.refs.isNotEmpty()) {
                    parts.add(
                        "refs=[${t.refs.joinToString(", ") { ref ->
                            "TRef(\"${ref.name}\", [${ref.args.joinToString(", ")}])"
                        }}]",
                    )
                }
                return "TypeComponents(${parts.joinToString(", ")})"
            }

            // Transitively close over variables reachable through bounds
            fun closeOver(
                initial: TVar,
                pol: Boolean,
            ): Set<TVar> {
                val result = mutableSetOf(initial)
                val worklist = mutableListOf(initial)
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
                trace { "closeOver($initial, $pol)  => [${result.joinToString(", ") { "$it" }}]" }
                return result
            }

            // Convert SimpleType into TypeComponents up to TVars and flatten transitive bounds.
            // In a way this turns a single 'layer' of SimpleType into TypeComponents.
            // TVar bounds are left as SimpleType, as this allows us to deal with cycles in the next phase
            fun fromSimpleType(
                ty: SimpleType,
                pol: Boolean,
            ): TypeComponents {
                return traced({ "fromSimpleType(${formatSimpleType(ty)}, $pol)" }) {
                    when (ty) {
                        is TNum -> TypeComponents.prim(PrimType.Num)
                        is TString -> TypeComponents.prim(PrimType.String)
                        is TBool -> TypeComponents.prim(PrimType.Bool)
                        is TNull -> TypeComponents.prim(PrimType.Null)
                        is TUnit -> TypeComponents.prim(PrimType.Unit)
                        is TOptional -> TypeComponents.optional(fromSimpleType(ty.inner, pol))
                        is TFun ->
                            TypeComponents.function(
                                ty.params.map { fromSimpleType(it, !pol) },
                                fromSimpleType(ty.result, pol),
                            )
                        is TRecord -> TypeComponents.record(ty.fields.mapValues { (_, v) -> fromSimpleType(v, pol) })
                        is TRef -> {
                            val typeDef = env.getTypeDef(ty.name)
                            trace { "typeParams for ${ty.name}: ${typeDef.typeParams.map { it.variance }}" }
                            val args =
                                ty.typeArgs.zip(typeDef.typeParams) { arg, paramInfo ->
                                    when (paramInfo.variance) {
                                        Variance.Invariant -> RefArg.Invariant(
                                            tvar = (arg as? TVar) ?: TVar(),
                                            pos = fromSimpleType(arg, true),
                                            neg = fromSimpleType(arg, false),
                                        )
                                        Variance.Contravariant -> RefArg.Resolved(fromSimpleType(arg, !pol), pol = !pol)
                                        else -> RefArg.Resolved(fromSimpleType(arg, pol), pol = pol)
                                    }
                                }
                            TypeComponents(refs = setOf(RefType(ty.name, args)))
                        }
                        is TVar -> TypeComponents(vars = closeOver(ty, pol))
                    }
                }.also { trace { "  => ${formatTypeComponents(it)}" } }
            }

            fun flattenBounds(
                ty: TypeComponents,
                pol: Boolean,
                inProgress: Set<Pair<TypeComponents, Boolean>>,
            ): TypeComponents =
                traced({ "flattenBounds(${formatTypeComponents(ty)}, $pol)" }) {
                    if (ty.isEmpty()) {
                        trace { "  => empty" }
                        return@traced ty
                    }

                    val key = ty to pol
                    if (key in inProgress) {
                        val recVar = recursive.getOrPut(key) { TVar() }
                        trace { "  => CYCLE, returning $recVar" }
                        return@traced TypeComponents.variable(recVar)
                    }
                    val newInProgress = inProgress + key

                    val bounds =
                        ty.vars
                            .flatMap { tv ->
                                traced({ "converting concrete bounds for $tv ($pol)" }) {
                                    if (pol) {
                                        tv.lowerBounds.filter { it !is TVar }.map {
                                            traced({ "$tv has lower bound $it" }) {
                                                fromSimpleType(it, true)
                                            }
                                        }
                                    } else {
                                        tv.upperBounds.filter { it !is TVar }.map {
                                            traced({ "$tv has upper bound $it" }) {
                                                fromSimpleType(it, false)
                                            }
                                        }
                                    }
                                }
                            }.fold(ty) { acc, t -> acc.merge(t, pol) }
                    trace { "merged concrete bounds: ${formatTypeComponents(bounds)}" }

                    val result =
                        TypeComponents(
                            vars = bounds.vars,
                            prims = bounds.prims,
                            rec =
                                bounds.rec?.let { r ->
                                    trace { "recursing into ${bounds.rec}" }
                                    RecordType(r.fields.mapValues { (_, v) -> flattenBounds(v, pol, newInProgress) })
                                },
                            func =
                                bounds.func?.let { (params, result) ->
                                    FunctionType(
                                        params.map { flattenBounds(it, !pol, newInProgress) },
                                        flattenBounds(result, pol, newInProgress),
                                    )
                                },
                            optional = bounds.optional?.let { flattenBounds(it, pol, newInProgress) },
                            refs =
                                bounds.refs
                                    .map { ref ->
                                        trace { "recursing into $ref" }
                                        RefType(ref.name, ref.args.map { arg ->
                                            when (arg) {
                                                is RefArg.Resolved -> {
                                                    RefArg.Resolved(flattenBounds(arg.components, arg.pol, newInProgress), arg.pol)
                                                }
                                                is RefArg.Invariant -> RefArg.Invariant(
                                                    arg.tvar,
                                                    flattenBounds(arg.pos, true, newInProgress),
                                                    flattenBounds(arg.neg, false, newInProgress),
                                                )
                                            }
                                        })
                                    }.toSet(),
                        )

                    val recVar = recursive[key]
                    if (recVar != null) {
                        recVars[recVar] = result
                        trace { "  => recursive $recVar" }
                        TypeComponents.variable(recVar)
                    } else {
                        trace { "  => ${formatTypeComponents(result)}" }
                        result
                    }
                }

            val components = flattenBounds(fromSimpleType(ty, pol), pol, inProgress = emptySet())
            return TypeScheme(components, recVars, pol)
        }
    }

    fun merge(
        other: TypeComponents,
        pol: Boolean,
    ): TypeComponents {
        val positive = pol
        val mergedVars = this.vars + other.vars
        val mergedPrims = this.prims + other.prims
        // TODO: merge refs by LUB/GLB. A wrinkle is invariant type args —
        // they would need to be equal. Constraint checking already takes care of that for us.
        val mergedRefs = this.refs + other.refs

        val mergedRec =
            when {
                this.rec == null -> other.rec
                other.rec == null -> this.rec
                positive -> {
                    val commonKeys =
                        this.rec.fields.keys
                            .intersect(other.rec.fields.keys)
                    RecordType(
                        commonKeys.associateWith { k ->
                            this.rec.fields[k]!!.merge(other.rec.fields[k]!!, pol)
                        },
                    )
                }
                else -> {
                    val allKeys =
                        this.rec.fields.keys
                            .union(other.rec.fields.keys)
                    RecordType(
                        allKeys.associateWith { k ->
                            val t1 = this.rec.fields[k]
                            val t2 = other.rec.fields[k]
                            when {
                                t1 == null -> t2!!
                                t2 == null -> t1
                                else -> t1.merge(t2, pol)
                            }
                        },
                    )
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
                            p1.merge(p2, !pol)
                        }
                    val mergedResult = result1.merge(result2, pol)
                    FunctionType(mergedParams, mergedResult)
                }
            }

        val mergedOptional =
            when {
                this.optional == null -> other.optional
                other.optional == null -> this.optional
                else -> this.optional.merge(other.optional, pol)
            }

        return TypeComponents(
            vars = mergedVars,
            prims = mergedPrims,
            rec = mergedRec,
            func = mergedFun,
            optional = mergedOptional,
            refs = mergedRefs,
        )
    }

    fun isEmpty(): Boolean = vars.isEmpty() && prims.isEmpty() && rec == null && func == null && optional == null && refs.isEmpty()

    override fun toString(): String {
        val parts = mutableListOf<String>()
        if (vars.isNotEmpty()) parts.add("vars=$vars")
        if (prims.isNotEmpty()) parts.add("prims=$prims")
        if (rec != null) parts.add("rec=$rec")
        if (func != null) parts.add("func=$func")
        if (optional != null) parts.add("optional=$optional")
        if (refs.isNotEmpty()) parts.add("refs=$refs")
        return if (parts.isEmpty()) "TypeComponents()" else "TypeComponents(${parts.joinToString(", ")})"
    }
}
