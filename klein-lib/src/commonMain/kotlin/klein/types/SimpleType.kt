package klein.types

import klein.SourceSpan

sealed class SimpleType {
    enum class Primitive(
        val typeName: String,
        val type: SimpleType,
    ) {
        Num("Num", TNum),
        Str("String", TString),
        Bool("Bool", TBool),
        Unit("Unit", TUnit),
    }

    object TNum : SimpleType() {
        override fun toString(): String = "Num"
    }

    object TString : SimpleType() {
        override fun toString(): String = "String"
    }

    object TBool : SimpleType() {
        override fun toString(): String = "Bool"
    }

    object TNull : SimpleType() {
        override fun toString(): String = "Null"
    }

    object TUnit : SimpleType() {
        override fun toString(): String = "Unit"
    }

    data class TOptional(
        val inner: SimpleType,
    ) : SimpleType() {
        override val level: Int
            get() = inner.level
    }

    class TVar(
        override val level: Int = 0,
        val lowerBounds: MutableSet<SimpleType> = mutableSetOf(),
        val upperBounds: MutableSet<SimpleType> = mutableSetOf(),
    ) : SimpleType() {
        val uid: Int = nextUid++

        override fun toString(): String {
            val letter = 'A' + (uid % 26)
            val suffix = if (uid >= 26) "${uid / 26}" else ""
            return "'$letter$suffix"
        }

        companion object {
            private var nextUid = 0
        }
    }

    data class TFun(
        val params: List<SimpleType>,
        val result: SimpleType,
        val paramNames: List<String> = emptyList(),
    ) : SimpleType() {
        override val level: Int
            get() = (params.map { it.level } + result.level).maxOrNull() ?: 0

        override fun toString(): String = "(${params.joinToString(", ")}) -> $result"
    }

    data class TRecord(
        val fields: Map<String, SimpleType>,
    ) : SimpleType() {
        override val level: Int
            get() = fields.values.maxOfOrNull { it.level } ?: 0
    }

    data class TRef(
        val name: String,
        val typeArgs: List<SimpleType>,
        val span: SourceSpan,
    ) : SimpleType() {
        override val level: Int
            get() = typeArgs.maxOfOrNull { it.level } ?: 0

        override fun toString(): String = if (typeArgs.isEmpty()) name else "$name<${typeArgs.joinToString(", ")}>"
    }

    /**
     * Get the level of this type. For TVars, returns the level.
     * For compound types, returns the minimum level of all contained TVars.
     * For ground types, returns 0 (always safe to use).
     */
    open val level: Int
        get() = 0

    companion object {
        val primitiveNames: Set<String> = Primitive.entries.map { it.typeName }.toSet()
        private val primitivesByName: Map<String, SimpleType> = Primitive.entries.associate { it.typeName to it.type }

        fun fromName(name: String): SimpleType? = primitivesByName[name]
    }

    /**
     * Used for copying types to the error context
     * Cloning ensures that errors can accumulate without the types getting more constraints on them as we go.
     */
    fun clone(): SimpleType = freshenAbove(-1, 0).component1()

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
    ): Pair<SimpleType, Map<TVar, TVar>> {
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
                        ty.paramNames,
                    )
                ty is TRecord ->
                    TRecord(
                        ty.fields.mapValues { freshen(it.value) },
                    )
                ty is TOptional -> TOptional(freshen(ty.inner))
                ty is TRef ->
                    TRef(
                        ty.name,
                        ty.typeArgs.map { freshen(it) },
                        ty.span,
                    )
                else -> ty
            }

        return freshen(this) to varMap.toMap()
    }
}
