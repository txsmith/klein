# Klein Calling Conventions

## Core Model

Functions take a single record argument. Everything else is syntactic sugar over this model.

## Records

Records use braces with `=` for field assignment:

```klein
person = { name = "Alice", age = 30 }
point = { x = 1, y = 2 }
```

### Field Shorthand

When a variable name matches the field name, you can omit the assignment:

```klein
name = "Alice"
age = 30
person = { name, age }  # same as { name = name, age = age }
```

This is purely syntactic sugar—bare identifiers in braces become `name = name`.

### Spread

Copy fields from an existing record:

```klein
person = { name = "Alice", age = 30 }
employee = { ...person, role = "Engineer" }  # adds field
older = { ...person, age = 31 }              # overrides field
merged = { ...defaults, ...overrides }       # merge records
```

## Tuples

Tuples use parentheses and always infer with positional field names `_1`, `_2`, `_3`, etc:

```klein
pair = ("Alice", 30)          # (String, Int), fields _1, _2
triple = (1, 2, 3)            # (Int, Int, Int), fields _1, _2, _3

pair._1                       # "Alice"
pair._2                       # 30
```

Tuples are structurally typed and scale to any arity. The field names `_1`, `_2`, etc. are fixed—tuples always infer this way regardless of context.

### Future Consideration: Tuple Subtyping

A potential extension is to make tuples subtypes of structurally-compatible records by position:

```klein
# HYPOTHETICAL - not currently part of Klein
pair = ("Alice", 30)          # (String, Int)
greet pair                    # would work if (String, Int) <: { name: String, age: Int }
```

