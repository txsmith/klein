# LUB/GLB Type Simplification

**Date:** 2026-02-02
**Status:** Implemented (revised 2026-04-11)

## Context

Klein uses the SimpleSub algorithm for type inference, which naturally produces union and intersection types. The type simplification pipeline converts these internal representations into types displayed to the user.

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

### What's implemented

Ref simplification rules, in `TypeComponents.mergeRefFamilies`:

| Union components | Result | Example |
|---|---|---|
| Same-name refs with different type args | Merged args by variance | `List<Dog> \| List<Cat>` → `List<Dog \| Cat>` |
| Exhaustive sibling constructors | Parent type with merged args | `Cons<Num> \| Nil` → `List<Num>` |
| Invariant type args | Where clause | `Handle<Dog> \| Handle<Cat>` → `Handle<'A> where 'A <: Cat & Dog` |
| Primitive + Null | Optional | `Num \| Null` → `Num?` |

Type args of merged refs are recursively simplified according to variance:
- Covariant params: type arg is LUB'd (positive merge)
- Contravariant params: type arg is GLB'd (negative merge)
- Invariant params: produce where clause with pos/neg bounds

Type variables don't participate in LUB/GLB — `'A | Num` stays as-is, not collapsed to `Any`.

### What's deferred

See `docs/ideas/type-simplification-future.md` for:
- Subtype elimination (`Dog | { name: String }` → `{ name: String }`)
- Structural expansion of unrelated refs (`Dog | Fish` → `{ name: String }`)
- Empty nominal intersection → Nothing (`Dog & Cat` → Nothing)
- Unrelated union → Any (`Num | String` → `Any`)

These depend on pattern matching design decisions and are lower priority.

### Data Structure

`TypeComponents` uses a wide representation — sets of vars, prims, plus optional record, function, optional, and a map of ref families. Refs are grouped by parent type: when all constructors of a parent are present, the family stores both the merged parent ref and the original constructors. The parent's merged args are used for display and co-occurrence analysis; the constructors are preserved for internal logic.

Invariant type args carry both positive and negative bounds plus a type variable that connects them. Co-occurrence analysis unifies inference variables into this tvar, and coalescing strips it from bounds to produce clean where clauses.

### Pipeline

1. **Canonicalization**: converts inference types to `TypeComponents`, merging ref families during bounds folding
2. **Co-occurrence analysis**: eliminates redundant type variables, using the parent ref's merged args for correct invariant handling
3. **Coalescing**: converts `TypeComponents` to display types, collapsing invariant args to concrete types or where clauses

## Consequences

- Types displayed to users become simpler and more useful.
- Some type precision is lost in display, but no expressiveness is lost (the user couldn't act on the lost precision anyway).
- Co-occurrence analysis correctly handles invariant type parameters through the invariant tvar mechanism.
