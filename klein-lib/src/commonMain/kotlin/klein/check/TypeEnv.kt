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
/**
 * Lexically-scoped name → type environment for the bidirectional checker.
 *
 * Path G has no inference variables, levels, or generalization, so the env is just a
 * scoped map. [bind] mutates the current scope, [lookup] walks the parent chain, and
 * [child] opens a nested scope that can shadow a name without affecting its parent.
 *
 * (The SimpleSub-era `lookupAndInstantiate` / `Poly` / level machinery is gone:
 * instantiation of a polymorphic signature is structural matching at the call site,
 * not an env operation.)
 */
class TypeEnv private constructor(
    private val parent: TypeEnv?,
    private val bindings: MutableMap<String, Type> = mutableMapOf(),
    val implicitParam: ImplicitParamContext = ImplicitParamContext.None,
) {
    /** Bind [name] to [type] in this scope, shadowing any outer binding of the same name. */
    fun bind(
        name: String,
        type: Type,
    ) {
        bindings[name] = type
    }

    /** Look up [name], walking outward through enclosing scopes; null if unbound. */
    fun lookup(name: String): Type? = bindings[name] ?: parent?.lookup(name)

    /** Open a nested scope whose bindings don't leak back into this one. */
    fun child(): TypeEnv = TypeEnv(parent = this)

    companion object {
        fun empty(): TypeEnv = TypeEnv(parent = null)
    }
}
