# Klein Kleene Types

**Status: Research feature, not part of initial implementation. Predates Path G and assumes Hindley-Milner inference — orthogonal to the current bidirectional checker; revisit only if pursued.**

Kleene types are a potential extension to Klein's type system that builds cardinality (how many values exist) into types as a first-class concept, with full type inference via Hindley-Milner extended with cardinality polymorphism.

## The Four Cardinalities

| Syntax | Explicit | Meaning | Range |
|--------|----------|---------|-------|
| `T` | `T[1..1]` | exactly one | 1..1 |
| `T?` | `T[0..1]` | zero or one | 0..1 |
| `T+` | `T[1..∞]` | one or more | 1..∞ |
| `T*` | `T[0..∞]` | zero or more | 0..∞ |

Cardinalities are ranges with a lower bound and upper bound. The sugared syntax (`T?`, `T+`, `T*`) is shorthand for common patterns.

## Subtyping Lattice

```
      0..∞  (*)
     /    \
  0..1    1..∞   (? and +)
     \    /
      1..1
```

Subtyping flows upward—a tighter range is a subtype of a looser one:
- `T[1..1] <: T[0..1]` (exactly one fits in zero-or-one)
- `T[1..1] <: T[1..∞]` (exactly one fits in one-or-more)
- `T[0..1] <: T[0..∞]` and `T[1..∞] <: T[0..∞]` (both fit in zero-or-more)

## Cardinality Polymorphism

The key innovation: cardinality bounds can be **polymorphic**, with variables for the lower bound, upper bound, or both.

### Polymorphic Bounds

A cardinality `[n..m]` has:
- `n` — lower bound (minimum count): 0 or 1
- `m` — upper bound (maximum count): 1 or ∞

Functions express how they transform these bounds:

```klein
fun filter(xs: a[n..m], p: a -> Bool): a[0..m]
# 'Lower bound drops to 0 (might remove all), upper bound preserved'

fun max(xs: a[n..m]): a[n..1]
# 'Lower bound preserved, upper bound capped at 1'

fun map(xs: a[n..m], f: a -> b): b[n..m]
# 'Both bounds preserved exactly'

fun identity(x: a[n..m]): a[n..m]
# 'Pass through unchanged'
```

### Tracing Through Examples

**`max(xs: a[n..m]): a[n..1]`** — "collapse to at most one, preserving emptiness"

| Input | `n..m` | `n..1` | Output |
|-------|--------|--------|--------|
| `a+` | `1..∞` | `1..1` | `a` |
| `a*` | `0..∞` | `0..1` | `a?` |
| `a` | `1..1` | `1..1` | `a` |
| `a?` | `0..1` | `0..1` | `a?` |

**`filter(xs: a[n..m], p: ...): a[0..m]`** — "might become empty, but won't grow"

| Input | `n..m` | `0..m` | Output |
|-------|--------|--------|--------|
| `a+` | `1..∞` | `0..∞` | `a*` |
| `a*` | `0..∞` | `0..∞` | `a*` |
| `a` | `1..1` | `0..1` | `a?` |
| `a?` | `0..1` | `0..1` | `a?` |

The signature directly shows which bound gets modified—no lattice operators to memorize.

## Type Inference Architecture

Kleene types extend Hindley-Milner with cardinality bound variables:

```
Type Schemes:  ∀a₁...aₙ. ∀n₁,m₁...nₖ,mₖ. T
               ─────────  ───────────────
               type vars  bound vars (each 0/1 or 1/∞)
```

### Bound Variables

Lower bounds (`n`) range over `{0, 1}`.
Upper bounds (`m`) range over `{1, ∞}`.

This gives exactly four concrete cardinalities:
- `0..1` = optional
- `0..∞` = zero or more
- `1..1` = exactly one
- `1..∞` = one or more

### Constraint Solving

When you write:
```klein
fun foo(xs: a[n..m]): a[n..1]
```

And call it with `foo(myList)` where `myList: Int[1..∞]`:

1. Instantiate `n := 1`, `m := ∞`
2. Return type becomes `Int[1..1]` = `Int`

When called with `foo(maybeEmpty)` where `maybeEmpty: Int[0..∞]`:

1. Instantiate `n := 0`, `m := ∞`
2. Return type becomes `Int[0..1]` = `Int?`

### Inference Example

```klein
fun filter(xs: a[n..m], p: a -> Bool): a[0..m]
fun head(xs: a[1..m]): a

# Infer the type of:
fun foo(xs) = head(filter(xs, p))
```

Step by step:
1. `xs` gets fresh type `a[n..m]`
2. `filter(xs, p)` returns `a[0..m]`
3. `head(...)` requires lower bound `≥ 1`
4. But filter's output has lower bound `0`
5. Type error: "filter may produce empty list, but head requires non-empty"

## Common Function Signatures

```klein
# Preserve cardinality exactly
fun map(xs: a[n..m], f: a -> b): b[n..m]
fun identity(x: a[n..m]): a[n..m]

# Collapse to at most one (reduce/aggregate)
fun max(xs: a[n..m]): a[n..1]
fun reduce(xs: a[n..m], f: (a, a) -> a): a[n..1]
fun first(xs: a[n..m]): a[n..1]
fun last(xs: a[n..m]): a[n..1]

# Might become empty (filtering)
fun filter(xs: a[n..m], p: a -> Bool): a[0..m]
fun take(xs: a[n..m], count: Int): a[0..m]
fun drop(xs: a[n..m], count: Int): a[0..m]

# Always returns exactly one
fun fold(xs: a[n..m], init: b, f: (b, a) -> b): b
fun length(xs: a[n..m]): Int
fun isEmpty(xs: a[n..m]): Bool

# Require non-empty input
fun head(xs: a[1..m]): a
fun tail(xs: a[1..m]): a[0..m]

# Might grow (concatenation)
fun concat(xs: a[n..m], ys: a[p..q]): a[min(n,p)..∞]
# Conservative: lower bound is min of inputs, upper bound is unbounded
```

