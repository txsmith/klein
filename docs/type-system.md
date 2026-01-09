# Klein Type System

Klein's type system is **structural by default, nominal when needed**, with full Hindley-Milner inference and row polymorphism.

## Design Principles

- Records are structurally typed with row polymorphism
- All `type` definitions create nominal types with constructors
- Nominal types are subtypes of their structural equivalents
- One keyword (`type`) for all type definitions
- Compatible with Hindley-Milner inference

## Primitive Types

Built-in types are capitalized:

```klein
Int
Double
String
Bool
```

## Type Variables

Type variables are lowercase and implicitly universally quantified:

```klein
a, b, t, e, key, value
```

The rule: **uppercase = concrete type, lowercase = type variable**.

## Records

Records are structural types with named fields:

```klein
{ name: String, age: Int }
```

### Open by Default

Records accept extra fields beyond what's specified:

```klein
fun greet(r: { name: String }): String = 'Hello, ${r.name}'

person = { name = 'Alice', age = 30 }
greet(person)  # works! extra fields ignored
```

### Row Variables

Use `...` to name a row variable when you need to preserve extra fields:

```klein
fun addAge(r: { name: String, ...rest }): { name: String, age: Int, ...rest } =
  { ...r, age = 0 }
```

The type-level `...` mirrors value-level spread:

| Level | Syntax | Meaning |
|-------|--------|---------|
| Type | `{ name: String, ...r }` | Record with `name` plus row variable `r` |
| Value | `{ ...person, age = 0 }` | Spread `person`, add/override `age` |

### Record Intersection

Use spread to combine record types:

```klein
type Named = { name: String }
type Aged = { age: Int }
type Person = { ...Named, ...Aged }
# = { name: String, age: Int }
```

Fields with the same name must have the same type:

```klein
type A = { x: Int }
type B = { x: String }
type Bad = { ...A, ...B }  # Error: conflicting types for 'x'
```

## Function Types

Function types use arrow syntax with positional parameters:

```klein
Int -> Int                    # single parameter
(Int, Int) -> Int             # multiple parameters
() -> Int                     # no parameters (thunk)
(a -> b, List(a)) -> List(b)  # higher-order
```

Examples:

```klein
fun double(x: Int): Int = x * 2

# The type of double is:
# Int -> Int

fun add(x: Int, y: Int): Int = x + y

# The type of add is:
# (Int, Int) -> Int
```

Type parameters are inferred from lowercase variables:

```klein
fun identity(x: a): a = x
fun map(f: a -> b, xs: List(a)): List(b) = ...
```

### The Tilde Operator (~)

The `~` operator transforms a positional function type to accept a record:

```klein
fun process(name: String, age: Int): Decision = ...

process  : (String, Int) -> Decision
process~ : { name: String, age: Int } -> Decision
```

This is useful when you have records and want to spread them into positional functions:

```klein
people.map(process~)  # process~ : { name: String, age: Int } -> Decision
```

### Structural Access via Spread

You can spread a nominal type in a function signature to accept its payload structurally:

```klein
type Person = { name: String, age: Int }

# Nominal - requires a Person
fun greetPerson(p: Person): String = 'Hello, ${p.name}'

# Structural - accepts anything with Person's fields
fun greetAnyone(r: { ...Person }): String = 'Hello, ${r.name}'

# Structural with row variable - preserves extra fields
fun withTitle(r: { ...Person, ...rest }): { ...Person, title: String, ...rest } =
  { ...r, title = 'Mr/Ms' }
```

This lets callers pass any structurally compatible type:

```klein
type Employee = { name: String, age: Int, department: String }

greetPerson(Person { name = 'Alice', age = 30 })      # works
greetPerson(Employee { ... })                          # error! Employee is not Person

greetAnyone(Person { name = 'Alice', age = 30 })      # works
greetAnyone(Employee { name = 'Bob', age = 25, department = 'Sales' })  # works!
greetAnyone({ name = 'Charlie', age = 40 })           # works!
```

## Defining Types

All type definitions use the `type` keyword and create nominal types with constructors.

### Single-Constructor Types

When the right-hand side is a record, a constructor with the same name as the type is created:

```klein
type Person = { name: String, age: Int }
type Point = { x: Double, y: Double }
```

Usage:

```klein
# Construction
p = Person { name = 'Alice', age = 30 }

# Pattern matching
match p
  Person { name, age } -> 'Hello, ${name}'
```

`Person` and `Point` are distinct nominal types, even if their fields happen to match.

### Wrapper Types

Wrapping a non-record type works the same way:

```klein
type CustomerId = Int
type Money = Double
```

Usage:

```klein
id = CustomerId(42)
price = Money(99.95)
```

This creates distinct nominal types—`CustomerId` and `Int` are not interchangeable (see "Nominal vs Structural Interop").

### Sum Types

Use pipe (`|`) to introduce multiple constructors:

