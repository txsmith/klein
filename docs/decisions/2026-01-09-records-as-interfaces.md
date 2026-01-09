# Records as Interfaces

**Date:** 2026-01-09

## Context

Klein needed a mechanism for polymorphism—allowing different implementations of the same interface. Options included:

1. **Type classes** (Haskell-style) with implicit resolution
2. **Traits/interfaces** (Rust/Java-style) with nominal typing
3. **Records with function fields** as structural interfaces

## Decision

**Records with function fields serve as interfaces.** A record type describes a shape; any record value matching that shape is an implementation.

```klein
type Policy = {
  evaluate: Customer -> Decision,
  maxAmount: Customer -> Money
}

standardPolicy: Policy = {
  evaluate = |customer ->
    if customer.creditScore > 700 then Approved
    else Rejected { reason = 'Credit score too low' }|,
  maxAmount = |customer -> customer.income * 3|
}

aggressivePolicy: Policy = {
  evaluate = |customer -> Approved|,
  maxAmount = |customer -> customer.income * 5|
}

# Runtime selection
policy = if customer.segment == Premium then aggressivePolicy else standardPolicy
policy.evaluate(customer)
```

## Rationale

This approach provides:

- **Swappable implementations** — policies, strategies, handlers
- **No implicit resolution complexity** — everything is explicit
- **First-class values** — pass interfaces around like any other value
- **Multiple implementations** — create as many as needed
- **Structural typing** — no need to declare "implements"

Type classes add implicit resolution machinery that's powerful but complex. For Klein's target audience (business rule authors), explicit passing is clearer.

## Consequences

**Positive:**
- Simple mental model: records are just data
- No "where does this instance come from?" confusion
- Easy to test with mock implementations
- Works with structural typing and row polymorphism

**Negative:**
- Must pass implementations explicitly
- No automatic instance derivation
- Verbose when many "instances" needed

## Future Consideration

Records with type parameters can serve as explicit "type classes":

```klein
type Monoid(a) = {
  empty: () -> a,
  combine: (a, a) -> a
}

intAddMonoid: Monoid(Int) = {
  empty = |0|,
  combine = |x, y -> x + y|
}

fun concat(monoid: Monoid(a), on xs: List(a)): a =
  xs.fold(monoid.empty(), |acc, x -> monoid.combine(acc, x)|)

[1, 2, 3].concat(intAddMonoid)  # 6
```

This keeps resolution explicit while providing the abstraction power of type classes.
