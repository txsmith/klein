package klein.host

import klein.Diagnostic
import klein.KleinException
import klein.SourceSpan
import klein.interp.RuntimeError
import klein.interp.Value

private const val FORMAT_MARKER = "klein-effect-log"
private const val JSON_VERSION = 1

fun encodeJson(log: EffectLog): String {
    val out = StringBuilder()
    out.append("{\"format\":\"")
    out.append(FORMAT_MARKER)
    out.append("\",\"version\":")
    out.append(JSON_VERSION)
    out.append(",\"entries\":[")
    log.entries.forEachIndexed { index, entry ->
        if (index > 0) out.append(',')
        out.writeEntry(entry)
    }
    out.append("]}")
    return out.toString()
}

fun decodeJson(text: String): EffectLog =
    try {
        readLog(text)
    } catch (malformed: MalformedJson) {
        reject(malformed.message)
    }

private fun readLog(text: String): EffectLog {
    val document = JsonReader(text).readDocument()
    if (document !is Json.JObj) reject("not an effect log: the document is not a JSON object")
    val marker = document.fields["format"]
    val markerText = (marker as? Json.JStr)?.value
    if (markerText != FORMAT_MARKER) reject("not an effect log: the document does not declare \"format\": \"$FORMAT_MARKER\"")
    document.expectOnly("the document", "format", "version", "entries")
    val versionJson = document.expectField("version", "the document")
    val version = toWholeNumber(versionJson, "the \"version\" field")
    if (version != JSON_VERSION) reject("unknown effect log version $version; this library reads version $JSON_VERSION")
    val entriesJson = document.expectField("entries", "the document")
    if (entriesJson !is Json.JArr) reject("the document's \"entries\" must be an array of log entries")
    val entries = entriesJson.items.map { toEntry(it) }
    if (entries.isEmpty()) reject("an effect log opens with its start entry; this document holds none")
    val start = entries.first() as? LogEntry.Start ?: reject("an effect log opens with its start entry")
    val replies = mutableListOf<LogEntry.Reply>()
    var ending: LogEntry.Ending? = null
    entries.drop(1).forEach { entry ->
        if (ending != null) reject("entries follow the log's ending")
        when (entry) {
            is LogEntry.Start -> reject("a second start entry mid-log")
            is LogEntry.Reply -> replies.add(entry)
            is LogEntry.Ending -> ending = entry
        }
    }
    return EffectLog(start, replies, ending)
}

private fun reject(message: String): Nothing = throw KleinException(listOf(UnreadableLog(message)))

private fun StringBuilder.writeEntry(entry: LogEntry) {
    when (entry) {
        is LogEntry.Start -> {
            append("{\"entry\":\"start\",\"inputs\":")
            writeValueFields(entry.inputs)
            append('}')
        }
        is LogEntry.Reply -> {
            append("{\"entry\":\"reply\",\"call\":")
            writeCall(entry.call)
            append(",\"answer\":")
            writeValue(entry.answer)
            append('}')
        }
        is LogEntry.Result -> {
            append("{\"entry\":\"result\",\"value\":")
            writeValue(entry.value)
            append('}')
        }
        is LogEntry.Failure -> {
            append("{\"entry\":\"failure\",\"errors\":[")
            entry.errors.forEachIndexed { index, diagnostic ->
                if (index > 0) append(',')
                writeDiagnostic(diagnostic)
            }
            append("]}")
        }
    }
}

private fun StringBuilder.writeCall(call: Call) {
    append("{\"name\":")
    writeText(call.name)
    append(",\"args\":[")
    call.args.forEachIndexed { index, arg ->
        if (index > 0) append(',')
        writeValue(arg)
    }
    append("]}")
}

private fun StringBuilder.writeDiagnostic(diagnostic: Diagnostic) {
    append("{\"message\":")
    writeText(diagnostic.message)
    append(",\"start\":")
    append(diagnostic.span.start)
    append(",\"end\":")
    append(diagnostic.span.end)
    append('}')
}

private fun StringBuilder.writeValue(value: Value) {
    when (value) {
        is Value.VNum -> writeNumber(value.value)
        is Value.VStr -> writeText(value.value)
        is Value.VBool -> append(value.value)
        Value.VNull -> append("null")
        Value.VUnit -> append("{\"unit\":true}")
        is Value.VStruct -> {
            append('{')
            val tag = value.tag
            if (tag != null) {
                append("\"tag\":")
                writeText(tag)
                append(',')
            }
            append("\"fields\":")
            writeValueFields(value.fields)
            append('}')
        }
        else -> throw IllegalArgumentException("a ${Value.print(value)} cannot be encoded: closures never cross the host boundary")
    }
}

private fun StringBuilder.writeValueFields(fields: Map<String, Value>) {
    append('{')
    var first = true
    fields.forEach { (name, field) ->
        if (!first) append(',')
        first = false
        writeText(name)
        append(':')
        writeValue(field)
    }
    append('}')
}

