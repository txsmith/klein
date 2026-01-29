# Nominal-Structural Subtyping Direction

**Date:** 2026-01-27

## Context

Klein has both nominal types (defined with `type`) and structural types (record literals). The type system needs to define the subtyping relationship between them.

```klein
type Money = Money { value: Num }

m = Money(100)
r = { value = 100 }
```

Both `m` and `r` have a `value: Num` field. The question is: which can substitute for the other?

## Decision

The subtyping relationship is **asymmetric**:

- **Nominal → Structural:** `Money <: { value: Num }`. A nominal type subtypes its structural equivalent. You can pass a `Money` wherever a `{ value: Num }` is expected.
- **Structural → Nominal:** `{ value: Num }` does NOT subtype `Money`. You cannot pass a plain record where a `Money` is expected.

### In practice

```klein
type Money = Money { value: Num }

// Nominal → Structural: OK
fun getValue(r) = r.value
getValue(Money(100))       // Works: Money <: { value: Num }

// Structural → Nominal: Error
type Account = Account { balance: Money }
Account({ value = 100 })   // Error: { value: Num } is not Money
```

### Constructor subtyping

Constructors subtype their parent sum type, which in turn subtypes its inferred interface:

```
Cons<Num> <: List<Num> <: { head: Num }   (if all constructors have `head`)
```

This chain is always one-directional. A `{ head: Num }` record does not subtype `List<Num>` or `Cons<Num>`.

### Nominal-to-nominal subtyping

Nominal types from different definitions are never subtypes of each other, even if structurally identical:

```klein
type Dollars = Dollars { amount: Num }
type Euros = Euros { amount: Num }

// Dollars and Euros are unrelated types, despite identical structure
```

The only nominal-to-nominal subtyping is constructor → parent (e.g., `Dog <: Animal`).

## Rationale

### Why allow nominal → structural?

Nominal types are *more specific* than their structural equivalents. A `Money` value is a `{ value: Num }` record with additional identity. Allowing nominal → structural means:

1. **Generic functions work naturally.** A function `fun getValue(r) = r.value` accepts any record with a `value` field, including nominal types. No special syntax needed.

2. **Gradual adoption.** Code written against structural types doesn't need to change when a nominal type is introduced. Existing functions that accept `{ value: Num }` automatically accept `Money`.

3. **Reflects reality.** A `Money` *has* a `value: Num`. Denying access to it through structural typing would be artificial.

### Why forbid structural → nominal?

The whole point of nominal types is to distinguish things that are structurally identical:

1. **Type safety.** `Dollars` and `Euros` have the same structure but different meanings. Allowing `{ amount = 100 }` to pass as either would defeat the purpose.

2. **Construction is explicit.** Creating a nominal value requires calling the constructor (`Money(100)`), which makes the intent clear and enables validation in the future.

3. **No accidental subsumption.** If structural records could silently become nominal types, refactoring a record into a nominal type wouldn't actually add any safety — existing code would continue working without calling the constructor.

### Why not bidirectional with explicit coercion?

Some languages allow structural → nominal via explicit casts or coercion functions. Klein doesn't need this because:

- Constructors are already the explicit coercion: `Money(100)` converts `Num` to `Money`
- Adding a separate coercion mechanism would be redundant

## Consequences

**Positive:**
- Nominal types provide real safety guarantees — you can't accidentally pass the wrong type
- Structural access to nominal values is ergonomic (field access just works)
- The asymmetry is easy to explain: "you can read fields, but you must construct explicitly"

**Negative:**
- Users may be surprised that `{ value = 100 }` can't be used where `Money` is expected, especially if they come from structural-typing-first languages like TypeScript
- Converting between nominal types with the same structure requires explicit deconstruction and reconstruction
