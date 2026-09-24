package klein

import klein.check.RuleType
import klein.check.Type
import klein.host.Call

sealed interface HostError {
    val message: String
}

class InvalidContract(
    val diagnostics: List<Diagnostic>,
) : HostError {
    override val message get() = diagnostics.joinToString("\n") { "${it.message} at ${it.span}" }
}

class UnknownRelease(
    val number: ReleaseNumber,
    val available: List<ReleaseNumber>,
) : HostError {
    override val message =
        "release ${number.value} is not in this contract; " +
            if (available.isEmpty()) "it has none" else "it has ${available.joinToString { it.value.toString() }}"
}

class UnknownPin(
    val name: String,
    val revision: RevisionNumber,
) : HostError {
    override val message = "pin '$name' revision ${revision.value} names a revision the contract does not declare"
}

class RegistrationError internal constructor(
    override val message: String,
) : HostError

class WrongEnvironment internal constructor(
    val edition: String,
    val environment: String,
) : HostError {
    override val message = "this edition belongs to environment '$edition' but was given to environment '$environment'"
}

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

class Diverged internal constructor(
    val expected: String,
    val got: String,
    val at: Int,
    val call: Call?,
) : HostError {
    override val message get() = "replay diverged at log entry $at: expected $expected, got $got"
}

class HandlerTypeMismatch internal constructor(
    val call: String,
    val answerType: String,
    val declaredType: RuleType,
) : HostError {
    override val message get() = "'$call' answered with $answerType where the contract declares ${Type.print(declaredType)}"
}

class CallTypeMismatch internal constructor(
    val call: String,
    val got: String,
    val declared: String,
) : HostError {
    override val message get() = "'$call' was called with $got where the contract declares $declared"
}

class UnreadableLog internal constructor(
    override val message: String,
) : HostError

class UnreadableEdition internal constructor(
    override val message: String,
) : HostError
