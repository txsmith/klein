package klein.host.codec

internal class MalformedBytes(
    override val message: String,
) : Exception(message)

private fun malformed(message: String): Nothing = throw MalformedBytes(message)

internal class ByteWriter {
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

internal class ByteReader(
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
            else -> malformed("expected a boolean byte, found $byte")
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
        if (count < 0 || count > remaining) malformed("implausible count $count at offset ${position - 4} with $remaining bytes left")
        return count
    }

    fun readString(): String {
        val encoded = readBytes(readCount())
        return try {
            encoded.decodeToString(throwOnInvalidSequence = true)
        } catch (ignored: CharacterCodingException) {
            malformed("malformed UTF-8 in a string at offset ${position - encoded.size}")
        }
    }

    private fun ensureAvailable(count: Int) {
        if (count < 0 || position + count > bytes.size) {
            malformed("the input ends early: needed $count more bytes at offset $position of ${bytes.size}")
        }
    }
}
