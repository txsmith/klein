package klein

class TypeEnv(
    private val parent: TypeEnv? = null,
    private val bindings: MutableMap<String, Type> = mutableMapOf(),
) {
    fun lookup(name: String): Type? = bindings[name] ?: parent?.lookup(name)

    fun bind(
        name: String,
        type: Type,
    ) {
        bindings[name] = type
    }

    fun child(): TypeEnv = TypeEnv(parent = this)

    companion object {
        fun empty(): TypeEnv = TypeEnv()
    }
}
