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
    data class Resolved(
        val components: TypeComponents,
        val pol: Boolean,
    ) : RefArg()

    data class Invariant(
        val tvar: TVar,
        val pos: TypeComponents,
        val neg: TypeComponents,
    ) : RefArg() {
        /**
         * Check if the positive and negative bounds are semantically equal,
         * meaning the type is the same regardless of polarity (a "tight sandwich").
         *
         * Structurally equal TypeComponents can still differ semantically when they
         * contain multiple components in the same slot (e.g., prims={Num, String}
         * renders as Num | String in positive but Num & String in negative).
         *
         * Returns true only when the bounds are equal AND polarity-independent
         * (at most one component per slot, recursively).
         */
        fun hasEqualBounds(): Boolean {
            val strippedPos = pos.copy(vars = pos.vars - tvar)
            val strippedNeg = neg.copy(vars = neg.vars - tvar)
            return strippedPos == strippedNeg &&
                !strippedPos.isEmpty() &&
                isPolarityIndependent(strippedPos)
        }

        private fun isPolarityIndependent(tc: TypeComponents): Boolean {
            val componentCount =
                tc.prims.size +
                    (if (tc.rec != null) 1 else 0) +
                    (if (tc.func != null) 1 else 0) +
                    (if (tc.optional != null) 1 else 0) +
                    tc.allRefs().size
            if (componentCount > 1) return false

            return (
                tc.rec
                    ?.fields
                    ?.values
                    ?.all { isPolarityIndependent(it) } ?: true
            ) &&
                (tc.func?.let { it.params.all { p -> isPolarityIndependent(p) } && isPolarityIndependent(it.result) } ?: true) &&
                (tc.optional?.let { isPolarityIndependent(it) } ?: true) &&
                tc.allRefs().all { ref ->
                    ref.args.all { arg ->
                        when (arg) {
                            is Resolved -> isPolarityIndependent(arg.components)
                            is Invariant -> arg.hasEqualBounds()
                        }
                    }
                }
        }
    }
}

// TODO: Carry relevant TypeDefInfo to avoid repeated env lookups during merging
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
 */
data class RefFamily(
    val parent: RefType?,
    val constructors: List<RefType>,
) {
    // override fun toString(): String =
    //     // if (parent != null) {
    //         // parent.toString()
    //     // } else {
    //         constructors.toString()
    //     // }
}

