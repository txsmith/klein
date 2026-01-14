# Single Numeric Type (Num)

**Status**: Accepted
**Date**: 2026-01-14

## Context

Most languages distinguish between integer and floating-point types (`Int`, `Double`, `Float`, etc.). This distinction requires decisions about:

- Numeric operator return types (`1 + 2` vs `1.0 + 2.0` vs `1 + 2.0`)
- Implicit coercion rules (or lack thereof)
- Literal inference (`42` vs `42.0`)

For type inference, this creates complexity. Consider `|x -> x + 1|`—should this be `(Int) -> Int`, `(Double) -> Double`, or polymorphic over numeric types?

## Decision

Klein has a single numeric type: `Num`.

```klein
42        : Num
3.14      : Num
x + y     : Num  (when x, y : Num)
```

All numeric literals and operations produce `Num`. There is no `Int` or `Double` at the type level.

## Rationale

**Simpler type inference.** Numeric operators have straightforward signatures:

```
(+) : (Num, Num) -> Num
(-) : (Num, Num) -> Num
(*) : (Num, Num) -> Num
(/) : (Num, Num) -> Num
```

No overloading, no type classes, no special inference rules for numeric literals.

**Appropriate for the domain.** Klein is a business rules DSL. Users write expressions like:

```klein
loan.amount * (1 + interestRate) ^ years
```

The distinction between integer and floating-point arithmetic is an implementation detail, not a domain concept.

**Defers runtime representation.** The host application controls how `Num` values are represented at runtime (64-bit float, arbitrary precision decimal, BigDecimal, etc.). This is a deployment decision, not a language design decision.

## Trade-offs

**No integer-specific operations.** Bitwise operations (`&`, `|`, `<<`) don't have an obvious home. If needed, these could be added as functions that operate on `Num` with runtime checks.

**No compile-time integer guarantees.** You can't express "this function only accepts whole numbers" in the type system. Domain validation happens at runtime or via nominal wrapper types.

**Runtime representation TBD.** We're deferring the question of whether `Num` is a float, a decimal, or something else. This is intentional—it lets us make pragmatic choices based on host platform requirements.

## Future Considerations

If integer vs float distinction becomes necessary, options include:

1. **Nominal wrappers**: `type Int = Num` with runtime validation
2. **Refinement types**: `Num where isInteger`
3. **Splitting the type**: Introduce `Int` and `Float` as separate primitives

For now, simplicity wins.
