# Positional Function Syntax

**Date:** 2026-01-09

## Context

Klein needed to decide how functions are defined and called. Two models were considered:

1. **Record-based:** Functions take a single record argument with named fields
   ```klein
   fun greet { name: String, age: Int }: String = ...
   greet { name = 'Alice', age = 30 }
   ```

2. **Positional:** Functions take conventional positional arguments
   ```klein
   fun greet(name: String, age: Int): String = ...
   greet('Alice', 30)
   ```

The record-based model was attractive because it unified named arguments, positional arguments, and record passing into one concept.

## Decision

**Use positional function syntax.** Functions are defined and called with conventional positional arguments.

```klein
# Definition
fun greet(name: String, age: Int): String = 'Hello, ${name}'
fun add(a: Int, b: Int): Int = a + b

# Types
greet : (String, Int) -> String
add : (Int, Int) -> Int

# Calling
greet('Alice', 30)
add(1, 2)
```

## Rationale

The record-based model breaks down with higher-order functions:

```klein
fun map(f: a -> b, xs: List(a)): List(b) = ...
  # internally calls f(elem)

fun greet { name: String }: String = ...

map(greet, people)   # people: List({ name: String, age: Int })
```

Inside `map`, we call `f(elem)`. Is this positional or structural? The type variable `a` would need to unify with both `String` (positional) and `{ name: String }` (structural). Standard Hindley-Milner can't handle this—a function type can't accept multiple input shapes.

We explored several solutions:
- **Two calling conventions** → requires two versions of `map`
- **Tuple-to-record subtyping** → promising but needs more research
- **MLsub/algebraic subtyping** → complex, uncertain if it solves this
- **Hybrid inference** → breaks principal types

All paths led to significant complexity or limitations.

## The Tilde Operator (~)

To bridge records to positional functions, use the `~` operator:

```klein
person = { name = 'Alice', age = 30 }

# greet~ lifts greet to accept a record
greet : (String, Int) -> String
greet~ : { name: String, age: Int } -> String

# Usage
greet~(person)
people.map(greet~)
```

The `~` operator is a function transformer (like `curry`/`uncurry`) that lifts a positional function into one accepting a record with matching field names.

## Consequences

**Positive:**
- Standard Hindley-Milner type inference works
- HOFs like `map` work naturally
- Familiar to programmers from most languages
- Simpler mental model

**Negative:**
- No unified calling convention
- Need explicit `~` when spreading records
- Named arguments require separate feature (optional sugar)

## Alternatives Considered

| Alternative | Why Not |
|-------------|---------|
| Record-based calling | Breaks HM inference with HOFs |
| Two calling conventions | Duplicates every HOF |
| Algebraic subtyping (MLsub) | Complex, uncertain if sufficient |
| Implicit coercion | Breaks principal types |
