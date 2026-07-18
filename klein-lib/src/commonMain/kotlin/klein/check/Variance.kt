package klein.check

enum class Variance {
    Covariant,
    Contravariant,
    Invariant,
    Bivariant, // N.B bivariance only occurs with phantom types, which always should be treated as invariant.
    ;

    val displayLabel: String get() =
        when (this) {
            Covariant -> "output"
            Contravariant -> "input"
            Invariant, Bivariant -> "invariant"
        }
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
            Contravariant -> this.flip()
            Invariant -> Invariant
            else -> this
        }
}
