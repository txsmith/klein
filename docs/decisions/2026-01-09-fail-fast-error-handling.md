# Fail-Fast Error Handling

**Date:** 2026-01-09

## Context

Klein needed an error handling strategy. Options included:

1. **Exceptions** (Java-style) — implicit propagation, try/catch
2. **Result types everywhere** (Rust-style) — explicit at every call site
3. **Fail-fast with opt-in recovery** — errors propagate by default, explicit conversion to Result when needed

## Decision

**Fail-fast by default with opt-in recovery via `.recover`.**

- Most errors propagate automatically to the host system
- The host handles reporting, logging, and stack traces
- When you need to handle errors in Klein, wrap in a lambda and call `.recover`

```klein
# Raising errors
error 'invalid input'
error { code = 'NOT_FOUND', id = customerId }

# Recovering (opt-in)
|parseNumber(input)|.recover    # Result(Int, Thrown)
```

## Rationale

1. **Simple for the common case** — Most business rule code should let errors propagate. Adding `Result` handling everywhere adds noise.

2. **Host handles infrastructure concerns** — Stack traces, source locations, monitoring, and logging are runtime concerns best handled by the host application.

3. **Explicit when needed** — The `.recover` syntax makes it clear you're entering error-handling territory.

4. **Throw anything** — No need for a special exception hierarchy. Strings work for simple cases, records for structured errors.

## Design Principles

- **Fail fast by default** — errors propagate to the host automatically
- **Simple to learn** — one keyword (`error`), one method (`.recover`)
- **Throw anything** — strings, records, any value works
- **Host handles reporting** — stack traces, source locations, monitoring are runtime concerns

## Consequences

**Positive:**
- Clean code for the happy path
- No exception handling boilerplate
- Host gets full error context automatically
- Simple mental model

**Negative:**
- Can't catch errors without lambda wrapper
- All error handling is explicit (no implicit catches)
- Must convert to Result for any error inspection

## The Thrown Type

When an error is caught via `.recover`, it's wrapped in a `Thrown` record:

```klein
type Thrown = {
  value: Any,          # the value passed to error
  location: Location,  # source file and span
  stack: List(Frame)   # call stack at time of error
}
```

## Alternatives Considered

| Alternative | Why Not |
|-------------|---------|
| Java-style exceptions | Implicit control flow, checked exceptions are verbose |
| Rust-style Result everywhere | Too verbose for business rules, not the common case |
| Go-style error returns | Repetitive `if err != nil` checks |
| Effect-based errors | More complex, overkill for this use case |
