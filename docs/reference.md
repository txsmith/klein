# Klein Reference

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
| Record field assignment | `{ x = 1 }` | Equals in literals |
| Record field types | `{ x: Num }` | Colon in type annotations |

## Functions

Functions use positional parameters in parentheses.

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
fun double(x: Num): Num = x * 2

fun calculate(a: Num, b: Num): Num =
  temp = a * 2
  temp + b
```

### Function Application

Functions are called with positional arguments in parentheses:

```klein
double(5)
calculate(10, 20)
greet("Alice", 30)
```

### Anonymous Lambdas (Pipes)

Pipes delimit lambdas. Arrow for explicit params:

```klein
# Explicit parameters
numbers.filter(|x -> x > 100|)
numbers.fold(0, |acc, x -> acc + x|)

# Multi-statement
items.filter(|
  p = .price
  t = .tax
  p > t
|)
```

### Dot Shorthand for Implicit Parameter

Inside pipes, `.` refers to the implicit lambda parameter:

```klein
# Field access
items.filter(|.price > 100|)
orders.map(|.total * taxRate|)
users.sortBy(|.age|)

# Multiple dots = same parameter
items.filter(|.price > .cost|)

# Bare dot = the parameter itself
nums.filter(|. > 100|)
nums.map(|. * 2|)
nums.map(|-.|)              # unary minus
bools.map(|not .|)          # boolean not
```

### Nested Lambdas

Each pipe pair creates a new dot scope:

```klein
items.filter(|.orders.any(|.price > 100|)|)
#              ^-- outer dot   ^-- inner dot

