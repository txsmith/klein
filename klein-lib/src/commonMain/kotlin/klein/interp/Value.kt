package klein.interp

/**
 * A Klein runtime value. The hierarchy is deliberately small and sealed: host interop later
 * grows by adding bindings, not by adding new value shapes.
 *
 * Equality: numbers, strings, booleans, null, unit, and structs compare structurally
 * (data classes). Closures compare by identity.
 */
sealed class Value {
    data class VNum(
        val value: Double,
    ) : Value()

    data class VStr(
        val value: String,
    ) : Value()

    data class VBool(
        val value: Boolean,
    ) : Value()

    data object VNull : Value()

    data object VUnit : Value()

    /** A closure: never in a public [Value] a host constructs or inspects — contracts.md §"No
     *  functions cross the boundary" means a capability never carries one either way. */
    internal class VClos(
        val arity: Int,
        val body: klein.core.CoreExpr,
        val scope: BindingScope,
    ) : Value()

    /** A record (tag = null) or a constructed value of a nominal sum type (tag = constructor). */
    data class VStruct(
        val tag: String?,
        val fields: Map<String, Value>,
    ) : Value()

    companion object {
        /** Render a value the way it would be written in Klein source. */
        fun print(value: Value): String =
            when (value) {
                is VNum -> printNum(value.value)
                is VStr -> "\"${value.value}\""
                is VBool -> value.value.toString()
                VNull -> "null"
                VUnit -> "()"
                is VStruct ->
                    when {
                        value.tag == null -> value.fields.entries.joinToString(", ", "{ ", " }") { (name, v) -> "$name = ${print(v)}" }
                        value.fields.isEmpty() -> value.tag
                        else -> value.fields.values.joinToString(", ", "${value.tag}(", ")") { print(it) }
                    }
                is VClos -> "<fun/${value.arity}>"
            }

        private fun printNum(value: Double): String =
            if (value.isFinite() && value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) {
                value.toLong().toString()
            } else {
                value.toString()
            }
    }
}
