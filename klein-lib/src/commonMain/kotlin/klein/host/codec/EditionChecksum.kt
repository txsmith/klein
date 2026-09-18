package klein.host.codec

import klein.Fnv
import klein.LanguageVersion
import klein.RevisionNumber

internal fun editionChecksum(
    language: LanguageVersion,
    source: String,
    pins: Map<String, RevisionNumber>,
    coreBytes: ByteArray,
): Long {
    val fnv = Fnv()
    fnv.int(language.value)
    fnv.string(source)
    for (name in pins.keys.sorted()) {
        fnv.string(name)
        fnv.int(pins.getValue(name).value)
    }
    fnv.bytes(coreBytes)
    return fnv.result()
}
