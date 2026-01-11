package klein.types

sealed class SimpleType {
    /**
     * Create a fresh copy of this type with all type variables replaced by new ones.
     * This is used for let polymorphism - each use of a polymorphic binding gets
     * fresh type variables so constraints don't pollute the original.
     */
    fun instantiate(): SimpleType {
        val varMap = mutableMapOf<TVar, TVar>()
        // First pass: create structure with fresh TVars (no bounds yet)
        val result = instantiateStructure(varMap)
        // Second pass: copy bounds (instantiating any TVars within bounds)
        for ((original, fresh) in varMap) {
            original.lowerBounds.mapTo(fresh.lowerBounds) { it.instantiateStructure(varMap) }
            original.upperBounds.mapTo(fresh.upperBounds) { it.instantiateStructure(varMap) }
        }
        return result
    }

    private fun instantiateStructure(varMap: MutableMap<TVar, TVar>): SimpleType =
        when (this) {
            is TVar -> varMap.getOrPut(this) { TVar() }
            is TFun ->
                TFun(
                    params.map { it.instantiateStructure(varMap) },
                    result.instantiateStructure(varMap),
                )
            is TRecord ->
                TRecord(
                    fields.mapValues { it.value.instantiateStructure(varMap) },
                )
            else -> this
        }

    object TNum : SimpleType() {
        override fun toString(): String = "TNum"
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
