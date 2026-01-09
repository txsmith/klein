# Klein Syntax

A complete reference for Klein's syntax, covering expressions, functions, control flow, types, and literals.

## Basic Syntax Choices

| Feature | Syntax | Notes |
|---------|--------|-------|
| Blocks | indentation | Significant whitespace |
| Lambdas | `\| \|` | Pipes |
| Variable binding | `x = 3` | No `let`/`val` keyword |
| Record access | `.field` | Dot notation |
| Type annotation | `x: T = ...` | Colon before type |
| Equality test | `==` | Double equals |
| Boolean operators | `and`, `or`, `not` | Keywords, not symbols |

## Functions

### Named Functions

Use `fun` keyword with `=` for definition:

```klein
fun double(x) = x * 2

fun calculate(a, b) =
  temp = a * 2
  temp + b
```

With type annotations:

```klein
fun double(x: int): int = x * 2

fun calculate(a: int, b: int): int =
  temp = a * 2
  temp + b
```

### Anonymous Lambdas (Pipes)

Pipes delimit lambdas. Arrow for explicit params:

```klein
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

```klein
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

```klein
filter(items, |.orders.any(|.price > 100|)|)
//             ^-- outer dot   ^-- inner dot

// outer dot = Item
// inner dot = Order
```

When you need to reference both scopes, use explicit params:

```klein
filter(items, |item -> item.orders.any(|.price > item.budget|)|)
```

## Blocks vs Lambdas

### The Rule

- **Pipes `|...|`** → always a lambda (works anywhere)
- **Indentation** → blocks (immediate evaluation)

### Examples

```klein
// Lambdas (pipes) — work anywhere
filter(items, |.price > 100|)        // (Item) -> bool
runLater(|1 + 2|)                    // () -> int
map(nums, |. * 2|)                   // (int) -> int

// Lambdas can be bound to variables
predicate = |.price > 100|           // (Item) -> bool
thunk = |1 + 2|                      // () -> int
constant = |42|                      // () -> int
identity = |.|                       // (a) -> a

// Blocks via indentation — immediate evaluation
x =
  a = 1
  b = 2
  a + b
// x: int = 3

// Named functions (alternative to binding lambdas)
fun predicate(item) = item.price > 100
fun thunk() = 1 + 2
fun addOne(x) = x + 1
```

### Constant Lambdas

A lambda with no dots or params is a constant function:

```klein
map(items, |42|)           // always returns 42
filter(items, |true|)      // matches everything
```

## Point-Free Style

### Functions as Values

Any named function can be passed directly:

```klein
fun add(a: int, b: int): int = a + b

fold(nums, 0, add)           // pass function directly
map(items, calculateTotal)   // no lambda needed
```

### Operators as Values

Infix operators in value position become functions:

```klein
fold(nums, 0, +)             // (int, int) -> int
fold(nums, 1, *)             // (int, int) -> int
reduce(strings, ++)          // (string, string) -> string
reduce(bools, and)           // (bool, bool) -> bool
```

Note: `-` in value position is binary subtraction. For unary negation, use a lambda `|-.|` or a `negate` function.

## No Currying

Functions are not auto-curried. Partial application requires explicit dot syntax:

```klein
// Error: add expects 2 arguments
add(1)

// Correct: use lambda with dot
map(nums, |add(1, .)|)

// Point-free only when all args omitted
fold(nums, 0, add)           // ✓ add is (int, int) -> int
```

## Function Types

Function types use arrow syntax:

```klein
(int) -> int                  // single param
(int, int) -> int             // multiple params
() -> int                     // no params (thunk)
(Item) -> bool                // with custom types
((int) -> int) -> int         // higher-order
```

## If/Then/Else

Uses `then` keyword to clearly separate condition from branches:

```klein
// Simple expression
max = if a > b then a else b

// Multi-line with indentation
result =
  if score >= 90 then
    grade = "A"
    sendCongrats(student)
    grade
  else if score >= 80 then
    "B"
  else
    "C"

// Nested
status =
  if approved then
    if amount > 1000 then "LargeApproved" else "SmallApproved"
  else
    "Rejected"
```

