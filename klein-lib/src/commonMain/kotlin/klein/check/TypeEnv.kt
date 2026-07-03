package klein.check


sealed class ImplicitParamContext {
    data object None : ImplicitParamContext()

    data object BlockedByNamedFunction : ImplicitParamContext()

    data class BlockedByExplicitParams(
        val params: List<String>,
    ) : ImplicitParamContext()

    data class Available(
        val type: Type,
    ) : ImplicitParamContext()
}

class TypeEnv private constructor(
    private val parent: TypeEnv?,
    private val bindings: MutableMap<String, Type> = mutableMapOf(),
    private val typeVars: MutableMap<String, Type.TSkolem> = mutableMapOf(),
    val implicitParam: ImplicitParamContext = ImplicitParamContext.None,
) {
    fun bind(
        name: String,
        type: Type,
    ) {
        bindings[name] = type
    }

    fun lookup(name: String): Type? = bindings[name] ?: parent?.lookup(name)

    fun bindTypeVar(
        name: String,
        skolem: Type.TSkolem,
    ) {
        typeVars[name] = skolem
    }

    fun lookupTypeVar(name: String): Type.TSkolem? = typeVars[name] ?: parent?.lookupTypeVar(name)

    fun localTypeVars(): Set<Type.TSkolem> = typeVars.values.toSet()

    /** Open a nested scope whose bindings don't leak back into this one. */
    fun child(): TypeEnv = TypeEnv(parent = this)

    companion object {
        fun empty(): TypeEnv = TypeEnv(parent = null)
    }
}
