package klein.types

import klein.types.SimpleType.*

object TypePrinter {
    fun print(type: SimpleType): String = Printer().print(type)

    private class Printer {
        private val varNames = mutableMapOf<TVar, String>()
        private var nextVarId = 0

        fun print(
            type: SimpleType,
            printBounds: Boolean = true,
        ): String =
            when (type) {
                TNum -> "Num"
                TString -> "String"
                TBool -> "Bool"
                TUnit -> "Unit"
                TTop -> "Any"
                TBottom -> "Nothing"
                is TVar -> printVar(type, printBounds)
                is TFun -> printFun(type, printBounds)
                is TRecord -> printRecord(type, printBounds)
            }

        private fun printVar(
            tv: TVar,
            printBounds: Boolean,
        ): String {
            val name = varNames.getOrPut(tv) { varName(nextVarId++) }
            if (!printBounds) return name

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
        }

        private fun varName(id: Int): String {
            val letter = 'a' + (id % 26)
            val suffix = if (id >= 26) "${id / 26}" else ""
            return "$letter$suffix"
        }

        private fun printAsBound(type: SimpleType): String {
            val printed = print(type, printBounds = false)
            return if (type is TFun) "($printed)" else printed
        }

        private fun printFun(
            fn: TFun,
            printBounds: Boolean,
        ): String {
            val params = "(${fn.params.joinToString(", ") { print(it, printBounds) }})"
            return "$params -> ${print(fn.result, printBounds)}"
        }

        private fun printRecord(
            rec: TRecord,
            printBounds: Boolean,
        ): String {
            if (rec.fields.isEmpty()) {
                return "{}"
            }
            val fields =
                rec.fields.entries
                    .sortedBy { it.key }
                    .joinToString(", ") { (k, v) -> "$k: ${print(v, printBounds)}" }
            return "{ $fields }"
        }
    }
}
