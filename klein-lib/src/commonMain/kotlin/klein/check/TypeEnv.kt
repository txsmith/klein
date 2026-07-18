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

    fun lookup(name: String): Type? = bindings[name] ?: parent?.lookup(name)

    fun bindTypeVar(
        name: String,
        skolem: Type.TSkolem,
    ) {
        typeVars[name] = skolem
    }

    fun lookupTypeVar(name: String): Type.TSkolem? = typeVars[name] ?: parent?.lookupTypeVar(name)

    fun localTypeVars(): Set<Type.TSkolem> = typeVars.values.toSet()

    fun registerTypeDef(info: TypeDefInfo) {
        typeDefs[info.name] = info
    }

    fun updateTypeDef(info: TypeDefInfo) {
        typeDefs[info.name] = info
    }

    fun lookupTypeDef(name: String): TypeDefInfo? = typeDefs[name]

    fun getTypeDef(name: String): TypeDefInfo = typeDefs.getValue(name)

    fun allTypeDefs(): Collection<TypeDefInfo> = typeDefs.values

    fun registerConstructor(info: ConstructorInfo) {
        constructors[info.name] = info
    }

    fun lookupConstructor(name: String): ConstructorInfo? = constructors[name]

    fun allConstructors(): Collection<ConstructorInfo> = constructors.values

    fun child(implicitParam: ImplicitParamContext = ImplicitParamContext.None): TypeEnv =
        TypeEnv(parent = this, typeDefs = typeDefs, constructors = constructors, implicitParam = implicitParam)

    /** The nearest enclosing binder's implicit-param context: a bare lambda makes `.` available, an
     *  explicit-param lambda or named function blocks it, and nothing at all leaves it unbound. */
    fun implicitParamContext(): ImplicitParamContext =
        if (implicitParam != ImplicitParamContext.None) implicitParam else parent?.implicitParamContext() ?: ImplicitParamContext.None

    companion object {
        fun empty(): TypeEnv = TypeEnv(parent = null)
    }
}
