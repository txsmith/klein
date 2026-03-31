# LUB/GLB Simplification Implementation Plan

## Summary

We're implementing LUB/GLB type simplification so that displayed types collapse unions to their least upper bound and intersections to their greatest lower bound.

## Design decisions

- **Option 2** — keep constructor union types. No constraint-level merging. See `docs/ideas/constructor-type-options.md`.
- **No Top/Bottom merging for now** — `Num | String` stays as `Num | String` (not `Any`). Revisit after pattern matching is designed.
- **null tightBound fallback** — when tightBound is null but concrete components exist, fall back to regular type (union/intersection) instead of Top/Bottom.

## What's done

- **Component sealed class** with `tightBound` field on `TypeComponents` for incremental LUB/GLB computation during merge.
- **tightBound populated** in `fromSimpleType` for prim, ref, record, function, optional.
- **Prim, record, function, optional merging** in `mergeTightBounds` — working and tested.
- **Same-name ref merging** in `mergeTightBounds` — merge args by variance. Working and tested.
- **Sibling and constructor+parent merging** — implemented but disabled pending exhaustiveness check.
- **null tightBound fallback** — falls back to regular type instead of Top/Bottom. Implemented.
- **`coalesceLeastUpperBound`** reads `tightBound` to produce simplified types. Invariant bounds fix applied.
- **Test infrastructure** — `assertType` checks both regular type and LUB. `inferLUB` helper for direct LUB testing.
- **Test expectations updated** — all tests assert desired behavior. 47 fail (expected — features not yet implemented).

## Current test failures (47)

- **24 RecursiveFunctionTest** — `coalesceLeastUpperBound` doesn't handle `as` clauses.
- **20 LubGlbSimplificationTest** — breakdown:
  - 8 exhaustive sibling → parent (disabled, needs exhaustiveness check)
  - 9 structural expansion (unrelated refs, ref+record — needs pre-canonicalized ifaces)
  - 3 other (invariant where clause, nested cases)
- **3 TypeDefInferenceTest** — exhaustive sibling LUB (same as above)

## Simplification avenues

Six distinct simplification rules, roughly in priority order:

### 1. Exhaustive constructor union → parent type
If a union contains ALL constructors of a parent type, replace with the parent.
- `Dog | Cat` → `Animal` (when those are all of Animal's constructors)
- `Nil | Cons<Num>` → `List<Num>`
- `Red | Yellow` stays as `Red | Yellow` (Green missing from Light)

Relevant to pattern matching: this is the type-level exhaustiveness check.

### 2. Subtype elimination
If A <: B, eliminate A from unions (A | B → B) and B from intersections (A & B → A).
- `Dog | { name: String }` → `{ name: String }` (Dog <: {name: String})
- `Dog & { name: String }` → `Dog` (Dog <: {name: String})
- `Cons<Num> | List<Num>` → `List<Num>` (Cons <: List)

### 3. Same-name ref merging
Merge type args by variance. **Already implemented.**
- `Cons<Num> | Cons<String>` → `Cons<Num | String>`
- `Drain<Dog> | Drain<Cat>` → `Drain<Dog & Cat>` (contravariant)
- `Handle<Dog> | Handle<Cat>` → where clause (invariant, can't merge)

### 4. Unrelated ref union → structural expansion (deferred)
Expand unrelated refs to their structural records and merge common fields.
- `Dog | Fish` → `{ name: String }`

Deferred: depends on pattern matching design (might prefer keeping the union if pattern matching can discriminate bare unions). Requires pre-canonicalizing constructor ifaces.

### 5. Empty nominal intersection → Nothing (low priority)
No value can inhabit two nominal types that are not subtypes of the other.
- `Dog & Fish` → Nothing (different type families)
- `Dog & Cat` → Nothing (different constructors, same family)
- `Num & String` → Nothing
- `Ref & Prim` → Nothing

Note: Record & Record merges structurally (already handled). Ref & Record stays as-is (valid structural subtyping, falls under subtype elimination).

### 6. Unrelated union → Any (low priority)
Last-pass: unions with no common operations collapse to Any.
- `Num | String` → `Any`
- `Num | Dog` → `Any`

Deferred until pattern matching design clarifies observational equivalence.

## What's next

### Implement #1 and #2
Re-enable sibling merging with exhaustiveness check. Implement subtype elimination for Ref|Record and Constructor|Parent cases.

Fixes: ~11 LubGlb tests + 3 TypeDefInference tests.

### Recursive type support
Handle `as` clauses in `coalesceLeastUpperBound` to fix 24 RecursiveFunctionTest failures.

## Key files

- `klein-lib/src/commonMain/kotlin/klein/types/TypeComponents.kt` — `TypeComponents`, `merge`, `mergeTightBounds`
- `klein-lib/src/commonMain/kotlin/klein/types/TypeSimplifier.kt` — `coalesceType`, `coalesceLeastUpperBound`
- `klein-lib/src/commonMain/kotlin/klein/Klein.kt` — `InferenceResult.leastUpperBound`
- `klein-lib/src/commonTest/kotlin/klein/types/LubGlbSimplificationTest.kt` — LUB test suite
- `docs/ideas/constructor-type-options.md` — design options comparison
