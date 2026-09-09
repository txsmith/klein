package klein.host.codec

import klein.CompilerVersion
import klein.KleinException
import klein.LanguageVersion
import klein.RevisionNumber
import klein.check.contract.Edition
import klein.host.DecodedEdition
import klein.host.Rederivation
import klein.host.UnreadableEdition
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val FORMAT_MARKER = "klein-edition"
private const val JSON_VERSION = 1

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
    edition.pins.keys.sorted().forEachIndexed { index, name ->
        if (index > 0) out.append(',')
        out.writeText(name)
        out.append(':')
        out.append(edition.pins.getValue(name).value)
    }
    out.append("},\"source\":")
    out.writeText(edition.source)
    out.append(",\"core\":\"")
    out.append(Base64.encode(coreBytes))
    out.append("\",\"checksum\":\"")
    out.append(editionChecksum(edition.language, edition.source, edition.pins, coreBytes).toULong().toString(16).padStart(16, '0'))
    out.append("\"}")
    return out.toString()
}

fun decodeEditionJson(text: String): DecodedEdition =
    try {
        readEdition(text)
    } catch (malformed: MalformedJson) {
        reject(malformed.message)
    }

private fun readEdition(text: String): DecodedEdition {
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
    if (language != LanguageVersion.CURRENT) {
        reject("the edition was written in language version $language; this library reads language version ${LanguageVersion.CURRENT}")
    }
    if (editionChecksum(language, source, pins, coreBytes) != checksum) {
        return DecodedEdition.Stale(language, pins, source, Rederivation.ChecksumMismatch)
    }
    if (readCoreVersion(coreBytes) != CompilerVersion.CURRENT) {
        return DecodedEdition.Stale(language, pins, source, Rederivation.LowererChanged)
    }
    return DecodedEdition.Fresh(Edition(language, decodeCore(coreBytes), pins, source))
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

private fun toPins(json: Json): Map<String, RevisionNumber> {
    if (json !is Json.JObj) reject("the document's \"pins\" must be an object of names to revisions")
    val pins = LinkedHashMap<String, RevisionNumber>(json.fields.size)
    json.fields.forEach { (name, revisionJson) ->
        val revision = toWholeNumber(revisionJson, "the revision of pin \"$name\"")
        if (revision < 1) reject("the revision of pin \"$name\" must be a whole number of 1 or more")
        pins[name] = RevisionNumber(revision)
    }
    return pins
}

private fun toChecksum(json: Json): Long {
    val text = (json as? Json.JStr)?.value
    val isHex = text != null && text.length == 16 && text.all { it in '0'..'9' || it in 'a'..'f' }
    if (text == null || !isHex) reject("the document's \"checksum\" must be a string of 16 lowercase hex digits")
    return text.toULong(16).toLong()
}
