package klein.types

import klein.Type
import klein.types.SimpleType.*

object TypePrinter {
    /**
     * Print a Type directly.
     */
    fun print(type: Type): String = Type.print(type)

    /**
     * Print TypeComponents representation after canonicalization but before coalescing.
     */
    fun printCompact(
        type: SimpleType,
        env: TypeEnv,
    ): String {
        val scheme = TypeComponents.canonicalizeType(type, true, env)
        return CompactPrinter(scheme.recVars).print(scheme.term, scheme.pol)
    }

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
     * Printer for TypeComponents representation.
     */
    private class CompactPrinter(
        private val recVars: Map<TVar, TypeComponents>,
    ) {
        private val varNames = mutableMapOf<TVar, String>()
        private var nextVarId = 0

        fun print(
            term: TypeComponents,
            pol: Boolean,
        ): String {
            val prefix = if (pol) "+" else "-"
            return "$prefix${printType(term, indent = 0)}"
        }

        private fun varName(tv: TVar): String =
            varNames.getOrPut(tv) {
                val id = nextVarId++
                val letter = 'A' + (id % 26)
                val suffix = if (id >= 26) "${id / 26}" else ""
                "'$letter$suffix"
            }

        private fun printType(
            ty: TypeComponents,
            indent: Int,
        ): String {
            if (ty.isEmpty()) return "TypeComponents()"

            val pad = "  ".repeat(indent)
            val innerPad = "  ".repeat(indent + 1)
            val fields = mutableListOf<String>()

            if (ty.vars.isNotEmpty()) {
                val varsStr =
                    ty.vars.sortedByDescending { it.uid }.joinToString(", ") { v ->
                        val bound = recVars[v]
                        if (bound != null) "μ${varName(v)}" else varName(v)
                    }
                fields.add("${innerPad}vars: [$varsStr]")
            }

            if (ty.prims.isNotEmpty()) {
                val primsStr =
                    ty.prims.joinToString(", ") { prim ->
                        when (prim) {
                            TypeComponents.PrimType.Num -> "Num"
                            TypeComponents.PrimType.String -> "String"
                            TypeComponents.PrimType.Bool -> "Bool"
                            TypeComponents.PrimType.Unit -> "()"
                            TypeComponents.PrimType.Top -> "Any"
                            TypeComponents.PrimType.Bottom -> "Nothing"
                        }
                    }
                fields.add("${innerPad}prims: [$primsStr]")
            }

            ty.rec?.let { rec ->
                val fieldsStr =
                    rec.fields.entries.sortedBy { it.key }.joinToString(",\n") { (k, v) ->
                        "$innerPad  $k:\n${printSub(v, indent + 3)}"
                    }
                fields.add("${innerPad}rec: {\n$fieldsStr\n$innerPad}")
            }

            ty.func?.let { (params, result) ->
                val paramsStr =
                    params
                        .mapIndexed { i, p ->
                            "$innerPad  param$i:\n${printSub(p, indent + 3)}"
                        }.joinToString(",\n")
                val resultStr = printSub(result, indent + 3)
                fields.add("${innerPad}func: (\n$paramsStr\n$innerPad) ->\n$resultStr")
            }

            ty.optional?.let { inner ->
                fields.add("${innerPad}optional:\n${printSub(inner, indent + 2)}")
            }

            if (ty.refs.isNotEmpty()) {
                val refsStr =
                    ty.allRefs().sortedBy { it.name }.joinToString(", ") { ref ->
                        if (ref.args.isEmpty()) {
                            ref.name
                        } else {
                            "${ref.name}<\n${ref.args.joinToString(",\n") { arg ->
                                when (arg) {
                                    is RefArg.Resolved -> printSub(arg.components, indent + 2)
                                    is RefArg.Invariant -> {
                                        val invPad = "  ".repeat(indent + 2)
                                        "${invPad}inv(+${printType(arg.pos, indent + 3)}, -${printType(arg.neg, indent + 3)})"
                                    }
                                }
                            }}\n$innerPad>"
                        }
                    }
                fields.add("${innerPad}refs: [$refsStr]")
            }

            return "TypeComponents(\n${fields.joinToString(",\n")}\n$pad)"
        }

        private fun printSub(
            ty: TypeComponents,
            indent: Int,
        ): String {
            val pad = "  ".repeat(indent)
            return if (ty.isEmpty()) pad else "$pad${printType(ty, indent)}"
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
                TNum, TString, TBool, TNull, TUnit, TTop, TBottom -> type.toString()
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
