package klein.host

import klein.Diagnostic
import klein.KleinException
import klein.LogAlreadyEnded
import klein.SecondStartEntry
import klein.interp.Value

data class Call(
    val name: String,
    val args: List<Value>,
) {
    fun print() = args.joinToString(", ", "$name(", ")") { Value.print(it) }
}

sealed interface LogEntry {
    data class Start(
        val inputs: Map<String, Value>,
    ) : LogEntry

    data class Reply(
        val call: Call,
        val answer: Value,
    ) : LogEntry

    sealed interface Ending : LogEntry

    data class Result(
        val value: Value,
    ) : Ending

    data class Failure(
        val errors: List<Diagnostic>,
    ) : Ending
}

data class EffectLog(
    val start: LogEntry.Start,
    val replies: List<LogEntry.Reply> = emptyList(),
    val ending: LogEntry.Ending? = null,
) {
    val entries: List<LogEntry>
        get() =
            buildList {
                add(start)
                addAll(replies)
                ending?.let { add(it) }
            }

    operator fun plus(entry: LogEntry): EffectLog {
        ending?.let { throw KleinException(listOf(LogAlreadyEnded(it))) }
        return when (entry) {
            is LogEntry.Start -> throw KleinException(listOf(SecondStartEntry()))
            is LogEntry.Reply -> copy(replies = replies + entry)
            is LogEntry.Ending -> copy(ending = entry)
        }
    }
}
