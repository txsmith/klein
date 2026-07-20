package klein.interp

import klein.Expr

/**
 * A Klein runtime value. The hierarchy is deliberately small and sealed: host interop later
 * grows by adding bindings (natives, data), not by adding new value shapes.
 *
 * Equality: numbers, strings, booleans, null, unit, records, and data values compare
 * structurally (data classes). Closures, constructors, and natives compare by identity.
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

    data class VRecord(
        val fields: Map<String, Value>,
    ) : Value()

    /** A constructed value of a nominal sum type, e.g. `Cons(head: 1, tail: Nil)`. */
    data class VData(
        val typeName: String,
        val constructorName: String,
        val fields: Map<String, Value>,
    ) : Value()

    /** A lambda or named function closed over its defining environment. */
    class VClosure(
        val params: List<String>,
        val isImplicit: Boolean,
        val body: Expr,
        val env: Env,
    ) : Value() {
        val arity: Int get() = if (isImplicit) 1 else params.size
    }

    /** A constructor with fields, waiting to be applied; nullary constructors bind as [VData] directly. */
    class VConstructor(
        val name: String,
        val typeName: String,
        val fieldNames: List<String>,
    ) : Value()

    /**
     * A declaration of a host function — the seam between Klein and its host. It carries no
     * code: applying one stops the machine and yields a [HostCall] to the host, which
     * executes it however it pleases and resumes with the result.
     */
    class VNative(
        val name: String,
        val arity: Int,
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
                is VRecord -> value.fields.entries.joinToString(", ", "{ ", " }") { (name, v) -> "$name = ${print(v)}" }
                is VData ->
                    if (value.fields.isEmpty()) {
                        value.constructorName
                    } else {
                        value.fields.values.joinToString(", ", "${value.constructorName}(", ")") { print(it) }
                    }
                is VClosure -> "<fun/${value.arity}>"
                is VConstructor -> "<constructor ${value.name}/${value.fieldNames.size}>"
                is VNative -> "<native ${value.name}/${value.arity}>"
            }

        private fun printNum(value: Double): String =
            if (value.isFinite() && value % 1.0 == 0.0 && kotlin.math.abs(value) < 1e15) {
                value.toLong().toString()
            } else {
                value.toString()
            }
    }
}
