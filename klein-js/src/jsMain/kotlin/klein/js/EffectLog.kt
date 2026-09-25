package klein.js

import klein.host.codec.decodeJson
import klein.host.codec.encodeJson
import klein.host.EffectLog as KleinEffectLog
import klein.host.LogEntry as KleinLogEntry
import kotlin.js.collections.JsReadonlyArray

@JsExport
class EffectLog internal constructor(
    internal val log: KleinEffectLog,
) {
    val entries: JsReadonlyArray<LogEntry> = log.entries.map(::toJs).frozen()

    fun append(entry: LogEntry): EffectLog = mapExceptions { EffectLog(log + entry.entry) }

    fun encodeJson(): String = encodeJson(log)
}

@JsExport
fun decodeLogJson(json: String): EffectLog = mapExceptions { EffectLog(decodeJson(json)) }

@JsExport
abstract class LogEntry internal constructor(
    internal val entry: KleinLogEntry,
) {
    abstract val kind: String
}

@JsExport
class StartEntry internal constructor(
    entry: KleinLogEntry.Start,
) : LogEntry(entry) {
    override val kind: String = "start"
    val inputs: dynamic = toJsObject(entry.inputs)
}

@JsExport
class ReplyEntry internal constructor(
    entry: KleinLogEntry.Reply,
) : LogEntry(entry) {
    override val kind: String = "reply"
    val call: Call = Call(entry.call)
    val answer: Any? = toJs(entry.answer)
}

@JsExport
class ResultEntry internal constructor(
    entry: KleinLogEntry.Result,
) : LogEntry(entry) {
    override val kind: String = "result"
    val value: Any? = toJs(entry.value)
}

@JsExport
class FailureEntry internal constructor(
    entry: KleinLogEntry.Failure,
) : LogEntry(entry) {
    override val kind: String = "failure"
    val diagnostics: JsReadonlyArray<Diagnostic> = toJs(entry.errors)
}

internal fun toJs(entry: KleinLogEntry): LogEntry =
    when (entry) {
        is KleinLogEntry.Start -> StartEntry(entry)
        is KleinLogEntry.Reply -> ReplyEntry(entry)
        is KleinLogEntry.Result -> ResultEntry(entry)
        is KleinLogEntry.Failure -> FailureEntry(entry)
    }
