# Most Useful Upper Bound (MUUB) Type Simplification

**Date:** 2026-02-02
**Status:** Draft

## Context

Klein uses the SimpleSub algorithm for type inference, which naturally produces union and intersection types. The type simplification pipeline converts these internal representations into types displayed to the user.

Currently, simplification is fragmented:
- **Records** are merged during canonicalization (`CompactType.merge`) — positive unions keep only common fields (structural LUB), negative intersections keep all fields (structural GLB).
- **Nominal types** are simplified during coalescing (`coalesceType`) — sibling constructors sharing a single parent type are collapsed to the parent. This is ad-hoc and doesn't handle mixed cases like `Cons<Num> | Nil | Num`.
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

Introduce a unified **Most Useful Upper Bound (MUUB)** simplification pass that works across all type kinds. In positive position, unions are collapsed to their MUUB. In negative position, the dual operation (Most Useful Lower Bound / MULB) applies to intersections.

### MUUB Rules

| Union components | MUUB | Example |
|---|---|---|
| Sibling constructors sharing a parent | Parent type (with MUUB'd type args) | `Cons<Num> \| Nil` → `List<Num>` |
| Constructor + its parent type | Parent type | `Cons<Num> \| List<Num>` → `List<Num>` |
| Unrelated nominal types | Top | `List<Num> \| Option<Num>` → `Any` |
| Mixed nominal + primitive | Top | `List<Num> \| Num` → `Any` |
| Mixed nominal + record | Top (even if record is a structural subtype) | `Light \| { hue: Num }` → `Any` |
| Unrelated primitives | Top | `Num \| String` → `Any` |
| Primitive + Null | Optional | `Num \| Null` → `Num?` |

### Pipeline Position

MUUB should happen during canonicalization, in `CompactType.merge()`. This is where record merging already happens — when two `CompactType`s are merged, common fields are kept (positive) or all fields are kept (negative). Extending this to handle refs uniformly means:

- When merging refs, collapse siblings to their parent type
- When mixing incompatible types (nominal + primitive, unrelated nominals), collapse to Top/Bottom
- Type variables don't participate in MUUB — `'A | Num` stays as-is, not collapsed to `Top`

This approach:
- Unifies MUUB with the existing record merge logic
- Means `simplifyType` (co-occurrence analysis) and `coalesceType` work on already-simplified types
- Removes the ad-hoc sibling detection from `coalesceType`, making it a straightforward `CompactType` → `Type` conversion without type-level logic

### Diagnostics for Top/Bottom

When MUUB collapses a union to `Top` (positive) or MULB collapses an intersection to `Bottom` (negative), this almost certainly indicates a mistake. Rather than silently displaying `Any` or `Nothing`, the simplifier should emit a warning that includes the participating types:

```
Warning: inferred type 'Any' from incompatible types: Num, String
Warning: inferred type 'Nothing' from incompatible constraints: Num, String
```

This means MUUB doesn't actually lose information — it moves it from the type representation to a diagnostic. The internal `CompactType` can track the contributing components so the warning message stays informative.

Similarly, we could consider warning when a nominal type gets collapsed to a structural record type, e.g. `Light | { hue: Num }` becoming `{ hue: Num }`:

```
Warning: nominal type 'Light' collapsed to structural type '{ hue: Num }'
```

## Consequences

- Types displayed to users become simpler and more useful.
- Some type precision is lost in display, but no expressiveness is lost (the user couldn't act on the lost precision anyway).
- Information about collapsed types is preserved in diagnostics, not lost.
- `CompactType.merge` becomes the single place where all MUUB logic lives, extending the existing record merge.
- `coalesceType` becomes a simple tree conversion without type-level logic — no more `merge` calls needed there.
