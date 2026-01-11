package klein

import klein.Type.*

object TypePrinter {
    fun print(type: Type): String = Printer().print(type)

    private class Printer {
        private val varNames = mutableMapOf<TVar, String>()
        private var nextVarId = 0

        fun print(type: Type): String =
            when (type) {
                TInt -> "Int"
                TDouble -> "Double"
                TString -> "String"
                TBool -> "Bool"
                TUnit -> "Unit"
                TTop -> "Any"
                TBottom -> "Nothing"
                is TVar -> varNames.getOrPut(type) { varName(nextVarId++) }
                is TFun -> printFun(type)
                is TRecord -> printRecord(type)
            }

        private fun varName(id: Int): String {
            val letter = 'a' + (id % 26)
            val suffix = if (id >= 26) "${id / 26}" else ""
            return "$letter$suffix"
        }

        private fun printFun(fn: TFun): String {
            val params =
                when {
                    fn.params.isEmpty() -> "()"
                    fn.params.size == 1 && fn.params[0] !is TFun -> print(fn.params[0])
                    else -> "(${fn.params.joinToString(", ") { print(it) }})"
                }
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
