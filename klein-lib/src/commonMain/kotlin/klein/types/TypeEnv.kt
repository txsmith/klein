package klein.types

sealed class ImplicitParamContext {
    data object None : ImplicitParamContext()

    data object BlockedByNamedFunction : ImplicitParamContext()

    data class BlockedByExplicitParams(
        val params: List<String>,
    ) : ImplicitParamContext()

    data class Available(
        val type: SimpleType,
    ) : ImplicitParamContext()
}

/**
 * Binding in the type environment - either a simple type or a polymorphic type scheme.
 */
sealed class TypeBinding {
    data class Mono(
        val type: SimpleType,
    ) : TypeBinding()

    data class Poly(
        val generalizationLevel: Int,
        val body: SimpleType,
    ) : TypeBinding() {
        fun instantiate(currentLevel: Int): SimpleType = body.freshenAbove(generalizationLevel, currentLevel)
    }
}

class TypeEnv(
    private val parent: TypeEnv? = null,
    private val bindings: MutableMap<String, TypeBinding> = mutableMapOf(),
    val implicitParam: ImplicitParamContext = ImplicitParamContext.None,
    val level: Int = 0,
    private val typeDefs: MutableMap<String, TypeDefInfo> = mutableMapOf(),
    private val constructors: MutableMap<String, ConstructorInfo> = mutableMapOf(),
) {
    fun lookupAndInstantiate(
        name: String,
        currentLevel: Int = this.level,
    ): SimpleType? {
        val binding = bindings[name]
        if (binding != null) {
            return when (binding) {
                is TypeBinding.Mono -> binding.type
                is TypeBinding.Poly -> binding.instantiate(currentLevel)
            }
        }
        return parent?.lookupAndInstantiate(name, currentLevel)
    }

    fun contains(name: String): Boolean = name in bindings

    fun bind(
        name: String,
        type: SimpleType,
    ) {
        bindings[name] = TypeBinding.Mono(type)
    }

    fun bindPolymorphic(
        name: String,
        type: SimpleType,
    ) {
        bindings[name] = TypeBinding.Poly(level, type)
    }

    fun freshVar(): SimpleType.TVar = SimpleType.TVar(level)

    fun child(implicitParam: ImplicitParamContext = this.implicitParam): TypeEnv =
        TypeEnv(parent = this, implicitParam = implicitParam, level = level, typeDefs = typeDefs, constructors = constructors)

    fun enterBindingScope(): TypeEnv =
        TypeEnv(parent = this, implicitParam = implicitParam, level = level + 1, typeDefs = typeDefs, constructors = constructors)

    fun registerTypeDef(info: TypeDefInfo) {
        typeDefs[info.name] = info
    }

    fun lookupTypeDef(name: String): TypeDefInfo? = typeDefs[name]

    fun registerConstructor(info: ConstructorInfo) {
        constructors[info.name] = info
    }

    fun lookupConstructor(name: String): ConstructorInfo? = constructors[name]

    companion object {
        fun empty(): TypeEnv = TypeEnv()
    }
}
