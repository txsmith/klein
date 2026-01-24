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
    val structure: SimpleType,
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

enum class Variance {
    Bivariant,
    Covariant,
    Contravariant,
    Invariant,
    ;

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
}
