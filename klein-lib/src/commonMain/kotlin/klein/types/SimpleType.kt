package klein.types

import klein.AppliedTypeExpr
import klein.FunctionTypeExpr
import klein.RecordTypeExpr
import klein.SourceSpan
import klein.TupleTypeExpr
import klein.TypeExpr
import klein.TypeName
import klein.TypeVar

sealed class SimpleType {
    enum class Primitive(
        val typeName: String,
        val type: SimpleType,
    ) {
        Num("Num", TNum),
        Str("String", TString),
        Bool("Bool", TBool),
        Unit("Unit", TUnit),
        Top("Any", TTop),
        Bottom("Nothing", TBottom),
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

    object TTop : SimpleType() {
        override fun toString(): String = "Any"
    }

    object TBottom : SimpleType() {
        override fun toString(): String = "Nothing"
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
        val nameHint: String? = null,
    ) : SimpleType() {
        val uid: Int = nextUid++

        override fun toString(): String {
            if (nameHint != null) return "'$nameHint"
            val letter = 'A' + (uid % 26)
            val suffix = if (uid >= 26) "${uid / 26}" else ""
            return "'$letter$suffix"
        }

        companion object {
            private var nextUid = 0

            /** Reset the uid counter at the start of a fresh inference so output is independent of prior runs. */
            internal fun resetUidCounter() {
                nextUid = 0
            }
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

        /**
         * Validate that all type names in a type expression are defined and used with correct arity.
         * Accumulates errors in the provided list; does not return anything.
         */
        fun validateTypeExprNames(
            typeExpr: TypeExpr,
            env: TypeEnv,
            errors: MutableList<TypeError>,
        ) {
            when (typeExpr) {
                is TypeVar -> {}
                is TypeName -> {
                    if (typeExpr.name in primitiveNames) return
                    val typeDef = env.lookupTypeDef(typeExpr.name)
                    if (typeDef == null) {
                        errors.add(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                    } else if (typeDef.typeParams.isNotEmpty()) {
                        errors.add(TypeError.TypeArityMismatch(typeExpr.name, typeDef.typeParams.size, 0, typeExpr.span))
                    }
                }
                is AppliedTypeExpr -> {
                    val typeDef = env.lookupTypeDef(typeExpr.name)
                    if (typeDef == null) {
                        errors.add(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                    } else if (typeExpr.args.size != typeDef.typeParams.size) {
                        errors.add(TypeError.TypeArityMismatch(typeExpr.name, typeDef.typeParams.size, typeExpr.args.size, typeExpr.span))
                    }
                    for (arg in typeExpr.args) {
                        validateTypeExprNames(arg, env, errors)
                    }
                }
                is FunctionTypeExpr -> {
                    for (param in typeExpr.paramTypes) validateTypeExprNames(param, env, errors)
                    validateTypeExprNames(typeExpr.returnType, env, errors)
                }
                is TupleTypeExpr -> {
                    for (element in typeExpr.elements) {
                        validateTypeExprNames(element, env, errors)
                    }
                }
                is RecordTypeExpr -> {
                    for ((_, fieldType) in typeExpr.fields) {
                        validateTypeExprNames(fieldType, env, errors)
                    }
                }
            }
        }

        /**
         * Convert a parsed type expression to a SimpleType.
         *
         * @param typeExpr The type expression to resolve
         * @param typeVarMap Map from type variable names to their resolved types (TVars or skolems)
         * @param env The type environment for looking up named types
         * @return The resolved SimpleType
         */
        fun fromTypeExpr(
            typeExpr: TypeExpr,
            typeVarMap: MutableMap<String, SimpleType>,
            env: TypeEnv,
        ): SimpleType =
            when (typeExpr) {
                is TypeVar ->
                    typeVarMap.getOrPut(typeExpr.name) {
                        env.freshVar(nameHint = typeExpr.name)
                    }

                is TypeName -> {
                    val prim = fromName(typeExpr.name)
                    val typeDef = env.lookupTypeDef(typeExpr.name)
                    when {
                        prim != null -> prim
                        typeDef != null && typeDef.typeParams.isEmpty() ->
                            TRef(typeExpr.name, emptyList(), typeExpr.span)
                        // Malformed (unknown or arity mismatch): fall back to freshVar.
                        // Validation is expected to have reported an error separately.
                        else -> env.freshVar()
                    }
                }

                is AppliedTypeExpr -> {
                    val typeDef = env.lookupTypeDef(typeExpr.name)
                    if (typeDef != null && typeDef.typeParams.size == typeExpr.args.size) {
                        val args = typeExpr.args.map { fromTypeExpr(it, typeVarMap, env) }
                        TRef(typeExpr.name, args, typeExpr.span)
                    } else {
                        // Malformed (unknown or arity mismatch): fall back to freshVar.
                        env.freshVar()
                    }
                }

                is FunctionTypeExpr ->
                    TFun(
                        typeExpr.paramTypes.map { fromTypeExpr(it, typeVarMap, env) },
                        fromTypeExpr(typeExpr.returnType, typeVarMap, env),
                    )

                is TupleTypeExpr -> {
                    if (typeExpr.elements.isEmpty()) {
                        TUnit
                    } else {
                        val fields =
                            typeExpr.elements
                                .mapIndexed { i, elem ->
                                    "_$i" to fromTypeExpr(elem, typeVarMap, env)
                                }.toMap()
                        TRecord(fields)
                    }
                }

                is RecordTypeExpr ->
                    TRecord(
                        typeExpr.fields.associate { (name, type) ->
                            name to fromTypeExpr(type, typeVarMap, env)
                        },
                    )
            }
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
                    val fresh = TVar(currentLevel, nameHint = ty.nameHint)
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
