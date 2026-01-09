# Klein Error Handling

Klein takes a fail-fast approach to errors. Most errors propagate to the host system, which handles reporting, logging, and stack traces. For the rare cases where you need to handle errors in Klein code, a simple `recover` mechanism converts errors to `Result` values.

## Design Principles

- **Fail fast by default** — errors propagate to the host automatically
- **Simple to learn** — one keyword (`error`), one method (`.recover`)
- **Throw anything** — strings, records, any value works
- **Host handles reporting** — stack traces, source locations, monitoring are runtime concerns

## Raising Errors

Use `error` to raise an error with any value:

```klein
error 'invalid input'
error 'amount must be positive'
error { code = 'NOT_FOUND', id = customerId }
error { code = 404, entity = 'Customer', detail = 'No customer with that ID' }
```

The value can be a string for simple messages or a record for structured logging. The host captures the value along with source location and stack trace.

### Built-in Errors

These operations raise errors automatically:

| Operation | Error |
|-----------|-------|
| Division by zero | `{ kind = 'DivisionByZero' }` |
| Pattern match failure | `{ kind = 'MatchFailed', value = ... }` |
| Index out of bounds | `{ kind = 'IndexOutOfBounds', index = ..., length = ... }` |

## Recovering from Errors

Wrap a potentially failing computation in a lambda and call `.recover` to get a `Result` instead of propagating the error:

```klein
|parseNumber(input)|.recover    # Result(Int, Thrown)
```

### The `Thrown` Type

When an error is caught, it's wrapped in a `Thrown` record:

```klein
type Thrown = {
  value: Any,          # the value passed to error
  location: Location,  # source file and span
  stack: List(Frame)   # call stack at time of error
}
```

### The `Result` Type

```klein
type Result(t, e) = Ok { value: t } | Err { error: e }
```

## Working with Results

### Unwrap or Raise

```klein
fun unwrap(on r: Result(t, e)): t
```

Returns the success value, or raises the error:

```klein
customer = |fetch(id)|.recover.unwrap
```

### Unwrap with Default

```klein
fun orDefault(default: t, on r: Result(t, e)): t
```

Returns the success value, or the default on error:

```klein
count = |parseNumber(input)|.recover.orDefault(0)
```

### Unwrap with Fallback Computation

```klein
fun orElse(fallback: () -> t, on r: Result(t, e)): t
```

Returns the success value, or computes a fallback:

```klein
config = |loadConfig()|.recover.orElse(|defaultConfig()|)
```

### Transform Success

```klein
fun map(f: t -> u, on r: Result(t, e)): Result(u, e)
```

Transforms the success value, passes through errors:

```klein
name = |fetch(id)|.recover.map(|.name|)
```

### Chain Computations

```klein
fun flatMap(f: t -> Result(u, e), on r: Result(t, e)): Result(u, e)
```

Chains dependent operations that may fail:

```klein
result = |fetch(id)|.recover
  .flatMap(|customer -> |validate(customer)|.recover|)
  .flatMap(|valid -> |save(valid)|.recover|)
```

### Pattern Matching

Match directly on Result for full control:

```klein
match |parseNumber(input)|.recover
  Ok { value } -> value * 2
  Err { error } ->
    log(error.value)
    0
```

## Effect Errors

Effects that can fail should return `Result` types in their signatures:

```json
{
  "effects": {
    "fetch": {
      "params": [{ "name": "id", "type": "CustomerId" }],
      "returnType": "Result(Customer, FetchError)",
      "effectKind": "suspend"
    }
  }
}
```

Handle effect errors with normal `Result` methods:

```klein
customer = fetch(id).unwrap                    # raise on error
customer = fetch(id).orDefault(guestCustomer)  # use fallback
```

## Examples

### Fail Fast (Recommended)

Most Klein code should let errors propagate:

```klein
fun processOrder(orderId) =
  order = fetch(orderId).unwrap
  validated = validate(order)
  charge(validated)
```

If anything fails, the host receives full error context.

### Defensive with Defaults

When you need a fallback value:

```klein
fun safeParseAmount(input: String): Double =
  |parseNumber(input)|.recover.orDefault(0.0)
```

### Structured Error Handling

When you need to inspect and handle different errors:

```klein
fun loadUserProfile(userId): Profile =
  match |fetch(userId)|.recover
    Ok { user } -> buildProfile(user)
    Err { error } ->
      match error.value
        { code = 'NOT_FOUND', ... } -> guestProfile
        { code = 'TIMEOUT', ... } -> cachedProfile(userId)
        _ -> error error.value  # re-raise unknown errors
```

### Chaining Fallible Operations

When composing multiple operations that can fail:

```klein
fun processPayment(orderId): Result(Receipt, Thrown) =
  |fetch(orderId)|.recover
    .flatMap(|order -> |validatePayment(order)|.recover|)
    .flatMap(|validated -> |chargeCard(validated)|.recover|)
    .map(|charge -> createReceipt(charge)|)
```

## Summary

| Want | Syntax |
|------|--------|
| Raise an error | `error 'message'` or `error { ... }` |
| Convert to Result | `\|expr\|.recover` |
| Unwrap or raise | `.unwrap` |
| Unwrap or default | `.orDefault(value)` |
| Unwrap or compute | `.orElse(\|fallback\|)` |
| Transform success | `.map(\|...\|)` |
| Chain Results | `.flatMap(\|...\|)` |
| Pattern match | `match result` with `Ok`/`Err` |

## Type Signature of `recover`

```klein
fun recover(on thunk: () -> a): Result(a, Thrown)
```

An extension method on zero-argument lambdas that executes the thunk and captures any error as a `Result`.
