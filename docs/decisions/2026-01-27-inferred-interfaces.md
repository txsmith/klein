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

Klein automatically computes an **inferred interface** for sum types by intersecting the field names across all constructors. Fields that appear in every constructor become part of the sum type's structural interface, making them directly accessible without pattern matching.

### Rules

1. **Common fields only.** Only fields present in *every* constructor are included. If any constructor lacks a field, it's excluded from the interface.

2. **Type unification via SimpleSub.** When a common field has different types across constructors, the types are unified using SimpleSub's subtyping. The interface field type becomes the least upper bound (join) of the individual types.

3. **Bare constructors have no fields.** If any constructor is bare (no fields), the interface is empty — no fields are directly accessible on the sum type.

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

### Why intersection (common fields only)?

The alternative is a union approach: include all fields from all constructors, making non-common fields optional (nullable). This was rejected because:

1. **Encourages pattern matching.** Non-common fields represent variant-specific data. Accessing them should go through pattern matching, which is safer and makes the branching explicit.

2. **No implicit nullability.** Klein's optional types are explicit. Automatically making fields nullable because they don't exist in some constructors would introduce nulls where the user didn't ask for them.

3. **Predictability.** With intersection, the rule is simple: if every constructor has it, you can access it. No surprises.

### Why unify types via SimpleSub rather than requiring exact matches?

Requiring identical field types across constructors would be too restrictive. Consider:

```klein
type Nested = A { data: { x: Num, y: String } } | B { data: { x: Num } }
```

The `data` field has different record types, but they share a common `x: Num`. SimpleSub's structural subtyping naturally computes the width-subtyping join (`{ x: Num }`), which is the most useful interface.

This also handles generics cleanly:

```klein
type Container<'A> = Box { value: 'A } | Wrapper { value: 'A, label: String }
```

Both constructors have `value: 'A`, so `Container<'A> <: { value: 'A }`.

## Consequences

**Positive:**
- Common fields are directly accessible, reducing boilerplate for types where all variants share data
- No new syntax or annotations needed — it falls out of the structural subtyping system
- Works with generics, nested records, and function types

**Negative:**
- Adding a bare constructor to a sum type is a breaking change (it empties the interface)
- Removing a field from any one constructor removes it from the interface for all users
- When field types differ across constructors, the unified type (e.g., `Num | String`) may be less useful than pattern matching for the specific variant