```klein
type Color = Red | Green | Blue

type Option(a) = Some { value: a } | None

type Result(t, e) = Ok { value: t } | Err { error: e }
```

Payloads are always records:

```klein
type Light =
  Red { duration: Int, intensity: Double }
  | Yellow { duration: Int }
  | Green { duration: Int, direction: String }
```

### Type Parameters

Type definitions take explicit parameter lists:

```klein
type Option(a) = Some { value: a } | None
type Result(t, e) = Ok { value: t } | Err { error: e }
type Pair(a, b) = { first: a, second: b }
```

Apply type arguments with parens:

```klein
x: Option(Int) = Some { value = 42 }
y: Result(String, Error) = Ok { value = 'hello' }
```

## Nominal vs Structural Interop

A nominal type is a subtype of its structural payload:

```klein
type Person = { name: String, age: Int }

# Person <: { name: String, age: Int }
# Person <: { name: String }  (via row polymorphism)
```

This means nominal types can be passed where structural types are expected:

```klein
fun greet(r: { name: String }): String = 'Hello, ${r.name}'

person = Person { name = 'Alice', age = 30 }
greet(person)  # works! Person <: { name: String }
```

### One-Directional

Subtyping only works from nominal to structural, not the reverse:

```klein
type CustomerId = Int

fun getCustomer(id: CustomerId): Customer = ...

getCustomer(CustomerId(42))   # Works
getCustomer(42)                # Error: Int is not CustomerId
```

This lets you enforce domain boundaries:

```klein
type CustomerId = Int
type OrderId = Int

# Can't accidentally mix these up
fun process(cid: CustomerId, oid: OrderId) = ...
```

### Nested Subtyping

Subtyping composes through nesting:

```klein
type Address = { city: String, zip: String }
type Person = { name: String, address: Address }

fun getCity(r: { address: { city: String } }): String = r.address.city

person = Person {
  name = 'Alice',
  address = Address { city = 'NYC', zip = '10001' }
}

getCity(person)  # Works!

# Because:
# Address <: { city: String, zip: String } <: { city: String }
```

### Inferred Interface

When all constructors of a sum type share common fields, those fields are accessible without pattern matching:

```klein
type Light =
  Red { duration: Int, intensity: Double }
  | Yellow { duration: Int }
  | Green { duration: Int, direction: String }

# Inferred interface: { duration: Int }

fun getDuration(light: Light): Int = light.duration  # No match needed
```

## Construction and Pattern Matching

### Construction

Just use the constructor name:

```klein
# Bare constructors
color = Red
result = None

# With payload (named fields)
person = Person { name = 'Alice', age = 30 }
result = Ok { value = 42 }

# Single-field shorthand
result: Result(Int, String) = Ok { 42 }
```

### Pattern Matching

```klein
match color
  Red -> 'stop'
  Yellow -> 'slow'
  Green -> 'go'

match result
  Ok { value } -> value
  Err { error } -> handleError(error)

match person
  Person { name, age } -> 'Hello, ${name}'
```

## Tuples

Tuples use parentheses and have positional field names `_1`, `_2`, etc:

```klein
pair = ('Alice', 30)
triple = (1, 2, 3)

pair._1                   # 'Alice'
pair._2                   # 30
```

Tuples are structurally typed and distinct from records. The tuple type `(String, Int)` is equivalent to `{ _1: String, _2: Int }`.

## Type Inference

Klein uses Hindley-Milner type inference. Type annotations are optional in most cases:

```klein
fun double(x) = x * 2                  # inferred: Int -> Int
fun identity(x) = x                    # inferred: a -> a
fun compose(f, g, x) = f(g(x))         # inferred polymorphic
```

Type variables are implicitly universally quantified at the function level.

## Summary Table

| Construct | Syntax | Example |
|-----------|--------|---------|
| Primitive | `Name` | `Int`, `String`, `Bool` |
| Type variable | `name` | `a`, `t`, `key` |
| Record (structural) | `{ field: Type }` | `{ name: String, age: Int }` |
| Open record | `{ field: Type, ...r }` | `{ name: String, ...r }` |
| Record intersection | `{ ...A, ...B }` | `{ ...Named, ...Aged }` |
| Function type (1 param) | `A -> B` | `Int -> Int` |
| Function type (n params) | `(A, B) -> C` | `(Int, Int) -> Int` |
| Function type (0 params) | `() -> A` | `() -> Int` |
| Single-constructor type | `type Name = { ... }` | `type Person = { name: String }` |
| Wrapper type | `type Name = Primitive` | `type Money = Double` |
| Sum type | `type Name = A \| B` | `type Color = Red \| Green` |
| Constructor with payload | `Name { fields }` | `Ok { value: t }` |
| Type parameters | `Name(a, b)` | `Option(a)`, `Result(t, e)` |
| Type application | `Name(Type)` | `Option(Int)`, `List(String)` |
| Tuple type | `(A, B)` | `(String, Int)` |
| Tilde transform | `f~` | `process~ : { name: String } -> R` |
