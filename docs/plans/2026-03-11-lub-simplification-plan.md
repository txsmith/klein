# LUB/GLB Simplification Implementation Plan

## Summary

We're implementing LUB/GLB type simplification so that displayed types collapse unions to their least upper bound and intersections to their greatest lower bound.

## What's done

- **Component sealed class** with `tightBound` field on `TypeComponents` for incremental LUB/GLB computation during merge.
- **tightBound populated** in `fromSimpleType` for prim, ref, record, function, optional.
- **Prim, record, function, optional merging** in `mergeTightBounds` — working and tested.
- **Ref merging** in `mergeTightBounds` — same-name refs (merge args by variance), sibling refs (merge to parent), constructor+parent (merge to parent). All working.
- **`coalesceLeastUpperBound`** reads `tightBound` to produce simplified types. Invariant bounds fix applied.
- **Test infrastructure** — `assertType` checks both regular type and LUB. `inferLUB` helper for direct LUB testing.
- **LubGlbSimplificationTest** — 22 passing tests covering sibling merging, same-name refs, variance, records, functions, optionals, vars+bound, nested simplification.

## Current issues

### null tightBound semantics
When `tightBound` is null (incompatible components), `coalesceLeastUpperBound` produces `Any`/`Nothing`. This is correct for `Num | String` (observationally equivalent to `Any`) but wrong for `Dog | Fish` where common fields are accessible. Need to either fall back to the regular type (union) or structurally expand.

### Constructor type design (blocking)
See `docs/ideas/constructor-type-options.md`. Leaning toward **option 3**: merge sibling ref bounds during constraint solving. This would:
- Eliminate sibling unions from TypeComponents entirely (already merged to parent)
- Make `mergeSiblingRefs` and `mergeConstructorWithParent` in TypeComponents dead code
- Require changes to `Subtyping.constrain` (scan lower bounds for sibling/same-name merge opportunities)

### Structural expansion for unrelated refs
If the LUB should show `{ name: String }` for `Dog | Fish` (rather than the union), we need structural expansion. This requires pre-canonicalizing constructor ifaces in TypeEnv so expansion works at the TypeComponents level without ad-hoc SimpleType conversion.

### Recursive types
25 RecursiveFunctionTest failures — `coalesceLeastUpperBound` doesn't handle `as` clauses / recursive type knots. Separate from the ref merging work.

## What's next

### 1. Implement option 3: merge sibling bounds in constraint system
In `Subtyping.constrain`, when adding a TRef lower bound to a TVar, scan existing lower bounds for siblings (same parent type). If found, merge both to parent ref. Also handle same-name refs with different type args (merge args, create fresh TVars).

### 2. Resolve null tightBound fallback
Decide: fall back to regular type (union) or structurally expand. If structural, pre-canonicalize constructor ifaces.

### 3. Update tests
Test expectations will change once the constraint system merges siblings. TypeDefInferenceTest, LubGlbSimplificationTest, and others need updating.

### 4. Recursive type support
Handle `as` clauses in `coalesceLeastUpperBound` to fix 25 RecursiveFunctionTest failures.

## Key files

- `klein-lib/src/commonMain/kotlin/klein/types/TypeComponents.kt` — `Component`, `TypeComponents`, `merge`, `mergeTightBounds`
- `klein-lib/src/commonMain/kotlin/klein/types/TypeSimplifier.kt` — `coalesceType`, `coalesceLeastUpperBound`
- `klein-lib/src/commonMain/kotlin/klein/types/Subtyping.kt` — constraint solving (option 3 changes here)
- `klein-lib/src/commonMain/kotlin/klein/Klein.kt` — `InferenceResult.leastUpperBound`
- `klein-lib/src/commonTest/kotlin/klein/types/LubGlbSimplificationTest.kt` — LUB test suite
- `docs/ideas/constructor-type-options.md` — design options comparison
