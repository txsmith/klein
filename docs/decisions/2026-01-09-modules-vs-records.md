# Modules vs Records

**Date:** 2026-01-09

## Context

Klein has both modules and records. Their purposes overlap in some ways (both can contain functions), so clear guidelines were needed for when to use each.

## Decision

**Modules and records serve distinct purposes:**

| Aspect | Module | Record |
|--------|--------|--------|
| Contains types | Yes | No |
| Contains extension methods | Yes | No |
| Multiple instances | No (singleton) | Yes (values) |
| Structural typing | No | Yes |
| First-class value | No | Yes |
| Purpose | Organization, namespacing | Data, interfaces, polymorphism |

### Use modules for:
- Grouping related types and functions
- Smart constructors
- Namespacing to avoid collisions
- Extension methods

### Use records-of-functions for:
- Swappable implementations (policies, strategies)
- Dependency injection
- Runtime selection of behavior

## Rationale

The key distinction is **singleton vs multiple instances**:

- A module is a fixed namespace—there's one `Math` module, one `Lending` module
- A record value can have many instances—multiple `Policy` implementations, different `Config` objects

If you need to swap implementations at runtime or pass different versions to different callers, use a record. If you're just organizing code, use a module.

## Example

```klein
# Module: organization and smart constructors
module Money
  type Money = { amount: Number, currency: Currency }

  fun fromDollars(d: Number): Money =
    Money { amount = d, currency = USD }

# Record: swappable implementation
type PaymentProcessor = {
  charge: (Money, Card) -> Result(Receipt, Error)
}

stripeProcessor: PaymentProcessor = { charge = |amount, card -> ...| }
testProcessor: PaymentProcessor = { charge = |amount, card -> Ok { ... }| }
```

## Consequences

**Positive:**
- Clear mental model for when to use each
- Modules stay simple (just namespacing)
- Records handle all polymorphism needs

**Negative:**
- Can't have "multiple module instances" (use records instead)
- Must choose upfront whether something is a module or record type
