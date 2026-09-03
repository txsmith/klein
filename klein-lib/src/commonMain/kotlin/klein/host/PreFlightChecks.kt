package klein.host

import klein.RevisionNumber
import klein.check.RuleType
import klein.check.Subtyping
import klein.check.Type
import klein.check.contract.Edition
import klein.check.contract.ResolvedRelease
import klein.check.infer
import klein.core.PreludeBinding
import klein.interp.Value

sealed interface PinProblem {
    val message: String
}

class UnservedPin(
    val name: String,
    val revision: RevisionNumber,
) : PinProblem {
    override val message = "the edition pins '$name' revision ${revision.value}, which this environment does not declare"
}

class MissingHandler(
    val name: String,
    val revision: RevisionNumber,
) : PinProblem {
    override val message =
        "'$name' revision ${revision.value} has no handler: register one at boot or supply one with the run"
}

internal fun Environment.checkPins(
    edition: Edition,
    handlers: Registry,
): List<PinProblem> {
    val release = contract.resolve(edition.release)
    val problems = mutableListOf<PinProblem>()
    for ((name, revision) in edition.pins) {
        when (release.bindingFor(name)) {
            is PreludeBinding.Ctor -> {}
            is PreludeBinding.Function, is PreludeBinding.Value -> {
                val capability = capability(name, revision)
                when {
                    capability == null -> problems.add(UnservedPin(name, revision))
                    handlers.registered[name to revision] == null && this[capability.id] == null ->
                        problems.add(MissingHandler(name, revision))
                }
            }
            // Null with the name still exposed is a type: erased by lowering, nothing to serve.
            null -> if (name !in release.revisions) problems.add(UnservedPin(name, revision))
        }
    }
    return problems
}

internal fun Environment.checkLog(
    edition: Edition,
    log: EffectLog,
): List<RunError.LogTypeMismatch.Problem> {
    val release = contract.resolve(edition.release)
    val problems = mutableListOf<RunError.LogTypeMismatch.Problem>()
    fun check(
        at: Int,
        name: String,
        answer: Value,
    ) {
        val revision = edition.pins[name] ?: return
        val declaredType = capability(name, revision)?.declaration?.answerType ?: return
        if (!fitsDeclaredType(answer, declaredType, release)) {
            problems.add(RunError.LogTypeMismatch.Problem(at, name, printType(answer, release), declaredType))
        }
    }
    for ((at, entry) in log.entries.withIndex()) {
        when (entry) {
            is LogEntry.Start -> entry.inputs.forEach { (name, value) -> check(at, name, value) }
            is LogEntry.Reply -> check(at, entry.call.name, entry.answer)
            is LogEntry.Result, is LogEntry.Failure -> {}
        }
    }
    return problems
}

private val subtyping = Subtyping()

internal fun fitsDeclaredType(
    value: Value,
    declared: RuleType,
    release: ResolvedRelease,
): Boolean = value !is Value.VClos && subtyping.isSubtype(infer(value, release.types), declared, release.types)

internal fun printType(
    value: Value,
    release: ResolvedRelease,
): String = if (value is Value.VClos) "a function" else Type.print(infer(value, release.types))
