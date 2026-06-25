package klein.check

/**
 * Path G types: concrete structural / nominal trees.
 *
 * Unlike the SimpleSub-era [klein.types.SimpleType], there are **no inference variables,
 * mutable bounds, or levels** — a type is fully determined by its structure. Polymorphism
 * is expressed by rigid skolems (added later) introduced at signatures, not by inference.
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

    /** Top — supertype of everything (`Any`). */
    data object TTop : Type()

    /** Bottom — subtype of everything (`Nothing`). */
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
}