The `then` keyword makes it unambiguous where the condition ends, avoiding C-style parsing issues and reading naturally as English.

## Pattern Matching

`match` serves two purposes: matching on values (like ML/Rust) and matching on conditions (like Kotlin's `when`).

### Value Matching

```klein
match status
  Approved -> processLoan(application)
  Rejected(reason) -> notifyRejection(reason)
  Pending -> waitForReview()
```

### Condition Matching

When no subject is provided, branches are boolean expressions:

```klein
match
  score >= 90 -> "A"
  score >= 80 -> "B"
  score >= 70 -> "C"
  else -> "F"
```

This reads like a decision table—exactly how non-engineers think about business rules:

```klein
riskCategory = match
  customer.creditScore < 500 -> "High"
  customer.yearsWithUs < 1 -> "Medium"
  customer.outstandingLoans > 3 -> "Medium"
  else -> "Low"
```

### Guards

Combine value matching with additional conditions:

```klein
match amount
  x if x > 10000 -> requiresDirectorApproval(x)
  x if x > 1000 -> requiresManagerApproval(x)
  x if x > 0 -> autoApprove(x)
  else -> reject("Invalid amount")
```

### Destructuring

```klein
// Records
match person
  { name, age } if age >= 18 -> allowEntry(name)
  { name, _ } -> denyEntry(name)

// Lists
match items
  [] -> "empty"
  [only] -> "just one: " ++ only
  [first, ...rest] -> "first is " ++ first
```

### Match Syntax Details

| Aspect | Choice | Rationale |
|--------|--------|-----------|
| Arrow | `->` | Consistent with function types and lambdas |
| Default case | `else` | Friendlier for non-engineers than `_` |
| Separators | newline | One arm per line |
| Exhaustiveness | Required for enums, `else` required for conditions | Safety |

```klein
// Multi-line (standard)
match status
  Pending -> wait()
  Approved -> go()
  else -> stop()

// Single-line arms with multi-line bodies
match status
  Pending ->
    log("waiting")
    wait()
  Approved ->
    log("going")
    go()
  else ->
    stop()
```

## Type Definitions

### Records

```klein
type Customer =
  id: int
  name: string
  creditLimit: double
  region: Region
  spouse: Customer?

// With Kleene cardinalities
type Order =
  items: LineItem+        // one or more (required)
  discounts: Discount*    // zero or more (optional list)
  primaryContact: string
  backupContacts: string*
```

### Enums / Sum Types

```klein
type Region = Nakuru | Kisumu | Nairobi

type PaymentStatus
  | Pending
  | Approved(approver: string, date: Date)
  | Rejected(reason: string)
  | Cancelled

// Usage
status = Approved("Jane", today)

match status
  Approved(who, when) -> "Approved by " ++ who
  Rejected(why) -> "Rejected: " ++ why
  else -> "Other"
```

### Type Aliases

```klein
type Money = double
type CustomerId = int
type Predicate<T> = (T) -> bool
```

## Iteration

Klein favors comprehensions over imperative loops, keeping the language expression-oriented.

### For Comprehensions

```klein
// Map
squares = for x in numbers yield x * x

// Filter + map
expensive =
  for item in items
  if item.price > 100
  yield item.name

// Nested (cartesian product)
pairs =
  for x in xs
  for y in ys
  yield (x, y)

// With destructuring
totals =
  for { price, qty } in lineItems
  yield price * qty
```

### Looping Constructs

TBD — need a construct for repeating effects. To be designed later.

## Literals

### Lists

```klein
nums = [1, 2, 3, 4, 5]
empty = []
single = [42]
```

### Records

```klein
person = { name: "Alice", age: 30 }

// Shorthand when variable name matches field
name = "Bob"
age = 25
person = { name, age }  // same as { name: name, age: age }

// Spread/update
updated = { ...person, age: 26 }
```

### Tuples

```klein
pair = (1, "hello")
(x, y) = pair  // destructuring

// In function returns
fun divmod(a: int, b: int): (int, int) = (a / b, a % b)
```

### Ranges

```klein
1..10      // 1 to 10 inclusive
1..<10     // 1 to 9 (exclusive end)
```

## Strings

Strings use single quotes and support interpolation with `${}`. Multiline strings use the same syntax—just include newlines:

```klein
message = 'Hello, ${customer.name}!'
summary = 'Total: ${formatMoney(subtotal + tax)}'

template = 'Dear ${name},

Your order of ${itemCount} items is confirmed.
Total: ${total}
'
```

## Comments

```klein
# Single line comment

# Multi-line comments just use
# multiple hash marks

# Doc comment for the following definition
# @param items The line items to sum
# @returns The total as Money
fun calculateTotal(items: LineItem+): Money = ...
```

## Complete Example

```klein
type Customer =
  id: int
  name: string
  creditScore: int
  segment: Segment
  verified: bool

type Segment = Premium | Standard | New

type Application =
  customer: Customer
  amount: double
  purpose: string

type Decision
  | AutoApproved
  | NeedsReview(reviewer: string)
  | Rejected(reason: string)


fun assessApplication(app: Application): Decision =
  customer = app.customer
  amount = app.amount

  # Early rejection using condition match
  match
    not customer.verified -> Rejected('Customer not verified')
    amount <= 0 -> Rejected('Invalid amount')
    customer.creditScore < 300 -> Rejected('Credit score too low')
    else -> continueAssessment(app)

fun continueAssessment(app: Application): Decision =
  riskScore = calculateRisk(app)

  match
    riskScore < 20 and app.amount < 5000 -> AutoApproved
    riskScore < 50 -> NeedsReview(assignReviewer(app))
    else -> Rejected('Risk too high: ${riskScore}')

fun calculateRisk(app: Application): int =
  base = match app.customer.segment
    Premium -> 0
    Standard -> 10
    New -> 25

  amountRisk = match
    app.amount > 50000 -> 30
    app.amount > 10000 -> 15
    app.amount > 5000 -> 5
    else -> 0

  base + amountRisk

fun assignReviewer(app: Application): string =
  if app.amount > 20000 then 'director@company.com'
  else 'manager@company.com'
```

## Summary Table

| Want | Syntax |
|------|--------|
| Named function | `fun name(params) = body` |
| Multi-line function | `fun name(params) =` + indented block |
| Lambda with explicit params | `\|x -> expr\|` or `\|x, y -> expr\|` |
| Lambda with implicit param | `\|.field\|` or `\|. > 100\|` |
| Zero-param lambda | `\|expr\|` |
| Constant lambda | `\|42\|` |
| Identity lambda | `\|.\|` |
| Pass function directly | `fold(nums, 0, add)` |
| Pass operator directly | `fold(nums, 0, +)` |
| Partial application | `\|add(1, .)\|` |
| Block (immediate eval) | indented lines after `=` |
| Bind a lambda | `x = \|.price > 100\|` |
| If/else | `if cond then a else b` |
| Multi-line if | `if cond then` + indented block |
| Value match | `match x` + indented arms |
| Condition match | `match` + indented condition arms |
| Guard | `pattern if cond -> expr` |
| Record type | `type R =` + indented fields |
| Enum type | `type E = A \| B` or `type E` + indented variants |
| Type alias | `type Name = T` |
| For comprehension | `for x in xs yield expr` |
| For with filter | `for x in xs if cond yield expr` |
| List literal | `[1, 2, 3]` |
| Record literal | `{ a: 1, b: 2 }` |
| Record update | `{ ...r, field: newVal }` |
| Tuple | `(a, b)` |
| Range | `1..10` or `1..<10` |
| String interpolation | `'Hello ${name}'` |
| Comments | `#` |
