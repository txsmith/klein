package klein.check.contract

import klein.Revision
import klein.check.Type
import klein.check.Type.*
import klein.check.TypeEnv

/**
 * `contracts.md` §"No functions cross the boundary": a capability may not carry a Klein function,
 * not as a parameter, not as a result, and not nested inside a type it mentions.
 *
 * A Klein function's only meaning is that the interpreter can run it, and whatever answers a
 * capability is not the interpreter. The restriction was always contract-only, which is why it
 * lives here rather than in the program checker.
 */
fun carriesFunctionType(
    bound: Type,
    env: TypeEnv,
    isCallable: Boolean,
): Boolean {
    val body = if (bound is TForall) bound.body else bound
    return if (isCallable && body is TFun) {
        body.params.any { carriesFunction(it, env, mutableSetOf()) } ||
            carriesFunction(body.result, env, mutableSetOf())
    } else {
        carriesFunction(body, env, mutableSetOf())
    }
}

/** Follows type references into their constructors' fields, so hiding a function one level down
 *  does not evade the check; the `(name, revision)` visited set terminates recursive types. */
private fun carriesFunction(
    type: Type,
    env: TypeEnv,
    seen: MutableSet<Pair<String, Revision>>,
): Boolean =
    when (type) {
        is TFun -> true
        is TOptional -> carriesFunction(type.type, env, seen)
        is TRecord -> type.fields.values.any { carriesFunction(it, env, seen) }
        is TForall -> carriesFunction(type.body, env, seen)
        is TRef ->
            if (!seen.add(type.name to type.revision)) {
                false
            } else {
                type.typeArgs.any { carriesFunction(it, env, seen) } ||
                    env.lookupTypeDef(type.name, type.revision)?.iface?.fields?.values.orEmpty().any {
                        carriesFunction(it, env, seen)
                    }
            }
        else -> false
    }
