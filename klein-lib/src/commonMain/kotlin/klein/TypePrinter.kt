package klein

/**
 * Pretty-prints types for display in error messages and inference results.
 */
object TypePrinter {
    /**
     * Convert a Type to its display string representation.
     */
    fun print(type: Type): String =
        when (type) {
            Type.TInt -> "Int"
            Type.TDouble -> "Double"
            Type.TString -> "String"
            Type.TBool -> "Bool"
            Type.TUnit -> "Unit"
            Type.TTop -> "Top"
            Type.TBottom -> "Bottom"
            is Type.TVar -> varName(type.id)
            is Type.TFun -> printFun(type)
            is Type.TRecord -> printRecord(type)
        }

    /**
     * Convert a type variable ID to a readable name.
     *
     * 0 -> a, 1 -> b, ..., 25 -> z, 26 -> a1, 27 -> b1, etc.
     *
     * Note: No quote prefix (use `a` not `'a`)
     */
    private fun varName(id: Int): String {
        val letter = 'a' + (id % 26)
        val suffix = if (id >= 26) "${id / 26}" else ""
        return "$letter$suffix"
    }

    /**
     * Print a function type.
     *
     * Single param: `Int -> Int`
     * Multi param: `(Int, String) -> Bool`
     * Zero param: `() -> Int`
     */
    private fun printFun(fn: Type.TFun): String {
        val params =
            when (fn.params.size) {
                0 -> "()"
                1 -> printParamWithParens(fn.params[0])
                else -> "(${fn.params.joinToString(", ") { print(it) }})"
            }
        return "$params -> ${print(fn.result)}"
    }

    /**
     * Print a single parameter, wrapping in parentheses if it's a function type.
     * This avoids ambiguity like `A -> B -> C` when the input is `(A -> B) -> C`.
     */
    private fun printParamWithParens(type: Type): String =
        when (type) {
            is Type.TFun -> "(${print(type)})"
            else -> print(type)
        }

    /**
     * Print a record type.
     *
     * Empty record: `{}`
     * With fields: `{ age: Int, name: String }` (sorted alphabetically)
     */
    private fun printRecord(rec: Type.TRecord): String {
        if (rec.fields.isEmpty()) {
            return "{}"
        }
        val fields =
            rec.fields.entries
                .sortedBy { it.key }
                .joinToString(", ") { (k, v) ->
                    "$k: ${print(v)}"
                }
        return "{ $fields }"
    }
}
