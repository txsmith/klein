package klein.check

import klein.*
import klein.check.Type.*
import klein.types.TypeError
import klein.types.Variance

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
        failures: MutableList<Failure>,
    ): ConstraintSet {
        require(lower !is TForall && upper !is TForall) {
            "generate received a polymorphic type — it must be instantiated at a demand first: $lower <: $upper"
        }
        return when {
            upper is TTop -> emptyMap()
            lower is TBottom -> emptyMap()
            lower is TNull && upper is TOptional -> emptyMap()

            lower is TSkolem && unknowns.contains(lower) -> mapOf(lower to ConstraintInterval.upper(eliminateViaDemotion(upper, quantified)))
            upper is TSkolem && unknowns.contains(upper) -> mapOf(upper to ConstraintInterval.lower(eliminateViaPromotion(lower, quantified)))

            lower is TOptional && upper is TOptional -> generate(quantified, unknowns, lower.type, upper.type, env, failures)
            upper is TOptional -> generate(quantified, unknowns, lower, upper.type, env, failures)

            lower is TFun && upper is TFun && lower.params.size == upper.params.size ->
                mergeAll(
                    lower.params.indices.map { i -> { generate(quantified, unknowns, upper.params[i], lower.params[i], env, failures) } } +
                        { generate(quantified, unknowns, lower.result, upper.result, env, failures) },
                    env,
                    failures,
                )

            lower is TRecord && upper is TRecord ->
                mergeAll(
                    upper.fields.map { (name, u) ->
                        {
                            val l = lower.fields[name]
                            if (l == null) {
                                failures.add(Failure(lower, upper))
                                emptyMap()
                            } else {
                                generate(quantified, unknowns, l, u, env, failures)
                            }
                        }
                    },
                    env,
                    failures,
                )

            else ->
                if (subtyping.isSubtype(lower, upper, env)) {
                    emptyMap()
                } else {
                    failures.add(Failure(lower, upper))
                    emptyMap()
                }
        }
    }

    private fun mergeInterval(
        a: ConstraintInterval,
        b: ConstraintInterval,
        env: TypeEnv,
        failures: MutableList<Failure>,
    ): ConstraintInterval =
        ConstraintInterval(
            lowerBound = subtyping.lub(a.lowerBound, b.lowerBound, env, failures),
            upperBound = subtyping.glb(a.upperBound, b.upperBound, env, failures),
        )

    private fun merge(
        a: ConstraintSet,
        b: ConstraintSet,
        env: TypeEnv,
        failures: MutableList<Failure>,
    ): ConstraintSet {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return (a.keys + b.keys).associateWith { name ->
            val constraintA = a[name]
            val constraintB = b[name]
            if (constraintA != null && constraintB != null) {
                mergeInterval(constraintA, constraintB, env, failures)
            } else {
                constraintA ?: constraintB!!
            }
        }
    }

    private fun mergeAll(
        parts: List<() -> ConstraintSet>,
        env: TypeEnv,
        failures: MutableList<Failure>,
    ): ConstraintSet {
        var acc: ConstraintSet = emptyMap()
        for (part in parts) {
            acc = merge(acc, part(), env, failures)
        }
        return acc
    }

    fun solve(
        constraints: ConstraintSet,
        unknowns: Set<TSkolem>,
        result: Type,
        env: TypeEnv,
        failures: MutableList<Failure>,
    ): Map<TSkolem, Type> {
        val subst = mutableMapOf<TSkolem, Type>()
        for (x in unknowns) {
            val interval = constraints[x] ?: ConstraintInterval(TBottom, TTop)
            val (lo, hi) = interval
            if (!subtyping.isSubtype(lo, hi, env)) {
                failures += Failure(lo, hi)
                continue
            }
            subst[x] =
                when (varianceOf(x, result)) {
                    Variance.Bivariant, Variance.Covariant -> lo
                    Variance.Contravariant -> hi
                    Variance.Invariant ->
                        if (lo == hi) {
                            lo
                        } else {
                            failures += Failure(lo, hi)
                            continue
                        }
                }
        }
        return subst
    }

    /**
     * Instantiate [type] so its body is a subtype of [demand], choosing the substitution that
     * minimizes [target]. Returns the instantiated body plus any constraint failures — no exception
     * escapes; the caller records the failures.
     */
    fun solveQuantified(
        type: TForall,
        demand: Type,
        target: Type,
        env: TypeEnv,
    ): Solved {
        val failures = mutableListOf<Failure>()
        val constraints = generate(emptySet(), type.params, type.body, demand, env, failures)
        val subst = solve(constraints, type.params, target, env, failures)
        return Solved(applySubst(type.body, subst), failures)
    }

    /** Substitute solved [subst] into [type]; quantifiers shadow their own variables. */
    fun applySubst(
        type: Type,
        subst: Map<TSkolem, Type>,
    ): Type =
        when (type) {
            is TSkolem -> subst[type] ?: type
            is TFun -> TFun(type.params.map { applySubst(it, subst) }, applySubst(type.result, subst), type.paramNames)
            is TRecord -> TRecord(type.fields.mapValues { applySubst(it.value, subst) })
            is TOptional -> TOptional(applySubst(type.type, subst))
            is TRef -> TRef(type.name, type.typeArgs.map { applySubst(it, subst) })
            is TForall -> TForall(type.params, applySubst(type.body, subst - type.params))
            TNum, TStr, TBool, TUnit, TNull, TTop, TBottom -> type
        }

    /** Where [x] occurs in [type]: nowhere (constant), only positively (covariant), only negatively, or both. */
    private fun varianceOf(
        x: TSkolem,
        type: Type,
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
