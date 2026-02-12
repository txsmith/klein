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
        return coalesceType(simplified, env, keepVars, pol)
    }

    /**
     * Simplifies a CompactTypeScheme by performing co-occurrence analysis.
     *
     * Two simplifications:
     * 1. Variables that only occur in one polarity can be eliminated
     *    (positive-only → Bottom, negative-only → Top)
     * 2. Variables that always co-occur at the same polarity can be unified
     */
    fun simplifyType(
        cty: CompactTypeScheme,
        keepVars: Boolean = false,
        env: TypeEnv? = null,
    ): CompactTypeScheme {
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

            fun analyzeBounded(b: BoundedCompactType): () -> BoundedCompactType {
                val lowerThunk = analyze(b.lower, true)
                val upperThunk = analyze(b.upper, false)
                return { BoundedCompactType(lower = lowerThunk(), upper = upperThunk()) }
            }

            val recThunks = ty.rec?.mapValues { (_, v) -> analyzeBounded(v) }
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
                    rec = recThunks?.mapValues { (_, thunk) -> thunk() },
                    func =
                        funThunks?.let { (paramThunks, resultThunk) ->
                            paramThunks.map { it() } to resultThunk()
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

            val components = mutableListOf<Type>()

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

            fun coalesceBounded(
                b: BoundedCompactType,
                composedPol: Boolean,
            ): Type =
                when {
                    !b.lower.isEmpty() -> go(b.lower, true, newInProcess)
                    !b.upper.isEmpty() -> go(b.upper, false, newInProcess)
                    else -> if (composedPol) Type.Bottom else Type.Top
                }

            // Add record
            ty.rec?.let { fields ->
                val typeFields = fields.mapValues { (_, v) -> coalesceBounded(v, pol) }
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

            // Add refs with where clauses for invariant type args
            for (ref in ty.refs) {
                val whereClauses = mutableListOf<Type.WhereClause>()
                val coalescedArgs =
                    ref.args.mapIndexed { i, arg ->
                        val lowerEmpty = arg.lower.isEmpty()
                        val upperEmpty = arg.upper.isEmpty()
                        when {
                            !lowerEmpty && !upperEmpty -> {
                                val lowerType = go(arg.lower, true, newInProcess)
                                val upperType = go(arg.upper, false, newInProcess)
                                if (lowerType == upperType) {
                                    lowerType
                                } else {
                                    val v = TVar()
                                    val vName = varName(v)
                                    whereClauses.add(Type.WhereClause(vName, lowerType, upperType))
                                    Type.Var(vName)
                                }
                            }
                            !lowerEmpty -> go(arg.lower, true, newInProcess)
                            !upperEmpty -> go(arg.upper, false, newInProcess)
                            else -> Type.Top
                        }
                    }
                components.add(Type.Ref(ref.name, coalescedArgs, whereClauses))
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
