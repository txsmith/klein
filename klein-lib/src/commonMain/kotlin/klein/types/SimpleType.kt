package klein.types

sealed class SimpleType {
    object TInt : SimpleType() {
        override fun toString(): String = "TInt"
    }

    object TDouble : SimpleType() {
        override fun toString(): String = "TDouble"
    }

    object TString : SimpleType() {
        override fun toString(): String = "TString"
    }

    object TBool : SimpleType() {
        override fun toString(): String = "TBool"
    }

    object TUnit : SimpleType() {
        override fun toString(): String = "TUnit"
    }

    object TTop : SimpleType() {
        override fun toString(): String = "TTop"
    }

    object TBottom : SimpleType() {
        override fun toString(): String = "TBottom"
    }

    class TVar(
        val lowerBounds: MutableSet<SimpleType> = mutableSetOf(),
        val upperBounds: MutableSet<SimpleType> = mutableSetOf(),
    ) : SimpleType()

    data class TFun(
        val params: List<SimpleType>,
        val result: SimpleType,
    ) : SimpleType()

    data class TRecord(
        val fields: Map<String, SimpleType>,
    ) : SimpleType()
}
