package klein.js

import klein.RevisionNumber
import klein.check.contract.Pin as KleinPin
import klein.host.codec.encodeEditionJson
import klein.check.contract.Edition as KleinEdition

@JsExport
class Pin internal constructor(
    internal val pin: KleinPin,
) {
    val revision: Int = pin.revision.value
    val hash: String = pin.hash.toString()
}

@JsExport
class Edition internal constructor(
    internal val edition: KleinEdition,
) {
    val environment: String = edition.environment
    val language: Int = edition.language.value
    val source: String = edition.source
    val pins: dynamic = toJsRevisions(edition.pins)
    val pinsWithHash: dynamic = toJsPins(edition.pinsWithHash)

    fun encodeJson(): String = encodeEditionJson(edition)
}

internal fun toJsPins(pins: Map<String, KleinPin>): dynamic = frozenRecord(pins.mapValues { Pin(it.value) })

internal fun toJsRevisions(revisions: Map<String, RevisionNumber>): dynamic = frozenRecord(revisions.mapValues { it.value.value })

internal fun fromJsPins(pins: dynamic): Map<String, KleinPin> {
    val names = js("Object.keys")(pins).unsafeCast<Array<String>>()
    return names.associateWith { name ->
        val pin = pins[name]
        if (pin !is Pin) throw unsupportedValue("the pin for '$name' must be a Pin the binding handed out")
        pin.pin
    }
}
