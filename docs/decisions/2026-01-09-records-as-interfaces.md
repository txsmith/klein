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
  fun evaluate(customer: Customer): Decision
  fun maxAmount(customer: Customer): Money
}

standardPolicy: Policy = {
  fun evaluate(customer) =
    if customer.creditScore > 700 then Approved
    else Rejected { reason = 'Credit score too low' }

  fun maxAmount(customer) = customer.income * 3
}

aggressivePolicy: Policy = {
  fun evaluate(customer) = Approved
  fun maxAmount(customer) = customer.income * 5
}

# Runtime selection
policy = if customer.segment == Premium then aggressivePolicy else standardPolicy
policy.evaluate(customer)
```

### Syntax

The `fun` keyword inside records is sugar for arrow types and lambda values:

```klein
# Type definition with fun (preferred for interfaces)
type Policy = {
  fun evaluate(customer: Customer): Decision
}

# Equivalent arrow syntax
type Policy = {
  evaluate: Customer -> Decision
}

# Value with fun (preferred for implementations)
myPolicy: Policy = {
  fun evaluate(customer) = ...
}

# Equivalent lambda syntax
myPolicy: Policy = {
  evaluate = |customer -> ...|
}
```

The `fun` syntax is preferred for interface-like records because it reads more naturally and mirrors top-level function definitions.

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
  fun empty(): a
  fun combine(x: a, y: a): a
}

intAddMonoid: Monoid(Int) = {
  fun empty() = 0
  fun combine(x, y) = x + y
}

fun concat(monoid: Monoid(a), on xs: List(a)): a =
  xs.fold(monoid.empty(), |acc, x -> monoid.combine(acc, x)|)

[1, 2, 3].concat(intAddMonoid)  # 6
```

This keeps resolution explicit while providing the abstraction power of type classes.
