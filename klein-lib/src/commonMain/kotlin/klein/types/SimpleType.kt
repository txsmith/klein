package klein.types

import klein.AppliedTypeExpr
import klein.FunctionTypeExpr
import klein.IntersectionTypeExpr
import klein.RecordTypeExpr
import klein.SourceSpan
import klein.TupleTypeExpr
import klein.TypeExpr
import klein.TypeName
import klein.TypeVar
import klein.UnionTypeExpr

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

    class TSkolem(
        override val level: Int,
        val name: String,
    ) : SimpleType() {
        val uid: Int = nextUid++

        override fun toString(): String = "'$name"

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

        /**
         * Resolve a type expression, collecting any name-resolution errors along the way.
         *
         * Type variables:
         * - When [isEnvClosed] is true, they must already be in scope via
         *   [TypeEnv.lookupTypeVar] (walks the parent chain). Unknown names produce
         *   [TypeError.UnboundTypeVar] and fall back to a fresh TVar.
         * - When [isEnvClosed] is false, a name not present in the *local* scope is
         *   introduced into [env] — as a [TSkolem] if [rigid], otherwise a flexible TVar.
         *   Inner scopes shadow outer ones: lookup is local-only, so same-named outer
         *   type vars don't leak into the new signature.
         *
         * Type names and applied types:
         * - Unknown names produce [TypeError.UnboundVariable].
         * - Wrong number of type arguments produces [TypeError.TypeArityMismatch].
         *
         * @return the resolved type along with any errors collected during resolution
         */
        fun fromTypeExpr(
            typeExpr: TypeExpr,
            env: TypeEnv,
            rigid: Boolean = false,
            isEnvClosed: Boolean = false,
        ): Pair<SimpleType, List<TypeError>> {
            val errors = mutableListOf<TypeError>()

            /** Report [error] and return a fresh TVar as a fallback so resolution can continue. */
            fun fail(error: TypeError): SimpleType {
                errors.add(error)
                return env.freshVar()
            }

            fun resolveTypeVar(t: TypeVar): SimpleType {
                val existing = env.lookupTypeVar(t.name)
                if (existing != null) return existing
                if (isEnvClosed) return fail(TypeError.UnboundTypeVar(t.name, t.span))
                val fresh =
                    if (rigid) TSkolem(env.level, t.name)
                    else env.freshVar(nameHint = t.name)
                env.bindTypeVar(t.name, fresh)
                return fresh
            }

            fun go(t: TypeExpr): SimpleType =
                when (t) {
                    is TypeVar -> resolveTypeVar(t)

                    is TypeName -> {
                        val prim = fromName(t.name)
                        val typeDef = env.lookupTypeDef(t.name)
                        when {
                            prim != null -> prim
                            typeDef == null -> fail(TypeError.UnboundVariable(t.name, t.span))
                            typeDef.typeParams.isNotEmpty() ->
                                fail(TypeError.TypeArityMismatch(t.name, typeDef.typeParams.size, 0, t.span))
                            else -> TRef(t.name, emptyList(), t.span)
                        }
                    }

                    is AppliedTypeExpr -> {
                        val typeDef = env.lookupTypeDef(t.name)
                        // Recurse into args regardless — we want to collect errors inside them too.
                        val resolvedArgs = t.args.map(::go)
                        when {
                            typeDef == null -> fail(TypeError.UnboundVariable(t.name, t.span))
                            typeDef.typeParams.size != t.args.size ->
                                fail(TypeError.TypeArityMismatch(t.name, typeDef.typeParams.size, t.args.size, t.span))
                            else -> TRef(t.name, resolvedArgs, t.span)
                        }
                    }

                    is FunctionTypeExpr ->
                        TFun(t.paramTypes.map(::go), go(t.returnType))

                    is TupleTypeExpr -> {
                        if (t.elements.isEmpty()) {
                            TUnit
                        } else {
                            TRecord(
                                t.elements.mapIndexed { i, elem -> "_$i" to go(elem) }.toMap(),
                            )
                        }
                    }

                    is RecordTypeExpr ->
                        TRecord(t.fields.associate { (name, type) -> name to go(type) })

                    is UnionTypeExpr -> fail(TypeError.UnsupportedAnnotation("union types", t.span))

                    is IntersectionTypeExpr -> fail(TypeError.UnsupportedAnnotation("intersection types", t.span))
                }

            return go(typeExpr) to errors.toList()
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
        val skolemMap = mutableMapOf<TSkolem, TVar>()

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
                ty is TSkolem -> skolemMap.getOrPut(ty) { TVar(currentLevel, nameHint = ty.name) }
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
