# Inferred Interfaces for Sum Types

**Date:** 2026-01-27

## Context

When a sum type has multiple constructors, a common question arises: can you access fields directly on the sum type, or must you pattern match first?

```klein
type Light = Red { duration: Num, intensity: Num }
           | Yellow { duration: Num }
           | Green { duration: Num, direction: String }
```

All three constructors have a `duration` field. Should `light.duration` work when `light: Light`, or should it require a match expression?

## Decision

Klein gives sum types a **structural interface** that emerges from subtyping constraints, not from an explicit field intersection pass.

During type definition preprocessing, the sum type's structure is represented as a fresh type variable. Each constructor's record type is then constrained to be a subtype of this variable. SimpleSub's constraint solver accumulates the lower bounds, which naturally produces the intersection of fields: only fields present in every constructor end up in the solved type, and their types are unified via the normal subtyping rules.

This means the interface is not a separate concept — it's just what SimpleSub infers when every constructor must subtype the same variable.

### Examples

**Same type across constructors:**

```klein
type Light = Red { duration: Num } | Yellow { duration: Num } | Green { duration: Num }

light.duration  // OK: Num
```

`Light <: { duration: Num }`

**Different types across constructors:**

```klein
type AB = A { x: Num } | B { x: String }

ab.x  // OK: Num | String
```

The interface field `x` has type `Num | String` because SimpleSub computes the join of `Num` and `String`.

**Partial overlap — only common fields:**

```klein
type AB = A { x: Num, y: String } | B { x: Num }

ab.x  // OK: Num
ab.y  // Error: MissingField
```

Only `x` is in the interface. `y` exists on `A` but not `B`.

**Bare constructor kills the interface:**

```klein
type Option<'A> = Some { value: 'A } | None

opt.value  // Error: MissingField
```

`None` has no fields, so the interface is empty. You must pattern match to access `value`.

**Function types with different signatures:**

```klein
type Handlers = A { f: Num -> Num } | B { f: String -> Num }

h.f  // OK: (Num & String) -> Num
```

Function parameters unify contravariantly (intersection), return types covariantly (union).

**Nested records:**

```klein
type Nested = A { data: { common: Num, only_a: String } }
            | B { data: { common: Num, only_b: Bool } }

n.data  // OK: { common: Num }
```

The nested record types are themselves unified — only their common fields survive.

### Constructor access is unaffected

Individual constructors retain their full field set regardless of the sum type interface:

```klein
type Light = Red { duration: Num, intensity: Num }
           | Yellow { duration: Num }

red = Red(10, 100)
red.intensity  // OK: Red <: { duration: Num, intensity: Num }
```

## Rationale

### Why constraint-based rather than explicit intersection?

The interface falls out of SimpleSub's existing machinery. By constraining each constructor's record to subtype a shared type variable, we get field intersection, type unification, and correct variance handling for free — no special-purpose code needed.

This also means nested structures unify correctly without extra work:

```klein
type Container<'A> = Box { value: 'A } | Wrapper { value: 'A, label: String }
```

Both constructors have `value: 'A`, so `Container<'A> <: { value: 'A }`. SimpleSub handles this through normal record width subtyping.

### Why not include all fields with non-common ones made optional?

The alternative is a union approach: include all fields from all constructors, making non-common fields nullable. This was rejected because:

1. **No implicit nullability.** Klein's optional types are explicit. Automatically making fields nullable because they don't exist in some constructors would introduce nulls where the user didn't ask for them.

2. **Encourages pattern matching.** Non-common fields represent variant-specific data. Accessing them should go through pattern matching, which is safer and makes the branching explicit.

3. **Predictability.** The constraint-based approach has a simple mental model: if every constructor has it, you can access it.

## Consequences

**Positive:**
- Common fields are directly accessible, reducing boilerplate for types where all variants share data
- No new syntax or annotations needed — it falls out of the structural subtyping system
- Works with generics, nested records, and function types

**Negative:**
- Adding a constraint per constructor means that violating the interface produces multiple type errors (one per constructor). Deduplicating these into a single clear error is future work.
