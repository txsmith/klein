package klein.types

/**
 * Clean, immutable type representation for display and output.
 *
 * This is the final stage of the type pipeline:
 *   SimpleType (inference) → CompactType (simplification) → DisplayType (output)
 *
 * DisplayType has no mutable bounds or internal structure - it's a pure
 * representation of what the user sees.
 *
 * Following SimpleSub, we support:
 * - Union types (A | B) for positive position merges
 * - Intersection types (A & B) for negative position merges
 */
sealed class DisplayType {
    data object DNum : DisplayType()

    data object DString : DisplayType()

    data object DBool : DisplayType()

    data object DUnit : DisplayType()

    data object DTop : DisplayType()

    data object DBottom : DisplayType()

    data class DVar(
        val name: String,
    ) : DisplayType()

    data class DFun(
        val params: List<DisplayType>,
        val result: DisplayType,
    ) : DisplayType()

    data class DRecord(
        val fields: Map<String, DisplayType>,
    ) : DisplayType()

    data class DRec(
        val varName: String,
        val body: DisplayType,
    ) : DisplayType()

    data class DUnion(
        val lhs: DisplayType,
        val rhs: DisplayType,
    ) : DisplayType()

    data class DInter(
        val lhs: DisplayType,
        val rhs: DisplayType,
    ) : DisplayType()

    override fun toString(): String = DisplayTypePrinter.print(this)
}

object DisplayTypePrinter {
    fun print(type: DisplayType): String =
        when (type) {
            DisplayType.DNum -> "Num"
            DisplayType.DString -> "String"
            DisplayType.DBool -> "Bool"
            DisplayType.DUnit -> "Unit"
            DisplayType.DTop -> "Any"
            DisplayType.DBottom -> "Nothing"
            is DisplayType.DVar -> type.name
            is DisplayType.DFun -> printFun(type)
            is DisplayType.DRecord -> printRecord(type)
            is DisplayType.DRec -> "${print(type.body)} as ${type.varName}"
            is DisplayType.DUnion -> printUnion(type)
            is DisplayType.DInter -> printInter(type)
        }

    private fun printFun(fn: DisplayType.DFun): String {
        val params = "(${fn.params.joinToString(", ") { print(it) }})"
        return "$params -> ${print(fn.result)}"
    }

    private fun printRecord(rec: DisplayType.DRecord): String {
        if (rec.fields.isEmpty()) return "{}"
        val fields =
            rec.fields.entries
                .sortedBy { it.key }
                .joinToString(", ") { (k, v) -> "$k: ${print(v)}" }
        return "{ $fields }"
    }

    private fun printUnion(union: DisplayType.DUnion): String {
        val components = flattenUnion(union).sortedWith(typeComparator)
        return components.joinToString(" | ") { printWithParensIfNeeded(it, isUnionOrInter = true) }
    }

    private fun printInter(inter: DisplayType.DInter): String {
        val components = flattenInter(inter).sortedWith(typeComparator)
        return components.joinToString(" & ") { printWithParensIfNeeded(it, isUnionOrInter = true) }
    }

    private fun flattenUnion(type: DisplayType): List<DisplayType> =
        when (type) {
            is DisplayType.DUnion -> flattenUnion(type.lhs) + flattenUnion(type.rhs)
            else -> listOf(type)
        }

    private fun flattenInter(type: DisplayType): List<DisplayType> =
        when (type) {
            is DisplayType.DInter -> flattenInter(type.lhs) + flattenInter(type.rhs)
            else -> listOf(type)
        }

    private val typeComparator: Comparator<DisplayType> =
        Comparator { a, b ->
            val orderA = typeOrder(a)
            val orderB = typeOrder(b)
            if (orderA != orderB) orderA - orderB else print(a).compareTo(print(b))
        }

    private fun typeOrder(type: DisplayType): Int =
        when (type) {
            is DisplayType.DVar -> 0
            DisplayType.DBool -> 1
            DisplayType.DNum -> 2
            DisplayType.DString -> 3
            DisplayType.DUnit -> 4
            is DisplayType.DRecord -> 5
            is DisplayType.DFun -> 6
            DisplayType.DTop -> 7
            DisplayType.DBottom -> 8
            is DisplayType.DUnion -> 9
            is DisplayType.DInter -> 10
            is DisplayType.DRec -> 11
        }

    private fun printWithParensIfNeeded(
        type: DisplayType,
        isUnionOrInter: Boolean,
    ): String {
        val str = print(type)
        return when (type) {
            is DisplayType.DFun -> "($str)"
            is DisplayType.DUnion, is DisplayType.DInter -> if (isUnionOrInter) str else "($str)"
            else -> str
        }
    }
}
