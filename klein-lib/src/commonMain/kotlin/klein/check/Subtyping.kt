package klein.check

import klein.Revision
import klein.check.Type.*

internal class Subtyping {

    fun <R : Revision?> isSubtype(
        lower: Type<R>,
        upper: Type<R>,
        env: TypeEnv<R>,
    ): Boolean {
        require(lower !is TForall && upper !is TForall) {
            "isSubtype received a polymorphic type — it must be instantiated at a demand first: $lower <: $upper"
        }
        if (lower == upper) return true
        return when {
            upper is TTop -> true
            lower is TBottom -> true

            lower is TFun && upper is TFun ->
                lower.params.size == upper.params.size &&
                    upper.params.indices.all { isSubtype(upper.params[it], lower.params[it], env) } &&
                    isSubtype(lower.result, upper.result, env)

            lower is TRecord && upper is TRecord ->
                upper.fields.all { (name, want) ->
                    val have = lower.fields[name]
                    have != null && isSubtype(have, want, env)
                }

            upper is TOptional ->
                lower is TNull || // Null <: T?
                    (lower is TOptional && isSubtype(lower.type, upper.type, env)) || // S? <: T?
                    isSubtype(lower, upper.type, env) // S <: T?  (when S <: T)

            lower is TRef && upper is TRecord -> {
                val def = env.lookupTypeDef(lower.name, lower.revision) ?: return false
                val subst = def.typeParams.map { it.skolem }.zip(lower.typeArgs).toMap()
                isSubtype(substitute<R>(def.iface, subst), upper, env)
            }

            lower is TRef && upper is TRef -> {
                val lowerDef = env.lookupTypeDef(lower.name, lower.revision) ?: return false
                val upperDef = env.lookupTypeDef(upper.name, upper.revision) ?: return false
                val related = lower.revision == upper.revision &&
                    (
                        lower.name == upper.name ||
                            env.lookupConstructor(lower.name, lower.revision)?.parentType == upper.name
                    )
                if (!related) return false
                val lowerApplied = lowerDef.typeParams.map { it.skolem.name }.zip(lower.typeArgs).toMap()
                val upperApplied = upperDef.typeParams.zip(upper.typeArgs).associate { (p, arg) -> p.skolem.name to (p.variance to arg) }
                lowerApplied.all { (name, lowerArg) ->
                    val (variance, upperArg) = upperApplied[name] ?: return@all true
                    when (variance) {
                        Variance.Covariant -> isSubtype(lowerArg, upperArg, env)
                        Variance.Contravariant -> isSubtype(upperArg, lowerArg, env)
                        Variance.Invariant, Variance.Bivariant ->
                            isSubtype(lowerArg, upperArg, env) && isSubtype(upperArg, lowerArg, env)
                    }
                }
            }

            else -> false
        }
    }

    fun <R : Revision?> lub(
        a: Type<R>,
        b: Type<R>,
        env: TypeEnv<R>,
    ): Pair<Type<R>, List<Failure>> {
        require(a !is TForall && b !is TForall) { "lub received a polymorphic type: $a, $b" }
        val (result, failures) = when {
            a == b -> a to emptyList()
            a is TFun && b is TFun && a.params.size == b.params.size -> {
                val params = a.params.zip(b.params) { pa, pb -> glb(pa, pb, env) }
                val (result, resultFailures) = lub(a.result, b.result, env)
                TFun(params.map { it.first }, result) to (params.flatMap { it.second } + resultFailures)
            }
            a is TRecord && b is TRecord -> {
                val fields = (a.fields.keys intersect b.fields.keys).associateWith { lub(a.fields.getValue(it), b.fields.getValue(it), env) }
                recordOf(fields.mapValues { it.value.first }) to fields.values.flatMap { it.second }
            }
            a is TOptional || b is TOptional || a is TNull || b is TNull -> {
                val (core, coreFailures) = lub(nonNullCore(a), nonNullCore(b), env)
                optionalOf(core) to coreFailures
            }
            isSubtype(a, b, env) -> b to emptyList()
            isSubtype(b, a, env) -> a to emptyList()
            a is TRef || b is TRef -> lubNominal(a, b, env)
            else -> TTop to listOf(Failure(a, b))
        }
        return if (failures.isEmpty() && result is TTop) {
            TTop to listOf(Failure(a, b))
        } else {
            result to failures
        }
    }

