# LUB/GLB Simplification Implementation Plan

## Summary

We're implementing LUB/GLB type simplification so that displayed types collapse unions to their least upper bound and intersections to their greatest lower bound.

## Design decisions

- **Option 2** — keep constructor union types. No constraint-level merging. See `docs/ideas/constructor-type-options.md`.
- **No Top/Bottom merging for now** — `Num | String` stays as `Num | String` (not `Any`). Revisit after pattern matching is designed.
- **null tightBound fallback** — when tightBound is null but concrete components exist, fall back to regular type (union/intersection) instead of Top/Bottom.
- **Exhaustive-only sibling merging** — only merge siblings to parent when ALL constructors are present.
- **Structural expansion for unrelated refs** — `Dog | Fish` → `{ name: String }` (common fields). Needs pre-canonicalized ifaces.
- **Intersection simplification** — ref & ref = Nothing, ref & prim = Nothing, prim & prim (different) = Nothing. Record & record = structural merge (all fields). Ref & record stays as-is (structural subtyping).

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

## What's next

### 1. Re-enable exhaustive sibling merging
Add exhaustiveness check to `mergeSiblingRefs`: count constructors of the parent type, only merge when all are present. Re-enable sibling and constructor+parent paths in `mergeRefTypes`.

Fixes: ~8 LubGlb tests + 3 TypeDefInference tests.

### 2. Pre-canonicalize constructor ifaces
In `fromSimpleType` for `TRef`, also canonicalize the constructor's iface fields (substituting type param TVars with actual type args via the same `fromSimpleType`). Store the resulting `RecordType` on the `RefType`. `flattenBounds` flattens it like any other nested TypeComponents.

This is the foundation for structural expansion and ref+record merging.

### 3. Structural expansion for unrelated refs and ref+record
During merge: when encountering ref+record, expand the ref to its pre-canonicalized record, merge records, nullify the ref. For unrelated refs, expand both and merge.

Fixes: ~9 LubGlb tests.

### 4. Intersection simplification
Simplify intersections of nominal types:
- `Ref & Ref` (different constructors) → Nothing
- `Ref & Prim` → Nothing
- `Prim & Prim` (different) → Nothing
- `Record & Record` → structural merge (all fields)
- `Ref & Record` → stays as-is (valid structural subtyping)

This can happen either in `mergeTightBounds` (negative position) or as a post-processing step.

### 5. Remove tightBound (optional, future)
With pre-canonicalized ifaces, `tightBound` becomes partially redundant:
- Same-name ref merging can happen in `merge` by intelligently merging the refs set
- Ref+record merging: expand and merge during `merge`
- Cross-kind detection moves to coalescing

May simplify the architecture but not blocking.

### 6. Recursive type support
Handle `as` clauses in `coalesceLeastUpperBound` to fix 24 RecursiveFunctionTest failures.

## Key files

- `klein-lib/src/commonMain/kotlin/klein/types/TypeComponents.kt` — `TypeComponents`, `merge`, `mergeTightBounds`
- `klein-lib/src/commonMain/kotlin/klein/types/TypeSimplifier.kt` — `coalesceType`, `coalesceLeastUpperBound`
- `klein-lib/src/commonMain/kotlin/klein/Klein.kt` — `InferenceResult.leastUpperBound`
- `klein-lib/src/commonTest/kotlin/klein/types/LubGlbSimplificationTest.kt` — LUB test suite
- `docs/ideas/constructor-type-options.md` — design options comparison
