package klein.types

import klein.types.CompactType.PrimType
import klein.types.SimpleType.TVar

/**
 * Type simplification following the SimpleSub algorithm.
 *
 * Pipeline:
 *   SimpleType → CompactType (fromSimpleType) → simplified CompactType → DisplayType
 *
 * Reference: https://lptk.github.io/programming/2020/03/26/demystifying-mlsub.html
 */
object TypeSimplifier {
    fun simplify(type: SimpleType): DisplayType {
        val scheme = CompactType.fromSimpleType(type)
        val simplified = simplifyType(scheme)
        return coalesceType(simplified)
    }

    /**
     * Like simplify, but uses canonicalization to merge co-occurring recursive types.
     * This produces simpler types when multiple recursive types with different cycle
     * lengths are merged (e.g., in a union).
     */
    fun simplifyCanonical(type: SimpleType): DisplayType {
        val scheme = CompactType.canonicalizeType(type)
        val simplified = simplifyType(scheme)
        return coalesceType(simplified)
    }

    /**
     * Simplifies a CompactTypeScheme by performing co-occurrence analysis.
     *
     * Two simplifications:
     * 1. Variables that only occur in one polarity can be eliminated
     *    (positive-only → Bottom, negative-only → Top)
     * 2. Variables that always co-occur at the same polarity can be unified
     */
    fun simplifyType(cty: CompactTypeScheme): CompactTypeScheme {
        val allVars = mutableSetOf<TVar>()
        val recVars = mutableMapOf<TVar, () -> CompactType>()

        // coOccurrences: for each (polarity, variable), track what other types always appear with it
        val coOccurrences = mutableMapOf<Pair<Boolean, TVar>, MutableSet<Any>>()

        // varSubst: substitution to apply during reconstruction
        // null value = eliminate, non-null = replace with that var
        val varSubst = mutableMapOf<TVar, TVar?>()

        // Phase 1: Traverse and collect co-occurrence information
        fun analyze(
            ty: CompactType,
            pol: Boolean,
        ): () -> CompactType {
            for (tv in ty.vars) {
                allVars.add(tv)
                val newOccs = mutableSetOf<Any>()
                newOccs.addAll(ty.vars)
                newOccs.addAll(ty.prims)
                val key = pol to tv
                val existing = coOccurrences[key]
                if (existing != null) {
                    existing.retainAll(newOccs)
                } else {
                    coOccurrences[key] = newOccs
                }

                // If tv is recursive, process its bound too
                cty.recVars[tv]?.let { bound ->
                    if (tv !in recVars) {
                        // Register before recursing to avoid infinite recursion
                        lateinit var goLater: () -> CompactType
                        recVars[tv] = { goLater() }
                        goLater = analyze(bound, pol)
                    }
                }
            }

            val recThunks = ty.rec?.mapValues { (_, v) -> analyze(v, pol) }
            val funThunks =
                ty.func?.let { (params, result) ->
                    params.map { analyze(it, !pol) } to analyze(result, pol)
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
                    rec = recThunks?.mapValues { (_, thunk) -> thunk() },
                    func =
                        funThunks?.let { (paramThunks, resultThunk) ->
                            paramThunks.map { it() } to resultThunk()
                        },
                )
            }
        }

        val termThunk = analyze(cty.term, pol = true)

        // Phase 2: Simplify away single-polarity non-recursive variables
        for (v in allVars) {
            if (v in recVars) continue
            val posOccs = coOccurrences[true to v]
            val negOccs = coOccurrences[false to v]
            if ((posOccs != null && negOccs == null) || (posOccs == null && negOccs != null)) {
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
                                    recVars[v] = { CompactType.empty.merge(boundV(), pol).merge(boundW(), pol) }
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
                    }
                }
            }
        }

        val newRecVars =
            recVars
                .filterKeys { it !in varSubst || varSubst[it] != null }
                .mapKeys { (k, _) -> varSubst[k] ?: k }
                .mapValues { (_, thunk) -> thunk() }

        return CompactTypeScheme(termThunk(), newRecVars)
    }

    /**
     * Coalesces a CompactTypeScheme into a DisplayType.
     * Uses hash-consing to tie recursive type knots tighter.
     * Variable names are assigned on first encounter during traversal.
     */
    fun coalesceType(cty: CompactTypeScheme): DisplayType {
        val varNames = mutableMapOf<TVar, String>()

        fun varName(v: TVar): String =
            varNames.getOrPut(v) {
                val idx = varNames.size
                val letter = 'a' + (idx % 26)
                val suffix = if (idx >= 26) "${idx / 26}" else ""
                "$letter$suffix"
            }

        fun go(
            ty: CompactType,
            pol: Boolean,
            inProcess: Map<Pair<CompactType, Boolean>, () -> DisplayType.DVar>,
        ): DisplayType {
            val key = ty to pol
            inProcess[key]?.let { return it() }

            if (ty.isEmpty()) {
                return if (pol) DisplayType.DBottom else DisplayType.DTop
            }

            var isRecursive = false
            val recVar by lazy {
                isRecursive = true
                val v = TVar()
                DisplayType.DVar(varName(v))
            }

            val newInProcess = inProcess + (key to { recVar })

            val components = mutableListOf<DisplayType>()

            // Process in same order as simple-sub: vars, prims, rec, func
            // This ensures variables at the current level are named before
            // descending into nested function types

            // Add variables (non-recursive ones become type vars, recursive ones expand)
            // Process in descending uid order to match simple-sub's variable naming
            for (v in ty.vars.sortedByDescending { it.uid }) {
                val bound = cty.recVars[v]
                if (bound != null) {
                    components.add(go(bound, pol, newInProcess))
                } else {
                    components.add(DisplayType.DVar(varName(v)))
                }
            }

            // Add primitives
            for (prim in ty.prims) {
                components.add(
                    when (prim) {
                        PrimType.Num -> DisplayType.DNum
                        PrimType.String -> DisplayType.DString
                        PrimType.Bool -> DisplayType.DBool
                        PrimType.Unit -> DisplayType.DUnit
                    },
                )
            }

            // Add record
            ty.rec?.let { fields ->
                val displayFields = fields.mapValues { (_, v) -> go(v, pol, newInProcess) }
                components.add(DisplayType.DRecord(displayFields))
            }

            // Add function last
            ty.func?.let { (params, result) ->
                val displayParams = params.map { go(it, !pol, newInProcess) }
                val displayResult = go(result, pol, newInProcess)
                components.add(DisplayType.DFun(displayParams, displayResult))
            }

            val result =
                if (components.isEmpty()) {
                    if (pol) DisplayType.DBottom else DisplayType.DTop
                } else if (components.size == 1) {
                    components[0]
                } else {
                    if (pol) {
                        components.reduce { acc, t -> DisplayType.DUnion(acc, t) }
                    } else {
                        components.reduce { acc, t -> DisplayType.DInter(acc, t) }
                    }
                }

            return if (isRecursive) {
                DisplayType.DRec(recVar.name, result)
            } else {
                result
            }
        }

        return go(cty.term, pol = true, inProcess = emptyMap())
    }
}
