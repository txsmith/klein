# Variance Inference for Type Parameters

**Date:** 2026-01-27

## Context

Klein's type definitions support type parameters:

```klein
type Box<'A> = Box { value: 'A }
type Consumer<'A> = Consumer { consume: 'A -> String }
type Ref<'A> = Ref { get: () -> 'A, set: 'A -> () }
```

When checking subtyping between instantiated types like `Box<Dog> <: Box<Animal>`, the type system needs to know the *variance* of each type parameter — whether it's safe to substitute subtypes, supertypes, or neither.

Languages like Kotlin and C# require explicit annotations (`out`/`in`). Scala infers variance in some cases but also uses annotations. The question is whether Klein should require annotations or infer variance automatically.

## Decision

Klein **automatically infers variance** for all type parameters. No variance annotations exist in the language.

Variance is computed during type definition preprocessing by analysing how each type parameter appears in the constructor fields. The result is one of:

| Variance | Meaning | Example |
|----------|---------|---------|
| Covariant (+) | Appears only in output positions | `Box { value: 'A }` |
| Contravariant (−) | Appears only in input positions | `Consumer { f: 'A -> String }` |
| Invariant (=) | Appears in both positions | `Ref { get: () -> 'A, set: 'A -> () }` |

### Subtyping rules

Given `T<'A>` where `'A` has variance `v`, then `T<X> <: T<Y>` requires:

- **Covariant:** `X <: Y`
- **Contravariant:** `Y <: X`
- **Invariant:** `X <: Y` and `Y <: X` (exact match)

### Phantom types

When a type parameter is unused in any constructor field, it is inferred as **invariant** (not bivariant). This supports phantom types — type-level tags that carry no runtime data but enforce distinctions at compile time:

```klein
type UserId<'State> = UserId { value: Num }
```

Here `'State` appears nowhere in the fields. Making it invariant means `UserId<Validated>` and `UserId<Unvalidated>` are incompatible types, which is the useful behavior for phantom types.

The alternative — treating unused parameters as bivariant (allowing any substitution) — would make phantom types useless since any `UserId<X>` could substitute for any `UserId<Y>`.

### Nested and recursive types

Variance composes through nesting:

- **Double contravariance = covariance:** `Handler { handle: ('A -> ()) -> () }` — `'A` is covariant because two contravariant positions cancel out.
- **Recursive types with swapped parameters:** `Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }` — both `'A` and `'B` become invariant because the swap places each parameter in conflicting positions across the recursion.

### Sum type variance

For sum types, variance is computed across all constructors. If one constructor uses `'A` covariantly and another uses it contravariantly, the parameter becomes invariant:

```klein
type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
```

Here `'A` is invariant for `V`, but the individual constructors `Cov` and `Cont` have their own variance (`Cov<'A>` is covariant, `Cont<'A>` is contravariant).

## Rationale

### Why infer rather than annotate?

1. **Simplicity for users.** Klein targets tech-savvy business users, not PL experts. Variance annotations are a leaky abstraction — they require understanding subtyping theory to use correctly.

2. **No ambiguity.** Variance is fully determined by how type parameters appear in fields. There's no case where the user's intent would differ from what inference produces.

3. **Fewer errors.** Explicit annotations can be wrong (e.g., marking a parameter as covariant when it also appears in contravariant position). Inference is always correct by construction.

4. **Klein types are simple.** Klein doesn't have mutable fields, method overriding, or other features that make variance inference difficult in general-purpose languages. The field-based analysis is sufficient.

### Why invariant for phantom types (not bivariant)?

The variance lattice has four positions:

```
    ± (bivariant)
       / \
      +   −
       \ /
        = (invariant)
```

Bivariant (±) means "both covariant and contravariant" — i.e., any substitution is valid. This is the mathematically natural result for unused parameters. However, it makes phantom types useless: `UserId<Validated> <: UserId<Unvalidated>` would hold in both directions.

Collapsing bivariant → invariant after inference is a pragmatic choice. It means unused type parameters enforce strict equality, which is the only reason you'd have a phantom type parameter in the first place.

## Consequences

**Positive:**
- No variance syntax to learn
- Phantom types work correctly out of the box
- Constructor types get their own variance independent of the parent sum type

**Negative:**
- Users cannot override inferred variance (e.g., force a covariant parameter to be invariant for safety). In practice this hasn't been needed.
- The bivariant → invariant collapse is a deviation from the pure lattice semantics. It's the right default but removes the ability to express "this parameter is truly irrelevant."
