package klein.js

import klein.interp.Value
import kotlin.js.collections.JsReadonlyArray

@JsExport
class TaggedValue(
    val tag: String,
    val fields: dynamic,
) {
    init {
        if (jsTypeOf(tag) != "string") throw unsupportedValue("a TaggedValue's tag must be a string, not $tag")
        if (fields == null || !isPlainObject(fields)) throw unsupportedValue("a TaggedValue's fields must be a plain object, not $fields")
    }
}

@JsExport
fun printValue(value: Any?): String = Value.print(fromJs(value))

internal fun toJs(value: Value): Any? =
    when (value) {
        is Value.VNum -> value.value
        is Value.VStr -> value.value
        is Value.VBool -> value.value
        Value.VNull -> null
        Value.VUnit -> undefined
        is Value.VStruct -> {
            val fields = toJsObject(value.fields)
            val tag = value.tag
            if (tag == null) fields else TaggedValue(tag, fields)
        }
        else -> throw IllegalStateException("a function value cannot cross to JavaScript")
    }

internal fun toJsObject(fields: Map<String, Value>): dynamic = frozenRecord(fields.mapValues { toJs(it.value) })

internal fun frozenRecord(entries: Map<String, Any?>): dynamic {
    val record: dynamic = js("({})")
    for ((name, entry) in entries) record[name] = entry
    return freeze(record)
}

internal fun <T> List<T>.frozen(): JsReadonlyArray<T> = freeze(toTypedArray()).unsafeCast<JsReadonlyArray<T>>()

private fun freeze(target: Any?): dynamic = js("Object.freeze")(target)

internal fun fromJs(value: Any?): Value =
    when {
        jsTypeOf(value) == "undefined" -> Value.VUnit
        value == null -> Value.VNull
        value is TaggedValue -> Value.VStruct(value.tag, fieldsFromJs(value.fields))
        jsTypeOf(value) == "boolean" -> Value.VBool(value as Boolean)
        jsTypeOf(value) == "string" -> Value.VStr(value as String)
        jsTypeOf(value) == "number" -> Value.VNum((value as Number).toDouble())
        isPlainObject(value) -> Value.VStruct(null, fieldsFromJs(value))
        else -> throw unsupportedValue("$value is not a Klein value: use a number, string, boolean, null, undefined, a plain object or a TaggedValue")
    }

private fun fieldsFromJs(record: dynamic): Map<String, Value> {
    val names = js("Object.keys")(record).unsafeCast<Array<String>>()
    return names.associateWith { fromJs(record[it]) }
}

private fun isPlainObject(value: Any): Boolean {
    if (jsTypeOf(value) != "object") return false
    val prototype = js("Object.getPrototypeOf")(value)
    return prototype == null || prototype === js("Object.prototype")
}