# outer dot = Item
# inner dot = Order
```

When you need to reference both scopes, use explicit params:

```klein
items.filter(|item -> item.orders.any(|.price > item.budget|)|)
```

### Extension Methods

Use the `on` keyword to mark which parameter becomes the method receiver:

```klein
fun map(f: 'A -> 'B, on xs: List<'A>): List<'B> = ...
fun filter(p: 'A -> Bool, on xs: List<'A>): List<'A> = ...
fun isAdult(on c: Customer): Bool = c.age >= 18
```

Both calling styles work:

```klein
# Function call style
map(double, numbers)
isAdult(customer)

# Method syntax
numbers.map(double)
customer.isAdult
```

Method syntax enables clean chaining:

```klein
[1, 2, 3].map(|. * 2|).filter(|. > 2|)
```

Rules:
- Only **one** `on` parameter per function
- Any parameter position works
- Must be imported to use method syntax

## The Tilde Operator (~)

The tilde operator transforms a positional function to accept a record with matching field names:

```klein
fun process(name: String, age: Num): Decision = ...

# process takes positional args
process : (String, Num) -> Decision

# process~ takes a record with matching field names
process~ : { name: String, age: Num } -> Decision
```

*Status: Not yet implemented*

Use tilde when you have a record and want to spread it into a positional function:

```klein
person = { name = "Alice", age = 30 }

# These are equivalent:
process(person.name, person.age)
process~(person)

# Works with HOFs
people.map(process~)
people.filter(isValid~)
```

This keeps function types simple (standard HM-compatible) while providing an escape hatch for record-to-positional bridging.

## Blocks vs Lambdas

### The Rule

- **Pipes `|...|`** → always a lambda (works anywhere)
- **Indentation** → blocks (immediate evaluation)

### Examples

```klein
# Lambdas (pipes) — work anywhere
items.filter(|.price > 100|)            # Item -> Bool
runLater(|1 + 2|)                       # () -> Int
nums.map(|. * 2|)                       # Int -> Int

# Lambdas can be bound to variables
predicate = |.price > 100|              # Item -> Bool
thunk = |1 + 2|                         # () -> Int
constant = |42|                         # () -> Int
identity = |.|                          # a -> a

# Blocks via indentation — immediate evaluation
x =
  a = 1
  b = 2
  a + b
# x: Int = 3

# Named functions (alternative to binding lambdas)
fun predicate(item) = item.price > 100
fun thunk() = 1 + 2
fun addOne(x) = x + 1
```

### Constant Lambdas

A lambda with no dots or params is a constant function:

```klein
items.map(|42|)            # always returns 42
items.filter(|true|)       # matches everything
```

## Point-Free Style

### Functions as Values

Any named function can be passed directly:

```klein
fun add(a: Num, b: Num): Num = a + b

nums.fold(0, add)                  # pass function directly
items.map(calculateTotal)          # no lambda needed
```

### Operators as Values

Infix operators in value position become functions:

```klein
nums.fold(0, (+))                  # (Num, Num) -> Num
nums.fold(1, (*))                  # (Num, Num) -> Num
strings.reduce((++))               # (String, String) -> String
bools.reduce(and)                  # (Bool, Bool) -> Bool
```

Note: `-` in value position is binary subtraction. For unary negation, use a lambda `|-.|` or a `negate` function.

## Function Types

Function types use arrow syntax with positional parameters:

```klein
Num -> Num                        # single parameter
(Num, Num) -> Num                 # multiple parameters
() -> Num                         # no parameters (thunk)
Item -> Bool                      # with custom types
(Num -> Num) -> Num               # higher-order
```

## If/Then/Else

Uses `then` keyword to clearly separate condition from branches:

```klein
# Simple expression
max = if a > b then a else b

# Multi-line with indentation
result =
  if score >= 90 then
    grade = "A"
    sendCongrats(student)
    grade
  else if score >= 80 then
    "B"
  else
    "C"

# Nested
status =
  if approved then
    if amount > 1000 then "LargeApproved" else "SmallApproved"
  else
    "Rejected"
```

The `then` keyword makes it unambiguous where the condition ends, avoiding C-style parsing issues and reading naturally as English.

## Pattern Matching

> **Status:** planned, not yet implemented (see [roadmap](./roadmap.md) Phase 2).

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
# Records
match person
  { name, age } if age >= 18 -> allowEntry(name)
  { name, _ } -> denyEntry(name)

# Lists
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
# Multi-line (standard)
match status
  Pending -> wait()
  Approved -> go()
  else -> stop()

# Single-line arms with multi-line bodies
match status
  Pending ->
    log('waiting')
    wait()
  Approved ->
    log('going')
    go()
  else ->
    stop()
```

## Type Definitions

Type definitions create nominal types with constructors. Constructors are first-class functions.

### Single-Constructor Types

```klein
type Money = Money { value: Num }
type CustomerId = CustomerId { value: Num }
type Customer = Customer { id: Num, name: String, creditLimit: Num, region: Region }
```

Constructors are called positionally:

```klein
price = Money(99.95)
customer = Customer(1, "Alice", 5000, Nairobi)
```

### Sum Types

```klein
type Region = Nakuru | Kisumu | Nairobi

type PaymentStatus =
  Pending
  | Approved { approver: String, date: Date }
  | Rejected { reason: String }
  | Cancelled

# Usage
status = Approved("Jane", today)

match status
  Approved(approver, date) -> "Approved by " ++ approver
  Rejected(reason) -> "Rejected: " ++ reason
  else -> "Other"
```

### Type Parameters

```klein
type Option<'A> = Some { value: 'A } | None
type Result<'T, 'E> = Ok { value: 'T } | Err { error: 'E }
type Predicate<'A> = Predicate { test: 'A -> Bool }
```

### Constructors as First-Class Functions

```klein
nums.map(Some)           # List<Num> -> List<Option<Num>>
nums.map(Money)          # List<Num> -> List<Money>
```

### Structural Record Types (in annotations)

Structural records still exist for type annotations and interfaces:

```klein
type Policy = {
  evaluate: Customer -> Decision,
  maxAmount: Customer -> Money
}

standardPolicy: Policy = {
  evaluate = |customer ->
    if customer.creditScore > 700 then Approved
    else Rejected("Credit score too low")|,
  maxAmount = |customer -> customer.income * 3|
}
```

## Modules

Modules are named containers for organizing types, functions, and values.

### Definition

```klein
module Lending
  type Loan = Loan { principal: Money, rate: Num }

  fun value(loan: Loan): Money = loan.principal

  fun totalValue(on xs: List<Loan>): Money =
    xs.fold(Money(0), |acc, l -> acc + l.principal|)
```

Indentation defines the module boundary.

### Imports

```klein
# Import all members
import Lending._

# Import specific items
import Lending.{ Loan, value }

# Qualified access (no import needed)
Lending.Loan
Lending.value(loan)
```

### Smart Constructors

```klein
module Money
  type Money = Money { amount: Num, currency: Currency }

  fun fromDollars(d: Num): Money =
    Money(d, USD)

  fun fromCents(c: Num): Money =
    Money(c / 100, USD)

  fun zero(currency: Currency): Money =
    Money(0, currency)

import Money._

price = fromDollars(10.50)
nothing = zero(USD)
```

## Iteration

Klein favors comprehensions over imperative loops, keeping the language expression-oriented.

### For Comprehensions

```klein
# Map
squares = for x in numbers yield x * x

# Filter + map
expensive =
  for item in items
  if item.price > 100
  yield item.name

# Nested (cartesian product)
pairs =
  for x in xs
  for y in ys
  yield (x, y)

# With destructuring
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
person = { name = "Alice", age = 30 }

# Shorthand when variable name matches field
name = "Bob"
age = 25
person = { name, age }  # same as { name = name, age = age }

# Spread/update
updated = { ...person, age = 26 }
```

### Tuples

Tuples use parentheses and have positional field names `_1`, `_2`, etc:

```klein
pair = ("Alice", 30)
triple = (1, 2, 3)

pair._1                   # "Alice"
pair._2                   # 30

# Destructuring
(x, y) = pair

# In function returns
fun divmod(a: Num, b: Num): (Num, Num) = (a / b, a % b)
```

Tuples are distinct from records. Use records when field names are meaningful, tuples for quick positional grouping.

### Ranges

```klein
1..10      # 1 to 10 inclusive
1..<10     # 1 to 9 (exclusive end)
```

## Strings

Strings use double quotes and support interpolation with `${}`. Multiline strings use the same syntax—just include newlines:

```klein
message = "Hello, ${customer.name}!"
summary = "Total: ${formatMoney(subtotal + tax)}"

template = "Dear ${name},

Your order of ${itemCount} items is confirmed.
Total: ${total}
"
```

## Comments

```klein
# Single line comment

# Multi-line comments just use
# multiple hash marks

# Doc comment for the following definition
# @param items The line items to sum
# @returns The total as Money
fun calculateTotal(items: List<LineItem>): Money = ...
```

## Error Handling

*Status: Not yet implemented*

Klein uses fail-fast error handling. Errors propagate to the host by default; use `.recover` to convert to `Result` when needed.

### Raising Errors

```klein
error "invalid input"
error { code = "NOT_FOUND", id = customerId }
```

### Recovering from Errors

Wrap in a lambda and call `.recover`:

```klein
|parseNumber(input)|.recover    # Result<Num, Thrown>
```

### Working with Results

```klein
type Result<'T, 'E> = Ok { value: 'T } | Err { error: 'E }

# Unwrap or raise
customer = |fetch(id)|.recover.unwrap

# Unwrap with default
count = |parseNumber(input)|.recover.orDefault(0)

# Unwrap with fallback computation
config = |loadConfig()|.recover.orElse(|defaultConfig()|)

# Transform success
name = |fetch(id)|.recover.map(|.name|)

# Chain computations
result = |fetch(id)|.recover
  .flatMap(|customer -> |validate(customer)|.recover|)
  .flatMap(|valid -> |save(valid)|.recover|)

# Pattern match
match |parseNumber(input)|.recover
  Ok(value) -> value * 2
  Err(error) ->
    log(error.value)
    0
```

### Summary

| Want | Syntax |
|------|--------|
| Raise an error | `error "message"` or `error { ... }` |
| Convert to Result | `\|expr\|.recover` |
| Unwrap or raise | `.unwrap` |
| Unwrap or default | `.orDefault(value)` |
| Unwrap or compute | `.orElse(\|fallback\|)` |
| Transform success | `.map(\|...\|)` |
| Chain Results | `.flatMap(\|...\|)` |

## Complete Example

```klein
type Customer = Customer {
  id: Num,
  name: String,
  creditScore: Num,
  segment: Segment,
  verified: Bool
}

type Segment = Premium | Standard | New

type Application = Application {
  customer: Customer,
  amount: Num,
  purpose: String
}

type Decision =
  AutoApproved
  | NeedsReview { reviewer: String }
  | Rejected { reason: String }


fun assessApplication(app: Application): Decision =
  customer = app.customer
  amount = app.amount

  # Early rejection using condition match
  match
    not customer.verified -> Rejected("Customer not verified")
    amount <= 0 -> Rejected("Invalid amount")
    customer.creditScore < 300 -> Rejected("Credit score too low")
    else -> continueAssessment(app)

fun continueAssessment(app: Application): Decision =
  riskScore = calculateRisk(app)

  match
    riskScore < 20 and app.amount < 5000 -> AutoApproved
    riskScore < 50 -> NeedsReview(assignReviewer(app))
    else -> Rejected("Risk too high: ${riskScore}")

fun calculateRisk(app: Application): Num =
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

fun assignReviewer(app: Application): String =
  if app.amount > 20000 then "director@company.com"
  else "manager@company.com"
```

## Summary Table

| Want | Syntax |
|------|--------|
| Named function | `fun name(params) = body` |
| Multi-line function | `fun name(params) =` + indented block |
| Zero-param function | `fun name() = body` |
| Lambda with explicit params | `\|x -> expr\|` or `\|x, y -> expr\|` |
| Lambda with implicit param | `\|.field\|` or `\|. > 100\|` |
| Zero-param lambda | `\|expr\|` |
| Constant lambda | `\|42\|` |
| Identity lambda | `\|.\|` |
| Pass function directly | `nums.fold(0, add)` |
| Pass operator directly | `nums.fold(0, (+))` |
| Partial application | `\|add(1, .)\|` |
| Block (immediate eval) | indented lines after `=` |
| Bind a lambda | `x = \|.price > 100\|` |
| If/else | `if cond then a else b` |
| Multi-line if | `if cond then` + indented block |
| Value match | `match x` + indented arms |
| Condition match | `match` + indented condition arms |
| Guard | `pattern if cond -> expr` |
| Record type | `type R = { fields }` |
| Enum type | `type E = A \| B` or `type E =` + indented variants |
| Type alias | `type Name = T` |
| For comprehension | `for x in xs yield expr` |
| For with filter | `for x in xs if cond yield expr` |
| List literal | `[1, 2, 3]` |
| Record literal | `{ a = 1, b = 2 }` |
| Record shorthand | `{ a, b }` (when vars match field names) |
| Record update | `{ ...r, field = newVal }` |
| Tuple literal | `(a, b)` |
| Tuple access | `tuple._1`, `tuple._2` |
| Range | `1..10` or `1..<10` |
| String interpolation | `"Hello ${name}"` |
| Comments | `#` |
| Module definition | `module Name` + indented body |
| Import all | `import Module._` |
| Import specific | `import Module.{ A, B }` |
| Extension method | `fun foo(on x: T, ...)` |
| Record-to-positional | `f~(record)` |
| Function type (1 param) | `A -> B` |
| Function type (n params) | `(A, B) -> C` |
