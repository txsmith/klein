package klein.check


sealed class ImplicitParamContext {
    data object None : ImplicitParamContext()

    data object NoExpectedType : ImplicitParamContext()

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
    private val typeDefs: MutableMap<String, TypeDefInfo> = mutableMapOf(),
    private val constructors: MutableMap<String, ConstructorInfo> = mutableMapOf(),
    val implicitParam: ImplicitParamContext = ImplicitParamContext.None,
) {
    fun bind(
        name: String,
        type: Type,
    ) {
        bindings[name] = type
    }

    fun bind(
        name: String,
        revision: Int,
        type: Type,
    ) {
        bindings[key(name, revision)] = type
    }

    fun lookup(name: String): Type? = bindings[name] ?: parent?.lookup(name)

    fun lookup(
        name: String,
        revision: Int,
    ): Type? = lookup(key(name, revision))

    fun bindTypeVar(
        name: String,
        skolem: Type.TSkolem,
    ) {
        typeVars[name] = skolem
    }

    fun lookupTypeVar(name: String): Type.TSkolem? = typeVars[name] ?: parent?.lookupTypeVar(name)

    fun localTypeVars(): Set<Type.TSkolem> = typeVars.values.toSet()

    fun registerTypeDef(info: TypeDefInfo) {
        typeDefs[key(info.name, info.revision)] = info
    }

    fun updateTypeDef(info: TypeDefInfo) {
        typeDefs[key(info.name, info.revision)] = info
    }

    fun lookupTypeDef(name: String): TypeDefInfo? = typeDefs[name]

    fun lookupTypeDef(
        name: String,
        revision: Int,
    ): TypeDefInfo? = typeDefs[key(name, revision)]

    fun getTypeDef(
        name: String,
        revision: Int,
    ): TypeDefInfo = typeDefs.getValue(key(name, revision))

    fun allTypeDefs(): Collection<TypeDefInfo> = typeDefs.values

    fun registerConstructor(info: ConstructorInfo) {
        constructors[key(info.name, info.revision)] = info
    }

    fun lookupConstructor(
        name: String,
        revision: Int,
    ): ConstructorInfo? = constructors[key(name, revision)]

    fun allConstructors(): Collection<ConstructorInfo> = constructors.values

    fun child(implicitParam: ImplicitParamContext = ImplicitParamContext.None): TypeEnv =
        TypeEnv(parent = this, typeDefs = typeDefs, constructors = constructors, implicitParam = implicitParam)

    fun copy(): TypeEnv =
        TypeEnv(
            parent = parent,
            bindings = bindings.toMutableMap(),
            typeVars = typeVars.toMutableMap(),
            typeDefs = typeDefs.toMutableMap(),
            constructors = constructors.toMutableMap(),
            implicitParam = implicitParam,
        )

    /** The nearest enclosing binder's implicit-param context: a bare lambda makes `.` available, an
     *  explicit-param lambda or named function blocks it, and nothing at all leaves it unbound. */
    fun implicitParamContext(): ImplicitParamContext =
        if (implicitParam != ImplicitParamContext.None) implicitParam else parent?.implicitParamContext() ?: ImplicitParamContext.None

    companion object {
        fun empty(): TypeEnv = TypeEnv(parent = null)

        private fun key(
            name: String,
            revision: Int,
        ): String = if (revision == 1) name else "$name/$revision"
    }
}
