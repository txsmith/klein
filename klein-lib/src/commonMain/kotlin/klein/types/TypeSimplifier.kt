package klein.types

import klein.Type
import klein.types.SimpleType.TSkolem
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
    private val usedNames = mutableSetOf<String>()
    private var nextIdx = 0

    fun varName(v: TVar): String =
        varNames.getOrPut(v) {
            val name = v.nameHint?.let { hint -> uniquify("'$hint") } ?: nextGenericName()
            usedNames.add(name)
            name
        }

    private fun uniquify(candidate: String): String {
        if (candidate !in usedNames) return candidate
        var n = 1
        while ("$candidate$n" in usedNames) n++
        return "$candidate$n"
    }

    private fun nextGenericName(): String {
        while (true) {
            val letter = 'A' + (nextIdx % 26)
            val suffix = if (nextIdx >= 26) "${nextIdx / 26}" else ""
            val name = "'$letter$suffix"
            nextIdx++
            if (name !in usedNames) return name
        }
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

        val ops =
            object {
                fun collect(
                    ty: TypeComponents,
                    pol: Boolean,
                ) {
                    for (tv in ty.vars) {
                        allVars.add(tv)
                        val newOccs = mutableSetOf<Any>()
                        newOccs.addAll(ty.vars)
                        newOccs.addAll(ty.skolems)
                        newOccs.addAll(ty.prims)
                        ty.rec?.let { newOccs.add(it) }
                        ty.func?.let { newOccs.add(it) }
                        newOccs.addAll(ty.allRefs())

                        val existing = coOccurrences[pol to tv]
                        if (existing != null) {
                            existing.retainAll(newOccs)
                        } else {
                            coOccurrences[pol to tv] = newOccs
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
                    ty.displayRefs().forEach { ref ->
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

        ops.collect(cty.term, cty.pol)
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
                        is PrimType, is TSkolem -> {
                            // Primitives and skolems have no internal structure, so direct comparison works
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

    private fun applyRefArgSubstitutions(
        arg: RefArg,
        varSubst: Map<TVar, TVar?>,
    ): RefArg =
        when (arg) {
            is RefArg.Resolved -> RefArg.Resolved(applySubstitutions(arg.components, varSubst), arg.pol)
            is RefArg.Invariant ->
                RefArg.Invariant(
                    varSubst[arg.tvar] ?: arg.tvar,
                    applySubstitutions(arg.pos, varSubst),
                    applySubstitutions(arg.neg, varSubst),
                )
        }

    private fun applySubstitutions(
        ty: TypeComponents,
        varSubst: Map<TVar, TVar?>,
    ): TypeComponents {
        val newVars = ty.vars.mapNotNull { tv -> if (tv !in varSubst) tv else varSubst[tv] }.toSet()

        return TypeComponents(
            vars = newVars,
            skolems = ty.skolems,
            prims = ty.prims,
            nullable = ty.nullable,
            rec = ty.rec?.let { RecordType(it.fields.mapValues { (_, v) -> applySubstitutions(v, varSubst) }) },
            func =
                ty.func?.let { (params, result) ->
                    FunctionType(params.map { applySubstitutions(it, varSubst) }, applySubstitutions(result, varSubst))
                },
            optional = ty.optional?.let { applySubstitutions(it, varSubst) },
            refs =
                ty.refs.mapValues { (_, family) ->
                    fun applyToRef(ref: RefType) = RefType(ref.name, ref.args.map { applyRefArgSubstitutions(it, varSubst) })
                    when {
                        family.parent != null -> RefFamily(parent = applyToRef(family.parent), family.constructors.map { applyToRef(it) })
                        else -> RefFamily(parent = null, family.constructors.map { applyToRef(it) })
                    }
                },
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

        val ops =
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
                            // If both bounds are semantically equal, collapse to the type directly
                            if (arg.hasEqualBounds()) {
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

                    for (sk in ty.skolems.sortedBy { it.uid }) {
                        components.add(Type.Var("'${sk.name}"))
                    }

                    for (prim in ty.prims) {
                        components.add(
                            when (prim) {
                                PrimType.Num -> Type.Num
                                PrimType.String -> Type.Str
                                PrimType.Bool -> Type.Bool
                                PrimType.Unit -> Type.Unit
                                PrimType.Top -> Type.Top
                                PrimType.Bottom -> Type.Bottom
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

                    for (ref in ty.displayRefs()) {
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
            ops.go(term, cty.pol, emptyMap())
        }
    }
}