// TODO: Add map/fold methods to TypeComponents to reduce duplication in recursive traversals
//   (flattenBounds, applySubstitutions, isPolarityIndependent, etc. all walk the same structure)
data class TypeComponents(
    val vars: Set<TVar> = emptySet(),
    val skolems: Set<TSkolem> = emptySet(),
    val prims: Set<PrimType> = emptySet(),
    val nullable: Boolean = false,
    val rec: RecordType? = null,
    val func: FunctionType? = null,
    val optional: TypeComponents? = null,
    val refs: Map<String, RefFamily> = emptyMap(),
) {
    enum class PrimType { Num, String, Bool, Unit, Top, Bottom }

    companion object {
        val empty = TypeComponents()

        fun prim(p: PrimType) = TypeComponents(prims = setOf(p))

        fun variable(v: TVar) = TypeComponents(vars = setOf(v))

        fun skolem(s: TSkolem) = TypeComponents(skolems = setOf(s))

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
            val family =
                if (ctor == null) {
                    // Parent ref (e.g. List<Num>) → expand to all constructors
                    val parentDef = env.getTypeDef(name)
                    val parentArgMap = parentDef.typeParams.zip(args).associate { (p, a) -> p.name to a }
                    val constructors =
                        env
                            .allConstructors()
                            .filter { it.parentType == name }
                            .map { ctorInfo ->
                                val ctorDef = env.getTypeDef(ctorInfo.name)
                                val ctorArgs = ctorDef.typeParams.map { param -> parentArgMap[param.name]!! }
                                RefType(ctorInfo.name, ctorArgs)
                            }
                    RefFamily(parent = refType, constructors)
                } else {
                    RefFamily(parent = null, listOf(refType))
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
                    is TTop -> "TTop"
                    is TBottom -> "TBottom"
                    is TVar -> "$t"
                    is TSkolem -> "$t"
                    is TOptional -> "TOptional(${formatSimpleType(t.inner)})"
                    is TFun -> "TFun([${t.params.joinToString(", ") { formatSimpleType(it) }}], ${formatSimpleType(t.result)})"
                    is TRecord -> "TRecord(${t.fields.map { (k, v) -> "$k: ${formatSimpleType(v)}" }})"
                    is TRef -> "TRef(\"${t.name}\", [${t.typeArgs.joinToString(", ") { formatSimpleType(it) }}])"
                }

            fun formatTypeComponents(t: TypeComponents): String {
                val parts = mutableListOf<String>()
                if (t.vars.isNotEmpty()) parts.add("vars=[${t.vars.joinToString(", ") { "$it" }}]")
                if (t.skolems.isNotEmpty()) parts.add("skolems=[${t.skolems.joinToString(", ") { "$it" }}]")
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
            ): TypeComponents =
                traced({ "fromSimpleType(${formatSimpleType(ty)}, $pol)" }) {
                    when (ty) {
                        is TNum -> TypeComponents.prim(PrimType.Num)
                        is TString -> TypeComponents.prim(PrimType.String)
                        is TBool -> TypeComponents.prim(PrimType.Bool)
                        is TNull -> TypeComponents(nullable = true)
                        is TUnit -> TypeComponents.prim(PrimType.Unit)
                        is TTop -> TypeComponents.prim(PrimType.Top)
                        is TBottom -> TypeComponents.prim(PrimType.Bottom)
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
                                        Variance.Invariant ->
                                            RefArg.Invariant(
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
                        is TSkolem -> TypeComponents.skolem(ty)
                    }
                }.also { trace { "  => ${formatTypeComponents(it)}" } }

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
                                is RefArg.Invariant ->
                                    RefArg.Invariant(
                                        arg.tvar,
                                        flattenBounds(arg.pos, true, newInProgress),
                                        flattenBounds(arg.neg, false, newInProgress),
                                    )
                            }
                        }

                    fun flattenRefType(ref: RefType): RefType = RefType(ref.name, flattenRefArgs(ref.args))

                    val result =
                        TypeComponents(
                            vars = bounds.vars,
                            skolems = bounds.skolems,
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
                                    val flatConstructors = family.constructors.map { flattenRefType(it) }
                                    val flatParent = family.parent?.let { flattenRefType(it) }
                                    RefFamily(flatParent, flatConstructors)
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
        ): RecordType =
            if (pol) {
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
                result[parentName] =
                    if (leftFamily != null) {
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
        ): RefFamily {
            val allCtors = left.constructors + right.constructors

            return when {
                left.parent != null && right.parent != null -> {
                    RefFamily(foldRefs(left.parent, listOf(right.parent), env), allCtors)
                }

                left.parent != null -> {
                    RefFamily(foldRefs(left.parent, right.constructors, env), allCtors)
                }

                right.parent != null -> {
                    RefFamily(foldRefs(right.parent, left.constructors, env), allCtors)
                }

                else -> {
                    // TODO: Could just compare counts instead of building sets
                    //   (same-name refs are already deduplicated, so merged.size == allPossibleCtors.size suffices)
                    val allPossibleCtors = env.allConstructors().filter { it.parentType == parentName }
                    val presentNames = allCtors.map { it.name }.toSet()

                    val isSingleCtorType = allPossibleCtors.size == 1 && allPossibleCtors[0].name != parentName
                    if (pol && !isSingleCtorType && presentNames.containsAll(allPossibleCtors.map { it.name }.toSet())) {
                        val parentDef = env.getTypeDef(parentName)
                        val emptyParent =
                            RefType(
                                parentName,
                                parentDef.typeParams.map { param ->
                                    when (param.variance) {
                                        Variance.Invariant -> RefArg.Invariant(TVar(), empty, empty)
                                        Variance.Contravariant -> RefArg.Resolved(empty, false)
                                        else -> RefArg.Resolved(empty, true)
                                    }
                                },
                            )
                        RefFamily(parent = foldRefs(emptyParent, allCtors, env), allCtors)
                    } else {
                        val byName = allCtors.groupBy { it.name }
                        RefFamily(parent = null, byName.map { (_, refs) -> foldRefs(refs.first(), refs.drop(1), env) })
                    }
                }
            }
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

            val merged =
                rest.fold(initial) { currentArgs, ref ->
                    val refDef = env.getTypeDef(ref.name)
                    val refArgMap = refDef.typeParams.zip(ref.args).associate { (p, a) -> p.name to a }

                    baseDef.typeParams.associate { param ->
                        val current = currentArgs[param.name]!!
                        val incoming = refArgMap[param.name]
                        param.name to
                            if (incoming != null) {
                                mergeRefArg(current, incoming, env)
                            } else {
                                current
                            }
                    }
                }

            return RefType(base.name, baseDef.typeParams.map { merged[it.name]!! })
        }

        /**
         * Project a RefArg into pos/neg TypeComponents for invariant merging.
         * For Resolved: the components go to the matching polarity, empty to the other.
         * For Invariant: strip the arg's own tvar (it's the bounded variable, not part of
         *   the bound) but keep other vars so flattenBounds can resolve them later.
         */
        private fun toPos(arg: RefArg): TypeComponents =
            when (arg) {
                is RefArg.Resolved -> if (arg.pol) arg.components else empty
                is RefArg.Invariant -> arg.pos
            }

        private fun toNeg(arg: RefArg): TypeComponents =
            when (arg) {
                is RefArg.Resolved -> if (!arg.pol) arg.components else empty
                is RefArg.Invariant -> arg.neg
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

            // General case: merge as invariant with both bounds.
            // The tvar is added to both pos and neg so that:
            //   1. Co-occurrence analysis can unify inference tvars into it
            //   2. Coalescing can strip it (and unified vars) from the bounds
            val tvar = TVar()
            val mergedPos = toPos(left).merge(toPos(right), true, env)
            val mergedNeg = toNeg(left).merge(toNeg(right), false, env)
            return RefArg.Invariant(
                tvar,
                mergedPos.copy(vars = mergedPos.vars + tvar),
                mergedNeg.copy(vars = mergedNeg.vars + tvar),
            )
        }
    }

    fun merge(
        other: TypeComponents,
        pol: Boolean,
        env: TypeEnv,
    ): TypeComponents {
        val mergedVars = this.vars + other.vars
        val mergedSkolems = this.skolems + other.skolems
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
            skolems = mergedSkolems,
            prims = mergedPrims,
            nullable = mergedNullable,
            rec = mergedRec,
            func = mergedFun,
            optional = mergedOptional,
            refs = mergedRefs,
        )
    }

    fun isEmpty(): Boolean =
        vars.isEmpty() && skolems.isEmpty() && prims.isEmpty() && !nullable && rec == null && func == null && optional == null && refs.isEmpty()

    fun hasConcreteComponents(): Boolean =
        skolems.isNotEmpty() || prims.isNotEmpty() || rec != null || func != null || optional != null || refs.isNotEmpty()

    fun allRefs(): Set<RefType> =
        refs
            .flatMap { (_, family) ->
                when {
                    family.parent != null -> family.constructors.ifEmpty { listOf(family.parent) }
                    else -> family.constructors
                }
            }.toSet()

    /** Get refs for display and co-occurrance analysis, uses parent ref for exhaustive families. */
    fun displayRefs(): Set<RefType> =
        refs
            .flatMap { (_, family) ->
                when {
                    family.parent != null -> listOf(family.parent)
                    else -> family.constructors
                }
            }.toSet()

    override fun toString(): String {
        val parts = mutableListOf<String>()
        if (vars.isNotEmpty()) parts.add("vars=$vars")
        if (skolems.isNotEmpty()) parts.add("skolems=$skolems")
        if (prims.isNotEmpty()) parts.add("prims=$prims")
        if (nullable) parts.add("nullable")
        if (rec != null) parts.add("rec=$rec")
        if (func != null) parts.add("func=$func")
        if (optional != null) parts.add("optional=$optional")
        if (refs.isNotEmpty()) parts.add("refs=${refs.values}")
        return if (parts.isEmpty()) "TypeComponents()" else "TypeComponents(${parts.joinToString(", ")})"
    }
}
