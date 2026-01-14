# Optional Types Implementation Plan

This document provides detailed implementation guidance for adding optional types (`T?`) and `Null` values to Klein's type system.

For design rationale, see [ADR: Optional Types and Null Safety](../decisions/2026-01-14-optional-types-null-safety.md).

## Implementation Order

1. **Lexer**: Add `NULL` keyword token
2. **AST**: Add `NullLiteral` node
3. **Parser**: Parse `null` keyword
4. **SimpleType**: Add `TNull` and `TOptional`
5. **Type (display)**: Add `Null` and `Optional`
6. **TypeError**: Add `NullNotAllowed`
7. **Subtyping**: Implement subtyping rules
8. **Typer**: Handle `NullLiteral`, update if-else inference
9. **CompactType**: Add fields and update methods
10. **TypeSimplifier**: Update coalescing for optional display

---

## Phase 1: Lexer Changes

**File: `klein-lib/src/commonMain/kotlin/klein/Token.kt`**

```kotlin
enum class TokenKind(...) {
    // ... existing tokens ...
    NULL(keyword = "null"),           // Null literal/keyword
}
```

The keyword `null` will be recognized via `fromKeyword()`.

---

## Phase 2: AST Changes

**File: `klein-lib/src/commonMain/kotlin/klein/Ast.kt`**

Add `NullLiteral` expression node:

```kotlin
data class NullLiteral(
    override val span: SourceSpan,
) : Expr()
```

Update `usesImplicitParam` extension:

```kotlin
val Expr.usesImplicitParam: Boolean
    get() = when (this) {
        is NullLiteral -> false  // Add this case
        // ... existing cases ...
    }
```

---

## Phase 3: Parser Changes

**File: `klein-lib/src/commonMain/kotlin/klein/Parser.kt`**

Parse `null` as a literal expression:

```kotlin
private fun parsePrimary(): Expr {
    return when {
        // ... existing cases ...
        check(TokenKind.NULL) -> {
            val token = advance()
            NullLiteral(token.span)
        }
        // ... rest of cases ...
    }
}
```

---

## Phase 4: SimpleType Changes

**File: `klein-lib/src/commonMain/kotlin/klein/types/SimpleType.kt`**

Add `TNull` and `TOptional`:

```kotlin
sealed class SimpleType {
    // ... existing types ...

    /**
     * The Null type - the type of the `null` literal.
     */
    object TNull : SimpleType() {
        override fun toString(): String = "TNull"
    }

    /**
     * Optional type T? - represents "T or Null".
     *
     * Key subtyping:
     *   T <: T?      (any T can be used where T? is expected)
     *   Null <: T?   (null can be used where T? is expected)
     */
    data class TOptional(
        val inner: SimpleType,
    ) : SimpleType() {
        override val level: Int
            get() = inner.level
    }
}
```

Update `freshenAbove`:

```kotlin
fun freshenAbove(above: Int, currentLevel: Int): SimpleType {
    // ... in the when block ...
    ty is TNull -> ty
    ty is TOptional -> TOptional(freshen(ty.inner))
    // ... rest ...
}
```

---

## Phase 5: Subtyping Changes

**File: `klein-lib/src/commonMain/kotlin/klein/types/Subtyping.kt`**

Add subtyping rules:

```kotlin
fun constrain(lhs: SimpleType, rhs: SimpleType, span: SourceSpan) {
    // ... existing cache check ...

    when {
        // ... existing cases ...

        // Null identity
        lhs is TNull && rhs is TNull -> return

        // Null <: T? (null is subtype of any optional)
        lhs is TNull && rhs is TOptional -> return

        // T <: T? (embed non-optional into optional)
        rhs is TOptional -> {
            if (lhs !is TNull) {
                when (lhs) {
                    is TOptional -> {
                        // T? <: U? if T <: U
                        constrain(lhs.inner, rhs.inner, span)
                    }
                    else -> {
                        // T <: U? if T <: U
                        constrain(lhs, rhs.inner, span)
                    }
                }
            }
        }

        // T? <: U only if U is also optional (or a type variable)
        lhs is TOptional && rhs !is TVar -> {
            if (rhs is TOptional) {
                constrain(lhs.inner, rhs.inner, span)
            } else {
                errors.add(TypeError.TypeMismatch(
                    simplifyCanonical(rhs),
                    simplifyCanonical(lhs),
                    span
                ))
            }
        }

        // Null cannot flow into non-optional types
        lhs is TNull && rhs !is TVar && rhs !is TOptional -> {
            errors.add(TypeError.NullNotAllowed(simplifyCanonical(rhs), span))
        }

        // ... rest of existing cases ...
    }
}
```

Update `extrude`:

```kotlin
private fun extrude(...): SimpleType {
    if (ty.level <= targetLevel) return ty

    return when (ty) {
        // ... existing cases ...
        is TNull -> ty
        is TOptional -> TOptional(extrude(ty.inner, positive, targetLevel, cache))
        // ... rest ...
    }
}
```

---

## Phase 6: Type Error Additions

**File: `klein-lib/src/commonMain/kotlin/klein/types/TypeError.kt`**

```kotlin
sealed class TypeError {
    // ... existing errors ...

    data class NullNotAllowed(
        val expected: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Null is not allowed here; expected non-optional type ${Type.print(expected)}"
    }
}
```

---

## Phase 7: Typer Changes

**File: `klein-lib/src/commonMain/kotlin/klein/types/Typer.kt`**

Handle `NullLiteral`:

```kotlin
fun infer(expr: Expr, env: TypeEnv): SimpleType =
    when (expr) {
        // ... existing cases ...
        is NullLiteral -> TNull
        // ... rest ...
    }
```

Update `inferIfThenElse` for special null handling:

```kotlin
private fun inferIfThenElse(expr: IfThenElse, env: TypeEnv): SimpleType {
    val condType = infer(expr.condition, env)
    subtyping.constrain(condType, TBool, expr.condition.span)

    val thenType = infer(expr.thenBranch, env)

    return if (expr.elseBranch != null) {
        val elseType = infer(expr.elseBranch, env)

        // Special handling for Null branches
        val resultType = when {
            thenType is TNull && elseType !is TNull && elseType !is TOptional -> {
                TOptional(elseType)
            }
            elseType is TNull && thenType !is TNull && thenType !is TOptional -> {
                TOptional(thenType)
            }
            thenType is TNull && elseType is TOptional -> elseType
            elseType is TNull && thenType is TOptional -> thenType
            thenType is TNull && elseType is TNull -> TNull
            else -> {
                val resultVar = env.freshVar()
                subtyping.constrain(thenType, resultVar, expr.thenBranch.span)
                subtyping.constrain(elseType, resultVar, expr.elseBranch.span)
                resultVar
            }
        }
        resultType
    } else {
        TUnit
    }
}
```

---

## Phase 8: CompactType Changes

**File: `klein-lib/src/commonMain/kotlin/klein/types/CompactType.kt`**

Add fields for optional types:

