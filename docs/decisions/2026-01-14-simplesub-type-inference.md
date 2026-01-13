# SimpleSub Type Inference Implementation

**Date:** 2026-01-14

## Context

Klein needed a type inference system that:
- Supports subtyping (records with width subtyping)
- Infers principal types (most general types)
- Handles recursive types cleanly
- Is well-understood and proven correct

The SimpleSub algorithm (Parreaux, 2020) meets these requirements and has a reference Scala implementation.

## Decision

**Port the SimpleSub reference implementation directly.** The Kotlin implementation follows the Scala code closely, translating idioms but preserving the algorithm structure.

Key components ported:

| Scala | Kotlin |
|-------|--------|
| `TypeSimplifier.scala` | `TypeSimplifier.kt`, `CompactType.kt` |
| `Typer.scala` | `Typer.kt`, `Subtyping.kt` |
| `Syntax.scala` type ADT | `SimpleType.kt` |

## Rationale

### Why a direct port?

1. **Proven correctness** — The reference implementation is tested and matches the paper
2. **Easier validation** — Can compare outputs directly with the Scala version
3. **Comprehensive test cases** — SimpleSub's tests translate directly to Klein tests
4. **Maintenance** — Future SimpleSub improvements can be back-ported

### Alternatives considered

| Alternative | Why Not |
|-------------|---------|
| Original implementation from paper | Risk of subtle bugs in corner cases |
| Hindley-Milner + separate subtype checks | Loses principal types |
| MLsub (predecessor) | More complex, same expressiveness |

## Type Representation Pipeline

The implementation uses three type representations:

```
SimpleType → CompactType → Type
 (inference)  (simplification) (display)
```

**SimpleType** — Mutable type variables with upper/lower bounds. Used during inference for efficient constraint propagation via bisubstitution.

**CompactType** — Immutable representation with explicit unions/intersections. Intermediate step during simplification. Makes co-occurrence analysis clean.

**Type** — Final display representation with named type variables. What users see.

## Canonicalization

The simplifier has two modes:

- **Pre-canonical** (`fromSimpleType`) — Direct conversion, preserves original cycle structure
- **Canonical** (`canonicalizeType`) — Merges co-occurring recursive types with different cycle lengths

**We made canonical the default.** This produces more normalized types at the cost of slightly more complexity in the output (e.g., `as c as b` instead of `as b`).

Example:
```klein
fun x(y) = { u = y, v = x(x) }
```

Pre-canonical: `(a) -> { u: a, v: b } as b`
Canonical: `(a) -> { u: a | ((a) -> b), v: c } as c as b`

The canonical form correctly captures that `v` contains a recursive type that merges with the outer recursion. This matches SimpleSub's behavior.

### Why canonical by default?

1. **Matches SimpleSub** — Reference implementation uses canonicalization
2. **More precise** — Captures all co-occurrence information
3. **Consistent** — Same program always produces same type (modulo variable naming)

## CLI Format Options

For debugging and understanding, the CLI exposes internal representations:

```bash
./klein infer example.klein              # --canonical (default)
./klein infer --pre-canonical example.klein
./klein infer --ir-compact example.klein  # CompactTypeScheme
./klein infer --ir-bounds example.klein   # SimpleType with bounds
```

This helps when:
- Debugging type inference issues
- Understanding why two types differ
- Comparing with SimpleSub's output

## Test Suite

Many tests are direct translations of SimpleSub's test cases:

- `SimpleSubTest.kt` — Core SimpleSub examples
- `RecursiveFunctionTest.kt` — Recursive type inference
- `TypeSimplifierTest.kt` — Simplification edge cases

This provides confidence that the port is faithful.

## Consequences

**Positive:**
- Principal types for all expressions
- Recursive types handled correctly
- Well-tested, proven algorithm
- Easy to compare with reference

**Negative:**
- Three type representations adds complexity
- Canonical output can look verbose (`as c as b`)
- Must maintain parity with SimpleSub for correctness

## References

- Parreaux, L. (2020). ["The Simple Essence of Algebraic Subtyping"](https://dl.acm.org/doi/10.1145/3409006)
- SimpleSub reference implementation: [LPTK/simple-sub](https://github.com/LPTK/simple-sub)
- Dolan, S. & Mycroft, A. (2017). "Polymorphism, Subtyping, and Type Inference in MLsub"
