# Klein Function Syntax Summary

## Basic Syntax Choices

| Feature | Syntax | Notes |
|---------|--------|-------|
| Blocks | `{ }` | Braces, not indentation |
| Lambdas | `\| \|` | Pipes |
| Variable binding | `x = 3` | Simple assignment |
| Record access | `.field` | Dot notation |
| Type annotation | `x: T = ...` | Colon before type |
| Equality test | `==` | Double equals |
| Boolean operators | `and`, `or`, `not` | Keywords, not symbols |

## Functions

### Named Functions

Use `fun` keyword with `=` for definition:

```
fun double(x) = x * 2

fun calculate(a, b) = {
  temp = a * 2
  temp + b
}
```

With type annotations:

```
fun double(x: int): int = x * 2

fun calculate(a: int, b: int): int = {
  temp = a * 2
  temp + b
}
```

### Anonymous Lambdas (Pipes)

Pipes delimit lambdas. Arrow for explicit params:

```
// Explicit parameters
filter(items, |x -> x.price > 100|)
fold(nums, 0, |acc, x -> acc + x|)

// Multi-statement
filter(items, |
  p = .price
  t = .tax
  p > t
|)
```

### Dot Shorthand for Implicit Parameter

Inside pipes, `.` refers to the implicit lambda parameter:

```
// Field access
filter(items, |.price > 100|)
map(orders, |.total * tax_rate|)
sort(users, |.age|)

// Multiple dots = same parameter
filter(items, |.price > .cost|)

// Bare dot = the parameter itself
filter(nums, |. > 100|)
map(nums, |. * 2|)
map(nums, |-.|)              // unary minus
map(bools, |not .|)          // boolean not

// Partial application (dot as argument)
map(nums, |add(1, .)|)
map(items, |calculate(.price, 10)|)
```

### Nested Lambdas

Each pipe pair creates a new dot scope:

```
filter(items, |.orders.any(|.price > 100|)|)
//             ^-- outer dot   ^-- inner dot

// outer dot = Item
// inner dot = Order
```

When you need to reference both scopes, use explicit params:

```
filter(items, |item -> item.orders.any(|.price > item.budget|)|)
```

## Blocks vs Lambdas

### The Rule

- **Pipes `|...|`** → always a lambda (works anywhere)
- **Braces `{...}`** → always a block (immediate evaluation)

### Examples

```
// Lambdas (pipes) — work anywhere
filter(items, |.price > 100|)        // (Item) -> bool
runLater(|1 + 2|)                     // () -> int
map(nums, |. * 2|)                    // (int) -> int

// Lambdas can be bound to variables
predicate = |.price > 100|        // (Item) -> bool
thunk = |1 + 2|                   // () -> int
constant = |42|                   // () -> int
identity = |.|                    // (a) -> a

// Blocks (braces) — immediate evaluation
x = {
  a = 1
  b = 2
  a + b
}
// x: int = 3

// Named functions (alternative to binding lambdas)
fun predicate(item) = item.price > 100
fun thunk() = 1 + 2
fun addOne(x) = x + 1
```

### Constant Lambdas

A lambda with no dots or params is a constant function:

```
map(items, |42|)           // always returns 42
filter(items, |true|)      // matches everything
```

## Point-Free Style

### Functions as Values

Any named function can be passed directly:

```
fun add(a: int, b: int): int = a + b

fold(nums, 0, add)           // pass function directly
map(items, calculateTotal)   // no lambda needed
```

### Operators as Values

Infix operators in value position become functions:

```
fold(nums, 0, +)             // (int, int) -> int
fold(nums, 1, *)             // (int, int) -> int
reduce(strings, ++)          // (string, string) -> string
reduce(bools, and)           // (bool, bool) -> bool
```

Note: `-` in value position is binary subtraction. For unary negation, use `{ -. }` or a `negate` function.

## No Currying

Functions are not auto-curried. Partial application requires explicit dot syntax:

```
// Error: add expects 2 arguments
add(1)

// Correct: use lambda with dot
map(nums, { add(1, .) })

// Point-free only when all args omitted
fold(nums, 0, add)           // ✓ add is (int, int) -> int
```

## Function Types

Function types use arrow syntax:

```
(int) -> int                  // single param
(int, int) -> int             // multiple params
() -> int                     // no params (thunk)
(Item) -> bool                // with custom types
((int) -> int) -> int         // higher-order
```

## Summary Table

| Want | Syntax |
|------|--------|
| Named function | `fun name(params) = body` |
| Lambda with explicit params | `\|x -> expr\|` or `\|x, y -> expr\|` |
| Lambda with implicit param | `\|.field\|` or `\|. > 100\|` |
| Zero-param lambda | `\|expr\|` |
| Constant lambda | `\|42\|` |
| Identity lambda | `\|.\|` |
| Pass function directly | `fold(nums, 0, add)` |
| Pass operator directly | `fold(nums, 0, +)` |
| Partial application | `\|add(1, .)\|` |
| Block (immediate eval) | `x = { stmts; expr }` |
| Bind a lambda | `x = \|.price > 100\|` |
