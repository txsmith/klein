package klein.check.contract

import klein.check.ContractEnv
import klein.check.ContractType
import klein.check.Type.*

/**
 * `contracts.md` §"No functions cross the boundary": a capability may not carry a Klein function,
 * not as a parameter, not as a result, and not nested inside a type it mentions.
 *
 * A Klein function's only meaning is that the interpreter can run it, and whatever answers a
 * capability is not the interpreter. The restriction was always contract-only, which is why it
 * lives here rather than in the program checker — and why this reads in [ContractType] rather than
 * being polymorphic in the regime: a rule's types never reach it.
 */
fun carriesFunctionType(
    bound: ContractType,
    env: ContractEnv,
    isCallable: Boolean,
): Boolean {
    val body = if (bound is TForall) bound.body else bound
    return if (isCallable && body is TFun) {
        body.params.any { carriesFunction(it, env) } || carriesFunction(body.result, env)
    } else {
        carriesFunction(body, env)
    }
}

/** [referencedTypes] does the following; this is the predicate over what it reaches, so hiding a
 *  function one level down does not evade the check. */
private fun carriesFunction(
    type: ContractType,
    env: ContractEnv,
): Boolean =
    type.holdsAFunction() ||
        type.referencedTypes(env).any { (name, revision) -> env.membersOf(name, revision).any { it.holdsAFunction() } }

/** A function in this type's own structure. Type references are not followed here — that is
 *  [referencedTypes]'s job, and following them twice is what would fail to terminate. */
private fun ContractType.holdsAFunction(): Boolean =
    when (this) {
        is TFun -> true
        is TOptional -> type.holdsAFunction()
        is TRecord -> fields.values.any { it.holdsAFunction() }
        is TForall -> body.holdsAFunction()
        is TRef -> typeArgs.any { it.holdsAFunction() }
        else -> false
    }
