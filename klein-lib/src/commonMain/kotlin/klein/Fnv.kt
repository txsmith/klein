package klein

private val FNV_OFFSET_BASIS = 0xcbf29ce484222325uL.toLong()
private const val FNV_PRIME = 0x100000001b3L

internal class Fnv {
    private var hash = FNV_OFFSET_BASIS

    fun byte(value: Int) {
        hash = hash xor (value.toLong() and 0xFF)
        hash *= FNV_PRIME
    }

    fun bytes(values: ByteArray) {
        for (value in values) byte(value.toInt())
    }

    fun int(value: Int) {
        for (shift in 24 downTo 0 step 8) byte(value ushr shift)
    }

    fun string(value: String) {
        val encoded = value.encodeToByteArray()
        int(encoded.size)
        bytes(encoded)
    }

    fun result(): Long = hash
}