```kotlin
data class CompactType(
    val vars: Set<TVar> = emptySet(),
    val prims: Set<PrimType> = emptySet(),
    val rec: Map<String, CompactType>? = null,
    val func: Pair<List<CompactType>, CompactType>? = null,
    val optional: CompactType? = null,  // NEW
    val isNull: Boolean = false,        // NEW
) {
    companion object {
        val nullType = CompactType(isNull = true)

        fun optional(inner: CompactType) = CompactType(optional = inner)

        fun fromSimpleType(ty: SimpleType): CompactTypeScheme {
            fun go(...): CompactType = when (ty) {
                // ... existing cases ...
                TNull -> CompactType.nullType
                is TOptional -> CompactType.optional(go(ty.inner, pol, parents, inProgress))
                // ... rest ...
            }
            // ...
        }

        fun canonicalizeType(ty: SimpleType): CompactTypeScheme {
            fun go0(ty: SimpleType, pol: Boolean): CompactType = when (ty) {
                // ... existing cases ...
                TNull -> CompactType.nullType
                is TOptional -> CompactType.optional(go0(ty.inner, pol))
                // ... rest ...
            }
            // ...
        }
    }

    fun merge(other: CompactType, positive: Boolean): CompactType {
        // ... existing merge logic ...

        val mergedOptional = when {
            this.optional != null && other.optional != null ->
                this.optional.merge(other.optional, positive)
            this.optional != null -> this.optional
            other.optional != null -> other.optional
            else -> null
        }

        val mergedIsNull = this.isNull || other.isNull

        return CompactType(
            vars = mergedVars,
            prims = mergedPrims,
            rec = mergedRec,
            func = mergedFun,
            optional = mergedOptional,
            isNull = mergedIsNull,
        )
    }

    fun isEmpty(): Boolean =
        vars.isEmpty() && prims.isEmpty() && rec == null &&
        func == null && optional == null && !isNull
}
```

---

## Phase 9: TypeSimplifier Changes

**File: `klein-lib/src/commonMain/kotlin/klein/types/TypeSimplifier.kt`**

Update coalescing:

```kotlin
fun coalesceType(cty: CompactTypeScheme): Type {
    fun go(ty: CompactType, pol: Boolean, inProcess: Map<...>): Type {
        val components = mutableListOf<Type>()

        // ... existing variable/prim/rec/func handling ...

        ty.optional?.let { inner ->
            val innerType = go(inner, pol, inProcess)
            components.add(Type.Optional(innerType))
        }

        if (ty.isNull) {
            components.add(Type.Null)
        }

        // ... existing result construction ...
    }

    return go(cty.term, pol = true, inProcess = emptyMap())
}
```

---

## Phase 10: Display Type Changes

**File: `klein-lib/src/commonMain/kotlin/klein/Type.kt`**

Add type variants:

```kotlin
sealed class Type {
    // ... existing types ...

    data object Null : Type()

    data class Optional(val inner: Type) : Type()

    companion object {
        fun print(type: Type): String = when (type) {
            // ... existing cases ...
            Null -> "Null"
            is Optional -> "${printWithParensIfNeeded(type.inner, isUnionOrInter = false)}?"
            // ... rest ...
        }

        private fun typeOrder(type: Type): Int = when (type) {
            // ... existing cases ...
            Null -> 4
            is Optional -> 5
            is Record -> 6
            // ... adjust subsequent numbers ...
        }
    }
}
```

---

## Testing Strategy

Three test files in `klein-lib/src/commonTest/kotlin/klein/types/`:

| File | Purpose | Tests |
|------|---------|-------|
| `OptionalTypeInferTest.kt` | Type inference through full pipeline | ~40 tests |
| `OptionalSubtypingTest.kt` | Direct SimpleType constraint propagation | ~70 tests |
| `OptionalTypeErrorTest.kt` | Error detection and message quality | ~25 tests |

Additional lexer/parser tests in:
- `klein-lib/src/commonTest/kotlin/klein/lexer/NullTest.kt`
- `klein-lib/src/commonTest/kotlin/klein/parser/NullTest.kt`

---

## Edge Cases

### Nested Optionals

`TOptional(TOptional(T))` should be simplified to `TOptional(T)` during type construction or simplification.

### Optional in Unions

When SimpleSub infers `Num | String` and one branch is null:

```klein
fun bar(b, s) = if b then 42 else if s then "hi" else null
# Type: (Bool, Bool) -> (Num | String)?
```

The nullability should factor out when possible.

---

## References

- [ADR: Optional Types and Null Safety](../decisions/2026-01-14-optional-types-null-safety.md)
- [Kleene Types (Experimental)](../kleene-types-experimental.md)
