package klein.types

class TypeEnv(
    private val parent: TypeEnv? = null,
    private val bindings: MutableMap<String, SimpleType> = mutableMapOf(),
    private val polymorphic: MutableSet<String> = mutableSetOf(),
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

    fun child(): TypeEnv = TypeEnv(parent = this)

    companion object {
        fun empty(): TypeEnv = TypeEnv()
    }
}
