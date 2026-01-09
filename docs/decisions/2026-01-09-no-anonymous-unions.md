# No Anonymous Unions

**Date:** 2026-01-09

## Context

Some languages support anonymous union types:

```typescript
// TypeScript
function foo(x: string | number): void { ... }
```

The question was whether Klein should support similar inline unions.

## Decision

**No anonymous unions.** Use explicit sum types with constructors.

```klein
# Not supported:
fun foo(x: Int | String): ...

# Instead, define a sum type:
type IntOrString = IntVal { value: Int } | StringVal { value: String }

fun foo(x: IntOrString): ...
```

## Rationale

1. **Simpler syntax** — No ambiguity between sum type definitions and inline unions

2. **Consistent mental model** — Every `|` defines constructors. No special case for "union without constructors"

3. **Pattern matching clarity** — With constructors, pattern matching is explicit:
   ```klein
   match x
     IntVal { value } -> ...
     StringVal { value } -> ...
   ```

4. **Avoids subtyping complexity** — Anonymous unions require deciding if `Int` is a subtype of `Int | String`, which complicates type inference

## Consequences

**Positive:**
- Simpler parser and type system
- Consistent syntax throughout
- Clear pattern matching

**Negative:**
- More verbose for simple cases
- Must define wrapper types

## Alternatives Considered

| Alternative | Why Not |
|-------------|---------|
| TypeScript-style unions | Complicates type inference, ambiguous syntax |
| Flow-style unions | Same issues |
| Only allow in type aliases | Inconsistent—why allow in one place but not another? |
