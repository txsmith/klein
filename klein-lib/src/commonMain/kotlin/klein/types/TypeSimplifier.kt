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
    private const val DEBUG = false

    private inline fun debug(msg: () -> String) {
        if (DEBUG) println(msg())
    }

    fun simplifyCanonical(
        type: SimpleType,
        env: TypeEnv,
        pol: Variance = Variance.Covariant,
        keepVars: Boolean = false,
    ): Type {
        val scheme = CompactType.canonicalizeType(type, pol, env)
        var simplified = scheme
        var iteration = 0
        do {
            iteration++
            debug { "=== Simplification iteration $iteration ===" }
            val (next, changed) = simplifyType(simplified, keepVars, env)
            simplified = next
        } while (changed && iteration < 1000)
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
    private fun simplifyType(
        cty: CompactTypeScheme,
        keepVars: Boolean = false,
        env: TypeEnv? = null,
    ): Pair<CompactTypeScheme, Boolean> {
        // Phase 1: Collect co-occurrence information
        val analysis = collectCoOccurrences(cty)

        debug { "=== Co-occurrence Analysis ===" }
        debug { "allVars: ${analysis.allVars.map { it.toString() }}" }
        debug { "recVars: ${analysis.recVars.map { it.toString() }}" }
        if (DEBUG) {
            for ((key, occs) in analysis.coOccurrences) {
                val (pol, tv) = key
                val polStr = if (pol) "+" else "-"
                debug { "  $tv @ $polStr: $occs" }
            }
        }
        debug { "==============================" }

        val varSubst = mutableMapOf<TVar, TVar?>()

        // Phase 2: Eliminate single-polarity variables
        eliminateSinglePolarityVars(analysis, varSubst, keepVars)

        // Phase 3: Unify co-occurring variables
        unifyCoOccurringVars(analysis, varSubst)

        debug { "varSubst: $varSubst" }

        // Phase 4: Apply substitutions
        val resultType = applySubstitutions(cty.term, varSubst)

        val newRecVars =
            cty.recVars
                .filterKeys { it !in varSubst || varSubst[it] != null }
                .mapKeys { (k, _) -> varSubst[k] ?: k }
                .mapValues { (_, v) -> applySubstitutions(v, varSubst) }

        debug { "=== After simplification ===" }
        debug { resultType.toString() }
        debug { "==============================" }

        val changed = varSubst.isNotEmpty()
        return CompactTypeScheme(resultType, newRecVars) to changed
    }

    /**
     * Phase 1: Traverse the type and collect co-occurrence information.
     *
     * For each variable, we track what other types always appear alongside it at each polarity.
     */

    private data class CoOccurrenceAnalysis(
        val allVars: Set<TVar>,
        val recVars: Set<TVar>,
        val coOccurrences: Map<Pair<Boolean, TVar>, Set<Any>>,
    )

    private fun collectCoOccurrences(cty: CompactTypeScheme): CoOccurrenceAnalysis {
        val allVars = mutableSetOf<TVar>()
        val recVars = mutableSetOf<TVar>()
        val coOccurrences = mutableMapOf<Pair<Boolean, TVar>, MutableSet<Any>>()

        fun collect(
            ty: CompactType,
            pol: Boolean,
        ) {
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

                // If tv is recursive, also collect from its expansion
                cty.recVars[tv]?.let { expansion ->
                    if (tv !in recVars) {
                        recVars.add(tv)
                        collect(expansion, pol)
                    }
                }
            }

            fun collectBounded(b: BoundedCompactType) {
                collect(b.lower, true)
                collect(b.upper, false)
            }

            ty.rec
                ?.fields
                ?.values
                ?.forEach { collectBounded(it) }
            ty.func?.let { (params, result) ->
                params.forEach { collectBounded(it) }
                collectBounded(result)
            }
            ty.optional?.let { collectBounded(it) }
            ty.refs.forEach { ref -> ref.args.forEach { collectBounded(it) } }
        }

        collect(cty.term, pol = true)
        return CoOccurrenceAnalysis(
            allVars = allVars.toSet(),
            recVars = recVars.toSet(),
            coOccurrences = coOccurrences.mapValues { (_, v) -> v.toSet() },
        )
    }

    /**
     * Phase 2: Eliminate variables that only occur at one polarity.
     *
     * Variables appearing only positively can be replaced with Bottom (they only flow out).
     * Variables appearing only negatively can be replaced with Top (they only flow in).
     */
    private fun eliminateSinglePolarityVars(
        analysis: CoOccurrenceAnalysis,
        varSubst: MutableMap<TVar, TVar?>,
        keepVars: Boolean,
    ) {
        for (v in analysis.allVars) {
            if (v in analysis.recVars) continue
            val posOccs = analysis.coOccurrences[true to v]
            val negOccs = analysis.coOccurrences[false to v]
            if ((posOccs != null && negOccs == null) || (posOccs == null && negOccs != null)) {
                if (keepVars && v.lowerBounds.isEmpty() && v.upperBounds.isEmpty()) continue
                varSubst[v] = null // eliminate
            }
        }
    }

    /**
     * Phase 3: Unify variables that always co-occur, and eliminate variables
     * that co-occur with the same concrete type at both polarities.
     */
    private fun unifyCoOccurringVars(
        analysis: CoOccurrenceAnalysis,
        varSubst: MutableMap<TVar, TVar?>,
    ) {
        // Process in descending uid order like SimpleSub
        for (v in analysis.allVars.sortedByDescending { it.uid }) {
            if (v in varSubst) continue

            for (pol in listOf(true, false)) {
                val vOccs = analysis.coOccurrences[pol to v] ?: continue

                for (w in vOccs.toList()) {
                    when (w) {
                        is TVar -> {
                            if (w == v) continue
                            if (w in varSubst) continue
                            // If v is to be eliminated, don't unify other vars into it
                            if (v in varSubst && varSubst[v] == null) continue
                            // Don't merge rec and non-rec vars
                            if ((v in analysis.recVars) != (w in analysis.recVars)) continue

                            val wOccs = analysis.coOccurrences[pol to w] ?: continue
                            if (v in wOccs) {
                                // v and w always co-occur at this polarity - unify w into v
                                varSubst[w] = v
                            }
                        }
                        is PrimType -> {
                            // Primitives have no internal structure, so direct comparison works
                            val vOppOccs = analysis.coOccurrences[!pol to v]
                            if (vOppOccs != null && w in vOppOccs) {
                                varSubst[v] = null
                            }
                        }
                        is RefType, is RecordType, is FunctionType -> {
                            // These types have polarity-dependent internal structure (BoundedCompactTypes
                            // inside them store content in lower vs upper based on polarity). To compare
                            // across polarities, we flip to convert from current polarity's form to the
                            // opposite polarity's form.
                            val vOppOccs = analysis.coOccurrences[!pol to v]
                            val flipped =
                                when (w) {
                                    is RefType -> w.flip()
                                    is RecordType -> w.flip()
                                    is FunctionType -> w.flip()
                                    else -> w
                                }
                            if (vOppOccs != null && flipped in vOppOccs) {
                                varSubst[v] = null
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applySubstitutions(
        ty: CompactType,
        varSubst: Map<TVar, TVar?>,
    ): CompactType {
        val newVars = ty.vars.mapNotNull { tv -> if (tv !in varSubst) tv else varSubst[tv] }.toSet()

        fun applyBounded(b: BoundedCompactType): BoundedCompactType =
            BoundedCompactType(
                lower = applySubstitutions(b.lower, varSubst),
                upper = applySubstitutions(b.upper, varSubst),
            )

        return CompactType(
            vars = newVars,
            prims = ty.prims,
            rec = ty.rec?.let { RecordType(it.fields.mapValues { (_, v) -> applyBounded(v) }) },
            func = ty.func?.let { (params, result) -> FunctionType(params.map { applyBounded(it) }, applyBounded(result)) },
            optional = ty.optional?.let { applyBounded(it) },
            refs = ty.refs.map { ref -> RefType(ref.name, ref.args.map { applyBounded(it) }) }.toSet(),
        )
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
                // If bounds are equal, it's just a concrete type
                if (b.lower == b.upper && !b.lower.isEmpty()) {
                    return go(b.lower, true, newInProcess)
                }

                val hasLower = !b.lower.isEmpty()
                val hasUpper = !b.upper.isEmpty()
                return when {
                    hasLower && hasUpper -> {
                        // Invariant position: find common variable and use it with bounds.
                        //
                        // This is essentially a late co-occurrence check - we're detecting that a var
                        // appears in both compartments with different surrounding types (bounds).
                        // In principle, simplification could eliminate such vars (when bounds are
                        // compatible, i.e., lower <: upper) and we'd synthesize a fresh var here.
                        // But that would require subtype checking during simplification.
                        // For now, we preserve the var through simplification and extract bounds here.
                        val commonVars = b.lower.vars.intersect(b.upper.vars)
                        when {
                            commonVars.size == 1 -> {
                                val commonVar = commonVars.single()
                                val lowerBound = go(b.lower.copy(vars = b.lower.vars - commonVar), true, newInProcess)
                                val upperBound = go(b.upper.copy(vars = b.upper.vars - commonVar), false, newInProcess)
                                val lower = if (lowerBound == Type.Bottom) null else lowerBound
                                val upper = if (upperBound == Type.Top) null else upperBound
                                Type.Var(varName(commonVar), lower, upper)
                            }
                            commonVars.isEmpty() -> {
                                // Var was eliminated because it co-occurred with same type at both polarities.
                                // Both bounds should coalesce to the same concrete type.
                                val lowerType = go(b.lower, true, newInProcess)
                                val upperType = go(b.upper, false, newInProcess)
                                if (lowerType == upperType) {
                                    lowerType
                                } else {
                                    error(
                                        "Invariant type arg with no common variables and mismatched bounds: lower=$lowerType, upper=$upperType",
                                    )
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
