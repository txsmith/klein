# Future Type Simplification

> **Status (2026-06-24): moot.** Operation Bidi deletes the type simplifier entirely — there are no inferred types left to simplify. Kept for historical context. See [decisions/2026-06-24-adopt-operation-bidi.md](../decisions/2026-06-24-adopt-operation-bidi.md).

Type simplification avenues that are not yet implemented. These are lower priority and some depend on pattern matching design decisions.

See `docs/decisions/2026-02-02-lub-glb-type-simplification.md` for what's already done (exhaustive collapse, same-name ref merging, invariant where clauses).

## Subtype elimination

If A <: B, eliminate A from unions (A | B → B) and B from intersections (A & B → A).

```
Dog | { name: String }   → { name: String }    (Dog <: {name: String})
Dog & { name: String }   → Dog                  (Dog <: {name: String})
Cons<Num> | List<Num>    → List<Num>            (Cons <: List)
```

Requires a subtype check between refs and records (expand ref to its structural interface, then compare fields). Related to structural expansion but distinct — this is about eliminating redundant components, not merging unrelated ones.

## Structural expansion of unrelated refs

Expand unrelated refs to their structural record interfaces and merge common fields.

```
Dog | Fish               → { name: String }
NumBox | StrTag           → { value: Num | String }
```

Depends on pattern matching design — if pattern matching can discriminate bare constructor unions, we might prefer keeping the union. Requires pre-canonicalizing constructor interfaces (computing the "inferred interface" of a type's common fields).

11 test cases exist in `LubGlbSimplificationTest` covering:
- Unrelated refs with common/partial/different field types (5 tests)
- Ref + record merging (4 tests)
- Optional + record in params (2 tests)

## Empty nominal intersection → Nothing

No value can inhabit two unrelated nominal types.

```
Dog & Fish     → Nothing   (different type families)
Dog & Cat      → Nothing   (different constructors, same family)
Num & String   → Nothing
Ref & Prim     → Nothing
```

Record & Record already merges structurally. Ref & Record stays as-is (valid structural subtyping).

## Unrelated union → Any

Last-pass: unions with no common operations collapse to Any.

```
Num | String   → Any
Num | Dog      → Any
```

Deferred until pattern matching design clarifies observational equivalence. Currently `Num | String` stays as-is, which may be preferable.