This would allow tuples to flow into any record type with matching field types in declaration order. The tradeoff is added flexibility vs. potential confusion (a tuple "acts like" a Person at call sites but doesn't have `.name` access).

**Current status:** Not implemented. Requires explicit conversion or annotation.

## Function Definition

Functions are defined with `fun`, listing parameters in braces:

```klein
fun greet { name: String, age: Int }: String =
  "Hello, ${name}, you are ${age} years old"
```

The parameter list `{ name: String, age: Int }` is the record type the function accepts. The fields are destructured and available in the function body.

### Type Inference

Type annotations on parameters are optional:

```klein
fun double { x } = x * 2
```

Type variables are implicitly universally quantified at the function level:

```klein
fun identity { x: a }: a = x
fun map { f: a -> b, xs: List(a) }: List(b) = ...
```

## Function Application

Function application is juxtaposition: `f x` where `x` is the single record argument.

### Passing Existing Records

When passing an existing record, no braces needed:

```klein
person = { name = "Alice", age = 30 }
greet person
```

### Calling with Braces

Braces construct a record to pass to the function. The behavior depends on the number of fields expected:

**Single-field functions:** The value is assigned to that field regardless of name:

```klein
fun double { x: Int }: Int = x * 2
fun process { person: Person }: String = ...

double { 5 }              # { x = 5 }
double { myNumber }       # { x = myNumber }
process { somePerson }    # { person = somePerson }
```

**Multi-field functions:** Use named or shorthand syntax:

```klein
fun greet { name: String, age: Int }: String = ...

greet { name = "Alice", age = 32 }   # named
greet { name, age }                   # shorthand (variables must match field names)
greet { n, a }                        # ERROR: no fields 'n' or 'a'
```

This distinction exists because:
- Single field → no ambiguity about which field gets the value
- Multi field → names clarify intent and prevent ordering mistakes

### Calling with Parens (Positional)

Parens provide positional call syntax as an escape hatch for the rare cases where named fields feel too verbose:

```klein
fun add { a: Int, b: Int }: Int = a + b
fun range { from: Int, to: Int }: List(Int) = ...

add (1, 2)                # { a = 1, b = 2 }
range (1, 100)            # { from = 1, to = 100 }
```

At call sites, parens construct positionally into the expected record type (not a tuple). The values are matched to fields in declaration order.

**When to use parens vs braces:**
- Braces are the default—use for most calls
- Parens are for terse positional calls when field names add noise

```klein
# Most code uses braces
xs.map { double }
xs.fold { init = 0, f = sum }
greet { name, age }
process { myPerson }

# Occasional parens for terse positional
add (1, 2)
range (1, 100)
min (a, b)
```

### Spread in Calls

Spread an existing record into the arguments:

```klein
greet { ...person }              # equivalent to: greet person
greet { ...person, age = 99 }    # spread + override
```

### Row Polymorphism

Functions accept records with extra fields beyond what they require:

```klein
fun greet { name: String }: String = "Hello, ${name}"

person = { name = "Alice", age = 30, email = "alice@example.com" }
greet person  # works! extra fields ignored
```

### Partial Application

Use lambdas with the dot shorthand to create partially applied functions:

```klein
fun filter { predicate: a -> Bool, xs: List(a) }: List(a) = ...

# Partial application - returns List(a) -> List(a):
|filter { predicate = isEven, xs = . }|
```

### Extension Methods

Use the `on` keyword to mark which parameter becomes the method receiver:

```klein
fun map { f: a -> b, on xs: List(a) }: List(b) = ...
fun filter { p: a -> Bool, on xs: List(a) }: List(a) = ...
fun isAdult { on c: Customer }: Bool = c.age >= 18
```

Both calling styles work:

```klein
# Function call style
map { f = double, xs = numbers }
filter { p = isEven, xs = numbers }
isAdult { c = customer }

# Method syntax (extension method becomes receiver, remaining fields in braces)
numbers.map { double }          # single remaining field, positional
numbers.filter { isEven }       # single remaining field, positional
customer.isAdult                # no remaining fields
```

Method syntax enables clean chaining:

```klein
[1, 2, 3].map { |. * 2| }.filter { |. > 2| }
```

Rules:
- Only **one** `on` parameter per function
- Any parameter position works
- Must be imported to use method syntax

See `modules-and-sugar-proposal.md` for full details on extension methods and modules.

## Summary of Calling Conventions

| Syntax | Meaning |
|--------|---------|
| `f x` | Pass record `x` to function `f` |
| `f { value }` | Single-field: assign value to the field |
| `f { x = a, y = b }` | Multi-field: named record construction |
| `f { name, age }` | Multi-field: shorthand for `{ name = name, age = age }` |
| `f (a, b)` | Positional: assign values to fields in order |
| `f { ...r }` | Spread record `r`, pass to `f` |
| `f { ...r, x = a }` | Spread `r`, override/add field |
| `x.f { arg }` | Method call: `x` is receiver, remaining args in braces |

## Summary of Construction Syntax

| Syntax | Context | Result |
|--------|---------|--------|
| `{ name = "Alice", age = 30 }` | Any | Record with named fields |
| `{ name, age }` | Any | Shorthand: `{ name = name, age = age }` |
| `("Alice", 30)` | Any | Tuple with fields `_1`, `_2` |
| `f { value }` | Single-field function | Assigns value to that field |
| `f (a, b)` | Call site | Positional into expected record type |

## Design Rationale

### Why single-record arguments?

- Unifies positional args, named args, and record passing into one concept
- No special cases for "multi-arg" vs "record arg" functions  
- Spread syntax works uniformly for construction and calls
- Row polymorphism gives flexible, structural typing

### Why braces for records, parens for tuples?

- Visually distinct: `{ }` for structured data with field names, `( )` for positional grouping
- `f x` (no braces) — passing an existing record through
- `f { ... }` (braces) — constructing a record for the call
- `(a, b)` — tuple, always has `_1`, `_2` fields

### Why single-field positional, multi-field named?

- Single field: no ambiguity, `{ value }` clearly goes to that one field
- Multi field: names clarify intent and prevent ordering errors
- Extension methods make most stdlib calls single-field anyway
- Parens available as escape hatch when positional is clearer

### Why parens as positional escape hatch?

- Some functions (`add`, `range`, `min`) have obvious positional semantics
- Named syntax for these adds noise: `add { a = 1, b = 2 }` vs `add (1, 2)`
- Parens are familiar from other languages
- Keeps braces consistent (always named/shorthand)
