package klein.types

import klein.types.SimpleType.*

object TypePrinter {
    /**
     * Print a type with simplification applied.
     * This is the main entry point for user-facing type display.
     */
    fun print(type: SimpleType): String = Printer().print(TypeSimplifier.simplify(type))

    /**
     * Print a type without simplification, showing bounds in a clean format.
     *
     * Output format:
     * ```
     * (a) -> b
     * where
     *   Num :> a
     *   a :> b <: String
     * ```
     */
    fun printRaw(type: SimpleType): String = RawPrinter().print(type)

    /**
     * Simple printer that doesn't show bounds (used after simplification).
     */
    private class Printer {
        private val varNames = mutableMapOf<TVar, String>()
        private var nextVarId = 0

        fun print(type: SimpleType): String =
            when (type) {
                TNum -> "Num"
                TString -> "String"
                TBool -> "Bool"
                TUnit -> "Unit"
                TTop -> "Any"
                TBottom -> "Nothing"
                is TVar -> varName(type)
                is TFun -> printFun(type)
                is TRecord -> printRecord(type)
            }

        private fun varName(tv: TVar): String =
            varNames.getOrPut(tv) { generateVarName(nextVarId++) }

        private fun generateVarName(id: Int): String {
            val letter = 'a' + (id % 26)
            val suffix = if (id >= 26) "${id / 26}" else ""
            return "$letter$suffix"
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

    /**
     * Raw printer that shows type structure with bounds in a where clause.
     */
    private class RawPrinter {
        private val varNames = mutableMapOf<TVar, String>()
        private var nextVarId = 0
        private val seenVars = mutableSetOf<TVar>()
        private val visiting = mutableSetOf<TVar>()

        fun print(type: SimpleType): String {
            // First pass: collect all variables and assign names
            collectVars(type)

            // Print the type structure
            val typeStr = printType(type)

            // Collect constraints for all variables with bounds
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
                        varName(type) // Assign name
                        // Collect vars from bounds (but don't recurse infinitely)
                        if (type !in visiting) {
                            visiting.add(type)
                            type.lowerBounds.forEach { collectVars(it) }
                            type.upperBounds.forEach { collectVars(it) }
                            visiting.remove(type)
                        }
                    }
                }
                is TFun -> {
                    type.params.forEach { collectVars(it) }
                    collectVars(type.result)
                }
                is TRecord -> {
                    type.fields.values.forEach { collectVars(it) }
                }
                else -> {}
            }
        }

        private fun printType(type: SimpleType): String =
            when (type) {
                TNum -> "Num"
                TString -> "String"
                TBool -> "Bool"
                TUnit -> "Unit"
                TTop -> "Any"
                TBottom -> "Nothing"
                is TVar -> varName(type)
                is TFun -> printFun(type)
                is TRecord -> printRecord(type)
            }

        private fun varName(tv: TVar): String =
            varNames.getOrPut(tv) { generateVarName(nextVarId++) }

        private fun generateVarName(id: Int): String {
            val letter = 'a' + (id % 26)
            val suffix = if (id >= 26) "${id / 26}" else ""
            return "$letter$suffix"
        }

        private fun printFun(fn: TFun): String {
            val params = "(${fn.params.joinToString(", ") { printType(it) }})"
            return "$params -> ${printType(fn.result)}"
        }

        private fun printRecord(rec: TRecord): String {
            if (rec.fields.isEmpty()) {
                return "{}"
            }
            val fields =
                rec.fields.entries
                    .sortedBy { it.key }
                    .joinToString(", ") { (k, v) -> "$k: ${printType(v)}" }
            return "{ $fields }"
        }

        private fun buildConstraints(): String {
            val lines = mutableListOf<String>()

            // Sort variables by name for consistent output
            val sortedVars = varNames.entries.sortedBy { it.value }

            for ((tv, name) in sortedVars) {
                val lower = tv.lowerBounds
                    .filter { it != TBottom }
                    .map { printBound(it) }
                    .sorted()

                val upper = tv.upperBounds
                    .filter { it != TTop }
                    .map { printBound(it) }
                    .sorted()

                if (lower.isNotEmpty() || upper.isNotEmpty()) {
                    val line = buildString {
                        append("    ")
                        if (lower.isNotEmpty()) {
                            append(lower.joinToString(", "))
                            append(" :> ")
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
            // Wrap function types in parens for clarity
            return if (type is TFun) "($str)" else str
        }
    }
}
