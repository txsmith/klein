# Klein Calling Conventions

## Core Model

Functions take positional arguments. This is the standard model used by most programming languages.

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

## Function Definition

Functions are defined with `fun`, listing parameters in parentheses:

```klein
fun greet(name: String, age: Num): String =
  "Hello, ${name}, you are ${age} years old"
```

Parameters are positional and available in the function body by name.

### Type Inference

Type annotations on parameters are optional:

```klein
fun double(x) = x * 2
```

Type variables are implicitly universally quantified at the function level:

```klein
fun identity(x: 'A): 'A = x
fun map(f: 'A -> 'B, xs: List<'A>): List<'B> = ...
```

## Function Application

Function application uses parentheses with positional arguments:

```klein
greet("Alice", 30)
double(5)
map(double, numbers)
```

### Width Subtyping for Records

When passing records to functions that expect them, extra fields are allowed (a record with more fields is a subtype of one with fewer):

```klein
fun getName(r: { name: String }): String = r.name

person = { name = "Alice", age = 30, email = "alice@example.com" }
getName(person)  # works! extra fields ignored
```

### Partial Application

Use lambdas with the dot shorthand to create partially applied functions:

```klein
fun filter(predicate: 'A -> Bool, xs: List<'A>): List<'A> = ...

# Partial application via lambda:
filterEvens = |filter(isEven, .)|
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
filter(isEven, numbers)
isAdult(customer)

# Method syntax (receiver moves to the left of the dot)
numbers.map(double)
numbers.filter(isEven)
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

The tilde operator bridges records to positional functions. It transforms a function that takes positional arguments into one that accepts a record with matching field names.

### Basic Usage

```klein
fun process(name: String, age: Num): Decision = ...

# Types:
process  : (String, Int) -> Decision
process~ : { name: String, age: Num } -> Decision
```

### When to Use Tilde

Use `~` when you have a record and want to spread it into a positional function:

```klein
person = { name = "Alice", age = 30 }

# Without tilde - extract fields manually:
process(person.name, person.age)

# With tilde - spread the record:
process~(person)
```

### With Higher-Order Functions

Tilde is particularly useful with HOFs when processing collections of records:

```klein
people: List<{ name: String, age: Num }> = [...]

# Map over records
people.map(process~)

# Filter with a predicate
fun isAdult(name: String, age: Num): Bool = age >= 18
people.filter(isAdult~)
```

### Why Tilde Exists

The problem: inside generic HOFs like `map`, we call `f(elem)`. If functions could accept either positional args OR records, the type variable would need to unify with multiple shapes—breaking standard Hindley-Milner inference.

The solution: functions are always positional. When you need record spreading, explicitly opt-in with `~`. This keeps the type system simple and predictable.

### Tilde is a Function Transformer

Conceptually, `~` is similar to combinators like `curry`/`uncurry`:

```klein
# curry transforms:   (a, b) -> c  into  a -> b -> c
# uncurry transforms: a -> b -> c  into  (a, b) -> c
# ~ transforms:       (a, b) -> c  into  { f1: a, f2: b } -> c
```

The field names in the resulting record type come from the parameter names in the function definition.

## Summary of Calling Conventions

| Syntax | Meaning |
|--------|---------|
| `f(a, b)` | Call `f` with positional arguments |
| `f~(record)` | Spread record fields into positional call |
| `x.f(arg)` | Method call: `x` is receiver (requires `on`) |
| `x.f` | Method call with no extra args |

## Summary of Construction Syntax

| Syntax | Result |
|--------|--------|
| `{ name = "Alice", age = 30 }` | Record with named fields |
| `{ name, age }` | Shorthand: `{ name = name, age = age }` |
| `{ ...r, field = val }` | Spread record, add/override field |
| `("Alice", 30)` | Tuple with fields `_1`, `_2` |

## Design Rationale

### Why positional arguments?

- Standard model that works with Hindley-Milner type inference
- HOFs like `map` work naturally: `map(f, xs)` where `f: a -> b`
- No special cases for "record arg" vs "positional arg" functions
- Familiar to programmers from most languages

### Why tilde (~) for record spreading?

- Explicit opt-in keeps the type system simple
- Clear visual marker that record-to-positional conversion is happening
- Works as a function transformer, composable with other combinators
- Avoids the complexity of having functions accept multiple calling conventions

### Why braces for records, parens for tuples/calls?

- Visually distinct: `{ }` for structured data with field names, `( )` for positional grouping
- `f(a, b)` — function call with positional args
- `{ a = 1, b = 2 }` — record literal with named fields
- `(a, b)` — tuple with positional access via `_1`, `_2`
