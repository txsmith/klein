package klein.types

class TypeEnv(
    private val parent: TypeEnv? = null,
    private val bindings: MutableMap<String, SimpleType> = mutableMapOf(),
) {
    fun lookup(name: String): SimpleType? = bindings[name] ?: parent?.lookup(name)

    fun contains(name: String): Boolean = name in bindings

    fun bind(
        name: String,
        type: SimpleType,
    ) {
        bindings[name] = type
    }

    fun child(): TypeEnv = TypeEnv(parent = this)

    companion object {
        fun empty(): TypeEnv = TypeEnv()
    }
}
