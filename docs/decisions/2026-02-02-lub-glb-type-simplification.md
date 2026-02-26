# LUB/GLB Type Simplification

**Date:** 2026-02-02
**Status:** Draft

## Context

Klein uses the SimpleSub algorithm for type inference, which naturally produces union and intersection types. The type simplification pipeline converts these internal representations into types displayed to the user.

Currently, simplification is fragmented:
- **Records** are merged during canonicalization (`TypeComponents.merge`) — positive unions keep only common fields (structural LUB), negative intersections keep all fields (structural GLB).
- **Nominal types** are not currently simplified — `Cons<Num> | Nil` stays as a union rather than becoming `List<Num>`.
- **Primitives** are never simplified — `Num | String` stays as `Num | String`.

## The Problem

Klein does not have (and does not plan to have) a boolean algebraic type system. There is no negation type, and pattern matching will be restricted to complete matches over constructors of a single parent type. This means:

- Union types cannot appear in input positions (intersections occupy that role).
- There is no dispatch mechanism that can recover individual members of a union.
- A type like `Num | String` is observationally equivalent to `Top` — you can't do anything useful with the value that you couldn't do with `Top`.

Therefore, displaying precise unions is precision without utility. The user sees `Cons<Num> | Nil` when `List<Num>` would be both simpler and equally expressive.

### Comparison with MLscript

MLscript deliberately preserves precise unions like `Cons[Num] | Nil` because its boolean algebraic type system makes them useful — union types can participate in input positions, and negation types enable pattern matching with refinement. Klein intentionally avoids this complexity.

### Comparison with SimpleSub

SimpleSub has the same property that unions are useless (no dispatch mechanism), but it only has records and functions — no nominal types to collapse.

## Decision

Introduce unified **LUB/GLB** simplification that works across all type kinds. In positive position, unions are collapsed to their LUB. In negative position, the dual operation (GLB) applies to intersections.

### LUB Rules

| Union components | LUB | Example |
|---|---|---|
| Sibling constructors sharing a parent | Parent type (with LUB'd type args) | `Cons<Num> \| Nil` → `List<Num>` |
| Constructor + its parent type | Parent type | `Cons<Num> \| List<Num>` → `List<Num>` |
| Unrelated nominal types | Top | `List<Num> \| Option<Num>` → `Any` |
| Mixed nominal + primitive | Top | `List<Num> \| Num` → `Any` |
| Mixed nominal + record | Top (even if record is a structural subtype) | `Light \| { hue: Num }` → `Any` |
| Unrelated primitives | Top | `Num \| String` → `Any` |
| Primitive + Null | Optional | `Num \| Null` → `Num?` |

### Pipeline Position

LUB/GLB computation happens during canonicalization, in `TypeComponents.merge()`. This is where record merging already happens — when two `TypeComponents` are merged, common fields are kept (positive) or all fields are kept (negative). Extending this to handle refs uniformly means:

- When merging refs from the same family, collapse siblings to their parent type (with recursively LUB'd type args)
- When merging refs from different families, collapse to Any (positive) or Nothing (negative)
- When mixing incompatible types (nominal + primitive, unrelated nominals), collapse to Any/Nothing
- Type variables don't participate in LUB/GLB — `'A | Num` stays as-is, not collapsed to `Any`

### Data Structure

The intermediate type representation uses two structures:

**`TypeComponents`** holds the accumulated type data from inference:

```kotlin
data class TypeComponents(
    val vars: Set<TVar> = emptySet(),
    val prims: Set<PrimType> = emptySet(),
    val rec: Map<String, TypeComponents>? = null,
    val func: Pair<List<TypeComponents>, TypeComponents>? = null,
    val refs: Set<RefType> = emptySet(),
)
```

**`PolarType`** wraps components with polarity and simplification:

```kotlin
sealed class PolarType {
    data class Positive(val raw: TypeComponents, val lub: TypeComponents?) : PolarType()
    data class Negative(val raw: TypeComponents, val glb: TypeComponents?) : PolarType()
    data class Invariant(val pos: Positive, val neg: Negative) : PolarType()
}
```

This makes polarity explicit in the type system:
- Covariant positions produce `Positive` with raw components and computed LUB
- Contravariant positions produce `Negative` with raw components and computed GLB
- Invariant positions produce `Invariant` containing both

This means `simplifyType` and `coalesceType` don't need polarity parameters — the structure itself determines which simplification applies.

This approach:
- Unifies LUB/GLB with the existing record merge logic in `TypeComponents.merge()`
- Preserves raw refs for diagnostic messages
- Means `coalesceType` becomes a straightforward `PolarType` → `Type` conversion
- Removes ad-hoc sibling detection from coalescing

### Diagnostics for Any/Nothing

When LUB collapses a union to `Any` (positive) or GLB collapses an intersection to `Nothing` (negative), this almost certainly indicates a mistake. The raw refs are preserved in `raw` while the simplified result goes in `lub`/`glb`. This enables informative warnings:

```
Warning: inferred type 'Any' from incompatible types: Num, String
Warning: inferred type 'Nothing' from incompatible constraints: Num, String
```

The coalescing step can compare raw vs simplified: if simplified is Top/Bottom but raw contains multiple concrete types, emit a warning listing the original types.

Similarly, we could consider warning when a nominal type gets collapsed to a structural record type, e.g. `Light | { hue: Num }` becoming `{ hue: Num }`:

```
Warning: nominal type 'Light' collapsed to structural type '{ hue: Num }'
```

## Consequences

- Types displayed to users become simpler and more useful.
- Some type precision is lost in display, but no expressiveness is lost (the user couldn't act on the lost precision anyway).
- Raw type information is preserved in `PolarType` for diagnostics; only display uses the simplified form.
- `TypeComponents.merge` becomes the single place where all LUB/GLB logic lives, extending the existing record merge.
- `coalesceType` becomes a simple tree conversion without type-level logic — no more `merge` calls or polarity parameters needed there.
- Polarity is encoded in the data structure (`Positive`/`Negative`/`Invariant`) rather than threaded as a runtime parameter.
