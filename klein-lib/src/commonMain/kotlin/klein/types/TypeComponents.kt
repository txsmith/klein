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
sealed class Component

sealed class RefArg {
    data class Resolved(val components: TypeComponents, val pol: Boolean) : RefArg()

    data class Invariant(val tvar: TVar, val pos: TypeComponents, val neg: TypeComponents) : RefArg()
}

data class RefType(
    val name: String,
    val args: List<RefArg>,
) : Component()

data class RecordType(
    val fields: Map<String, TypeComponents>,
) : Component()

data class FunctionType(
    val params: List<TypeComponents>,
    val result: TypeComponents,
) : Component()

data class PrimComponent(val prim: TypeComponents.PrimType) : Component()

data class OptionalComponent(val inner: TypeComponents) : Component()

data class TypeComponents(
    val vars: Set<TVar> = emptySet(),
    val prims: Set<PrimType> = emptySet(),
    val nullable: Boolean = false,
    val rec: RecordType? = null,
    val func: FunctionType? = null,
    val optional: TypeComponents? = null,
    val refs: Set<RefType> = emptySet(),
    val tightBound: Component? = null,
) {
    enum class PrimType { Num, String, Bool, Unit }

    companion object {
        val empty = TypeComponents()

        fun prim(p: PrimType) = TypeComponents(prims = setOf(p), tightBound = PrimComponent(p))

        fun variable(v: TVar) = TypeComponents(vars = setOf(v))

        fun function(
            params: List<TypeComponents>,
            result: TypeComponents,
        ) = FunctionType(params, result).let { f -> TypeComponents(func = f, tightBound = f) }

        fun record(fields: Map<String, TypeComponents>) = RecordType(fields).let { r -> TypeComponents(rec = r, tightBound = r) }

        fun optional(inner: TypeComponents) = TypeComponents(optional = inner, tightBound = OptionalComponent(inner))

        fun ref(
            name: String,
            args: List<RefArg>,
        ) = RefType(name, args).let { r -> TypeComponents(refs = setOf(r), tightBound = r) }

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
                        is TNull -> TypeComponents(nullable = true)
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
                            TypeComponents.ref(ty.name, args)
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

                    fun flattenComponent(comp: Component, pol: Boolean): Component =
                        when (comp) {
                            is PrimComponent -> comp
                            is RecordType -> RecordType(comp.fields.mapValues { (_, v) -> flattenBounds(v, pol, newInProgress) })
                            is FunctionType -> FunctionType(
                                comp.params.map { flattenBounds(it, !pol, newInProgress) },
                                flattenBounds(comp.result, pol, newInProgress),
                            )
                            is OptionalComponent -> OptionalComponent(flattenBounds(comp.inner, pol, newInProgress))
                            is RefType -> RefType(comp.name, comp.args.map { arg ->
                                when (arg) {
                                    is RefArg.Resolved -> RefArg.Resolved(flattenBounds(arg.components, arg.pol, newInProgress), arg.pol)
                                    is RefArg.Invariant -> RefArg.Invariant(
                                        arg.tvar,
                                        flattenBounds(arg.pos, true, newInProgress),
                                        flattenBounds(arg.neg, false, newInProgress),
                                    )
                                }
                            })
                        }

                    val result =
                        TypeComponents(
                            vars = bounds.vars,
                            prims = bounds.prims,
                            nullable = bounds.nullable,
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
                            tightBound = bounds.tightBound?.let { flattenComponent(it, pol) },
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

        private fun mergeRecords(
            left: RecordType,
            right: RecordType,
            pol: Boolean,
        ): RecordType {
            return if (pol) {
                val commonKeys = left.fields.keys.intersect(right.fields.keys)
                RecordType(
                    commonKeys.associateWith { k ->
                        left.fields[k]!!.merge(right.fields[k]!!, pol)
                    },
                )
            } else {
                val allKeys = left.fields.keys.union(right.fields.keys)
                RecordType(
                    allKeys.associateWith { k ->
                        val t1 = left.fields[k]
                        val t2 = right.fields[k]
                        when {
                            t1 == null -> t2!!
                            t2 == null -> t1
                            else -> t1.merge(t2, pol)
                        }
                    },
                )
            }
        }

        private fun mergeFunctions(
            left: FunctionType,
            right: FunctionType,
            pol: Boolean,
        ): FunctionType {
            val mergedParams =
                left.params.zip(right.params).map { (p1, p2) ->
                    p1.merge(p2, !pol)
                }
            val mergedResult = left.result.merge(right.result, pol)
            return FunctionType(mergedParams, mergedResult)
        }

        private fun componentToTypeComponents(comp: Component): TypeComponents =
            when (comp) {
                is PrimComponent -> prim(comp.prim)
                is RecordType -> record(comp.fields)
                is FunctionType -> function(comp.params, comp.result)
                is OptionalComponent -> optional(comp.inner)
                is RefType -> ref(comp.name, comp.args)
            }

        private fun mergeOptionalWithOther(
            opt: OptionalComponent,
            other: Component,
            pol: Boolean,
        ): Component? {
            val merged = opt.inner.merge(componentToTypeComponents(other), pol)
            return if (pol) {
                // LUB: T? | U = (T | U)?
                merged.tightBound?.let { OptionalComponent(merged) }
            } else {
                // GLB: T? & U = T & U (null excluded by non-optional side)
                merged.tightBound
            }
        }

        fun mergeTightBounds(
            left: Component?,
            right: Component?,
            pol: Boolean,
        ): Component? {
            if (left == null) return right
            if (right == null) return left

            return when {
                left is PrimComponent && right is PrimComponent ->
                    if (left.prim == right.prim) left else null

                left is RecordType && right is RecordType ->
                    mergeRecords(left, right, pol)

                left is FunctionType && right is FunctionType ->
                    mergeFunctions(left, right, pol)

                left is OptionalComponent && right is OptionalComponent ->
                    OptionalComponent(left.inner.merge(right.inner, pol))

                // Optional + non-optional
                left is OptionalComponent -> mergeOptionalWithOther(left, right, pol)
                right is OptionalComponent -> mergeOptionalWithOther(right, left, pol)

                // Same-kind: ref — TODO next chunk
                left is RefType && right is RefType -> null

                // Ref + record — TODO next chunk
                left is RefType && right is RecordType -> null
                left is RecordType && right is RefType -> null

                // Cross-kind: incompatible
                else -> null
            }
        }
    }

    fun merge(
        other: TypeComponents,
        pol: Boolean,
    ): TypeComponents {
        val mergedVars = this.vars + other.vars
        val mergedPrims = this.prims + other.prims
        val mergedNullable = this.nullable || other.nullable
        // TODO: merge refs by LUB/GLB. A wrinkle is invariant type args —
        // they would need to be equal. Constraint checking already takes care of that for us.
        val mergedRefs = this.refs + other.refs

        val mergedRec =
            when {
                this.rec == null -> other.rec
                other.rec == null -> this.rec
                else -> mergeRecords(this.rec, other.rec, pol)
            }

        val mergedFun =
            when {
                this.func == null -> other.func
                other.func == null -> this.func
                else -> mergeFunctions(this.func, other.func, pol)
            }

        val mergedOptional =
            when {
                this.optional == null -> other.optional
                other.optional == null -> this.optional
                else -> this.optional.merge(other.optional, pol)
            }

        val mergedTightBound = mergeTightBounds(this.tightBound, other.tightBound, pol)

        return TypeComponents(
            vars = mergedVars,
            prims = mergedPrims,
            nullable = mergedNullable,
            rec = mergedRec,
            func = mergedFun,
            optional = mergedOptional,
            refs = mergedRefs,
            tightBound = mergedTightBound,
        )
    }

    fun isEmpty(): Boolean = vars.isEmpty() && prims.isEmpty() && !nullable && rec == null && func == null && optional == null && refs.isEmpty()

    fun hasConcreteComponents(): Boolean = prims.isNotEmpty() || rec != null || func != null || optional != null || refs.isNotEmpty()

    override fun toString(): String {
        val parts = mutableListOf<String>()
        if (vars.isNotEmpty()) parts.add("vars=$vars")
        if (prims.isNotEmpty()) parts.add("prims=$prims")
        if (nullable) parts.add("nullable")
        if (rec != null) parts.add("rec=$rec")
        if (func != null) parts.add("func=$func")
        if (optional != null) parts.add("optional=$optional")
        if (refs.isNotEmpty()) parts.add("refs=$refs")
        return if (parts.isEmpty()) "TypeComponents()" else "TypeComponents(${parts.joinToString(", ")})"
    }
}
