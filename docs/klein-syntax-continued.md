# Klein Syntax: Control Flow & Type Definitions

Building on the function syntax established earlier, this document covers control flow, pattern matching, type definitions, and iteration.

## If/Then/Else

Uses `then` keyword to clearly separate condition from branches:

```klein
// Simple expression
max = if a > b then a else b

// Multi-line with blocks
result = if score >= 90 then {
  grade = "A"
  sendCongrats(student)
  grade
} else if score >= 80 then {
  "B"
} else {
  "C"
}

// Nested
status = if approved then
           if amount > 1000 then "LargeApproved" else "SmallApproved"
         else
           "Rejected"
```

The `then` keyword makes it unambiguous where the condition ends, avoiding C-style parsing issues and reading naturally as English.

## Pattern Matching

`match` serves two purposes: matching on values (like ML/Rust) and matching on conditions (like Kotlin's `when`).

### Value Matching

```klein
match status {
  Approved -> processLoan(application)
  Rejected(reason) -> notifyRejection(reason)
  Pending -> waitForReview()
}
```

### Condition Matching

When no subject is provided, branches are boolean expressions:

```klein
match {
  score >= 90 -> "A"
  score >= 80 -> "B"
  score >= 70 -> "C"
  else -> "F"
}
```

This reads like a decision table—exactly how non-engineers think about business rules:

```klein
riskCategory = match {
  customer.creditScore < 500 -> "High"
  customer.yearsWithUs < 1 -> "Medium"
  customer.outstandingLoans > 3 -> "Medium"
  else -> "Low"
}
```

### Guards

Combine value matching with additional conditions:

```klein
match amount {
  x if x > 10000 -> requiresDirectorApproval(x)
  x if x > 1000 -> requiresManagerApproval(x)
  x if x > 0 -> autoApprove(x)
  else -> reject("Invalid amount")
}
```

### Destructuring

```klein
// Records
match person {
  { name, age } if age >= 18 -> allowEntry(name)
  { name, _ } -> denyEntry(name)
}

// Lists
match items {
  [] -> "empty"
  [only] -> "just one: " ++ only
  [first, ...rest] -> "first is " ++ first
}


```

### Syntax Details

| Aspect | Choice | Rationale |
|--------|--------|-----------|
| Arrow | `->` | Consistent with function types and lambdas |
| Default case | `else` | Friendlier for non-engineers than `_` |
| Separators | newline or comma | Flexible formatting |
| Exhaustiveness | Required for enums, `else` required for conditions | Safety |

```klein
// Single line (comma separated)
match status { Pending -> wait(), Approved -> go(), else -> stop() }

// Multi-line (newline separated)
match status {
  Pending -> wait()
  Approved -> go()
  else -> stop()
}
```

## Type Definitions

### Records

```klein
type Customer = {
  id: int,
  name: string,
  creditLimit: double,
  region: Region,
  spouse: Customer?
}

// With Kleene cardinalities
type Order = {
  items: LineItem+,       // one or more (required)
  discounts: Discount*,   // zero or more (optional list)
  primaryContact: string,
  backupContacts: string*
}
```

### Enums / Sum Types

```klein
type Region = Nakuru | Kisumu | Nairobi

type PaymentStatus =
  | Pending
  | Approved(approver: string, date: Date)
  | Rejected(reason: string)
  | Cancelled

// Usage
status = Approved("Jane", today)

match status {
  Approved(who, when) -> "Approved by " ++ who
  Rejected(why) -> "Rejected: " ++ why
  else -> "Other"
}
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
expensive = for item in items
            if item.price > 100
            yield item.name

// Nested (cartesian product)
pairs = for x in xs
        for y in ys
        yield (x, y)

// With destructuring
totals = for { price, qty } in lineItems
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
type Customer = {
  id: int,
  name: string,
  creditScore: int,
  segment: Segment,
  verified: bool
}

type Segment = Premium | Standard | New

type Application = {
  customer: Customer,
  amount: double,
  purpose: string
}

type Decision =
  | AutoApproved
  | NeedsReview(reviewer: string)
  | Rejected(reason: string)


fun assessApplication(app: Application): Decision = {
  customer = app.customer
  amount = app.amount

  # Early rejection using condition match
  match {
    not customer.verified -> Rejected('Customer not verified')
    amount <= 0 -> Rejected('Invalid amount')
    customer.creditScore < 300 -> Rejected('Credit score too low')
    else -> continueAssessment(app)
  }
}

fun continueAssessment(app: Application): Decision = {
  riskScore = calculateRisk(app)

  match {
    riskScore < 20 and app.amount < 5000 -> AutoApproved
    riskScore < 50 -> NeedsReview(assignReviewer(app))
    else -> Rejected('Risk too high: ${riskScore}')
  }
}

fun calculateRisk(app: Application): int = {
  base = match app.customer.segment {
    Premium -> 0
    Standard -> 10
    New -> 25
  }

  amountRisk = match {
    app.amount > 50000 -> 30
    app.amount > 10000 -> 15
    app.amount > 5000 -> 5
    else -> 0
  }

  base + amountRisk
}

fun assignReviewer(app: Application): string =
  if app.amount > 20000 then 'director@company.com'
  else 'manager@company.com'
```

## Summary Table

| Feature | Syntax |
|---------|--------|
| If/else | `if cond then a else b` |
| Value match | `match x { Pat -> expr }` |
| Condition match | `match { cond -> expr, else -> expr }` |
| Guard | `match x { p if cond -> expr }` |
| Record type | `type R = { field: T, ... }` |
| Enum type | `type E = A \| B(x: T) \| C` |
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
