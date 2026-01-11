package klein

sealed class Type {
    object TInt : Type() {
        override fun toString(): String = "TInt"
    }

    object TDouble : Type() {
        override fun toString(): String = "TDouble"
    }

    object TString : Type() {
        override fun toString(): String = "TString"
    }

    object TBool : Type() {
        override fun toString(): String = "TBool"
    }

    object TUnit : Type() {
        override fun toString(): String = "TUnit"
    }

    object TTop : Type() {
        override fun toString(): String = "TTop"
    }

    object TBottom : Type() {
        override fun toString(): String = "TBottom"
    }

    class TVar(
        val lowerBounds: MutableSet<Type> = mutableSetOf(),
        val upperBounds: MutableSet<Type> = mutableSetOf(),
    ) : Type()

    data class TFun(
        val params: List<Type>,
        val result: Type,
    ) : Type()

    data class TRecord(
        val fields: Map<String, Type>,
    ) : Type()
}
