# ADR: Optional Types and Null Safety

**Status**: Accepted
**Date**: 2026-01-14 (updated 2026-01-15)

## Context

Klein needs a way to represent values that may or may not be present. This is essential for:
- Expressing partial data (e.g., optional record fields)
- Representing lookup failures without exceptions
- Modeling nullable values from external systems

## Decision

Klein will support Kotlin-style optional types with `T?` syntax and a `null` keyword.

```klein
x: Num? = 42       # Can hold Num or Null
y: Num? = null     # Explicitly null
z: Num = 42        # Cannot be null

fun acceptOptional(n: Num?): Unit = ...
acceptOptional(42)      # Works: Num <: Num?
acceptOptional(null)    # Works: Null <: Num?
```

### Key Design Decisions

#### 1. TOptional as First-Class Type Constructor

`T?` is implemented as a **first-class type constructor** (`TOptional`), NOT as syntactic sugar for `T | Null`.

**Rationale**: In SimpleSub's polar type system, unions only appear in positive (covariant) positions, and intersections only in negative (contravariant) positions. Using `T | Null` for optionals would violate these polarity invariants and produce unsound types in function parameters.

By making `TOptional` a proper type constructor (like `TFun` and `TRecord`):
- **Positive position**: `TOptional(Num)` means "might produce Num or Null"
- **Negative position**: `TOptional(Num)` means "accepts Num or Null"

Both interpretations are consistent and sound.

#### 2. Subtyping Rules

```
            T <: T?          (embed)
            Null <: T?       (null injection)
    T <: U implies T? <: U?  (covariant)
            T? NOT <: T      (no implicit unwrap)
```

These rules ensure:
- Any value can be used where an optional is expected
- Null can be used where an optional is expected
- Optional types are covariant in their inner type
- Optional values cannot escape without explicit handling

#### 3. No Automatic Lifting

Klein will **not** automatically lift operations over optional types:

```klein
maybeNum + 1              # Type error
maybeNum.map(|. + 1|)     # OK: explicit lifting via stdlib
maybeNum.orDefault(0) + 1 # OK: explicit unwrapping with default
```

**Rationale: Locality of Errors**

When null propagates automatically, it becomes impossible to know which operation was responsible:

```klein
# With automatic lifting
fun pipeline(a, b, c) =
    x = a + 1        # Num? if a is Num?
    y = x * b        # Num? (propagates)
    z = y - c        # Num? (propagates)
    z / 2            # Num? (propagates)

result = pipeline(maybeA, maybeB, maybeC)
# If result is Null, was it maybeA, maybeB, or maybeC?
```

With explicit handling, nullability is handled at the point where it's introduced:

```klein
# Without automatic lifting
fun pipeline(a, b, c) =
    x = a.orError("a was null") + 1
    y = x * b.orError("b was null")
    z = y - c.orDefault(0)  # c being null is OK, use 0
    z / 2
```

Each decision about null handling is explicit and documented.

**What This Decision Is NOT About**

We explicitly considered and rejected these arguments against auto-lifting:

| Argument | Why It's Not Compelling |
|----------|------------------------|
| "Loss of principal types" | Solvable with proper constraint-based inference |
| "Nested optionals are confusing" | Flattening `T??` to `T?` is correct (monadic join) |
| "Accidental API changes" | Compiler catches incompatible call sites regardless |
| "SQL NULL is bad" | SQL's problem is *untyped* null propagation, not lifting per se |

Auto-lifting is technically feasible. We're choosing not to do it for ergonomic reasons, not technical limitations.

## Consequences

### Positive

- **Sound type system**: Null safety is enforced at compile time
- **Clear error locality**: When something is null, you know exactly where it came from
- **Explicit intent**: Code documents how null cases are handled
- **Predictable types**: `+` always means numeric addition, returns `Num`
- **SimpleSub compatible**: Integrates cleanly with bisubstitution algorithm

### Negative

- **More verbose**: Users must write `.map(|...|)` or similar for lifted operations
- **Learning curve**: Users from languages with auto-lifting may find this tedious initially

### Neutral

- **Stdlib design**: We'll provide ergonomic functions like `map`, `flatMap`, `orDefault`, `orError`

## Type Theory Details

### Type Variables and Optionals

Type variables remain polymorphic and can be instantiated to `Null`, `T`, or `T?`:

```klein
fun identity(x) = x
# Type: 'A -> 'A

identity(null)    # Instantiates 'A := Null, result: Null
identity(42)      # Instantiates 'A := Num, result: Num
```

### If-Then-Else Inference

When one branch is `Null` and the other is `T`, the result type is `T?`:

```klein
if condition then 42 else null  # Type: Num?
```

### Semantic Meaning at Different Polarities

- **Positive position** (return, binding): "might produce T or Null"
- **Negative position** (parameter): "accepts both T and Null"

Both interpretations are consistent because `TOptional` is a proper type constructor.

## Edge Cases

### Nested Optionals

`T??` collapses to `T?`. Following Kotlin, nested optionals are not distinct types.

### Optional Functions vs Functions Returning Optionals

```klein
f: (Num -> Num)?   # Optional function
g: Num -> Num?     # Function returning optional
```

Both are valid and distinct. The `?` binds tightly to the preceding type.

### Optional Records vs Records with Optional Fields

```klein
r: { name: String }?  # Optional record
s: { name: String? }  # Record with optional field
```

Both are valid and distinct.

## Alternatives Considered

### 1. T? as Sugar for T | Null

Rejected because unions only appear in positive positions in SimpleSub.

### 2. Full Automatic Lifting

Rejected for locality of errors.

### 3. Syntactic Sugar for Lifting

Could add `maybeX?+ 1` that desugars to `maybeX.map(|. + 1|)`.
*Status*: Potentially revisit later.

### 4. Kleene-Style Cardinality Polymorphism

Track optionality in function signatures: `fun (+)(a: Num[n..1], b: Num[m..1]): Num[min(n,m)..1]`
*Status*: Deferred for future exploration.

## Future Work (Out of Scope)

- Elvis operator (`?:`)
- Not-null assertion (`!!`)
- Pattern matching refinement
- Smart casts

These will be addressed when pattern matching and stdlib are implemented.

## Related

- [Safe Navigation Operator](./2026-01-16-safe-navigation-operator.md) - `?.` for field access and method calls

## References

- [Optional Types Implementation Plan](../implementation/optional-types-implementation.md)
- [Kleene Types (Experimental)](../kleene-types-experimental.md)
