package klein

sealed class Type {
    data object Num : Type()

    data object Str : Type()

    data object Bool : Type()

    data object Unit : Type()

    data object Top : Type()

    data object Bottom : Type()

    data class Var(
        val name: String,
    ) : Type()

    data class Fun(
        val params: List<Type>,
        val result: Type,
    ) : Type()

    data class Record(
        val fields: Map<String, Type>,
    ) : Type()

    data class Rec(
        val varName: String,
        val body: Type,
    ) : Type()

    data class Union(
        val lhs: Type,
        val rhs: Type,
    ) : Type()

    data class Inter(
        val lhs: Type,
        val rhs: Type,
    ) : Type()

    override fun toString(): String = print(this)

    companion object {
        fun print(type: Type): String =
            when (type) {
                Num -> "Num"
                Str -> "String"
                Bool -> "Bool"
                Unit -> "Unit"
                Top -> "Any"
                Bottom -> "Nothing"
                is Var -> type.name
                is Fun -> printFun(type)
                is Record -> printRecord(type)
                is Rec -> "${print(type.body)} as ${type.varName}"
                is Union -> printUnion(type)
                is Inter -> printInter(type)
            }

        private fun printFun(fn: Fun): String {
            val params = "(${fn.params.joinToString(", ") { print(it) }})"
            return "$params -> ${print(fn.result)}"
        }

        private fun printRecord(rec: Record): String {
            if (rec.fields.isEmpty()) return "{}"
            val fields =
                rec.fields.entries
                    .sortedBy { it.key }
                    .joinToString(", ") { (k, v) -> "$k: ${print(v)}" }
            return "{ $fields }"
        }

        private fun printUnion(union: Union): String {
            val components = flattenUnion(union).sortedWith(typeComparator)
            return components.joinToString(" | ") { printWithParensIfNeeded(it, isUnionOrInter = true) }
        }

        private fun printInter(inter: Inter): String {
            val components = flattenInter(inter).sortedWith(typeComparator)
            return components.joinToString(" & ") { printWithParensIfNeeded(it, isUnionOrInter = true) }
        }

        private fun flattenUnion(type: Type): List<Type> =
            when (type) {
                is Union -> flattenUnion(type.lhs) + flattenUnion(type.rhs)
                else -> listOf(type)
            }

        private fun flattenInter(type: Type): List<Type> =
            when (type) {
                is Inter -> flattenInter(type.lhs) + flattenInter(type.rhs)
                else -> listOf(type)
            }

        private val typeComparator: Comparator<Type> =
            Comparator { a, b ->
                val orderA = typeOrder(a)
                val orderB = typeOrder(b)
                if (orderA != orderB) orderA - orderB else print(a).compareTo(print(b))
            }

        private fun typeOrder(type: Type): Int =
            when (type) {
                is Var -> 0
                Bool -> 1
                Num -> 2
                Str -> 3
                Unit -> 4
                is Record -> 5
                is Fun -> 6
                Top -> 7
                Bottom -> 8
                is Union -> 9
                is Inter -> 10
                is Rec -> 11
            }

        private fun printWithParensIfNeeded(
            type: Type,
            isUnionOrInter: Boolean,
        ): String {
            val str = print(type)
            return when (type) {
                is Fun -> "($str)"
                is Union, is Inter -> if (isUnionOrInter) str else "($str)"
                else -> str
            }
        }
    }
}
