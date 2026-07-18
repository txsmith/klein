package klein.check

import klein.*
import klein.check.Type.*

data class ConstraintInterval(
    val lowerBound: Type,
    val upperBound: Type,
) {
    companion object {
        fun lower(type: Type): ConstraintInterval = ConstraintInterval(type, TTop)

        fun upper(type: Type): ConstraintInterval = ConstraintInterval(TBottom, type)
    }
}

typealias ConstraintSet = Map<TSkolem, ConstraintInterval>

data class Failure(
    val lower: Type,
    val upper: Type,
)

/** A solver result: the produced [type], plus any constraint [errors] (empty on success). */
data class Solved(
    val type: Type,
    val errors: List<Failure>,
)

/** Which end of each variable's interval to pick against the target: [Minimize] it (most precise
 *  result) or [Maximize] it (ground to the demand's bound). */
enum class Objective { Minimize, Maximize }

class ConstraintGenerator(
    private val subtyping: Subtyping,
) {
    private fun eliminateViaPromotion(
        type: Type,
        quantified: Set<TSkolem>,
    ): Type = type

    private fun eliminateViaDemotion(
        type: Type,
        quantified: Set<TSkolem>,
    ): Type = type

    fun generate(
        quantified: Set<TSkolem>,
        unknowns: Set<TSkolem>,
        lower: Type,
        upper: Type,
        env: TypeEnv,
    ): Pair<ConstraintSet, List<Failure>> {
        require(lower !is TForall && upper !is TForall) {
            "generate received a polymorphic type — it must be instantiated at a demand first: $lower <: $upper"
        }
        return when {
            upper is TTop -> emptyConstraints()
            lower is TBottom -> emptyConstraints()
            lower is TNull && upper is TOptional -> emptyConstraints()

            lower is TSkolem && unknowns.contains(lower) ->
                mapOf(lower to ConstraintInterval.upper(eliminateViaDemotion(upper, quantified))) to emptyList()
            upper is TSkolem && unknowns.contains(upper) ->
                mapOf(upper to ConstraintInterval.lower(eliminateViaPromotion(lower, quantified))) to emptyList()

            lower is TOptional && upper is TOptional -> generate(quantified, unknowns, lower.type, upper.type, env)
            upper is TOptional -> generate(quantified, unknowns, lower, upper.type, env)

            lower is TFun && upper is TFun && lower.params.size == upper.params.size ->
                mergeAll(
                    lower.params.indices.map { i -> generate(quantified, unknowns, upper.params[i], lower.params[i], env) } +
                        generate(quantified, unknowns, lower.result, upper.result, env),
                    env,
                )

            lower is TRecord && upper is TRecord ->
                mergeAll(
                    upper.fields.map { (name, u) ->
                        val l = lower.fields[name]
                        if (l == null) {
                            emptyMap<TSkolem, ConstraintInterval>() to listOf(Failure(lower, upper))
                        } else {
                            generate(quantified, unknowns, l, u, env)
                        }
                    },
                    env,
                )

            lower is TRef && upper is TRef -> {
                val lowerDef = env.lookupTypeDef(lower.name)
                val upperDef = env.lookupTypeDef(upper.name)
                val related =
                    lower.name == upper.name || env.lookupConstructor(lower.name)?.parentType == upper.name
                if (lowerDef == null || upperDef == null || !related) {
                    emptyMap<TSkolem, ConstraintInterval>() to listOf(Failure(lower, upper))
                } else {
                    val upperApplied =
                        upperDef.typeParams.zip(upper.typeArgs).associate { (p, arg) -> p.skolem.name to (p.variance to arg) }
                    val parts =
                        lowerDef.typeParams.map { it.skolem.name }.zip(lower.typeArgs).mapNotNull { (name, lowerArg) ->
                            val (variance, upperArg) = upperApplied[name] ?: return@mapNotNull null
                            when (variance) {
                                Variance.Covariant -> generate(quantified, unknowns, lowerArg, upperArg, env)
                                Variance.Contravariant -> generate(quantified, unknowns, upperArg, lowerArg, env)
                                Variance.Invariant, Variance.Bivariant -> {
                                    val (down, downFailures) = generate(quantified, unknowns, lowerArg, upperArg, env)
                                    val (up, upFailures) = generate(quantified, unknowns, upperArg, lowerArg, env)
                                    val (merged, mergeFailures) = merge(down, up, env)
                                    merged to (downFailures + upFailures + mergeFailures)
                                }
                            }
                        }
                    mergeAll(parts, env)
                }
            }

            // A nominal lower bound meets a structural record: unfold to its interface and match
            // structurally, so a type variable in the record can be solved from a nominal argument.
            lower is TRef && upper is TRecord && env.lookupTypeDef(lower.name) != null ->
                generate(quantified, unknowns, subtyping.ifaceOf(lower, env), upper, env)

            else ->
                if (subtyping.isSubtype(lower, upper, env)) {
                    emptyConstraints()
                } else {
                    emptyMap<TSkolem, ConstraintInterval>() to listOf(Failure(lower, upper))
                }
        }
    }

    private fun emptyConstraints(): Pair<ConstraintSet, List<Failure>> = emptyMap<TSkolem, ConstraintInterval>() to emptyList()

    private fun mergeInterval(
        a: ConstraintInterval,
        b: ConstraintInterval,
        env: TypeEnv,
    ): Pair<ConstraintInterval, List<Failure>> {
        val (lowerBound, lowerFailures) = subtyping.lub(a.lowerBound, b.lowerBound, env)
        val (upperBound, upperFailures) = subtyping.glb(a.upperBound, b.upperBound, env)
        return ConstraintInterval(lowerBound, upperBound) to (lowerFailures + upperFailures)
    }

    private fun merge(
        a: ConstraintSet,
        b: ConstraintSet,
        env: TypeEnv,
    ): Pair<ConstraintSet, List<Failure>> {
        if (a.isEmpty()) return b to emptyList()
        if (b.isEmpty()) return a to emptyList()
        val merged =
            (a.keys + b.keys).associateWith { name ->
                val constraintA = a[name]
                val constraintB = b[name]
                if (constraintA != null && constraintB != null) {
                    mergeInterval(constraintA, constraintB, env)
                } else {
                    (constraintA ?: constraintB!!) to emptyList()
                }
            }
        return merged.mapValues { it.value.first } to merged.values.flatMap { it.second }
    }

    private fun mergeAll(
        parts: List<Pair<ConstraintSet, List<Failure>>>,
        env: TypeEnv,
    ): Pair<ConstraintSet, List<Failure>> =
        parts.fold(emptyConstraints()) { (accConstraints, accFailures), (constraints, partFailures) ->
            val (merged, mergeFailures) = merge(accConstraints, constraints, env)
            merged to (accFailures + partFailures + mergeFailures)
        }

    fun solve(
        constraints: ConstraintSet,
        unknowns: Set<TSkolem>,
        target: Type,
        objective: Objective,
        env: TypeEnv,
    ): Pair<Map<TSkolem, Type>, List<Failure>> {
        val subst = mutableMapOf<TSkolem, Type>()
        val failures = mutableListOf<Failure>()
        for (x in unknowns) {
            val interval = constraints[x] ?: ConstraintInterval(TBottom, TTop)
            val (lo, hi) = interval
            if (!subtyping.isSubtype(lo, hi, env)) {
                failures += Failure(lo, hi)
                continue
            }
            subst[x] =
                when (varianceOf(x, target, env)) {
                    Variance.Bivariant, Variance.Covariant -> if (objective == Objective.Minimize) lo else hi
                    Variance.Contravariant -> if (objective == Objective.Minimize) hi else lo
                    Variance.Invariant ->
                        if (lo == hi) {
                            lo
                        } else {
                            failures += Failure(lo, hi)
                            continue
                        }
                }
        }
        return subst to failures
    }

    /**
     * Instantiate [lowerBound] so its body is a subtype of [upperBound], choosing the substitution that
     * minimizes [target]. Returns the instantiated body plus any constraint failures — no exception
     * escapes; the caller records the failures.
     */
    fun solveQuantified(
        lowerBound: TForall,
        upperBound: Type,
        target: Type,
        env: TypeEnv,
    ): Solved {
        val (constraints, genFailures) = generate(emptySet(), lowerBound.params, lowerBound.body, upperBound, env)
        val (subst, solveFailures) = solve(constraints, lowerBound.params, target, Objective.Minimize, env)
        return Solved(applySubst(lowerBound.body, subst), genFailures + solveFailures)
    }

    /**
     * Partially solve the parameters that the result [demand] determines, maximizing [result] toward
     * the demand's bound so arguments can then be checked against concrete parameter types. Leaves the
     * demand-undetermined parameters unsolved for the argument phase ([solveQuantified]).
     */
    fun solveFromResult(
        unknowns: Set<TSkolem>,
        result: Type,
        demand: Type,
        env: TypeEnv,
    ): Pair<Map<TSkolem, Type>, List<Failure>> {
        val (constraints, genFailures) = generate(emptySet(), unknowns, result, demand, env)
        val (subst, solveFailures) = solve(constraints, constraints.keys, result, Objective.Maximize, env)
        val determined = subst.filterValues { it != TBottom && it != TTop }
        return determined to (genFailures + solveFailures)
    }

    /** Substitute solved [subst] into [type]; quantifiers shadow their own variables. */
    fun applySubst(
        type: Type,
        subst: Map<TSkolem, Type>,
    ): Type = substitute(type, subst)

    /** Where [x] occurs in [type]: nowhere (constant), only positively (covariant), only negatively, or both. */
    private fun varianceOf(
        x: TSkolem,
        type: Type,
        env: TypeEnv,
    ): Variance {
        val polarities = mutableSetOf<Boolean>()
        fun walk(
            t: Type,
            positive: Boolean,
        ) {
            when (t) {
                is TSkolem -> if (t == x) polarities += positive
                is TFun -> {
                    t.params.forEach { walk(it, !positive) } // params flip
                    walk(t.result, positive)
                }
                is TRecord -> t.fields.values.forEach { walk(it, positive) }
                is TOptional -> walk(t.type, positive)
                is TRef -> {
                    val def = env.lookupTypeDef(t.name)
                    t.typeArgs.forEachIndexed { i, arg ->
                        when (def?.typeParams?.getOrNull(i)?.variance) {
                            Variance.Covariant -> walk(arg, positive)
                            Variance.Contravariant -> walk(arg, !positive)
                            Variance.Invariant -> {
                                walk(arg, positive)
                                walk(arg, !positive)
                            }
                            Variance.Bivariant, null -> {}
                        }
                    }
                }
                else -> {}
            }
        }
        walk(type, true)
        return when {
            polarities.isEmpty() -> Variance.Bivariant
            polarities == setOf(true) -> Variance.Covariant
            polarities == setOf(false) -> Variance.Contravariant
            else -> Variance.Invariant
        }
    }
}
