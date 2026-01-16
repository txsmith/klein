# Safe Navigation Operator

**Date:** 2026-01-16

## Context

Klein has optional types (`T?`) and `null`. When working with optional records, users need a way to safely access fields and call methods without manual null checks.

## Decision

Klein supports the `?.` operator for safe field access and method calls on optional values.

### Field Access

```klein
user?.name        # String? (if user is { name: String }?)
user?.address?.city  # String? (chained access)
```

### Method Calls

```klein
record?.method(args)  # ReturnType? (not a type error)
order?.purchasedAt?.isAfter(now())  # Bool?
```

The key insight: `x?.method(args)` is treated as a single operation, not as calling an optional function. This matches Kotlin's semantics.

### Chaining Behavior

Chained `?.` operations collapse to a single optional:

```klein
a?.b?.c?.method()  # Returns T?, not T????
```

This is correct — any null in the chain produces null, so multiple optional layers are semantically equivalent to one.

### Implicit Parameters

The combination of implicit param (`.`) and safe navigation (`?.`) is **not supported**:

```klein
|.?field|   # Parse error — use explicit form instead
|x -> x?.field|  # OK
```

The `.?` sequence is visually confusing and the explicit lambda form isn't much longer. This can be revisited if there's demand.

## Consequences

### Positive

- Familiar syntax for Kotlin/TypeScript/Swift developers
- Enables fluent null-safe chains
- No need for explicit `.map()` or pattern matching for simple access

### Negative

- `?.` on non-optional values is allowed (since `T <: T?`) — slightly redundant but harmless
- Nested optional fields still produce `T??` (optional flattening not yet implemented)
