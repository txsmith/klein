package klein.host.codec

import klein.Diagnostic
import klein.HostError
import klein.KleinException
import klein.SourceSpan
import klein.host.Call
import klein.host.EffectLog
import klein.host.LogEntry
import klein.interp.RuntimeError
import klein.interp.Value

class UnreadableLog internal constructor(
    override val message: String,
) : HostError

private val MAGIC = byteArrayOf('K'.code.toByte(), 'L'.code.toByte(), 'O'.code.toByte(), 'G'.code.toByte())
private const val VERSION = 1

private const val ENTRY_START = 0
private const val ENTRY_REPLY = 1
private const val ENTRY_RESULT = 2
private const val ENTRY_FAILURE = 3

private const val VALUE_NUM = 0
private const val VALUE_STR = 1
private const val VALUE_BOOL = 2
private const val VALUE_NULL = 3
private const val VALUE_UNIT = 4
private const val VALUE_STRUCT = 5

fun encodeBinary(log: EffectLog): ByteArray {
    val out = ByteWriter()
    out.writeBytes(MAGIC)
    out.writeByte(VERSION)
    out.writeInt(log.entries.size)
    log.entries.forEach { out.writeEntry(it) }
    return out.toByteArray()
}

fun decodeBinary(bytes: ByteArray): EffectLog =
    try {
        readLog(bytes)
    } catch (malformed: MalformedBytes) {
        reject(malformed.message)
    }

private fun readLog(bytes: ByteArray): EffectLog {
    val input = ByteReader(bytes)
    if (bytes.size < MAGIC.size + 1 || !MAGIC.contentEquals(input.readBytes(MAGIC.size))) {
        reject("not an effect log: the bytes do not open with the effect log stamp")
    }
    val version = input.readByte()
    if (version != VERSION) {
        reject("unknown effect log version $version; this library reads version $VERSION")
    }
    val count = input.readCount()
    if (count == 0) reject("an effect log opens with its start entry; these bytes hold none")
    val start = input.readEntry() as? LogEntry.Start ?: reject("an effect log opens with its start entry")
    val replies = mutableListOf<LogEntry.Reply>()
    var ending: LogEntry.Ending? = null
    repeat(count - 1) {
        if (ending != null) reject("entries follow the log's ending")
        when (val entry = input.readEntry()) {
            is LogEntry.Start -> reject("a second start entry mid-log")
            is LogEntry.Reply -> replies.add(entry)
            is LogEntry.Ending -> ending = entry
        }
    }
    if (!input.isExhausted) reject("${input.remaining} unexpected trailing bytes after the last entry")
    return EffectLog(start, replies, ending)
}

private fun reject(message: String): Nothing = throw KleinException(listOf(UnreadableLog(message)))

private fun ByteWriter.writeEntry(entry: LogEntry) {
    when (entry) {
        is LogEntry.Start -> {
            writeByte(ENTRY_START)
            writeInt(entry.inputs.size)
            entry.inputs.forEach { (name, value) ->
                writeString(name)
                writeValue(value)
            }
        }
        is LogEntry.Reply -> {
            writeByte(ENTRY_REPLY)
            writeCall(entry.call)
            writeValue(entry.answer)
        }
        is LogEntry.Result -> {
            writeByte(ENTRY_RESULT)
            writeValue(entry.value)
        }
        is LogEntry.Failure -> {
            writeByte(ENTRY_FAILURE)
            writeInt(entry.errors.size)
            entry.errors.forEach { writeDiagnostic(it) }
        }
    }
}

private fun ByteReader.readEntry(): LogEntry =
    when (val kind = readByte()) {
        ENTRY_START -> LogEntry.Start(readMap { readValue() })
        ENTRY_REPLY -> LogEntry.Reply(readCall(), readValue())
        ENTRY_RESULT -> LogEntry.Result(readValue())
        ENTRY_FAILURE -> LogEntry.Failure(List(readCount()) { readDiagnostic() })
        else -> reject("unknown log entry kind $kind")
    }

private fun ByteWriter.writeCall(call: Call) {
    writeString(call.name)
    writeInt(call.args.size)
    call.args.forEach { writeValue(it) }
}

private fun ByteReader.readCall() = Call(readString(), List(readCount()) { readValue() })

private fun ByteWriter.writeDiagnostic(diagnostic: Diagnostic) {
    writeString(diagnostic.message)
    writeInt(diagnostic.span.start)
    writeInt(diagnostic.span.end)
}

private fun ByteReader.readDiagnostic(): Diagnostic = RuntimeError(readString(), SourceSpan(readInt(), readInt()))

private fun ByteWriter.writeValue(value: Value) {
    when (value) {
        is Value.VNum -> {
            writeByte(VALUE_NUM)
            writeLong(value.value.toRawBits())
        }
        is Value.VStr -> {
            writeByte(VALUE_STR)
            writeString(value.value)
        }
        is Value.VBool -> {
            writeByte(VALUE_BOOL)
            writeBoolean(value.value)
        }
        Value.VNull -> writeByte(VALUE_NULL)
        Value.VUnit -> writeByte(VALUE_UNIT)
        is Value.VStruct -> {
            writeByte(VALUE_STRUCT)
            val tag = value.tag
            writeBoolean(tag != null)
            if (tag != null) writeString(tag)
            writeInt(value.fields.size)
            value.fields.forEach { (name, field) ->
                writeString(name)
                writeValue(field)
            }
        }
        else -> throw IllegalArgumentException("a ${Value.print(value)} cannot be encoded: closures never cross the host boundary")
    }
}

private fun ByteReader.readValue(): Value =
    when (val kind = readByte()) {
        VALUE_NUM -> Value.VNum(Double.fromBits(readLong()))
        VALUE_STR -> Value.VStr(readString())
        VALUE_BOOL -> Value.VBool(readBoolean())
        VALUE_NULL -> Value.VNull
        VALUE_UNIT -> Value.VUnit
        VALUE_STRUCT -> {
            val tag = if (readBoolean()) readString() else null
            Value.VStruct(tag, readMap { readValue() })
        }
        else -> reject("unknown value kind $kind")
    }

private fun <T> ByteReader.readMap(readEntry: ByteReader.() -> T): Map<String, T> {
    val count = readCount()
    val map = LinkedHashMap<String, T>(count)
    repeat(count) { map[readString()] = readEntry() }
    return map
}
