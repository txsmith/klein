# LUB/GLB Type Simplification

**Date:** 2026-02-02
**Status:** Draft (revised 2026-03-11)

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

### LUB/GLB Rules

In positive position (LUB of a union):

| Union components | LUB | Example |
|---|---|---|
| Sibling constructors sharing a parent | Parent type (with LUB'd type args) | `Cons<Num> \| Nil` → `List<Num>` |
| Constructor + its parent type | Parent type | `Cons<Num> \| List<Num>` → `List<Num>` |
| Same-name refs with different type args | Same ref with merged args (respecting variance) | `List<Dog> \| List<Cat>` → `List<Animal>` |
| Unrelated nominal types | Structural record LUB of their interfaces | `Dog \| Cat` → `{ name: String }` (if both have `name`) |
| Unrelated types with no common structure | Top | `Num \| String` → `Any` |
| Primitive + Null | Optional | `Num \| Null` → `Num?` |

Type args of merged refs are recursively simplified according to the variance of the corresponding type parameter:
- Covariant params: type arg is LUB'd (positive merge)
- Contravariant params: type arg is GLB'd (negative merge)
- Invariant params: type args must be equal, otherwise fall back to structural record LUB of the refs' interfaces

In negative position, the dual (GLB of an intersection) applies.

Type variables don't participate in LUB/GLB — `'A | Num` stays as-is, not collapsed to `Any`.

### Data Structure

`TypeComponents` retains its existing wide representation — sets of vars, prims, refs, plus optional record, function, and optional type. This wide bag is optimized for the accumulation that happens during bounds merging.

A new `tightBound: Component?` field on `TypeComponents` holds the incrementally computed LUB (positive) or GLB (negative). The name is polarity-agnostic since the same field serves both roles depending on context.

```kotlin
sealed class Component

data class RefType(
    val name: String,
    val args: List<RefArg>,
) : Component()

data class RecordType(
    val fields: Map<String, TypeComponents>,
) : Component()

data class FunctionType(
    val params: List<TypeComponents>,
    val result: TypeComponents,
) : Component()

data class PrimComponent(val prim: PrimType) : Component()
data class OptionalComponent(val inner: TypeComponents) : Component()

data class TypeComponents(
    val vars: Set<TVar> = emptySet(),
    val prims: Set<PrimType> = emptySet(),
    val rec: RecordType? = null,
    val func: FunctionType? = null,
    val optional: TypeComponents? = null,
    val refs: Set<RefType> = emptySet(),
    val tightBound: Component? = null,
)
```

The existing `RefType`, `RecordType`, and `FunctionType` classes directly extend `Component` — no wrapper classes needed. `Component` is intentionally flat: when a `RefType`'s args contain `TypeComponents`, the simplified nested types are found by reading the `tightBound` of those nested `TypeComponents`. The simplification threads through the existing tree structure.

### Pipeline Position

LUB/GLB computation happens during `TypeComponents.merge()`, which is called during canonicalization as bounds are folded in. This piggybacks on the existing merge algorithm which already handles record and function merging.

The key property: `merge` is called as a fold over singleton `TypeComponents` (each representing one bound from inference). At each fold step, two `tightBound` values are combined pairwise:

1. `fromSimpleType` produces singleton `TypeComponents` where `tightBound` is trivially the singleton's own component
2. `flattenBounds` folds singletons together via `merge`
3. At each `merge` step: take the two `tightBound`s, compute pairwise LUB/GLB, produce the new `tightBound`

This means `merge` needs access to `TypeEnv` for looking up parent types and structural interfaces during ref simplification.

Pairwise `Component` combination (positive position):

| Left | Right | Result |
|---|---|---|
| `Ref(A)` | `Ref(A)` (same name) | `Ref(A)` with merged type args |
| `Ref(X)` | `Ref(Y)` (siblings) | `Ref(Parent)` with merged type args |
| `Ref(X)` | `Ref(Y)` (unrelated) | Structural record LUB of their interfaces, or `Top` |
| `Ref(X)` | `Rec(R)` | Expand ref to structural record, then record LUB |
| `Prim(P)` | `Prim(P)` (same) | `Prim(P)` |
| `Prim(P)` | `Prim(Q)` (different) | `Top` |
| `Rec(R1)` | `Rec(R2)` | `Rec` with common fields, field types merged |
| `Func(F1)` | `Func(F2)` | `Func` with merged params (contravariant) and result (covariant) |
| Any | `null` | `null` (Top in positive, Bottom in negative) |

### How coalesceType uses tightBound

`coalesceType` reads `tightBound` when present to produce the simplified display type. The raw wide fields (prims, refs, etc.) remain available for:

- Error messages: showing the user what concrete types conflicted
- LSP hover: displaying the full union/intersection when useful
- Diagnostics: detecting when simplification collapsed to Top/Bottom

### Diagnostics for Any/Nothing

When `tightBound` is `null` but the raw components contain multiple concrete types, this almost certainly indicates a mistake. A `null` bound means Top (positive) or Bottom (negative) — incompatible types were merged. The raw components are preserved alongside the bound, enabling informative warnings:

```
Warning: inferred type 'Any' from incompatible types: Num, String
Warning: inferred type 'Nothing' from incompatible constraints: Num, String
```

The coalescing step can check: if `tightBound` is `null` but raw contains multiple concrete types, emit a warning listing the original types.

## Consequences

- Types displayed to users become simpler and more useful.
- Some type precision is lost in display, but no expressiveness is lost (the user couldn't act on the lost precision anyway).
- Raw type components are preserved for error messages, LSP hover, and diagnostics.
- `TypeComponents.merge` becomes the single place where all LUB/GLB logic lives, extending the existing record/function merge.
- `tightBound` is computed incrementally during the merge fold — no separate simplification pass needed for this.
- `coalesceType` reads `tightBound` for simplified output, falling back to raw fields when detail is needed.
