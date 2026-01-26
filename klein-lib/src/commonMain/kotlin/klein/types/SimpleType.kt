package klein.types

import klein.SourceSpan

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
                ty is TOptional -> TOptional(freshen(ty.inner))
                ty is TRef ->
                    TRef(
                        ty.name,
                        ty.typeArgs.map { freshen(it) },
                        ty.span,
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

    object TNull : SimpleType() {
        override fun toString(): String = "TNull"
    }

    object TUnit : SimpleType() {
        override fun toString(): String = "TUnit"
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

    data class TRef(
        val name: String,
        val typeArgs: List<SimpleType>,
        val span: SourceSpan,
    ) : SimpleType() {
        override val level: Int
            get() = typeArgs.maxOfOrNull { it.level } ?: 0

        private var _expandedStructure: TRecord? = null

        fun expandToStructure(typeLookup: (String) -> TypeDefInfo?): TRecord {
            _expandedStructure?.let { return it }

            val typeDef = typeLookup(name) ?: error("Type '$name' not registered")
            check(typeArgs.size == typeDef.typeParams.size) {
                "Type '$name' expects ${typeDef.typeParams.size} args but got ${typeArgs.size}"
            }

            val substitution =
                typeDef.typeParams.zip(typeArgs).associate { (param, arg) ->
                    param.tvar to arg
                }

            fun substitute(ty: SimpleType): SimpleType =
                when (ty) {
                    is TVar -> substitution[ty] ?: ty
                    is TFun -> TFun(ty.params.map { substitute(it) }, substitute(ty.result))
                    is TRecord -> TRecord(ty.fields.mapValues { substitute(it.value) })
                    is TOptional -> TOptional(substitute(ty.inner))
                    is TRef -> TRef(ty.name, ty.typeArgs.map { substitute(it) }, ty.span)
                    else -> ty
                }

            return TRecord(typeDef.iface.fields.mapValues { substitute(it.value) }).also {
                _expandedStructure = it
            }
        }
    }
}
