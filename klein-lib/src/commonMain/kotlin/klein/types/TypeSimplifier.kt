package klein.types

import klein.Type
import klein.types.SimpleType.TVar
import klein.types.TypeComponents.PrimType

/**
 * Type simplification following the SimpleSub algorithm.
 *
 * Pipeline:
 *   SimpleType → TypeComponents (canonicalizeType) → simplified TypeComponents → Type
 *
 * Reference: https://lptk.github.io/programming/2020/03/26/demystifying-mlsub.html
 */
private class VarNamer {
    private val varNames = mutableMapOf<TVar, String>()

    fun varName(v: TVar): String =
        varNames.getOrPut(v) {
            val idx = varNames.size
            val letter = 'A' + (idx % 26)
            val suffix = if (idx >= 26) "${idx / 26}" else ""
            "'$letter$suffix"
        }
}

object TypeSimplifier {
    private const val DEBUG = false

    private inline fun debug(msg: () -> String) {
        if (DEBUG) println(msg())
    }

    fun simplify(
        type: SimpleType,
        env: TypeEnv,
        pol: Boolean = true,
        keepVars: Boolean = false,
    ): TypeScheme {
        var typeToSimplify = TypeComponents.canonicalizeType(type, pol, env)
        var iteration = 0
        do {
            iteration++
            debug { "=== Simplification iteration $iteration ===" }
            val (next, changed) = simplifyType(typeToSimplify, keepVars, env)
            typeToSimplify = next
        } while (changed && iteration < 1000)
        return typeToSimplify
    }

    fun simplifyCanonical(
        type: SimpleType,
        env: TypeEnv,
        pol: Boolean = true,
        keepVars: Boolean = false,
    ): Type = coalesceType(simplify(type, env, pol, keepVars), env, keepVars)

