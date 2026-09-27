package klein.jsbinding

import klein.host.codec.decodeJson as kleinDecodeJson
import klein.host.codec.encodeJson as kleinEncodeJson
import kotlin.js.collections.JsReadonlyArray
import kotlin.js.collections.toList

@JsExport
class Call internal constructor(
    internal val call: klein.host.Call,
) {
    val name: String get() = call.name
    val args: JsReadonlyArray<Any?> get() = toJs(call.args)

    fun print(): String = call.print()
}

@JsExport
fun createCall(
    name: String,
    args: JsReadonlyArray<Any?>,
): Call = Call(klein.host.Call(name, fromJs(args)))

@JsExport
class LogEntry internal constructor(
    internal val entry: klein.host.LogEntry,
) {
    val kind: String =
        when (entry) {
            is klein.host.LogEntry.Start -> "Start"
            is klein.host.LogEntry.Reply -> "Reply"
            is klein.host.LogEntry.Result -> "Result"
            is klein.host.LogEntry.Failure -> "Failure"
        }
    val inputNames: JsReadonlyArray<String>? = (entry as? klein.host.LogEntry.Start)?.inputs?.keys?.toList()?.toJs()
    val inputValues: JsReadonlyArray<Any?>? = (entry as? klein.host.LogEntry.Start)?.inputs?.values?.toList()?.let(::toJs)
    val call: Call? = (entry as? klein.host.LogEntry.Reply)?.call?.let(::Call)
    val answer: Any? = (entry as? klein.host.LogEntry.Reply)?.answer?.let(::toJs)
    val value: Any? = (entry as? klein.host.LogEntry.Result)?.value?.let(::toJs)
    val errors: JsReadonlyArray<Diagnostic>? = (entry as? klein.host.LogEntry.Failure)?.errors?.let(::toJs)
}

@JsExport
fun createStartEntry(
    names: JsReadonlyArray<String>,
    values: JsReadonlyArray<Any?>,
): LogEntry = LogEntry(klein.host.LogEntry.Start(names.toList().zip(fromJs(values)).toMap()))

@JsExport
fun createReplyEntry(
    call: Call,
    answer: Any?,
): LogEntry = LogEntry(klein.host.LogEntry.Reply(call.call, fromJs(answer)))

@JsExport
fun createResultEntry(value: Any?): LogEntry = LogEntry(klein.host.LogEntry.Result(fromJs(value)))

@JsExport
fun createFailureEntry(errors: JsReadonlyArray<Diagnostic>): LogEntry = LogEntry(klein.host.LogEntry.Failure(fromJs(errors)))

@JsExport
class EffectLog internal constructor(
    internal val log: klein.host.EffectLog,
) {
    val entries: JsReadonlyArray<LogEntry> get() = log.entries.map(::LogEntry).toJs()

    fun plus(entry: LogEntry): EffectLog = EffectLog(log + entry.entry)
}

@JsExport
fun createEffectLog(
    start: LogEntry,
    replies: JsReadonlyArray<LogEntry>,
    ending: LogEntry?,
): EffectLog =
    EffectLog(
        klein.host.EffectLog(
            start.entry as klein.host.LogEntry.Start,
            replies.toList().map { it.entry as klein.host.LogEntry.Reply },
            ending?.entry as klein.host.LogEntry.Ending?,
        ),
    )

@JsExport
fun encodeJson(log: EffectLog): String = kleinEncodeJson(log.log)

@JsExport
fun decodeJson(text: String): EffectLog = EffectLog(kleinDecodeJson(text))
