package klein.jsbinding

import klein.interp.Value
import kotlin.js.collections.JsReadonlyArray
import kotlin.js.collections.toList

@JsExport
class Struct(
    val tag: String?,
    val names: JsReadonlyArray<String>,
    val values: JsReadonlyArray<Any?>,
)

@JsExport
fun printValue(value: Any?): String = Value.print(fromJs(value))

internal fun toJs(value: Value): Any? =
    when (value) {
        is Value.VNum -> value.value
        is Value.VStr -> value.value
        is Value.VBool -> value.value
        Value.VNull -> null
        Value.VUnit -> undefined
        is Value.VStruct -> Struct(value.tag, value.fields.keys.toList().toJs(), value.fields.values.map(::toJs).toJs())
        else -> throw IllegalStateException("a function value cannot cross to JavaScript")
    }

internal fun toJs(values: List<Value>): JsReadonlyArray<Any?> = values.map(::toJs).toJs()

internal fun fromJs(value: Any?): Value =
    when {
        jsTypeOf(value) == "undefined" -> Value.VUnit
        value == null -> Value.VNull
        value is Struct -> Value.VStruct(value.tag, value.names.toList().zip(value.values.toList().map(::fromJs)).toMap())
        jsTypeOf(value) == "boolean" -> Value.VBool(value as Boolean)
        jsTypeOf(value) == "string" -> Value.VStr(value as String)
        jsTypeOf(value) == "number" -> Value.VNum((value as Number).toDouble())
        else -> throw IllegalArgumentException("$value is not a Klein value")
    }

internal fun fromJs(values: JsReadonlyArray<Any?>): List<Value> = values.toList().map(::fromJs)
