package klein.js

import klein.KleinException
import klein.check.Type
import klein.Diagnostic as KleinDiagnostic
import klein.HostError as KleinHostError
import klein.CallTypeMismatch as KleinCallTypeMismatch
import klein.Diverged as KleinDiverged
import klein.HandlerTypeMismatch as KleinHandlerTypeMismatch
import klein.InvalidContract as KleinInvalidContract
import klein.LogAlreadyEnded as KleinLogAlreadyEnded
import klein.LogTypeMismatch as KleinLogTypeMismatch
import klein.MissingHandler as KleinMissingHandler
import klein.RegistrationError as KleinRegistrationError
import klein.SecondStartEntry as KleinSecondStartEntry
import klein.TransactionSkippedBlock as KleinTransactionSkippedBlock
import klein.UnknownPin as KleinUnknownPin
import klein.UnknownRelease as KleinUnknownRelease
import klein.UnreadableEdition as KleinUnreadableEdition
import klein.UnreadableLog as KleinUnreadableLog
import klein.WrongEnvironment as KleinWrongEnvironment
import kotlin.js.collections.JsReadonlyArray
import kotlin.js.collections.toList

@JsExport
class Diagnostic internal constructor(
    val message: String,
    val start: Int,
    val end: Int,
)

@JsExport
abstract class Checked<T> internal constructor() {
    abstract val kind: String
}

@JsExport
class Accepted<T> internal constructor(
    val value: T,
) : Checked<T>() {
    override val kind: String = "accepted"
}

@JsExport
class Rejected<T> internal constructor(
    val diagnostics: JsReadonlyArray<Diagnostic>,
) : Checked<T>() {
    override val kind: String = "rejected"
    val value: Nothing get() = throw RejectedError(diagnostics)
}

@JsExport
class RejectedError internal constructor(
    val diagnostics: JsReadonlyArray<Diagnostic>,
) : Throwable("rejected: " + diagnostics.toList().joinToString("\n") { it.message })

internal fun <T, R> toJs(
    checked: klein.Checked<T>,
    transform: (T) -> R,
): Checked<R> =
    when (checked) {
        is klein.Checked.Accepted -> Accepted(transform(checked.value))
        is klein.Checked.Rejected -> Rejected(toJs(checked.diagnostics))
    }

@JsExport
abstract class HostError internal constructor(
    val kind: String,
    val message: String,
)

@JsExport
class InvalidContract internal constructor(
    message: String,
    val diagnostics: JsReadonlyArray<Diagnostic>,
) : HostError("invalidContract", message)

@JsExport
class UnknownRelease internal constructor(
    message: String,
    val number: Int,
    val available: JsReadonlyArray<Int>,
) : HostError("unknownRelease", message)

@JsExport
class UnknownPin internal constructor(
    message: String,
    val name: String,
    val revision: Int,
) : HostError("unknownPin", message)

@JsExport
class WrongEnvironment internal constructor(
    message: String,
    val edition: String,
    val environment: String,
) : HostError("wrongEnvironment", message)

@JsExport
class MissingHandler internal constructor(
    message: String,
    val name: String,
    val revision: Int,
) : HostError("missingHandler", message)

@JsExport
class LogTypeMismatch internal constructor(
    message: String,
    val at: Int,
    val name: String,
    val answerType: String,
    val declaredType: String,
) : HostError("logTypeMismatch", message)

@JsExport
class Diverged internal constructor(
    message: String,
    val expected: String,
    val got: String,
    val at: Int,
    val call: Call?,
) : HostError("diverged", message)

@JsExport
class CallTypeMismatch internal constructor(
    message: String,
    val call: String,
    val got: String,
    val declared: String,
) : HostError("callTypeMismatch", message)

@JsExport
class HandlerTypeMismatch internal constructor(
    message: String,
    val call: String,
    val answerType: String,
    val declaredType: String,
) : HostError("handlerTypeMismatch", message)

@JsExport
class RegistrationError internal constructor(
    message: String,
) : HostError("registrationError", message)

@JsExport
class UnreadableEdition internal constructor(
    message: String,
) : HostError("unreadableEdition", message)

@JsExport
class UnreadableLog internal constructor(
    message: String,
) : HostError("unreadableLog", message)

@JsExport
class UnsupportedValue internal constructor(
    message: String,
) : HostError("unsupportedValue", message)

@JsExport
class LogAlreadyEnded internal constructor(
    message: String,
    val ending: LogEntry,
) : HostError("logAlreadyEnded", message)

@JsExport
class SecondStartEntry internal constructor(
    message: String,
) : HostError("secondStartEntry", message)

@JsExport
class TransactionSkippedBlock internal constructor(
    message: String,
) : HostError("transactionSkippedBlock", message)

@JsExport
class KleinError internal constructor(
    val errors: JsReadonlyArray<HostError>,
) : Throwable(errors.toList().joinToString("\n") { it.message })

internal fun toJs(diagnostic: KleinDiagnostic) = Diagnostic(diagnostic.message, diagnostic.span.start, diagnostic.span.end)

internal fun toJs(diagnostics: List<KleinDiagnostic>): JsReadonlyArray<Diagnostic> = diagnostics.map(::toJs).frozen()

internal inline fun <T> mapExceptions(block: () -> T): T =
    try {
        block()
    } catch (e: KleinException) {
        throw KleinError(e.errors.map(::toJs).frozen())
    }

internal fun toJs(error: KleinHostError): HostError =
    when (error) {
        is KleinInvalidContract -> InvalidContract(error.message, toJs(error.diagnostics))
        is KleinUnknownRelease -> UnknownRelease(error.message, error.number.value, error.available.map { it.value }.frozen())
        is KleinUnknownPin -> UnknownPin(error.message, error.name, error.revision.value)
        is KleinWrongEnvironment -> WrongEnvironment(error.message, error.edition, error.environment)
        is KleinMissingHandler -> MissingHandler(error.message, error.name, error.revision.value)
        is KleinLogTypeMismatch -> LogTypeMismatch(error.message, error.at, error.name, error.answerType, Type.print(error.declaredType))
        is KleinDiverged -> Diverged(error.message, error.expected, error.got, error.at, error.call?.let(::Call))
        is KleinCallTypeMismatch -> CallTypeMismatch(error.message, error.call, error.got, error.declared)
        is KleinHandlerTypeMismatch -> HandlerTypeMismatch(error.message, error.call, error.answerType, Type.print(error.declaredType))
        is KleinRegistrationError -> RegistrationError(error.message)
        is KleinUnreadableEdition -> UnreadableEdition(error.message)
        is KleinUnreadableLog -> UnreadableLog(error.message)
        is KleinLogAlreadyEnded -> LogAlreadyEnded(error.message, toJs(error.ending))
        is KleinSecondStartEntry -> SecondStartEntry(error.message)
        is KleinTransactionSkippedBlock -> TransactionSkippedBlock(error.message)
    }

internal fun unsupportedValue(message: String) = KleinError(listOf(UnsupportedValue(message)).frozen())