    /**
     * Simplifies a TypeScheme by performing co-occurrence analysis.
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
        cty: TypeScheme,
        keepVars: Boolean = false,
        env: TypeEnv? = null,
    ): Pair<TypeScheme, Boolean> {
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
        return TypeScheme(resultType, newRecVars, cty.pol) to changed
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

    private fun collectCoOccurrences(cty: TypeScheme): CoOccurrenceAnalysis {
        val allVars = mutableSetOf<TVar>()
        val recVars = mutableSetOf<TVar>()
        val coOccurrences = mutableMapOf<Pair<Boolean, TVar>, MutableSet<Any>>()

        val functions =
            object {
                fun collect(
                    ty: TypeComponents,
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

                    ty.rec
                        ?.fields
                        ?.values
                        ?.forEach { collect(it, pol) }
                    ty.func?.let { (params, result) ->
                        params.forEach { collect(it, !pol) }
                        collect(result, pol)
                    }
                    ty.optional?.let { collect(it, pol) }
                    ty.refs.forEach { ref ->
                        ref.args.forEach { arg ->
                            when (arg) {
                                is RefArg.Resolved -> {
                                    collect(arg.components, arg.pol)
                                }
                                is RefArg.Invariant -> {
                                    collect(arg.pos, true)
                                    collect(arg.neg, false)
                                }
                            }
                        }
                    }
                }
            }

        functions.collect(cty.term, cty.pol)
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
                            // Since sub-components no longer carry polarity, structural equality
                            // across polarities works by direct comparison.
                            val vOppOccs = analysis.coOccurrences[!pol to v]
                            if (vOppOccs != null && w in vOppOccs) {
                                varSubst[v] = null
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyComponentSubstitutions(
        comp: Component,
        varSubst: Map<TVar, TVar?>,
    ): Component =
        when (comp) {
            is PrimComponent -> comp
            is RecordType -> RecordType(comp.fields.mapValues { (_, v) -> applySubstitutions(v, varSubst) })
            is FunctionType -> FunctionType(
                comp.params.map { applySubstitutions(it, varSubst) },
                applySubstitutions(comp.result, varSubst),
            )
            is OptionalComponent -> OptionalComponent(applySubstitutions(comp.inner, varSubst))
            is RefType -> RefType(comp.name, comp.args.map { arg ->
                when (arg) {
                    is RefArg.Resolved -> RefArg.Resolved(applySubstitutions(arg.components, varSubst), arg.pol)
                    is RefArg.Invariant -> RefArg.Invariant(
                        varSubst[arg.tvar] ?: arg.tvar,
                        applySubstitutions(arg.pos, varSubst),
                        applySubstitutions(arg.neg, varSubst),
                    )
                }
            })
        }

    private fun applySubstitutions(
        ty: TypeComponents,
        varSubst: Map<TVar, TVar?>,
    ): TypeComponents {
        val newVars = ty.vars.mapNotNull { tv -> if (tv !in varSubst) tv else varSubst[tv] }.toSet()

        return TypeComponents(
            vars = newVars,
            prims = ty.prims,
            nullable = ty.nullable,
            rec = ty.rec?.let { RecordType(it.fields.mapValues { (_, v) -> applySubstitutions(v, varSubst) }) },
            func =
                ty.func?.let { (params, result) ->
                    FunctionType(params.map { applySubstitutions(it, varSubst) }, applySubstitutions(result, varSubst))
                },
            optional = ty.optional?.let { applySubstitutions(it, varSubst) },
            refs = ty.refs.map { ref ->
                RefType(ref.name, ref.args.map { arg ->
                    when (arg) {
                        is RefArg.Resolved -> RefArg.Resolved(applySubstitutions(arg.components, varSubst), arg.pol)
                        is RefArg.Invariant -> RefArg.Invariant(
                            varSubst[arg.tvar] ?: arg.tvar,
                            applySubstitutions(arg.pos, varSubst),
                            applySubstitutions(arg.neg, varSubst),
                        )
                    }
                })
            }.toSet(),
            tightBound = ty.tightBound?.let { applyComponentSubstitutions(it, varSubst) },
        )
    }

    /**
     * Coalesces a TypeScheme into a Type.
     * Uses hash-consing to tie recursive type knots tighter.
     * Variable names are assigned on first encounter during traversal.
     */
    fun coalesceType(
        cty: TypeScheme,
        env: TypeEnv,
        keepVars: Boolean = false,
    ): Type {
        val namer = VarNamer()
        fun varName(v: TVar) = namer.varName(v)

        val functions =
            object {
                fun coalesceRefArg(
                    arg: RefArg,
                    pol: Boolean,
                    inProcess: Map<Pair<TypeComponents, Boolean>, () -> Type.Var>,
                ): Type =
                    when (arg) {
                        is RefArg.Resolved -> {
                            go(arg.components, arg.pol, inProcess)
                        }
                        is RefArg.Invariant -> {
                            val tv = arg.tvar
                            val lowerRest = arg.pos.copy(vars = arg.pos.vars - tv)
                            val upperRest = arg.neg.copy(vars = arg.neg.vars - tv)
                            // If both bounds are equal, it's a concrete type — no variable needed
                            if (lowerRest == upperRest && !lowerRest.isEmpty()) {
                                go(lowerRest, true, inProcess)
                            } else {
                                val lowerBound = go(lowerRest, true, inProcess)
                                val upperBound = go(upperRest, false, inProcess)
                                val lowerT = if (lowerBound == Type.Bottom) null else lowerBound
                                val upperT = if (upperBound == Type.Top) null else upperBound
                                Type.Var(varName(tv), lowerT, upperT)
                            }
                        }
                    }

                fun go(
                    ty: TypeComponents,
                    pol: Boolean,
                    inProcess: Map<Pair<TypeComponents, Boolean>, () -> Type.Var>,
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

                    // Process in same order as simple-sub: vars, prims, rec, func, ...
                    for (v in ty.vars.sortedByDescending { it.uid }) {
                        val bound = cty.recVars[v]
                        if (bound != null) {
                            components.add(go(bound, pol, newInProcess))
                        } else {
                            components.add(Type.Var(varName(v)))
                        }
                    }

                    for (prim in ty.prims) {
                        components.add(
                            when (prim) {
                                PrimType.Num -> Type.Num
                                PrimType.String -> Type.Str
                                PrimType.Bool -> Type.Bool
                                PrimType.Unit -> Type.Unit
                            },
                        )
                    }

                    ty.rec?.let { rec ->
                        val typeFields = rec.fields.mapValues { (_, v) -> go(v, pol, newInProcess) }
                        components.add(Type.Record(typeFields))
                    }

                    ty.func?.let { (params, result) ->
                        val typeParams = params.map { go(it, !pol, newInProcess) }
                        val typeResult = go(result, pol, newInProcess)
                        components.add(Type.Fun(typeParams, typeResult))
                    }

                    ty.optional?.let { inner ->
                        val innerType = go(inner, pol, newInProcess)
                        components.add(Type.Optional(innerType))
                    }

                    for (ref in ty.refs) {
                        val coalescedArgs = ref.args.map { arg -> coalesceRefArg(arg, pol, newInProcess) }
                        components.add(Type.Ref(ref.name, coalescedArgs))
                    }

                    val hasOtherComponents = components.isNotEmpty()
                    val wrapInOptional = pol && ty.nullable && hasOtherComponents

                    if (ty.nullable && !hasOtherComponents) {
                        components.add(Type.Null)
                    }

                    val baseResult =
                        if (components.isEmpty()) {
                            if (pol) Type.Bottom else Type.Top
                        } else if (components.size == 1) {
                            components[0]
                        } else {
                            if (pol) {
                                components.reduce { acc, t -> Type.Union(acc, t) }
                            } else {
                                components.reduce { acc, t -> Type.Inter(acc, t) }
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
            }

        val term = cty.term
        return if (term.isEmpty()) {
            if (cty.pol) Type.Bottom else Type.Top
        } else {
            functions.go(term, cty.pol, emptyMap())
        }
    }

    /**
     * Coalesce a TypeScheme into a Type by reading tightBound at every level.
     * This produces the LUB (positive) or GLB (negative) — no unions or intersections.
     * Returns Top/Bottom when tightBound is null (incompatible components).
     */
    fun coalesceLeastUpperBound(
        cty: TypeScheme,
        env: TypeEnv,
    ): Type {
        val namer = VarNamer()
        fun varName(v: TVar) = namer.varName(v)

        val functions =
            object {
                fun goComponents(ty: TypeComponents, pol: Boolean): Type {
                    val parts = mutableListOf<Type>()
                    for (v in ty.vars.sortedByDescending { it.uid }) parts.add(Type.Var(varName(v)))
                    if (ty.tightBound != null) parts.add(goComponent(ty.tightBound, pol))
                    else if (ty.hasConcreteComponents()) parts.add(if (pol) Type.Top else Type.Bottom)

                    val base = when {
                        parts.isNotEmpty() && pol -> parts.reduce { acc, t -> Type.Union(acc, t) }
                        parts.isNotEmpty() -> parts.reduce { acc, t -> Type.Inter(acc, t) }
                        ty.nullable -> Type.Null
                        pol -> Type.Bottom
                        else -> Type.Top
                    }

                    return if (ty.nullable && base != Type.Null) Type.Optional(base) else base
                }

                fun goComponent(
                    comp: Component,
                    pol: Boolean,
                ): Type =
                    when (comp) {
                        is PrimComponent ->
                            when (comp.prim) {
                                PrimType.Num -> Type.Num
                                PrimType.String -> Type.Str
                                PrimType.Bool -> Type.Bool
                                PrimType.Unit -> Type.Unit
                            }

                        is RecordType -> {
                            val typeFields = comp.fields.mapValues { (_, v) -> goComponents(v, pol) }
                            Type.Record(typeFields)
                        }

                        is FunctionType -> {
                            val typeParams = comp.params.map { goComponents(it, !pol) }
                            val typeResult = goComponents(comp.result, pol)
                            Type.Fun(typeParams, typeResult)
                        }

                        is OptionalComponent -> Type.Optional(goComponents(comp.inner, pol))

                        is RefType -> {
                            val coalescedArgs = comp.args.map { arg ->
                                when (arg) {
                                    is RefArg.Resolved -> goComponents(arg.components, arg.pol)
                                    is RefArg.Invariant -> {
                                        val lowerRest = arg.pos.copy(vars = arg.pos.vars - arg.tvar)
                                        val upperRest = arg.neg.copy(vars = arg.neg.vars - arg.tvar)
                                        if (lowerRest == upperRest && !lowerRest.isEmpty()) {
                                            goComponents(lowerRest, true)
                                        } else {
                                            val lowerBound = goComponents(lowerRest, true)
                                            val upperBound = goComponents(upperRest, false)
                                            val lowerT = if (lowerBound == Type.Top) null else lowerBound
                                            val upperT = if (upperBound == Type.Bottom) null else upperBound
                                            Type.Var(varName(arg.tvar), lowerT, upperT)
                                        }
                                    }
                                }
                            }
                            Type.Ref(comp.name, coalescedArgs)
                        }
                    }
            }

        return functions.goComponents(cty.term, cty.pol)
    }
}
