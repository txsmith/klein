package klein.check

import klein.check.Type.*

class Subtyping {

    fun isSubtype(
        lower: Type,
        upper: Type,
        env: TypeEnv,
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

            else -> false
        }
    }

    fun lub(
        a: Type,
        b: Type,
        env: TypeEnv,
        failures: MutableList<Failure>,
    ): Type {
        require(a !is TForall && b !is TForall) { "lub received a polymorphic type: $a, $b" }
        return when {
            a == b -> a
            a is TFun && b is TFun && a.params.size == b.params.size ->
                TFun(a.params.zip(b.params) { pa, pb -> glb(pa, pb, env, failures) }, lub(a.result, b.result, env, failures))
            a is TRecord && b is TRecord ->
                TRecord((a.fields.keys intersect b.fields.keys).associateWith { lub(a.fields.getValue(it), b.fields.getValue(it), env, failures) })
            a is TOptional || b is TOptional || a is TNull || b is TNull ->
                optionalOf(lub(nonNullCore(a), nonNullCore(b), env, failures))
            isSubtype(a, b, env) -> b
            isSubtype(b, a, env) -> a
            else -> {
                failures.add(Failure(a, b)) // no common supertype without unions; widen to Top to recover
                TTop
            }
        }
    }

    fun glb(
        a: Type,
        b: Type,
        env: TypeEnv,
        failures: MutableList<Failure>,
    ): Type {
        require(a !is TForall && b !is TForall) { "glb received a polymorphic type: $a, $b" }
        return when {
            a == b -> a
            a is TFun && b is TFun && a.params.size == b.params.size ->
                TFun(a.params.zip(b.params) { pa, pb -> lub(pa, pb, env, failures) }, glb(a.result, b.result, env, failures))
            a is TRecord && b is TRecord ->
                TRecord((a.fields.keys + b.fields.keys).associateWith { k ->
                    val fa = a.fields[k]
                    val fb = b.fields[k]
                    if (fa != null && fb != null) glb(fa, fb, env, failures) else (fa ?: fb!!)
                })
            a is TOptional && b is TOptional -> optionalOf(glb(a.type, b.type, env, failures))
            isSubtype(a, b, env) -> a
            isSubtype(b, a, env) -> b
            else -> {
                failures.add(Failure(a, b)) // no common subtype without intersections; narrow to Bottom to recover
                TBottom
            }
        }
    }

    private fun nonNullCore(t: Type): Type =
        when (t) {
            is TOptional -> t.type
            TNull -> TBottom
            else -> t
        }

    private fun optionalOf(t: Type): Type =
        when (t) {
            TTop -> TTop
            TBottom -> TNull
            is TOptional -> t
            else -> TOptional(t)
        }
}
