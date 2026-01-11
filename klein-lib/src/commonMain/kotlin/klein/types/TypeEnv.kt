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
        val scheme: PolymorphicType,
    ) : TypeBinding()
}

class TypeEnv(
    private val parent: TypeEnv? = null,
    private val bindings: MutableMap<String, TypeBinding> = mutableMapOf(),
    val implicitParam: ImplicitParamContext = ImplicitParamContext.None,
    val level: Int = 0,
) {
    fun lookup(
        name: String,
        currentLevel: Int = this.level,
    ): SimpleType? {
        val binding = bindings[name]
        if (binding != null) {
            return when (binding) {
                is TypeBinding.Mono -> binding.type
                is TypeBinding.Poly -> binding.scheme.instantiate(currentLevel)
            }
        }
        return parent?.lookup(name, currentLevel)
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
        generalizationLevel: Int,
    ) {
        bindings[name] = TypeBinding.Poly(PolymorphicType(generalizationLevel, type))
    }

    fun child(
        implicitParam: ImplicitParamContext = this.implicitParam,
        level: Int = this.level,
    ): TypeEnv = TypeEnv(parent = this, implicitParam = implicitParam, level = level)

    companion object {
        fun empty(): TypeEnv = TypeEnv()
    }
}
