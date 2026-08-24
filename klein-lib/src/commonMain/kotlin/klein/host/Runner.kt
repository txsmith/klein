package klein.host

import klein.KleinError
import klein.KleinException
import klein.RevisionNumber
import klein.SourceSpan
import klein.check.contract.Edition
import klein.core.PreludeBinding
import klein.interp.Execution
import klein.interp.Machine
import klein.interp.Value

class UnservedPin(
    val name: String,
    val revision: RevisionNumber,
) : KleinError {
    override val message = "the edition pins '$name' revision ${revision.value}, which this environment does not declare"
    override val span = SourceSpan.zero
}

class MissingImplementation(
    val name: String,
    val declared: RevisionNumber,
) : KleinError {
    override val message =
        "'$name' revision ${declared.value} has no implementation: register one at boot or supply one with the run"
    override val span = SourceSpan.zero
}

/**
 * Run an [Edition] to its final [Value], answering each suspension from [supply] first and the
 * boot registrations second. [checkPins] rejects the run before the machine starts if any pinned
 * capability could go unanswered, so a rule never performs half its effects and then hits a
 * missing handler. Throws [KleinException] carrying every [UnservedPin] and [MissingImplementation].
 */
fun Environment.run(
    edition: Edition,
    supply: Registry.() -> Unit = {},
): Value {
    val supplied = Registry(contract.declarations).apply(supply)
    if (supplied.errors.isNotEmpty()) throw KleinException(supplied.errors)
    checkPins(edition, supplied)
    var execution = Machine.start(edition.core)
    while (execution is Execution.AwaitingHost) {
        execution = execution.resume(answer(execution, edition.pins, supplied))
    }
    return (execution as Execution.Done).value
}

internal fun Environment.checkPins(
    edition: Edition,
    supplied: Registry,
) {
    val release = contract.resolve(edition.release)
    val errors = mutableListOf<KleinError>()
    for ((name, revision) in edition.pins) {
        when (release.bindingFor(name)) {
            is PreludeBinding.Ctor -> {}
            is PreludeBinding.Function, is PreludeBinding.Value -> {
                val capability = capability(name, revision)
                when {
                    capability == null -> errors.add(UnservedPin(name, revision))
                    supplied.registered[name to revision] == null && this[capability.id] == null ->
                        errors.add(MissingImplementation(name, revision))
                }
            }
            // Null with the name still exposed is a type: erased by lowering, nothing to serve.
            null -> if (name !in release.revisions) errors.add(UnservedPin(name, revision))
        }
    }
    if (errors.isNotEmpty()) throw KleinException(errors)
}

private fun Environment.answer(
    suspension: Execution.AwaitingHost,
    pins: Map<String, RevisionNumber>,
    supplied: Registry,
): Value {
    val revision = pins.getValue(suspension.call)
    val implementation =
        supplied.registered[suspension.call to revision]
            ?: this[capability(suspension.call, revision)!!.id]
    return when (implementation) {
        is Implementation.Immediate -> implementation.answer(suspension.args)
        null -> throw IllegalStateException("no implementation for '${suspension.call}' although checkPins passed")
    }
}
