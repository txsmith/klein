package klein.jsbinding

import klein.KleinException
import kotlin.js.collections.JsReadonlyArray

@JsExport
fun getHostErrors(error: Any?): JsReadonlyArray<HostError>? = (error as? KleinException)?.errors?.map(::toJs)?.toJs()

@JsExport
abstract class HostError internal constructor(
    val message: String,
)

@JsExport
class InvalidContract internal constructor(
    message: String,
    val diagnostics: JsReadonlyArray<Diagnostic>,
) : HostError(message)

@JsExport
class UnknownRelease internal constructor(
    message: String,
    val number: Int,
    val available: JsReadonlyArray<Int>,
) : HostError(message)

@JsExport
class UnknownPin internal constructor(
    message: String,
    val name: String,
    val revision: Int,
) : HostError(message)

@JsExport
class RegistrationError internal constructor(
    message: String,
) : HostError(message)

@JsExport
class WrongEnvironment internal constructor(
    message: String,
    val edition: String,
    val environment: String,
) : HostError(message)

@JsExport
class MissingHandler internal constructor(
    message: String,
    val name: String,
    val revision: Int,
) : HostError(message)

@JsExport
class LogTypeMismatch internal constructor(
    message: String,
    val at: Int,
    val name: String,
    val answerType: String,
    val declaredType: Type,
) : HostError(message)

@JsExport
class Diverged internal constructor(
    message: String,
    val expected: String,
    val got: String,
    val at: Int,
    val call: Call?,
) : HostError(message)

@JsExport
class HandlerTypeMismatch internal constructor(
    message: String,
    val call: String,
    val answerType: String,
    val declaredType: Type,
) : HostError(message)

@JsExport
class CallTypeMismatch internal constructor(
    message: String,
    val call: String,
    val got: String,
    val declared: String,
) : HostError(message)

@JsExport
class UnreadableLog internal constructor(
    message: String,
) : HostError(message)

@JsExport
class UnreadableEdition internal constructor(
    message: String,
) : HostError(message)

@JsExport
class LogAlreadyEnded internal constructor(
    message: String,
    val ending: LogEntry,
) : HostError(message)

@JsExport
class SecondStartEntry internal constructor(
    message: String,
) : HostError(message)

private fun toJs(error: klein.HostError): HostError =
    when (error) {
        is klein.InvalidContract -> InvalidContract(error.message, toJs(error.diagnostics))
        is klein.UnknownRelease -> UnknownRelease(error.message, error.number.value, error.available.map { it.value }.toJs())
        is klein.UnknownPin -> UnknownPin(error.message, error.name, error.revision.value)
        is klein.RegistrationError -> RegistrationError(error.message)
        is klein.WrongEnvironment -> WrongEnvironment(error.message, error.edition, error.environment)
        is klein.MissingHandler -> MissingHandler(error.message, error.name, error.revision.value)
        is klein.LogTypeMismatch -> LogTypeMismatch(error.message, error.at, error.name, error.answerType, Type(error.declaredType))
        is klein.Diverged -> Diverged(error.message, error.expected, error.got, error.at, error.call?.let(::Call))
        is klein.HandlerTypeMismatch -> HandlerTypeMismatch(error.message, error.call, error.answerType, Type(error.declaredType))
        is klein.CallTypeMismatch -> CallTypeMismatch(error.message, error.call, error.got, error.declared)
        is klein.UnreadableLog -> UnreadableLog(error.message)
        is klein.UnreadableEdition -> UnreadableEdition(error.message)
        is klein.LogAlreadyEnded -> LogAlreadyEnded(error.message, LogEntry(error.ending))
        is klein.SecondStartEntry -> SecondStartEntry(error.message)
    }
