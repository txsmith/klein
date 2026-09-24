package klein.host

import klein.LogTypeMismatch
import klein.check.RuleEnv
import klein.check.RuleType
import klein.check.Subtyping
import klein.check.Type
import klein.check.contract.Edition
import klein.check.infer
import klein.interp.Value

internal fun Environment.checkLog(
    edition: Edition,
    log: EffectLog,
): List<LogTypeMismatch> {
    val ruleTypeEnv = edition.surface.ruleTypeEnv
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
