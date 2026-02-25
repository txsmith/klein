package klein.types

import klein.Type
import klein.types.CompactType.PrimType
import klein.types.SimpleType.TVar

/**
 * Type simplification following the SimpleSub algorithm.
 *
 * Pipeline:
 *   SimpleType → CompactType (canonicalizeType) → simplified CompactType → Type
 *
 * Reference: https://lptk.github.io/programming/2020/03/26/demystifying-mlsub.html
 */
object TypeSimplifier {
    fun simplifyCanonical(
        type: SimpleType,
        env: TypeEnv,
        pol: Variance = Variance.Covariant,
        keepVars: Boolean = false,
    ): Type {
        val scheme = CompactType.canonicalizeType(type, pol, env)
        val simplified = simplifyType(scheme, keepVars, env)
        // val simplifiedTwice = simplifyType(simplified, keepVars, env)
        return coalesceType(simplified, env, keepVars, pol)
    }

    /**
     * Simplifies a CompactTypeScheme by performing co-occurrence analysis.
     *
     * Two simplifications:
     * 1. Variables that only occur in one polarity can be eliminated
     *    (positive-only → Bottom, negative-only → Top)
     *    Example: ('a & 'b) -> ('a, 'b) is the same as 'a -> ('a, 'a)
     *    Example: ('a & 'b) -> 'b -> ('a, 'b) is NOT the same as 'a -> 'a -> ('a, 'a)
     *      there is no value of 'a that can make 'a -> 'a -> ('a, 'a) <: (a & b) -> b -> (a, b) work
     *      we'd require 'a :> b | a & b <: a & b, which are NOT valid bounds!
     *    Example: 'a -> 'b -> 'a | 'b is the same as 'a -> 'a -> 'a
     *    Justification: the other var 'b can always be taken to be 'a & 'b (resp. a | b)
     *       without loss of generality. Indeed, on the pos side we'll have 'a <: 'a & 'b and 'b <: 'a & 'b
     *       and on the neg side, we'll always have 'a and 'b together, i.e., 'a & 'b
     *
     * 2. Variables that always co-occur at the same polarity can be unified
     *    This would arise from constraints such as: Int <: 'a, 'a <:'b and 'b <: Int
     *      (contraints which basically say 'a =:= 'b =:= Int)
     *    Example: 'a ∧ Int -> 'a ∨ Int is the same as Int -> Int
     *    Currently, we only do this for primitive types.
     *    In principle it could be done for functions and records, too.
     *    Note: conceptually, this idea subsumes the simplification that removes variables occurring
     *        exclusively in positive or negative positions.
     *      Indeed, if 'a never occurs positively, it's like it always occurs both positively AND
     *      negatively along with the type Bot, so we can replace it with Bot.
     */
    fun simplifyType(
        cty: CompactTypeScheme,
        keepVars: Boolean = false,
        env: TypeEnv? = null,
    ): CompactTypeScheme {
        // The following three mutable collections are all constucted by calling `analyze`:
        // - allVars is simply the collection of all TVars that occur anywhere in `cty`
        // - recVars is the set of all recursive TVars along with their expanded type
        // - coOccurrences is the collection of types that always co-occur with a variable at a given variance.

        val allVars = mutableSetOf<TVar>()
        val recVars = mutableMapOf<TVar, () -> CompactType>()
        val coOccurrences = mutableMapOf<Pair<Boolean, TVar>, MutableSet<Any>>()

        // varSubst: substitution to apply during reconstruction
        // This is populated after `analyze` runs.
        // null value = eliminate, non-null = replace with that var
        // Note that null vs 'no substitution present' are different.
        val varSubst = mutableMapOf<TVar, TVar?>()

        // Phase 1: Traverse and collect co-occurrence information, then return a thunk that reconstructs the final simplified CompactType.
        // The thunk uses `varSubst` to reconstruct the original type while eliminating/merging variables that have been deemed redundant or equivalent.
        //
        // The 'architecture' of this function is a quite peculiar when you first look at it, mostly because it mixes two key concerns:
        // - collecting co-occurrence information
        // - reconstructing the simplified type with substitutions (that it itself doesn't calculate!)
        //
        // The main reason this is done this way is (i think) for performance: all these things are done in a single pass over the entire CompactType.
        // Splitting the concerns would require at least two passes (potentially three if we look at recVars too).
        // Is performance gain worth the 'clever' opaqueness? I don't know, no benchmarks have been run so we really don't know the difference it makes.
        fun analyze(
            ty: CompactType,
            pol: Boolean,
        ): () -> CompactType {
            for (tv in ty.vars) {
                allVars.add(tv)
                val newOccs = mutableSetOf<Any>()
                newOccs.addAll(ty.vars)
                newOccs.addAll(ty.prims)
                ty.rec?.let { newOccs.add(it) }
                ty.func?.let { newOccs.add(it) }
                newOccs.addAll(ty.refs)
                val key = pol to tv
                val existing = coOccurrences[key]
                if (existing != null) {
                    existing.retainAll(newOccs)
                } else {
                    coOccurrences[key] = newOccs
                }

                // If tv is recursive, also analyze its expansion
                cty.recVars[tv]?.let { expansion ->
                    if (tv !in recVars) {
                        // Register before recursing to avoid infinite recursion
                        lateinit var goLater: () -> CompactType
                        recVars[tv] = { goLater() }
                        goLater = analyze(expansion, pol)
                    }
                }
            }

            fun analyzeBounded(b: BoundedCompactType): () -> BoundedCompactType {
                val lowerThunk = analyze(b.lower, true)
                val upperThunk = analyze(b.upper, false)
                return { BoundedCompactType(lower = lowerThunk(), upper = upperThunk()) }
            }

            val recThunks = ty.rec?.fields?.mapValues { (_, v) -> analyzeBounded(v) }
            val funThunks =
                ty.func?.let { (params, result) ->
                    params.map { analyzeBounded(it) } to analyzeBounded(result)
                }
            val optionalThunk = ty.optional?.let { analyzeBounded(it) }
            val refThunks =
                ty.refs.map { ref ->
                    val argThunks = ref.args.map { arg -> analyzeBounded(arg) }
                    ref.name to argThunks
                }

            // Return a thunk that applies substitutions during reconstruction
            return {
                val newVars =
                    ty.vars
                        .mapNotNull { tv ->
                            // Only replace if present in substitutions map
                            if (tv !in varSubst) {
                                tv
                            } else {
                                varSubst[tv]
                            }
                        }.toSet()

                CompactType(
                    vars = newVars,
                    prims = ty.prims,
                    rec = recThunks?.let { RecordType(it.mapValues { (_, thunk) -> thunk() }) },
                    func =
                        funThunks?.let { (paramThunks, resultThunk) ->
                            FunctionType(paramThunks.map { it() }, resultThunk())
                        },
                    optional = optionalThunk?.invoke(),
                    refs =
                        refThunks
                            .map { (name, argThunks) ->
                                RefType(name, argThunks.map { it() })
                            }.toSet(),
                )
            }
        }

        val termThunk = analyze(cty.term, pol = true)

        // DEBUG: Print co-occurrence information
        println("=== Co-occurrence Analysis ===")
        println("allVars: ${allVars.map { it.toString() }}")
        println("recVars: ${recVars.keys.map { it.toString() }}")
        for ((key, occs) in coOccurrences) {
            val (pol, tv) = key
            val polStr = if (pol) "+" else "-"
            println("  $tv @ $polStr: $occs")
        }
        println("==============================")

        // Phase 2: Simplify away single-polarity non-recursive variables
        for (v in allVars) {
            if (v in recVars) continue
            val posOccs = coOccurrences[true to v]
            val negOccs = coOccurrences[false to v]
            if ((posOccs != null && negOccs == null) || (posOccs == null && negOccs != null)) {
                if (keepVars && v.lowerBounds.isEmpty() && v.upperBounds.isEmpty()) continue
                varSubst[v] = null // eliminate
            }
        }

        // Phase 3: Unify co-occurring variables (process in descending uid order like SimpleSub)
        for (v in allVars.sortedByDescending { it.uid }) {
            if (v in varSubst) continue

            for (pol in listOf(true, false)) {
                val vOccs = coOccurrences[pol to v] ?: continue

                for (w in vOccs.toList()) {
                    when (w) {
                        is TVar -> {
                            if (w == v) continue
                            if (w in varSubst) continue
                            // Don't merge rec and non-rec vars
                            if ((v in recVars) != (w in recVars)) continue

                            val wOccs = coOccurrences[pol to w] ?: continue
                            if (v in wOccs) {
                                // v and w always co-occur at this polarity - unify w into v
                                varSubst[w] = v

                                // Merge co-occurrences from opposite polarity
                                if (w in recVars) {
                                    // Recursive: merge bounds
                                    val boundW = recVars[w]!!
                                    val boundV = recVars[v]!!
                                    val polVariance = if (pol) Variance.Covariant else Variance.Contravariant
                                    recVars[v] = { CompactType.empty.merge(boundV(), polVariance).merge(boundW(), polVariance) }
                                    recVars.remove(w)
                                } else {
                                    // Non-recursive: intersect opposite polarity co-occurrences
                                    val vOppOccs = coOccurrences[!pol to v]
                                    val wOppOccs = coOccurrences[!pol to w]
                                    if (vOppOccs != null && wOppOccs != null) {
                                        vOppOccs.retainAll(wOppOccs)
                                        vOppOccs.add(v)
                                    }
                                }
                            }
                        }
                        is PrimType -> {
                            // If variable co-occurs with same primitive at both polarities, eliminate it
                            val vOppOccs = coOccurrences[!pol to v]
                            if (vOppOccs != null && w in vOppOccs) {
                                varSubst[v] = null
                            }
                        }
                        is RefType -> {
                            // If variable co-occurs with same ref at both polarities, eliminate it
                            val vOppOccs = coOccurrences[!pol to v]
                            if (vOppOccs != null && w in vOppOccs) {
                                varSubst[v] = null
                            }
                        }
                        is RecordType -> {
                            // If variable co-occurs with same record at both polarities, eliminate it
                            val vOppOccs = coOccurrences[!pol to v]
                            if (vOppOccs != null && w in vOppOccs) {
                                varSubst[v] = null
                            }
                        }
                        is FunctionType -> {
                            // If variable co-occurs with same function at both polarities, eliminate it
                            val vOppOccs = coOccurrences[!pol to v]
                            if (vOppOccs != null && w in vOppOccs) {
                                varSubst[v] = null
                            }
                        }
                    }
                }
            }
        }

        val newRecVars =
            recVars
                .filterKeys { it !in varSubst || varSubst[it] != null }
                .mapKeys { (k, _) -> varSubst[k] ?: k }
                .mapValues { (_, thunk) -> thunk() }

        val resultType = termThunk()

        println("=== After simplification ===")
        println(resultType)
        println("==============================")

        return CompactTypeScheme(resultType, newRecVars)
    }

