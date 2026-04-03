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
 * Most type args are simply resolved to a TypeComponents, this means they have only lower or upper bounds. Invariant type args on refs genuinely carry both bounds (positive and negative).
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

/**
 * A family of refs sharing the same parent type.
 * - Some: a partial set of constructors (not yet exhaustive)
 * - All: all constructors present, collapsed to the parent ref with merged type args
 */
sealed class RefFamily {
    data class Some(val constructors: List<RefType>) : RefFamily()
    data class All(val ref: RefType) : RefFamily()
}

data class TypeComponents(
    val vars: Set<TVar> = emptySet(),
    val prims: Set<PrimType> = emptySet(),
    val nullable: Boolean = false,
    val rec: RecordType? = null,
    val func: FunctionType? = null,
    val optional: TypeComponents? = null,
    val refs: Map<String, RefFamily> = emptyMap(),
) {
    enum class PrimType { Num, String, Bool, Unit }

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
            env: TypeEnv,
        ): TypeComponents {
            val refType = RefType(name, args)
            val ctor = env.lookupConstructor(name)
            val parentName = ctor?.parentType ?: name
            val family = if (ctor == null) {
                // Parent ref (e.g. List<Num>) → All
                RefFamily.All(refType)
            } else {
                RefFamily.Some(listOf(refType))
            }
            return TypeComponents(refs = mapOf(parentName to family))
        }

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
                        "refs=[${t.allRefs().joinToString(", ") { ref ->
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
                            TypeComponents.ref(ty.name, args, env)
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
                            }.fold(ty) { acc, t -> acc.merge(t, pol, env) }
                    trace { "merged concrete bounds: ${formatTypeComponents(bounds)}" }

                    fun flattenRefArgs(args: List<RefArg>): List<RefArg> =
                        args.map { arg ->
                            when (arg) {
                                is RefArg.Resolved -> RefArg.Resolved(flattenBounds(arg.components, arg.pol, newInProgress), arg.pol)
                                is RefArg.Invariant -> RefArg.Invariant(
                                    arg.tvar,
                                    flattenBounds(arg.pos, true, newInProgress),
                                    flattenBounds(arg.neg, false, newInProgress),
                                )
                            }
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
                                bounds.refs.mapValues { (_, family) ->
                                    when (family) {
                                        is RefFamily.All -> {
                                            trace { "recursing into ${family.ref}" }
                                            RefFamily.All(RefType(family.ref.name, flattenRefArgs(family.ref.args)))
                                        }
                                        is RefFamily.Some -> RefFamily.Some(
                                            family.constructors.map { ref ->
                                                trace { "recursing into $ref" }
                                                RefType(ref.name, flattenRefArgs(ref.args))
                                            },
                                        )
                                    }
                                },
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
            env: TypeEnv,
        ): RecordType {
            return if (pol) {
                val commonKeys = left.fields.keys.intersect(right.fields.keys)
                RecordType(
                    commonKeys.associateWith { k ->
                        left.fields[k]!!.merge(right.fields[k]!!, pol, env)
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
                            else -> t1.merge(t2, pol, env)
                        }
                    },
                )
            }
        }

        private fun mergeFunctions(
            left: FunctionType,
            right: FunctionType,
            pol: Boolean,
            env: TypeEnv,
        ): FunctionType {
            val mergedParams =
                left.params.zip(right.params).map { (p1, p2) ->
                    p1.merge(p2, !pol, env)
                }
            val mergedResult = left.result.merge(right.result, pol, env)
            return FunctionType(mergedParams, mergedResult)
        }

        private fun mergeRefFamilies(
            left: Map<String, RefFamily>,
            right: Map<String, RefFamily>,
            pol: Boolean,
            env: TypeEnv,
        ): Map<String, RefFamily> {
            if (left.isEmpty()) return right
            if (right.isEmpty()) return left

            val result = left.toMutableMap()
            for ((parentName, rightFamily) in right) {
                val leftFamily = result[parentName]
                result[parentName] = if (leftFamily != null) {
                    mergeFamily(leftFamily, rightFamily, parentName, pol, env)
                } else {
                    rightFamily
                }
            }
            return result
        }

        private fun mergeFamily(
            left: RefFamily,
            right: RefFamily,
            parentName: String,
            pol: Boolean,
            env: TypeEnv,
        ): RefFamily = when {
            left is RefFamily.All && right is RefFamily.All ->
                RefFamily.All(foldRefs(left.ref, listOf(right.ref), env))

            left is RefFamily.All && right is RefFamily.Some ->
                RefFamily.All(foldRefs(left.ref, right.constructors, env))

            left is RefFamily.Some && right is RefFamily.All ->
                RefFamily.All(foldRefs(right.ref, left.constructors, env))

            left is RefFamily.Some && right is RefFamily.Some -> {
                val all = left.constructors + right.constructors
                val allCtors = env.allConstructors().filter { it.parentType == parentName }
                val presentNames = all.map { it.name }.toSet()

                val isSingleCtorType = allCtors.size == 1 && allCtors[0].name != parentName
                if (pol && !isSingleCtorType && presentNames.containsAll(allCtors.map { it.name }.toSet())) {
                    val parentDef = env.getTypeDef(parentName)
                    val emptyParent = RefType(parentName, parentDef.typeParams.map { param ->
                        when (param.variance) {
                            Variance.Invariant -> RefArg.Invariant(TVar(), empty, empty)
                            Variance.Contravariant -> RefArg.Resolved(empty, false)
                            else -> RefArg.Resolved(empty, true)
                        }
                    })
                    RefFamily.All(foldRefs(emptyParent, all, env))
                } else {
                    val byName = all.groupBy { it.name }
                    RefFamily.Some(byName.map { (name, refs) -> foldRefs(refs.first(), refs.drop(1), env) })
                }
            }

            else -> error("unreachable")
        }

        /**
         * Fold refs into a base ref by mapping each ref's type args
         * to the base type's params and merging. Works for same-name refs,
         * constructors into parent, or any combination.
         */
        private fun foldRefs(
            base: RefType,
            rest: List<RefType>,
            env: TypeEnv,
        ): RefType {
            val baseDef = env.getTypeDef(base.name)
            val initial = baseDef.typeParams.zip(base.args).associate { (p, a) -> p.name to a }

            val merged = rest.fold(initial) { currentArgs, ref ->
                val refDef = env.getTypeDef(ref.name)
                val refArgMap = refDef.typeParams.zip(ref.args).associate { (p, a) -> p.name to a }

                baseDef.typeParams.associate { param ->
                    val current = currentArgs[param.name]!!
                    val incoming = refArgMap[param.name]
                    param.name to if (incoming != null) {
                        mergeRefArg(current, incoming, env)
                    } else {
                        current
                    }
                }
            }

            return RefType(base.name, baseDef.typeParams.map { merged[it.name]!! })
        }

        /** Extract the positive (output) bound of a RefArg. */
        private fun posBound(arg: RefArg): TypeComponents = when (arg) {
            is RefArg.Resolved -> if (arg.pol) arg.components else empty
            is RefArg.Invariant -> arg.pos.copy(vars = arg.pos.vars - arg.tvar)
        }

        /** Extract the negative (input) bound of a RefArg. */
        private fun negBound(arg: RefArg): TypeComponents = when (arg) {
            is RefArg.Resolved -> if (!arg.pol) arg.components else empty
            is RefArg.Invariant -> arg.neg.copy(vars = arg.neg.vars - arg.tvar)
        }

        private fun mergeRefArg(
            left: RefArg,
            right: RefArg,
            env: TypeEnv,
        ): RefArg {
            if (left == right) return left

            // Same polarity Resolved args — fast path
            if (left is RefArg.Resolved && right is RefArg.Resolved && left.pol == right.pol) {
                return RefArg.Resolved(left.components.merge(right.components, left.pol, env), left.pol)
            }

            // General case: merge as invariant with both bounds
            val mergedPos = posBound(left).merge(posBound(right), true, env)
            val mergedNeg = negBound(left).merge(negBound(right), false, env)
            return RefArg.Invariant(TVar(), mergedPos, mergedNeg)
        }
    }

    fun merge(
        other: TypeComponents,
        pol: Boolean,
        env: TypeEnv,
    ): TypeComponents {
        val mergedVars = this.vars + other.vars
        val mergedPrims = this.prims + other.prims
        val mergedNullable = this.nullable || other.nullable
        val mergedRefs = mergeRefFamilies(this.refs, other.refs, pol, env)

        val mergedRec =
            when {
                this.rec == null -> other.rec
                other.rec == null -> this.rec
                else -> mergeRecords(this.rec, other.rec, pol, env)
            }

        val mergedFun =
            when {
                this.func == null -> other.func
                other.func == null -> this.func
                else -> mergeFunctions(this.func, other.func, pol, env)
            }

        val mergedOptional =
            when {
                this.optional == null -> other.optional
                other.optional == null -> this.optional
                else -> this.optional.merge(other.optional, pol, env)
            }

        return TypeComponents(
            vars = mergedVars,
            prims = mergedPrims,
            nullable = mergedNullable,
            rec = mergedRec,
            func = mergedFun,
            optional = mergedOptional,
            refs = mergedRefs,
        )
    }

    fun isEmpty(): Boolean = vars.isEmpty() && prims.isEmpty() && !nullable && rec == null && func == null && optional == null && refs.isEmpty()

    fun hasConcreteComponents(): Boolean = prims.isNotEmpty() || rec != null || func != null || optional != null || refs.isNotEmpty()

    /** Get all refs as a flat set (for coalescing). */
    fun allRefs(): Set<RefType> = refs.flatMap { (_, family) ->
        when (family) {
            is RefFamily.All -> listOf(family.ref)
            is RefFamily.Some -> family.constructors
        }
    }.toSet()

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
