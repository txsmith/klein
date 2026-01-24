package klein.types

import klein.Type
import klein.types.SimpleType.*

object TypePrinter {
    /**
     * Print a Type directly.
     */
    fun print(type: Type): String = Type.print(type)

    /**
     * Print a type without simplification, showing bounds in a clean format.
     *
     * Output format:
     * ```
     * (a) -> b
     *   where
     *     Num <: a
     *     a <: b <: String
     * ```
     */
    fun printRaw(type: SimpleType): String = RawPrinter().print(type)

    /**
     * Raw printer that shows type structure with bounds in a where clause.
     */
    private class RawPrinter {
        private val varNames = mutableMapOf<TVar, String>()
        private var nextVarId = 0
        private val seenVars = mutableSetOf<TVar>()
        private val visiting = mutableSetOf<TVar>()

        fun print(type: SimpleType): String {
            collectVars(type)
            val typeStr = printType(type)
            val constraints = buildConstraints()

            return if (constraints.isEmpty()) {
                typeStr
            } else {
                "$typeStr\n  where\n$constraints"
            }
        }

        private fun collectVars(type: SimpleType) {
            when (type) {
                is TVar -> {
                    if (type !in seenVars) {
                        seenVars.add(type)
                        varName(type)
                        if (type !in visiting) {
                            visiting.add(type)
                            type.lowerBounds.forEach { collectVars(it) }
                            type.upperBounds.forEach { collectVars(it) }
                            visiting.remove(type)
                        }
                    }
                }
                is TOptional -> {
                    collectVars(type.inner)
                }
                is TFun -> {
                    type.params.forEach { collectVars(it) }
                    collectVars(type.result)
                }
                is TRecord -> {
                    type.fields.values.forEach { collectVars(it) }
                }
                is TRef -> {
                    type.typeArgs.forEach { collectVars(it) }
                }
                else -> {}
            }
        }

        private fun printType(type: SimpleType): String =
            when (type) {
                TNum -> "Num"
                TString -> "String"
                TBool -> "Bool"
                TNull -> "Null"
                TUnit -> "Unit"
                is TOptional -> "${printType(type.inner)}?"
                is TVar -> varName(type)
                is TFun -> printFun(type)
                is TRecord -> printRecord(type)
                is TRef -> printRef(type)
            }

        private fun printRef(ref: TRef): String {
            val args = if (ref.typeArgs.isEmpty()) "" else "<${ref.typeArgs.joinToString(", ") { printType(it) }}>"
            return "${ref.name}$args"
        }

        private fun varName(tv: TVar): String = varNames.getOrPut(tv) { generateVarName(nextVarId++) }

        private fun generateVarName(id: Int): String {
            val letter = 'A' + (id % 26)
            val suffix = if (id >= 26) "${id / 26}" else ""
            return "'$letter$suffix"
        }

        private fun printFun(fn: TFun): String {
            val params = "(${fn.params.joinToString(", ") { printType(it) }})"
            return "$params -> ${printType(fn.result)}"
        }

        private fun printRecord(rec: TRecord): String {
            if (rec.fields.isEmpty()) return "{}"
            val fields =
                rec.fields.entries
                    .sortedBy { it.key }
                    .joinToString(", ") { (k, v) -> "$k: ${printType(v)}" }
            return "{ $fields }"
        }

        private fun buildConstraints(): String {
            val lines = mutableListOf<String>()
            val sortedVars = varNames.entries.sortedBy { it.value }

            for ((tv, name) in sortedVars) {
                val lower =
                    tv.lowerBounds
                        .map { printBound(it) }
                        .sorted()

                val upper =
                    tv.upperBounds
                        .map { printBound(it) }
                        .sorted()

                if (lower.isNotEmpty() || upper.isNotEmpty()) {
                    val line =
                        buildString {
                            append("    ")
                            if (lower.isNotEmpty()) {
                                append(lower.joinToString(", "))
                                append(" <: ")
                            }
                            append(name)
                            if (upper.isNotEmpty()) {
                                append(" <: ")
                                append(upper.joinToString(", "))
                            }
                        }
                    lines.add(line)
                }
            }

            return lines.joinToString("\n")
        }

        private fun printBound(type: SimpleType): String {
            val str = printType(type)
            return if (type is TFun) "($str)" else str
        }
    }
}