    fun <R : Revision?> glb(
        a: Type<R>,
        b: Type<R>,
        env: TypeEnv<R>,
    ): Pair<Type<R>, List<Failure>> {
        require(a !is TForall && b !is TForall) { "glb received a polymorphic type: $a, $b" }
        return when {
            a == b -> a to emptyList()
            a is TFun && b is TFun && a.params.size == b.params.size -> {
                val params = a.params.zip(b.params) { pa, pb -> lub(pa, pb, env) }
                val (result, resultFailures) = glb(a.result, b.result, env)
                TFun(params.map { it.first }, result) to (params.flatMap { it.second } + resultFailures)
            }
            a is TRecord && b is TRecord -> {
                val fields =
                    (a.fields.keys + b.fields.keys).associateWith { k ->
                        val fa = a.fields[k]
                        val fb = b.fields[k]
                        if (fa != null && fb != null) glb(fa, fb, env) else (fa ?: fb!!) to emptyList()
                    }
                recordOf(fields.mapValues { it.value.first }) to fields.values.flatMap { it.second }
            }
            a is TOptional && b is TOptional -> {
                val (core, coreFailures) = glb(a.type, b.type, env)
                optionalOf(core) to coreFailures
            }
            isSubtype(a, b, env) -> a to emptyList()
            isSubtype(b, a, env) -> b to emptyList()
            a is TRef && b is TRef && a.name == b.name && a.revision == b.revision -> mergeArgs(a, b, join = false, env)
            else -> TBottom to listOf(Failure(a, b))
        }
    }

    private fun <R : Revision?> lubNominal(
        a: Type<R>,
        b: Type<R>,
        env: TypeEnv<R>,
    ): Pair<Type<R>, List<Failure>> {
        if (a is TRef && b is TRef) {
            if (a.name == b.name && a.revision == b.revision) return mergeArgs(a, b, join = true, env)
            val parentA = env.lookupConstructor(a.name, a.revision)?.parentType ?: a.name
            val parentB = env.lookupConstructor(b.name, b.revision)?.parentType ?: b.name
            if (parentA == parentB && a.revision == b.revision) {
                return lub(promoteToParent(a, env), promoteToParent(b, env), env)
            }
        }
        val unfoldedA = if (a is TRef) ifaceOf(a, env) else a
        val unfoldedB = if (b is TRef) ifaceOf(b, env) else b
        return lub(unfoldedA, unfoldedB, env)
    }

    private fun <R : Revision?> mergeArgs(
        a: TRef<R>,
        b: TRef<R>,
        join: Boolean,
        env: TypeEnv<R>,
    ): Pair<Type<R>, List<Failure>> {
        val def = env.getTypeDef(a.name, a.revision)
        val merged =
            def.typeParams.mapIndexed { i, param ->
                val argA = a.typeArgs[i]
                val argB = b.typeArgs[i]
                when (param.variance) {
                    Variance.Covariant, Variance.Bivariant -> if (join) lub(argA, argB, env) else glb(argA, argB, env)
                    Variance.Contravariant -> if (join) glb(argA, argB, env) else lub(argA, argB, env)
                    Variance.Invariant -> if (argA == argB) argA to emptyList() else argA to listOf(Failure(a, b))
                }
            }
        return TRef(a.name, merged.map { it.first }, a.revision) to merged.flatMap { it.second }
    }

    private fun <R : Revision?> promoteToParent(
        ref: TRef<R>,
        env: TypeEnv<R>,
    ): TRef<R> {
        val ctor = env.lookupConstructor(ref.name, ref.revision)
        if (ctor == null) {
            env.getTypeDef(ref.name, ref.revision)
            return ref
        }
        val parent = env.getTypeDef(ctor.parentType, ref.revision)
        val argByName = ctor.typeParams.zip(ref.typeArgs).toMap()
        val parentArgs =
            parent.typeParams.map { p ->
                argByName[p.skolem.name] ?: if (p.variance == Variance.Contravariant) TTop else TBottom
            }
        return TRef(ctor.parentType, parentArgs, ref.revision)
    }

    internal fun <R : Revision?> ifaceOf(
        ref: TRef<R>,
        env: TypeEnv<R>,
    ): Type<R> {
        val def = env.getTypeDef(ref.name, ref.revision)
        val subst = def.typeParams.map { it.skolem }.zip(ref.typeArgs).toMap()
        return recordOf((substitute<R>(def.iface, subst) as TRecord).fields)
    }

}