private fun toEntry(json: Json): LogEntry {
    if (json !is Json.JObj) reject("a log entry must be a JSON object")
    val kindJson = json.expectField("entry", "a log entry")
    if (kindJson !is Json.JStr) reject("a log entry's \"entry\" field must be a string")
    return when (val kind = kindJson.value) {
        "start" -> {
            json.expectOnly("a start entry", "entry", "inputs")
            val inputsJson = json.expectField("inputs", "a start entry")
            val inputs = toValueMap(inputsJson, "a start entry's \"inputs\"")
            LogEntry.Start(inputs)
        }
        "reply" -> {
            json.expectOnly("a reply entry", "entry", "call", "answer")
            val callJson = json.expectField("call", "a reply entry")
            val answerJson = json.expectField("answer", "a reply entry")
            LogEntry.Reply(toCall(callJson), toValue(answerJson))
        }
        "result" -> {
            json.expectOnly("a result entry", "entry", "value")
            val valueJson = json.expectField("value", "a result entry")
            LogEntry.Result(toValue(valueJson))
        }
        "failure" -> {
            json.expectOnly("a failure entry", "entry", "errors")
            val errorsJson = json.expectField("errors", "a failure entry")
            if (errorsJson !is Json.JArr) reject("a failure entry's \"errors\" must be an array")
            LogEntry.Failure(errorsJson.items.map { toDiagnostic(it) })
        }
        else -> reject("unknown log entry kind \"$kind\"")
    }
}

private fun toCall(json: Json): Call {
    if (json !is Json.JObj) reject("a reply's call must be a JSON object")
    json.expectOnly("a call", "name", "args")
    val nameJson = json.expectField("name", "a call")
    if (nameJson !is Json.JStr) reject("a call's \"name\" must be a string")
    val argsJson = json.expectField("args", "a call")
    if (argsJson !is Json.JArr) reject("a call's \"args\" must be an array")
    val args = argsJson.items.map { toValue(it) }
    return Call(nameJson.value, args)
}

private fun toDiagnostic(json: Json): Diagnostic {
    if (json !is Json.JObj) reject("a diagnostic must be a JSON object")
    json.expectOnly("a diagnostic", "message", "start", "end")
    val messageJson = json.expectField("message", "a diagnostic")
    if (messageJson !is Json.JStr) reject("a diagnostic's \"message\" must be a string")
    val start = toWholeNumber(json.expectField("start", "a diagnostic"), "a span's \"start\"")
    val end = toWholeNumber(json.expectField("end", "a diagnostic"), "a span's \"end\"")
    return RuntimeError(messageJson.value, SourceSpan(start, end))
}

private fun toValue(json: Json): Value =
    when (json) {
        is Json.JStr -> Value.VStr(json.value)
        is Json.JNum -> Value.VNum(json.value)
        is Json.JBool -> Value.VBool(json.value)
        Json.JNull -> Value.VNull
        is Json.JArr -> reject("a bare JSON array is not a Klein value")
        is Json.JObj -> toObjectValue(json)
    }

private fun toObjectValue(json: Json.JObj): Value {
    val keys = json.fields.keys
    return when {
        "bits" in keys -> {
            json.expectOnly("a raw-bits number", "bits")
            val bitsJson = json.fields["bits"]
            val bitsText = (bitsJson as? Json.JStr)?.value
            val isHex = bitsText != null && bitsText.length == 16 && bitsText.all { it in '0'..'9' || it in 'a'..'f' }
            if (bitsText == null || !isHex) reject("a number's \"bits\" must be a string of 16 lowercase hex digits")
            val bits = bitsText.toULong(16)
            Value.VNum(Double.fromBits(bits.toLong()))
        }
        "unit" in keys -> {
            json.expectOnly("a unit value", "unit")
            if (json.fields["unit"] != Json.JBool(true)) reject("a unit value is written {\"unit\": true}")
            Value.VUnit
        }
        "fields" in keys || "tag" in keys -> {
            json.expectOnly("a struct value", "tag", "fields")
            val tagJson = json.fields["tag"]
            if (tagJson != null && tagJson !is Json.JStr) reject("a struct's \"tag\" must be a string")
            val tag = (tagJson as? Json.JStr)?.value
            val fieldsJson = json.expectField("fields", "a struct value")
            val fields = toValueMap(fieldsJson, "a struct's \"fields\"")
            Value.VStruct(tag, fields)
        }
        else -> reject("an object value must carry \"fields\", \"unit\", or \"bits\"; this one has ${keys.joinToString(", ") { "\"$it\"" }}")
    }
}

private fun toValueMap(json: Json, owner: String): Map<String, Value> {
    if (json !is Json.JObj) reject("$owner must be a JSON object")
    val values = LinkedHashMap<String, Value>(json.fields.size)
    json.fields.forEach { (name, field) -> values[name] = toValue(field) }
    return values
}

private fun toWholeNumber(json: Json, owner: String): Int {
    if (json !is Json.JNum) reject("$owner must be a whole number")
    val value = json.value
    val whole = value.toInt()
    if (whole.toDouble() != value) reject("$owner must be a whole number")
    return whole
}
