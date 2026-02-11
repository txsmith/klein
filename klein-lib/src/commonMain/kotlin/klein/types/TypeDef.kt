package klein.types

import klein.FieldDecl
import klein.SourceSpan

/**
 * Internal representation for type definitions.
 * These get used for recognizing user defined types like List
 */

data class TypeDefInfo(
    val name: String,
    val typeParams: List<TypeParamInfo>,
    val iface: TypeBinding.Poly,
    val span: SourceSpan,
)

data class TypeParamInfo(
    val name: String,
    val variance: Variance,
    val tvar: SimpleType.TVar,
)

data class ConstructorInfo(
    val name: String,
    val typeParams: List<String>,
    val fields: List<FieldDecl>,
    val parentType: String,
    val span: SourceSpan,
)

data class FunDefInfo(
    val name: String,
    val paramNames: List<String>,
)

enum class Variance {
    Bivariant,
    Covariant,
    Contravariant,
    Invariant,
    ;

    val isPositive: Boolean get() =
        when (this) {
            Covariant, Invariant, Bivariant -> true
            Contravariant -> false
        }

    val isNegative: Boolean get() =
        when (this) {
            Contravariant, Invariant, Bivariant -> true
            Covariant -> false
        }

    fun meet(other: Variance): Variance =
        when {
            this == other -> this
            this == Bivariant -> other
            other == Bivariant -> this
            else -> Invariant
        }

    fun flip(): Variance =
        when (this) {
            Covariant -> Contravariant
            Contravariant -> Covariant
            else -> this
        }

    fun compose(other: Variance): Variance =
        when (other) {
            Covariant -> this
            Contravariant -> this.flip()
            Invariant -> Invariant
            Bivariant -> this
        }
}
