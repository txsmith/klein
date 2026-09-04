package klein.host

import klein.KleinError
import klein.KleinException
import klein.SourceSpan
import klein.interp.Value

class UnreadableLog internal constructor(
    override val message: String,
) : KleinError {
    override val span: SourceSpan? get() = null
}

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

fun encode(log: EffectLog): ByteArray {
    val out = ByteWriter()
    out.writeBytes(MAGIC)
    out.writeByte(VERSION)
    out.writeInt(log.entries.size)
    log.entries.forEach { out.writeEntry(it) }
    return out.toByteArray()
}

fun decode(bytes: ByteArray): EffectLog {
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
    val span = diagnostic.span
    writeBoolean(span != null)
    if (span != null) {
        writeInt(span.start)
        writeInt(span.end)
    }
}

private fun ByteReader.readDiagnostic(): Diagnostic {
    val message = readString()
    val span = if (readBoolean()) SourceSpan(readInt(), readInt()) else null
    return Diagnostic(message, span)
}

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

private class ByteWriter {
    private var buffer = ByteArray(256)
    private var size = 0

    fun writeByte(value: Int) {
        ensureRoom(1)
        buffer[size++] = value.toByte()
    }

    fun writeBytes(bytes: ByteArray) {
        ensureRoom(bytes.size)
        bytes.copyInto(buffer, size)
        size += bytes.size
    }

    fun writeBoolean(value: Boolean) = writeByte(if (value) 1 else 0)

    fun writeInt(value: Int) {
        ensureRoom(4)
        for (shift in 24 downTo 0 step 8) buffer[size++] = (value ushr shift).toByte()
    }

    fun writeLong(value: Long) {
        ensureRoom(8)
        for (shift in 56 downTo 0 step 8) buffer[size++] = (value ushr shift).toByte()
    }

    fun writeString(value: String) {
        val bytes = value.encodeToByteArray()
        writeInt(bytes.size)
        writeBytes(bytes)
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    private fun ensureRoom(count: Int) {
        if (size + count > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + count))
    }
}

private class ByteReader(
    private val bytes: ByteArray,
) {
    private var position = 0

    val isExhausted: Boolean get() = position == bytes.size
    val remaining: Int get() = bytes.size - position

    fun readByte(): Int {
        ensureAvailable(1)
        return bytes[position++].toInt() and 0xFF
    }

    fun readBytes(count: Int): ByteArray {
        ensureAvailable(count)
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    fun readBoolean(): Boolean =
        when (val byte = readByte()) {
            0 -> false
            1 -> true
            else -> reject("expected a boolean byte, found $byte")
        }

    fun readInt(): Int {
        ensureAvailable(4)
        var value = 0
        repeat(4) { value = (value shl 8) or (bytes[position++].toInt() and 0xFF) }
        return value
    }

    fun readLong(): Long {
        ensureAvailable(8)
        var value = 0L
        repeat(8) { value = (value shl 8) or (bytes[position++].toLong() and 0xFF) }
        return value
    }

    fun readCount(): Int {
        val count = readInt()
        if (count < 0 || count > remaining) reject("implausible count $count at offset ${position - 4} with $remaining bytes left")
        return count
    }

    fun readString(): String {
        val encoded = readBytes(readCount())
        return try {
            encoded.decodeToString(throwOnInvalidSequence = true)
        } catch (ignored: CharacterCodingException) {
            reject("malformed UTF-8 in a string at offset ${position - encoded.size}")
        }
    }

    private fun ensureAvailable(count: Int) {
        if (count < 0 || position + count > bytes.size) {
            reject("the log ends early: needed $count more bytes at offset $position of ${bytes.size}")
        }
    }
}
