package klein.types

sealed class SimpleType {
    /**
     * Get the level of this type. For TVars, returns the level.
     * For compound types, returns the minimum level of all contained TVars.
     * For ground types, returns 0 (always safe to use).
     */
    open val level: Int
        get() = 0

    /**
     * Create a fresh copy of this type, only freshening TVars above the given level.
     * TVars at or below the limit are kept as-is (they're from outer scope).
     * This is used for let polymorphism - only generalize variables created in the binding.
     *
     * @param above Only freshen TVars with level > above
     * @param currentLevel The level to assign to fresh TVars
     */
    fun freshenAbove(
        above: Int,
        currentLevel: Int,
    ): SimpleType {
        val varMap = mutableMapOf<TVar, TVar>()

        fun freshen(ty: SimpleType): SimpleType =
            when {
                ty.level <= above -> ty
                ty is TVar -> {
                    // Check if we've already started processing this TVar
                    varMap[ty]?.let { return it }
                    // Create fresh TVar first (without bounds) to handle cycles
                    val fresh = TVar(currentLevel)
                    varMap[ty] = fresh
                    // Process bounds in order to preserve relative UID ordering
                    // of type variables (important for simplification)
                    ty.lowerBounds.forEach { fresh.lowerBounds.add(freshen(it)) }
                    ty.upperBounds.forEach { fresh.upperBounds.add(freshen(it)) }
                    fresh
                }
                ty is TFun ->
                    TFun(
                        ty.params.map { freshen(it) },
                        freshen(ty.result),
                    )
                ty is TRecord ->
                    TRecord(
                        ty.fields.mapValues { freshen(it.value) },
                    )
                else -> ty
            }

        return freshen(this)
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

    class TVar(
        override val level: Int = 0,
        val lowerBounds: MutableSet<SimpleType> = mutableSetOf(),
        val upperBounds: MutableSet<SimpleType> = mutableSetOf(),
    ) : SimpleType() {
        val uid: Int = nextUid++

        companion object {
            private var nextUid = 0
        }
    }

    data class TFun(
        val params: List<SimpleType>,
        val result: SimpleType,
    ) : SimpleType() {
        override val level: Int
            get() = (params.map { it.level } + result.level).maxOrNull() ?: 0
    }

    data class TRecord(
        val fields: Map<String, SimpleType>,
    ) : SimpleType() {
        override val level: Int
            get() = fields.values.maxOfOrNull { it.level } ?: 0
    }
}
