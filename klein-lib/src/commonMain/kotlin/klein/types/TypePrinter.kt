package klein.types

import klein.types.SimpleType.*

object TypePrinter {
    /**
     * Print a type with simplification applied.
     * This is the main entry point for user-facing type display.
     */
    fun print(type: SimpleType): String = Printer().print(TypeSimplifier.simplify(type))

    /**
     * Print a type without simplification.
     * Useful for debugging and testing the raw inference output.
     */
    fun printRaw(type: SimpleType): String = Printer().print(type)

    private class Printer {
        private val varNames = mutableMapOf<TVar, String>()
        private var nextVarId = 0
        private val printingBoundsOf = mutableSetOf<TVar>()

        fun print(type: SimpleType): String =
            when (type) {
                TNum -> "Num"
                TString -> "String"
                TBool -> "Bool"
                TUnit -> "Unit"
                TTop -> "Any"
                TBottom -> "Nothing"
                is TVar -> printVar(type)
                is TFun -> printFun(type)
                is TRecord -> printRecord(type)
            }

        private fun printVar(tv: TVar): String {
            val name = varNames.getOrPut(tv) { varName(nextVarId++) }

            if (tv in printingBoundsOf) return name

            printingBoundsOf.add(tv)
            try {
                val lower =
                    tv.lowerBounds
                        .filter { it != TBottom }
                        .map { printAsBound(it) }
                        .sorted()
                val upper =
                    tv.upperBounds
                        .filter { it != TTop }
                        .map { printAsBound(it) }
                        .sorted()
                return buildString {
                    append(name)
                    if (lower.isNotEmpty()) {
                        append(" | ")
                        append(lower.joinToString(" | "))
                    }
                    if (upper.isNotEmpty()) {
                        append(" & ")
                        append(upper.joinToString(" & "))
                    }
                }
            } finally {
                printingBoundsOf.remove(tv)
            }
        }

        private fun varName(id: Int): String {
            val letter = 'a' + (id % 26)
            val suffix = if (id >= 26) "${id / 26}" else ""
            return "$letter$suffix"
        }

        private fun printAsBound(type: SimpleType): String {
            val printed = print(type)
            return if (type is TFun) "($printed)" else printed
        }

        private fun printFun(fn: TFun): String {
            val params = "(${fn.params.joinToString(", ") { print(it) }})"
            return "$params -> ${print(fn.result)}"
        }

        private fun printRecord(rec: TRecord): String {
            if (rec.fields.isEmpty()) {
                return "{}"
            }
            val fields =
                rec.fields.entries
                    .sortedBy { it.key }
                    .joinToString(", ") { (k, v) -> "$k: ${print(v)}" }
            return "{ $fields }"
        }
    }
}
