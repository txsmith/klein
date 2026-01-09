# Klein Type System

Klein uses **SimpleSub-style type inference** with subtyping. Records have width subtyping—a record with more fields is a subtype of one with fewer fields.

## Design Principles

- Subtyping integrated into type inference (à la SimpleSub/MLSub)
- Records are structurally typed with width subtyping
- All `type` definitions create nominal types with constructors
- Nominal types are subtypes of their structural equivalents
- Principal types exist despite subtyping

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

### Width Subtyping

A record with more fields is a subtype of a record with fewer fields:

```klein
{ name: String, age: Int } <: { name: String }
{ x: Int, y: Int, z: Int } <: { x: Int, y: Int }
```

This means functions accepting records work with any record that has at least the required fields:

```klein
fun greet(r: { name: String }): String = 'Hello, ${r.name}'

person = { name = 'Alice', age = 30 }
greet(person)  # works! { name, age } <: { name }
```

### Depth Subtyping

Subtyping is covariant in field types:

```klein
{ point: { x: Int, y: Int } } <: { point: { x: Int } }
```

## Function Types

Function types use arrow syntax with positional parameters:

```klein
Int -> Int                    # single parameter
(Int, Int) -> Int             # multiple parameters
() -> Int                     # no parameters (thunk)
(a -> b, List(a)) -> List(b)  # higher-order
```

### Variance

Function types are contravariant in parameters, covariant in return:

```klein
# If A <: B, then:
(B -> C) <: (A -> C)    # contravariant input
(C -> A) <: (C -> B)    # covariant output
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

This creates distinct nominal types—`CustomerId` and `Int` are not interchangeable.

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

# Person <: { name: String, age: Int } <: { name: String }
```

This means nominal types can be passed where structural types are expected:

```klein
fun greet(r: { name: String }): String = 'Hello, ${r.name}'

person = Person { name = 'Alice', age = 30 }
greet(person)  # works! Person <: { name: String }
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
# Person <: { name: String, address: Address }
# Address <: { city: String, zip: String } <: { city: String }
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

Tuples are structurally typed. The tuple type `(String, Int)` is equivalent to `{ _1: String, _2: Int }`.

## Type Inference

Klein uses SimpleSub-style type inference—subtyping is integrated into unification rather than being a separate check. This gives us:

- **Principal types** — every expression has a most general type
- **No explicit subtype coercions** — subtyping happens automatically
- **Predictable inference** — similar to Hindley-Milner but with subtyping

Type annotations are optional in most cases:

```klein
fun double(x) = x * 2                  # inferred: Int -> Int
fun identity(x) = x                    # inferred: a -> a
fun compose(f, g, x) = f(g(x))         # inferred polymorphic
```

Type variables are implicitly universally quantified at the function level.

### How SimpleSub Works

SimpleSub tracks subtyping constraints during inference using polar types—distinguishing between types in positive positions (outputs) and negative positions (inputs). This lets it compute principal types that capture "at least" and "at most" constraints:

```klein
fun getX(r) = r.x
# r is in negative position (input): must have at least { x }
# result is in positive position (output): exactly the type of x

# Inferred type: { x: a } -> a
# But { x: a } here means "any record with at least an x field"
```

The subtyping is implicit in how types are used, not in explicit syntax.

## Summary Table

| Construct | Syntax | Example |
|-----------|--------|---------|
| Primitive | `Name` | `Int`, `String`, `Bool` |
| Type variable | `name` | `a`, `t`, `key` |
| Record (structural) | `{ field: Type }` | `{ name: String, age: Int }` |
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

## References

- Parreaux, L. (2020). "The Simple Essence of Algebraic Subtyping"
- Dolan, S. & Mycroft, A. (2017). "Polymorphism, Subtyping, and Type Inference in MLsub"
