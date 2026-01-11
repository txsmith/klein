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

class TypeEnv(
    private val parent: TypeEnv? = null,
    private val bindings: MutableMap<String, SimpleType> = mutableMapOf(),
    private val polymorphic: MutableSet<String> = mutableSetOf(),
    val implicitParam: ImplicitParamContext = ImplicitParamContext.None,
) {
    fun lookup(name: String): SimpleType? {
        val type = bindings[name]
        if (type != null) {
            return if (name in polymorphic) type.instantiate() else type
        }
        return parent?.lookup(name)
    }

    fun contains(name: String): Boolean = name in bindings

    fun bind(
        name: String,
        type: SimpleType,
        isPolymorphic: Boolean = false,
    ) {
        bindings[name] = type
        if (isPolymorphic) {
            polymorphic.add(name)
        }
    }

    fun child(implicitParam: ImplicitParamContext = this.implicitParam): TypeEnv = TypeEnv(parent = this, implicitParam = implicitParam)

    companion object {
        fun empty(): TypeEnv = TypeEnv()
    }
}
