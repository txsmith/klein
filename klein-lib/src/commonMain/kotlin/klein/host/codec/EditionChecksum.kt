package klein.host.codec

import klein.LanguageVersion
import klein.RevisionNumber

private val FNV_OFFSET_BASIS = 0xcbf29ce484222325uL.toLong()
private const val FNV_PRIME = 0x100000001b3L

internal fun editionChecksum(
    language: LanguageVersion,
    source: String,
    pins: Map<String, RevisionNumber>,
): Long {
    val out = ByteWriter()
    out.writeInt(language.value)
    out.writeString(source)
    for (name in pins.keys.sorted()) {
        out.writeString(name)
        out.writeInt(pins.getValue(name).value)
    }
    var hash = FNV_OFFSET_BASIS
    for (byte in out.toByteArray()) {
        hash = hash xor (byte.toLong() and 0xFF)
        hash *= FNV_PRIME
    }
    return hash
}