    /**
     * Coalesces a CompactTypeScheme into a Type.
     * Uses hash-consing to tie recursive type knots tighter.
     * Variable names are assigned on first encounter during traversal.
     */
    fun coalesceType(
        cty: CompactTypeScheme,
        env: TypeEnv,
        keepVars: Boolean = false,
        pol: Variance = Variance.Covariant,
    ): Type {
        val varNames = mutableMapOf<TVar, String>()

        fun varName(v: TVar): String =
            varNames.getOrPut(v) {
                val idx = varNames.size
                val letter = 'A' + (idx % 26)
                val suffix = if (idx >= 26) "${idx / 26}" else ""
                "'$letter$suffix"
            }

        fun go(
            ty: CompactType,
            pol: Boolean,
            inProcess: Map<Pair<CompactType, Boolean>, () -> Type.Var>,
        ): Type {
            val key = ty to pol
            inProcess[key]?.let { return it() }

            if (ty.isEmpty()) {
                if (keepVars) {
                    val v = TVar()
                    return Type.Var(varName(v))
                }
                return if (pol) Type.Bottom else Type.Top
            }

            var isRecursive = false
            val recVar by lazy {
                isRecursive = true
                val v = TVar()
                Type.Var(varName(v))
            }

            val newInProcess = inProcess + (key to { recVar })

            fun coalesceBounded(
                b: BoundedCompactType,
                pol: Boolean,
            ): Type {
                val hasLower = !b.lower.isEmpty()
                val hasUpper = !b.upper.isEmpty()
                return when {
                    hasLower && hasUpper -> {
                        // Invariant position: find common variable and use it with bounds
                        val commonVars = b.lower.vars.intersect(b.upper.vars)
                        when (commonVars.size) {
                            1 -> {
                                val commonVar = commonVars.single()
                                val lowerBound = go(b.lower.copy(vars = b.lower.vars - commonVar), true, newInProcess)
                                val upperBound = go(b.upper.copy(vars = b.upper.vars - commonVar), false, newInProcess)
                                // If bounds are equal, just use that type directly
                                // if (lowerBound == upperBound) {
                                //     lowerBound
                                // } else {
                                val lower = if (lowerBound == Type.Bottom) null else lowerBound
                                val upper = if (upperBound == Type.Top) null else upperBound
                                Type.Var(varName(commonVar), lower, upper)
                                // }
                            }
                            0 -> {
                                val lowerType = go(b.lower, true, newInProcess)
                                val upperType = go(b.upper, false, newInProcess)
                                if (lowerType == upperType) {
                                    lowerType
                                } else {
                                    error("Invariant type arg with no common variable: lower=$lowerType, upper=$upperType")
                                }
                            }
                            else -> error("Invariant type arg with multiple common variables: $commonVars")
                        }
                    }
                    hasLower -> go(b.lower, true, newInProcess)
                    hasUpper -> go(b.upper, false, newInProcess)
                    else -> if (pol) Type.Bottom else Type.Top
                }
            }

            val components = mutableListOf<Type>()

            // Process in same order as simple-sub: vars, prims, rec, func, ...
            // This ensures variables at the current level are named before
            // descending into nested function types

            // Add variables (non-recursive ones become type vars, recursive ones expand)
            // Process in descending uid order to match simple-sub's variable naming
            for (v in ty.vars.sortedByDescending { it.uid }) {
                val bound = cty.recVars[v]
                if (bound != null) {
                    components.add(go(bound, pol, newInProcess))
                } else {
                    components.add(Type.Var(varName(v)))
                }
            }

            // Add primitives
            for (prim in ty.prims) {
                components.add(
                    when (prim) {
                        PrimType.Num -> Type.Num
                        PrimType.String -> Type.Str
                        PrimType.Bool -> Type.Bool
                        PrimType.Null -> Type.Null
                        PrimType.Unit -> Type.Unit
                    },
                )
            }

            // Add record
            ty.rec?.let { rec ->
                val typeFields = rec.fields.mapValues { (_, v) -> coalesceBounded(v, pol) }
                components.add(Type.Record(typeFields))
            }

            // Add function
            ty.func?.let { (params, result) ->
                val typeParams = params.map { coalesceBounded(it, !pol) }
                val typeResult = coalesceBounded(result, pol)
                components.add(Type.Fun(typeParams, typeResult))
            }

            // Add optional
            ty.optional?.let { inner ->
                val innerType = coalesceBounded(inner, pol)
                components.add(Type.Optional(innerType))
            }

            // Add refs
            for (ref in ty.refs) {
                val coalescedArgs = ref.args.map { arg -> coalesceBounded(arg, pol) }
                components.add(Type.Ref(ref.name, coalescedArgs))
            }

            // In positive position, if Null appears with other types, convert to optional
            // e.g., Num | Null becomes Num?
            val hasNullPrim = PrimType.Null in ty.prims
            val hasOtherComponents = components.any { it != Type.Null }
            val wrapInOptional = pol && hasNullPrim && hasOtherComponents

            val componentsWithoutNull =
                if (wrapInOptional) {
                    components.filter { it != Type.Null }
                } else {
                    components
                }

            val baseResult =
                if (componentsWithoutNull.isEmpty()) {
                    if (pol) Type.Bottom else Type.Top
                } else if (componentsWithoutNull.size == 1) {
                    componentsWithoutNull[0]
                } else {
                    if (pol) {
                        componentsWithoutNull.reduce { acc, t -> Type.Union(acc, t) }
                    } else {
                        componentsWithoutNull.reduce { acc, t -> Type.Inter(acc, t) }
                    }
                }

            val result =
                if (wrapInOptional) {
                    Type.Optional(baseResult)
                } else {
                    baseResult
                }

            return if (isRecursive) {
                Type.Rec(recVar.name, result)
            } else {
                result
            }
        }

        return go(cty.term, pol = pol.isPositive, inProcess = emptyMap())
    }
}
