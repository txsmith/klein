package klein

/**
 * Type representation for Klein's type inference system.
 *
 * Uses SimpleSub-style type variables with bounds rather than equality constraints.
 */
sealed class Type {
    // Primitive types
    object TInt : Type() {
        override fun toString(): String = "TInt"
    }

    object TDouble : Type() {
        override fun toString(): String = "TDouble"
    }

    object TString : Type() {
        override fun toString(): String = "TString"
    }

    object TBool : Type() {
        override fun toString(): String = "TBool"
    }

    /** Unit type for expressions with no meaningful value (e.g., if without else) */
    object TUnit : Type() {
        override fun toString(): String = "TUnit"
    }

    /**
     * Type variable with bounds (SimpleSub style).
     *
     * Bounds are accumulated during inference rather than solved eagerly.
     * - lowerBounds: T :> lower (the type variable is a supertype of these)
     * - upperBounds: T <: upper (the type variable is a subtype of these)
     */
    data class TVar(
        val id: Int,
        val lowerBounds: MutableSet<Type> = mutableSetOf(),
        val upperBounds: MutableSet<Type> = mutableSetOf(),
    ) : Type()

    /**
     * Function type: (params) -> result
     *
     * Single-parameter functions are displayed as `A -> B`
     * Multi-parameter functions are displayed as `(A, B) -> C`
     * Zero-parameter functions (thunks) are displayed as `() -> A`
     */
    data class TFun(
        val params: List<Type>,
        val result: Type,
    ) : Type()

    /**
     * Record type: { field1: T1, field2: T2 }
     *
     * Closed records only - no row polymorphism for now.
     * Width subtyping: a record with more fields is a subtype of one with fewer.
     */
    data class TRecord(
        val fields: Map<String, Type>,
    ) : Type()

    /** Top type - supertype of all types */
    object TTop : Type() {
        override fun toString(): String = "TTop"
    }

    /** Bottom type - subtype of all types */
    object TBottom : Type() {
        override fun toString(): String = "TBottom"
    }
}
