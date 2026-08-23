package klein.check

import klein.RevisionNumber
import klein.SourceSpan
import klein.check.Type.*

/**
 * Internal representation for types used by both the rule checker and the contract checker.
 *
 * ## The revision witness
 *
 * [R] indexes the checked tree the way it indexes the surface one ([klein.surface.TypeExpr]).
 * Revisions exist only during contract checking. Contract-side types are [ContractType], and
 * everything from projection onward is [RuleType], where [TRef.revision] has type `Nothing?` and is
 * therefore necessarily null rather than null by convention.
 *
 * [klein.check.contract.strip] is the only function in the system whose signature changes [R], so a
 * revision reaching a rule is a compile error rather than a leak to be tested for.
 *
 * The `data object`s and [TSkolem] are pinned at `Type<Nothing>` — Kotlin forbids a type parameter
 * on an object but not a fixed argument on its supertype, and `Nothing` is the bottom of the
 * lattice, so it fits every instantiation with no cast.
 */
sealed class Type<out R : RevisionNumber?> {
    data object TNum : Type<Nothing>()

    data object TStr : Type<Nothing>()

    data object TBool : Type<Nothing>()

    data object TUnit : Type<Nothing>()

    data object TNull : Type<Nothing>()

    data object TTop : Type<Nothing>()
    data object TBottom : Type<Nothing>()

    data class TFun<out R : RevisionNumber?>(
        val params: List<Type<R>>,
        val result: Type<R>,
        val paramNames: List<String> = emptyList(),
    ) : Type<R>()

    data class TRecord<out R : RevisionNumber?>(
        val fields: Map<String, Type<R>>,
    ) : Type<R>()

    data class TOptional<out R : RevisionNumber?>(
        val type: Type<R>,
    ) : Type<R>()

    /**
     * A reference to a declared nominal type. [revision] is the witness in person: at
     * `R = RevisionNumber` it is the `/N` half of the declaration's key, and at `R = Nothing?` there is
     * no value to carry, so a projected reference cannot name one.
     */
    data class TRef<out R : RevisionNumber?>(
        val name: String,
        val typeArgs: List<Type<R>> = emptyList(),
        val revision: R,
    ) : Type<R>()

    data class TSkolem(
        val name: String,
        val id: Int,
    ) : Type<Nothing>()

    /**
     * A polymorphic type — `∀quantified. body`. Following Pierce–Turner, schemes *are* types, so a
     * polymorphic binding's type is a value of this hierarchy. Kept **shallow** (never nested inside
     * another type) by discipline: annotations can't express a nested `∀`, and every demand point
     * instantiates it away before it could nest — that's what keeps the system rank-1.
     */
    data class TForall<out R : RevisionNumber?>(
        val params: Set<TSkolem>,
        val body: Type<R>,
    ) : Type<R>()

    companion object {
        /** Render a type in surface syntax. A [TForall] prints as its body — surface types
         *  have no quantifiers — so its skolems appear by name (`(A) -> A`).
         *
         *  One function over `Type<*>`: the `/N` branch is simply unreachable at [RuleType],
         *  because no value can inhabit its revision. */
        fun print(type: Type<*>): String =
            when (type) {
                TNum -> "Num"
                TStr -> "String"
                TBool -> "Bool"
                TNull -> "Null"
                TUnit -> "Unit"
                TTop -> "Any"
                TBottom -> "Nothing"
                is TSkolem -> type.name
                is TForall -> print(type.body)
                is TFun -> "(${type.params.joinToString(", ") { print(it) }}) -> ${print(type.result)}"
                is TRecord -> printRecord(type)
                is TOptional -> if (type.type is TFun) "(${print(type.type)})?" else "${print(type.type)}?"
                is TRef -> {
                    val revision = type.revision
                    val name = if (revision == null || revision.value == 1) type.name else "${type.name}/${revision.value}"
                    if (type.typeArgs.isEmpty()) {
                        name
                    } else {
                        "$name<${type.typeArgs.joinToString(", ") { print(it) }}>"
                    }
                }
            }

        private fun printRecord(rec: TRecord<*>): String {
            if (rec.fields.isEmpty()) return "{}"
            val fields =
                rec.fields.entries
                    .sortedBy { it.key }
                    .joinToString(", ") { (k, v) -> "$k: ${print(v)}" }
            return "{ $fields }"
        }
    }
}

/**
 * The two regimes, named. `Nothing?` is an *encoding* of "no revision" and a reader should not have
 * to decode it at every signature.
 *
 * These aliases add no safety — a typealias is transparent, so the type parameter remains the whole
 * of the enforcement. Code that is genuinely polymorphic in the regime keeps writing `Type<R>`, so
 * the raw parameter marks exactly the code that works on both sides of the boundary.
 */
typealias ContractType = Type<RevisionNumber>

/** A type as a rule sees it: revisions cannot exist here. See [ContractType]. */
typealias RuleType = Type<Nothing?>

/** The environment a contract checks into: entries keyed `(name, revision)`. See [ContractType]. */
typealias ContractEnv = TypeEnv<RevisionNumber>

/** The environment a rule checks against: plain names, no revisions. See [ContractType]. */
typealias RuleEnv = TypeEnv<Nothing?>

internal data class TypeParamInfo(
    val variance: Variance,
    val skolem: Type.TSkolem,
)

internal data class TypeDefInfo<out R : RevisionNumber?>(
    val name: String,
    val revision: R,
    val typeParams: List<TypeParamInfo>,
    val iface: Type.TRecord<R>,
    val span: SourceSpan,
)

internal data class ConstructorInfo<out R : RevisionNumber?>(
    val name: String,
    val revision: R,
    val typeParams: List<String>,
    val fields: Map<String, Type<R>>,
    val parentType: String,
    val span: SourceSpan,
)

/** A record type with no fields demands nothing, so it is the top; never observe an empty record as a type. */
internal fun <R : RevisionNumber?> recordOf(fields: Map<String, Type<R>>): Type<R> = if (fields.isEmpty()) TTop else TRecord(fields)

/** Wrap [t] in one optional layer, idempotently (`T?` stays `T?`); `Top` absorbs null, `Bottom` becomes `Null`. */
internal fun <R : RevisionNumber?> optionalOf(t: Type<R>): Type<R> =
    when (t) {
        TTop -> TTop
        TBottom -> TNull
        is TOptional -> t
        else -> TOptional(t)
    }

/** Strip one optional layer: `T?` → `T`, `Null` → `Bottom`, anything else unchanged. */
internal fun <R : RevisionNumber?> nonNullCore(t: Type<R>): Type<R> =
    when (t) {
        is TOptional -> t.type
        TNull -> TBottom
        else -> t
    }

internal fun <R : RevisionNumber?> substitute(
    type: Type<R>,
    subst: Map<TSkolem, Type<R>>,
): Type<R> =
    when (type) {
        is TSkolem -> subst[type] ?: type
        is TFun -> TFun(type.params.map { substitute(it, subst) }, substitute(type.result, subst), type.paramNames)
        is TRecord -> TRecord(type.fields.mapValues { substitute(it.value, subst) })
        is TOptional -> TOptional(substitute(type.type, subst))
        is TRef -> TRef(type.name, type.typeArgs.map { substitute(it, subst) }, type.revision)
        is TForall -> TForall(type.params, substitute(type.body, subst - type.params))
        TNum, TStr, TBool, TUnit, TNull, TTop, TBottom -> type
    }
