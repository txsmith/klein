package klein.check

/**
 * Path G types: concrete structural / nominal trees.
 *
 * Unlike the SimpleSub-era [klein.types.SimpleType], there are **no inference variables,
 * mutable bounds, or levels** — a type is fully determined by its structure. Polymorphism
 * is expressed by rigid skolems ([TSkolem]) introduced at signatures, not by inference.
 *
 * This hierarchy grows as the checker does; it currently covers what the synth skeleton
 * needs (primitives, functions, records).
 */
sealed class Type {
    data object TNum : Type()

    data object TStr : Type()

    data object TBool : Type()

    data object TUnit : Type()

    data object TNull : Type()

    data object TTop : Type()
    data object TBottom : Type()

    data class TFun(
        val params: List<Type>,
        val result: Type,
        val paramNames: List<String> = emptyList(),
    ) : Type()

    data class TRecord(
        val fields: Map<String, Type>,
    ) : Type()

    data class TOptional(
        val type: Type
    ) : Type()

    data class TSkolem(
        val name: String,
        val id: Int,
    ) : Type()

    /**
     * A polymorphic type — `∀quantified. body`. Following Pierce–Turner, schemes *are* types, so a
     * polymorphic binding's type is a value of this hierarchy. Kept **shallow** (never nested inside
     * another type) by discipline: annotations can't express a nested `∀`, and every demand point
     * instantiates it away before it could nest — that's what keeps the system rank-1.
     */
    data class TForall(
        val params: Set<TSkolem>,
        val body: Type,
    ) : Type()
}
