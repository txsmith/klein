package klein.host.codec

import klein.Fnv
import klein.LanguageVersion
import klein.check.contract.Pin

internal fun editionChecksum(
    language: LanguageVersion,
    source: String,
    pins: Map<String, Pin>,
    coreBytes: ByteArray,
): Long {
    val fnv = Fnv()
    fnv.int(language.value)
    fnv.string(source)
    for (name in pins.keys.sorted()) {
        val pin = pins.getValue(name)
        fnv.string(name)
        fnv.int(pin.revision.value)
        fnv.long(pin.hash)
    }
    fnv.bytes(coreBytes)
    return fnv.result()
}
