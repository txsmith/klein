package klein.jsbinding

import klein.check.contract.Pin as KleinPin
import klein.host.StaleReason
import klein.host.codec.encodeEditionJson
import kotlin.js.collections.JsReadonlyArray

@JsExport
class Edition internal constructor(
    internal val edition: klein.check.contract.Edition,
) {
    val environment: String get() = edition.environment
    val language: Int get() = edition.language.value
    val source: String get() = edition.source
    val pinsWithHash: JsReadonlyArray<Pin> get() = toJs(edition.pinsWithHash)

    fun encodeJson(): String = encodeEditionJson(edition)
}

@JsExport
class Pin internal constructor(
    val name: String,
    val revision: Int,
    val hash: String?,
)

@JsExport
class DecodedEdition internal constructor(
    val edition: Edition?,
    val language: Int,
    val source: String,
    val pins: JsReadonlyArray<Pin>,
    val reason: String?,
    val unknownPins: JsReadonlyArray<Pin>?,
)

internal fun toJs(decoded: klein.host.DecodedEdition): DecodedEdition =
    when (decoded) {
        is klein.host.DecodedEdition.Intact -> {
            val edition = decoded.edition
            DecodedEdition(Edition(edition), edition.language.value, edition.source, toJs(edition.pinsWithHash), null, null)
        }
        is klein.host.DecodedEdition.Stale -> {
            val reason = decoded.reason
            val name =
                when (reason) {
                    StaleReason.ChecksumMismatch -> "ChecksumMismatch"
                    StaleReason.LanguageChanged -> "LanguageChanged"
                    StaleReason.CompilerChanged -> "CompilerChanged"
                    is StaleReason.UnknownPins -> "UnknownPins"
                    StaleReason.DeclarationChanged -> "DeclarationChanged"
                }
            val unknownPins = (reason as? StaleReason.UnknownPins)?.pins?.map { (pinName, revision) -> Pin(pinName, revision.value, null) }?.toJs()
            DecodedEdition(null, decoded.language.value, decoded.source, toJs(decoded.pins), name, unknownPins)
        }
    }

private fun toJs(pins: Map<String, KleinPin>): JsReadonlyArray<Pin> = pins.map { (name, pin) -> Pin(name, pin.revision.value, pin.hash.toString()) }.toJs()
