package klein.host

import klein.HostError
import klein.RevisionNumber
import klein.check.RuleEnv
import klein.check.RuleType
import klein.check.Subtyping
import klein.check.Type
import klein.check.contract.Edition
import klein.check.contract.UnknownPin
import klein.check.infer
import klein.interp.Value

class MissingHandler internal constructor(
    val name: String,
    val revision: RevisionNumber,
) : HostError {
    override val message =
        "'$name' revision ${revision.value} has no handler: register one at boot or supply one with the run"
}

class LogTypeMismatch internal constructor(
    val at: Int,
    val name: String,
    val answerType: String,
    val declaredType: RuleType,
) : HostError {
    override val message get() = "log entry $at holds $answerType for '$name' where the contract declares ${Type.print(declaredType)}"
}

internal fun Environment.checkPins(
    edition: Edition,
    handlers: HandlerRegistry,
): List<HostError> {
    val problems = mutableListOf<HostError>()
    for ((name, revision) in edition.pins) {
        val capability = getCapabilityDeclaration(name, revision)
        when {
            capability != null ->
                if (handlers.registered[name to revision] == null && getHandler(name, revision) == null) {
                    problems.add(MissingHandler(name, revision))
                }
            contract.declaresVocabulary(name, revision) -> {}
            else -> problems.add(UnknownPin(name, revision))
        }
    }
    return problems
}

internal fun Environment.checkLog(
    edition: Edition,
    log: EffectLog,
): List<LogTypeMismatch> {
    val ruleTypeEnv = contract.resolvePins(edition.pins).ruleTypeEnv
    val problems = mutableListOf<LogTypeMismatch>()
    fun check(
        at: Int,
        name: String,
        answer: Value,
    ) {
        val revision = edition.pins[name] ?: return
        val declaredType = getCapabilityDeclaration(name, revision)?.answerType ?: return
        if (!fitsDeclaredType(answer, declaredType, ruleTypeEnv)) {
            problems.add(LogTypeMismatch(at, name, printType(answer, ruleTypeEnv), declaredType))
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
    ruleTypeEnv: RuleEnv,
): Boolean = value !is Value.VClos && subtyping.isSubtype(infer(value, ruleTypeEnv), declared, ruleTypeEnv)

internal fun printType(
    value: Value,
    ruleTypeEnv: RuleEnv,
): String = if (value is Value.VClos) "a function" else Type.print(infer(value, ruleTypeEnv))
