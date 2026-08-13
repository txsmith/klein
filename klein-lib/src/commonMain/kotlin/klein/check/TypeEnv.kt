package klein.check

import klein.Revision


internal sealed class ImplicitParamContext {
    data object None : ImplicitParamContext()

    data object NoExpectedType : ImplicitParamContext()

    data object BlockedByNamedFunction : ImplicitParamContext()

    data class BlockedByExplicitParams(
        val params: List<String>,
    ) : ImplicitParamContext()

    data class Available(
        val type: RuleType,
    ) : ImplicitParamContext()
}

/**
 * The name environment, carrying the same revision witness its types do.
 *
 * The type parameter R is used (invariantly) to distinguish RuleEnv from ContractEnv:
 * - the RuleEnv is guaranteed to not hold revision numbers, and can only be used to check rules
 * - the ContractEnv does mention revision numbers, and must be projected into a RuleEnv by constructing a Release
 */
class TypeEnv<R : Revision?> private constructor(
    private val parent: TypeEnv<R>?,
    private val bindings: MutableMap<String, Type<R>> = mutableMapOf(),
    private val typeVars: MutableMap<String, Type.TSkolem> = mutableMapOf(),
    private val typeDefs: MutableMap<String, TypeDefInfo<R>> = mutableMapOf(),
    private val constructors: MutableMap<String, ConstructorInfo<R>> = mutableMapOf(),
    internal val implicitParam: ImplicitParamContext = ImplicitParamContext.None,
) {
    /** Bind a plain name to a type — how a host pre-binds capability vocabulary or reads back an
     *  inferred one; see [klein.Klein.check]. */
    fun bind(
        name: String,
        type: Type<R>,
    ) {
        bindings[name] = type
    }

    internal fun bind(
        name: String,
        revision: R,
        type: Type<R>,
    ) {
        bindings[key(name, revision)] = type
    }

    /** Look up a plain name — how a host reads back what [klein.Klein.check] bound. */
    fun lookup(name: String): Type<R>? = bindings[name] ?: parent?.lookup(name)

    internal fun lookup(
        name: String,
        revision: R,
    ): Type<R>? = lookup(key(name, revision))

    internal fun bindTypeVar(
        name: String,
        skolem: Type.TSkolem,
    ) {
        typeVars[name] = skolem
    }

    internal fun lookupTypeVar(name: String): Type.TSkolem? = typeVars[name] ?: parent?.lookupTypeVar(name)

    internal fun localTypeVars(): Set<Type.TSkolem> = typeVars.values.toSet()

    internal fun registerTypeDef(info: TypeDefInfo<R>) {
        typeDefs[key(info.name, info.revision)] = info
    }

    internal fun updateTypeDef(info: TypeDefInfo<R>) {
        typeDefs[key(info.name, info.revision)] = info
    }

    internal fun lookupTypeDef(name: String): TypeDefInfo<R>? = typeDefs[name]

    internal fun lookupTypeDef(
        name: String,
        revision: R,
    ): TypeDefInfo<R>? = typeDefs[key(name, revision)]

    internal fun getTypeDef(
        name: String,
        revision: R,
    ): TypeDefInfo<R> = typeDefs.getValue(key(name, revision))

    internal fun allTypeDefs(): Collection<TypeDefInfo<R>> = typeDefs.values

    internal fun registerConstructor(info: ConstructorInfo<R>) {
        constructors[key(info.name, info.revision)] = info
    }

    internal fun updateConstructor(info: ConstructorInfo<R>) {
        constructors[key(info.name, info.revision)] = info
    }

    internal fun lookupConstructor(
        name: String,
        revision: R,
    ): ConstructorInfo<R>? = constructors[key(name, revision)]

    internal fun allConstructors(): Collection<ConstructorInfo<R>> = constructors.values

    /** The constructors belonging to `(name, revision)`. They are never registered against a type
     *  they do not belong to, so exposing a type carries exactly these. */
    internal fun constructorsOf(
        name: String,
        revision: R,
    ): List<ConstructorInfo<R>> = constructors.values.filter { it.parentType == name && it.revision == revision }

    internal fun child(implicitParam: ImplicitParamContext = ImplicitParamContext.None): TypeEnv<R> =
        TypeEnv(parent = this, typeDefs = typeDefs, constructors = constructors, implicitParam = implicitParam)

    internal fun copy(): TypeEnv<R> =
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
    internal fun implicitParamContext(): ImplicitParamContext =
        if (implicitParam != ImplicitParamContext.None) implicitParam else parent?.implicitParamContext() ?: ImplicitParamContext.None

    /**
     * The key an entry is stored under. A method rather than a companion function so it can read
     * [R]: at [RuleEnv] the revision is necessarily absent and the key *is* the plain name, which
     * retires the conflation that made bare-name visibility an accident of the key space. On the
     * contract side revision 1 still shares the bare name's slot, because a bare declaration *is*
     * revision 1.
     */
    private fun key(
        name: String,
        revision: R,
    ): String = if (revision == null || revision.value == 1) name else "$name/${revision.value}"

    companion object {
        fun <R : Revision?> empty(): TypeEnv<R> = TypeEnv(parent = null)
    }
}
