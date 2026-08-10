package klein.check.contract

import klein.Revision
import klein.check.ContractEnv
import klein.check.ContractType
import klein.check.Type.TForall
import klein.check.Type.TFun
import klein.check.Type.TOptional
import klein.check.Type.TRecord
import klein.check.Type.TRef

/**
 * Following type references, which two checks need: no functions cross the boundary, and a release
 * is self-contained.
 *
 * Contract-side by construction — a `(name, revision)` answer is only meaningful where revisions
 * exist, and the witness says so in the signature rather than in a comment.
 */

/**
 * Every `(name, revision)` this type reaches, transitively: through type arguments, a type's
 * interface, and the fields of the constructors that travel with it. The visited set is what
 * terminates a recursive type.
 */
internal fun ContractType.referencedTypes(env: ContractEnv): Set<Pair<String, Revision>> {
    val seen = mutableSetOf<Pair<String, Revision>>()

    fun walk(type: ContractType) {
        when (type) {
            is TFun -> {
                type.params.forEach(::walk)
                walk(type.result)
            }
            is TOptional -> walk(type.type)
            is TRecord -> type.fields.values.forEach(::walk)
            is TForall -> walk(type.body)
            is TRef -> {
                // Arguments are walked whatever the visited set says: `Box<Order/2>` and `Box<Foo/3>`
                // are one entry under the same key but two references to follow.
                type.typeArgs.forEach(::walk)
                if (seen.add(type.name to type.revision)) env.membersOf(type.name, type.revision).forEach(::walk)
            }
            else -> {}
        }
    }

    walk(this)
    return seen
}

/** What a rule holding a `(name, revision)` can read out of it: the type's interface, and the
 *  fields of every constructor that travels with it. */
internal fun ContractEnv.membersOf(
    name: String,
    revision: Revision,
): List<ContractType> =
    lookupTypeDef(name, revision)?.iface?.fields?.values.orEmpty() +
        constructorsOf(name, revision).flatMap { it.fields.values }

/** Constructors are never pointed at individually: exposing `Shape/2` carries `Circle` and
 *  `Square` with it. */
internal fun ContractEnv.constructorsOf(
    name: String,
    revision: Revision,
) = allConstructors().filter { it.parentType == name && it.revision == revision }
