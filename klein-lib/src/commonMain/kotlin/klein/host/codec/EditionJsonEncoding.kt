package klein.host.codec

import klein.CompilerVersion
import klein.HostError
import klein.KleinException
import klein.LanguageVersion
import klein.RevisionNumber
import klein.check.contract.Edition
import klein.check.contract.EnvironmentContract
import klein.check.contract.Pin
import klein.check.contract.PinResolution
import klein.check.contract.UnknownPin
import klein.host.DecodedEdition
import klein.host.StaleReason
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val FORMAT_MARKER = "klein-edition"
private const val JSON_VERSION = 1

class UnreadableEdition internal constructor(
    override val message: String,
) : HostError

@OptIn(ExperimentalEncodingApi::class)
fun encodeEditionJson(edition: Edition): String {
    val coreBytes = encodeCore(edition.core)
    val out = StringBuilder()
    out.append("{\"format\":\"")
    out.append(FORMAT_MARKER)
    out.append("\",\"version\":")
    out.append(JSON_VERSION)
    out.append(",\"language\":")
    out.append(edition.language.value)
    out.append(",\"pins\":{")
    edition.pinsWithHash.keys.sorted().forEachIndexed { index, name ->
        val pin = edition.pinsWithHash.getValue(name)
        if (index > 0) out.append(',')
        out.writeText(name)
        out.append(":{\"revision\":")
        out.append(pin.revision.value)
        out.append(",\"hash\":\"")
        out.append(hex16(pin.hash))
        out.append("\"}")
    }
    out.append("},\"source\":")
    out.writeText(edition.source)
    out.append(",\"core\":\"")
    out.append(Base64.encode(coreBytes))
    out.append("\",\"checksum\":\"")
    out.append(hex16(editionChecksum(edition.language, edition.source, edition.pinsWithHash, coreBytes)))
    out.append("\"}")
    return out.toString()
}

fun EnvironmentContract.decodeEditionJson(text: String): DecodedEdition = readArtifact(text).decode(this)

private class Artifact(
    val language: LanguageVersion,
    val pins: Map<String, Pin>,
    val source: String,
    val coreBytes: ByteArray,
    val checksum: Long,
) {
    fun decode(contract: EnvironmentContract): DecodedEdition {
        if (editionChecksum(language, source, pins, coreBytes) != checksum) return stale(StaleReason.ChecksumMismatch)
        if (language != LanguageVersion.CURRENT) return stale(StaleReason.LanguageChanged)
        if (readCoreVersion(coreBytes) != CompilerVersion.CURRENT) return stale(StaleReason.CompilerChanged)
        val surface =
            when (val resolution = contract.resolvePins(pins.mapValues { it.value.revision })) {
                is PinResolution.Resolved -> resolution.surface
                is PinResolution.Unknown -> return unknownPins(resolution.pins)
            }
        if (pins.any { (name, pin) -> contract.hashOf(name, pin.revision) != pin.hash }) return stale(StaleReason.DeclarationChanged)
        return DecodedEdition.Intact(Edition(language, decodeCore(coreBytes), pins, source, surface))
    }

    private fun stale(reason: StaleReason): DecodedEdition = DecodedEdition.Stale(language, pins, source, reason)

    private fun unknownPins(unknown: List<UnknownPin>): DecodedEdition {
        val known = pins.filterKeys { name -> unknown.none { it.name == name } }
        return DecodedEdition.Stale(language, known, source, StaleReason.UnknownPins(unknown.associate { it.name to it.revision }))
    }
}

private fun readArtifact(text: String): Artifact =
    try {
        readDocument(text)
    } catch (malformed: MalformedJson) {
        reject(malformed.message)
    }

private fun readDocument(text: String): Artifact {
    val document = JsonReader(text).readDocument()
    if (document !is Json.JObj) reject("not a Klein edition: the document is not a JSON object")
    val marker = (document.fields["format"] as? Json.JStr)?.value
    if (marker != FORMAT_MARKER) reject("not a Klein edition: the document does not declare \"format\": \"$FORMAT_MARKER\"")
    document.expectOnly("the document", "format", "version", "language", "pins", "source", "core", "checksum")
    val version = toWholeNumber(document.expectField("version", "the document"), "the \"version\" field")
    if (version != JSON_VERSION) reject("unknown edition version $version; this library reads version $JSON_VERSION")
    val language = LanguageVersion(toWholeNumber(document.expectField("language", "the document"), "the \"language\" field"))
    val pins = toPins(document.expectField("pins", "the document"))
    val sourceJson = document.expectField("source", "the document")
    if (sourceJson !is Json.JStr) reject("the document's \"source\" must be a string")
    val source = sourceJson.value
    val coreBytes = toCoreBytes(document.expectField("core", "the document"))
    val checksum = toChecksum(document.expectField("checksum", "the document"))
    return Artifact(language, pins, source, coreBytes, checksum)
}

private fun reject(message: String): Nothing = throw KleinException(listOf(UnreadableEdition(message)))

@OptIn(ExperimentalEncodingApi::class)
private fun toCoreBytes(json: Json): ByteArray {
    if (json !is Json.JStr) reject("the document's \"core\" must be a base64 string")
    return try {
        Base64.decode(json.value)
    } catch (malformed: IllegalArgumentException) {
        reject("the document's \"core\" is not valid base64: ${malformed.message}")
    }
}

private fun toPins(json: Json): Map<String, Pin> {
    if (json !is Json.JObj) reject("the document's \"pins\" must be an object of names to pins")
    val pins = LinkedHashMap<String, Pin>(json.fields.size)
    json.fields.forEach { (name, pinJson) -> pins[name] = toPin(name, pinJson) }
    return pins
}

private fun toPin(
    name: String,
    json: Json,
): Pin {
    val owner = "pin \"$name\""
    if (json !is Json.JObj) reject("$owner must be an object with a revision and a hash")
    json.expectOnly(owner, "revision", "hash")
    val revision = toWholeNumber(json.expectField("revision", owner), "the revision of $owner")
    if (revision < 1) reject("the revision of $owner must be a whole number of 1 or more")
    val hash = parseHex16((json.expectField("hash", owner) as? Json.JStr)?.value) ?: reject("the hash of $owner must be a string of 16 lowercase hex digits")
    return Pin(RevisionNumber(revision), hash)
}

private fun toChecksum(json: Json): Long =
    parseHex16((json as? Json.JStr)?.value) ?: reject("the document's \"checksum\" must be a string of 16 lowercase hex digits")
