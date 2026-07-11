package klein.check

import klein.FieldDecl
import klein.SourceSpan
import klein.check.Type.*
import klein.types.Variance

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

    data class TRef(
        val name: String,
        val typeArgs: List<Type> = emptyList(),
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

data class TypeParamInfo(
    val variance: Variance,
    val skolem: Type.TSkolem,
)

data class TypeDefInfo(
    val name: String,
    val typeParams: List<TypeParamInfo>,
    val iface: Type.TRecord,
    val span: SourceSpan,
)

data class ConstructorInfo(
    val name: String,
    val typeParams: List<String>,
    val fields: List<FieldDecl>,
    val parentType: String,
    val span: SourceSpan,
)

/** A record type with no fields demands nothing, so it is the top; never observe an empty record as a type. */
internal fun recordOf(fields: Map<String, Type>): Type = if (fields.isEmpty()) Type.TTop else Type.TRecord(fields)

/** Wrap [t] in one optional layer, idempotently (`T?` stays `T?`); `Top` absorbs null, `Bottom` becomes `Null`. */
internal fun optionalOf(t: Type): Type =
    when (t) {
        Type.TTop -> Type.TTop
        Type.TBottom -> Type.TNull
        is Type.TOptional -> t
        else -> Type.TOptional(t)
    }

/** Strip one optional layer: `T?` → `T`, `Null` → `Bottom`, anything else unchanged. */
internal fun nonNullCore(t: Type): Type =
    when (t) {
        is Type.TOptional -> t.type
        Type.TNull -> Type.TBottom
        else -> t
    }

internal fun substitute(
    type: Type,
    subst: Map<TSkolem, Type>,
): Type =
    when (type) {
        is TSkolem -> subst[type] ?: type
        is TFun -> TFun(type.params.map { substitute(it, subst) }, substitute(type.result, subst), type.paramNames)
        is TRecord -> TRecord(type.fields.mapValues { substitute(it.value, subst) })
        is TOptional -> TOptional(substitute(type.type, subst))
        is TRef -> TRef(type.name, type.typeArgs.map { substitute(it, subst) })
        is TForall -> TForall(type.params, substitute(type.body, subst - type.params))
        TNum, TStr, TBool, TUnit, TNull, TTop, TBottom -> type
    }
