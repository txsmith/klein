# Pattern Matching via Synthetic Type Constructors

**Status:** Shelved idea, not planned for implementation yet.

## Observation

`TOptional` is the dual of `TNull`. In negative position it acts as a union: "this input can be `A` or `Null`". This is something SimpleSub doesn't officially support — unions in negative position — but we hacked it in by encoding it as an explicit type constructor with a built-in discriminator (`?.`).

Pattern matching generalizes this. A match expression says "this input can be `A` or `B` or `C`", with runtime discrimination on each branch. `TOptional` is just the special case where the branches are `A` and `Null`.

## Why this is non-trivial

SimpleSub's constraint solver is fundamentally **conjunctive**. Multiple uses of a parameter create intersections:

```
fun f(x) =
  _ = x.name   // x <: {name: ...}
  _ = x.age    // x <: {age: ...}
  // result: x: {name, age}  (intersection — both constraints must hold simultaneously)
```

Pattern matching is **disjunctive**:

```
fun f(x) = match x with
  | Dog d -> d.name   // in this branch: x is Dog
  | Cat c -> c.lives  // in this branch: x is Cat
  // result: x: Dog | Cat  (union — only one constraint holds per branch)
```

There is no mechanism in SimpleSub for `T <: A | B` in negative position. The solver only knows how to handle `T <: A` and `T <: B` separately, which gives `T <: A & B`.

## The TOptional precedent

TOptional works because:
1. It's a single, closed, built-in discriminator (`?.` / null check)
2. The solver treats `TOptional(A)` as an opaque type constructor, not as `A | Null`
3. Exhaustiveness is trivially guaranteed (it's always `A` or `Null`)

## Proposed approach: synthetic type constructors

Rather than adding general negative unions to the constraint solver, encode each match expression as a synthetic type constructor — the same strategy as TOptional, scaled up.

A match expression like:
```
match x with
  | Dog d -> d.name
  | Cat c -> c.lives
```

Would generate a synthetic type `TMatch(Dog, Cat)` that:
- In negative position, acts as a union (accepts Dog or Cat)
- Has a built-in discriminator (constructor tag check)
- Requires exhaustiveness checking against the parent type

This keeps SimpleSub's core constraint solver unchanged.

## Open questions

- **Exhaustiveness checking:** TOptional is always exhaustive. General pattern matches require proving coverage, and non-exhaustive matches need a failure type/effect.
- **Type simplification:** Co-occurrence analysis assumes unions are positive, intersections are negative. Synthetic match types would need special handling in simplification and LUB/GLB merging.
- **Interaction with structural subtyping:** If `Dog <: {name: String}` structurally, how does `TMatch<Dog, Cat>` relate to `TMatch<{name: String}, Cat>`? Do we preserve nominal discrimination or allow structural patterns?
- **Nested patterns:** `match x with | Cons(Dog d, _) -> ...` introduces discrimination at multiple levels.
- **Inference:** When the user writes a match, how much of the synthetic type can be inferred vs. requires annotation?