## Lifting Rules for Functions

### Lifting over `?` (optionality)

Binary operations on optionals:

```klein
fun (+)(a: Int[n..1], b: Int[p..1]): Int[min(n,p)..1]
```

If either operand might be absent (`n=0` or `p=0`), result might be absent.

| `a` | `b` | `min(n,p)..1` | Result |
|-----|-----|---------------|--------|
| `Int` | `Int` | `1..1` | `Int` |
| `Int?` | `Int` | `0..1` | `Int?` |
| `Int?` | `Int?` | `0..1` | `Int?` |

### Mapping over collections

At most ONE collection argument per function call:

```klein
fun f(a: Int, b: Int): Int

f(Int*, Int)   ✓  →  Int*
f(Int+, Int)   ✓  →  Int+
f(Int*, Int*)  ✗  # ambiguous: cartesian or zip?
```

Map preserves cardinality:
```klein
fun double(x: Int): Int = x * 2

xs: Int*
double(xs)   # returns Int*, this IS map
```

### Why Disallow Multiple Collections?

With two collections, semantics are ambiguous:
- Cartesian product? `[1,2] + [10,20]` → `[11,21,12,22]`?
- Zipwise? `[1,2] + [10,20]` → `[11,22]`?

Require explicit operations instead:
```klein
zip(xs, ys, f)      # pairwise
product(xs, ys, f)  # cartesian
```

## Pattern Matching

Pattern matching refines cardinalities in branches:

```klein
match xs
  [] -> ...          # xs was *, now known empty
  [h, ...t] -> ...   # xs had at least one
```

| Pattern | Input | `h` type | `t` type |
|---------|-------|----------|----------|
| `[]` | `a*` or `a?` | — | — |
| `[h, ...t]` | `a*` | `a` | `a*` |
| `[h, ...t]` | `a+` | `a` | `a*` |

Exhaustiveness checking uses cardinality:
```klein
fun head(xs: a+): a =
  match xs
    [h, ...t] -> h
# No [] case needed — a+ guarantees non-empty
```

## Structural Typing

Kleene types compose with structural records:

```klein
type Person = {
  name: String,
  spouse: String?,    # 0..1
  children: String*,  # 0..∞
  parents: String+    # 1..∞
}
```

With width subtyping:
```klein
fun getName(x: { name: String[n..m] }): String[n..m]
# Accepts any record with at least a name field, preserves cardinality
```

## No Nesting

Cardinality modifiers do not nest:

```klein
Int??   # not allowed
Int*?   # not allowed
```

Use explicit wrapper types:
```klein
type MaybeInt = { value: Int? }
xs: MaybeInt*   # list of optional ints
```

## Implementation Notes

### Cardinality Representation

```kotlin
data class Cardinality(
    val lower: Lower,  // Zero or One
    val upper: Upper   // One or Inf
) {
    enum class Lower { Zero, One }
    enum class Upper { One, Inf }

    companion object {
        val One = Cardinality(Lower.One, Upper.One)    // 1..1
        val Opt = Cardinality(Lower.Zero, Upper.One)   // 0..1
        val Plus = Cardinality(Lower.One, Upper.Inf)   // 1..∞
        val Star = Cardinality(Lower.Zero, Upper.Inf)  // 0..∞
    }
}

// Polymorphic cardinality with bound variables
data class CardinalityScheme(
    val lower: LowerBound,  // Concrete or Variable
    val upper: UpperBound   // Concrete or Variable
)

sealed class LowerBound {
    object Zero : LowerBound()
    object One : LowerBound()
    data class Var(val id: Int) : LowerBound()
}

sealed class UpperBound {
    object One : UpperBound()
    object Inf : UpperBound()
    data class Var(val id: Int) : UpperBound()
}
```

### Constraint Solving

Constraints are simple bounds:
- `n = 0` or `n = 1` (lower bound)
- `m = 1` or `m = ∞` (upper bound)

Solving is just substitution—no complex lattice propagation needed.

## Comparison to Related Work

| System | What it tracks | Mechanism |
|--------|---------------|-----------|
| Linear types (Rust) | Usage count | Affine/linear annotations |
| Liquid types | Arbitrary predicates | SMT solver |
| Dependent types (Idris) | Values in types | Full dependent type theory |
| **Kleene types** | Cardinality bounds | 2-bit bounds + HM polymorphism |

Kleene types are simpler—just two binary choices (lower: 0/1, upper: 1/∞) with standard polymorphism.

## Open Questions

1. **Explicit coercion syntax** — e.g., `xs!` to assert non-empty?

2. **System boundaries** — how to handle external data (JSON parsing)?

3. **Effect integration** — do effect return types carry cardinality?

4. **Error messages** — how to explain bound mismatches clearly?

5. **Arithmetic on bounds** — concat needs `min`/`max`; how far to go?

## Summary

Kleene types track "how many" values exist via cardinality bounds:

- **Intuitive syntax**: `T`, `T?`, `T+`, `T*` for users; `T[n..m]` in signatures
- **Readable signatures**: `a[n..m] -> a[0..m]` clearly shows "lower bound drops to 0"
- **Full inference**: bound polymorphism alongside Hindley-Milner
- **Zero runtime cost**: bounds are erased, purely compile-time
- **Simple implementation**: just two binary choices per cardinality

The key insight: cardinality is a range `[lower..upper]` where lower ∈ {0,1} and upper ∈ {1,∞}. Functions transform these bounds in predictable ways that are easy to read directly from the signature.
