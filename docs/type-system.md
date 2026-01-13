# Klein Type System

Klein uses **SimpleSub-style type inference** with subtyping. Records have width subtyping meaning a record with more fields is a subtype of one with fewer fields.

## Design Principles

- Subtyping integrated into type inference (à la SimpleSub/MLSub)
- Records are structurally typed with width subtyping
- All `type` definitions create nominal types with constructors
- Nominal types are subtypes of their structural equivalents
- Principal types exist despite subtyping

## Primitive Types

Built-in types are capitalized:

```klein
Num
String
Bool
Unit
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
{ name: String, age: Num }
```

### Width Subtyping

A record with more fields is a subtype of a record with fewer fields:

```klein
{ name: String, age: Num } <: { name: String }
{ x: Num, y: Num, z: Num } <: { x: Num, y: Num }
```

This means functions accepting records work with any record that has at least the required fields:

```klein
fun greet(r: { name: String }): String = "Hello, ${r.name}"

person = { name = "Alice", age = 30 }
greet(person)  # works! { name, age } <: { name }
```

### Depth Subtyping

Subtyping is covariant in field types:

```klein
{ point: { x: Num, y: Num } } <: { point: { x: Num } }
```

## Function Types

Function types use arrow syntax with positional parameters:

```klein
Num -> Num                    # single parameter
(Num, Num) -> Num             # multiple parameters
() -> Num                     # no parameters (thunk)
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
fun double(x: Num): Num = x * 2

# The type of double is:
# Num -> Num

fun add(x: Num, y: Num): Num = x + y

# The type of add is:
# (Num, Num) -> Num
```

Type parameters are inferred from lowercase variables:

```klein
fun identity(x: a): a = x
fun map(f: a -> b, xs: List(a)): List(b) = ...
```

### The Tilde Operator (~)

The `~` operator transforms a positional function type to accept a record:

```klein
fun process(name: String, age: Num): Decision = ...

process  : (String, Num) -> Decision
process~ : { name: String, age: Num } -> Decision
```

This is useful when you have records and want to spread them into positional functions:

```klein
people.map(process~)  # process~ : { name: String, age: Num } -> Decision
```

## Defining Types

All type definitions use the `type` keyword and create nominal types with constructors.

### Single-Constructor Types

When the right-hand side is a record, a constructor with the same name as the type is created:

```klein
type Person = { name: String, age: Num }
type Point = { x: Num, y: Num }
```

Usage:

```klein
# Construction
p = Person { name = "Alice", age = 30 }

# Pattern matching
match p
  Person { name, age } -> "Hello, ${name}"
```

`Person` and `Point` are distinct nominal types, even if their fields happen to match.

### Wrapper Types

Wrapping a non-record type works the same way:

```klein
type CustomerId = Num
type Money = Num
```

Usage:

```klein
id = CustomerId(42)
price = Money(99.95)
```

This creates distinct nominal types—`CustomerId` and `Num` are not interchangeable.

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
  Red { duration: Num, intensity: Num }
  | Yellow { duration: Num }
  | Green { duration: Num, direction: String }
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
x: Option(Num) = Some { value = 42 }
y: Result(String, Error) = Ok { value = 'hello' }
```

## Nominal vs Structural Interop

A nominal type is a subtype of its structural payload:

```klein
type Person = { name: String, age: Num }

# Person <: { name: String, age: Num } <: { name: String }
```

This means nominal types can be passed where structural types are expected:

```klein
fun greet(r: { name: String }): String = "Hello, ${r.name}"

person = Person { name = "Alice", age = 30 }
greet(person)  # works! Person <: { name: String }
```

### Nested Subtyping

Subtyping composes through nesting:

```klein
type Address = { city: String, zip: String }
type Person = { name: String, address: Address }

fun getCity(r: { address: { city: String } }): String = r.address.city

person = Person {
  name = "Alice",
  address = Address { city = "NYC", zip = "10001" }
}

getCity(person)  # Works!

# Because:
# Person <: { name: String, address: Address }
# Address <: { city: String, zip: String } <: { city: String }
```

### One-Directional

Subtyping only works from nominal to structural, not the reverse:

```klein
type CustomerId = Num

fun getCustomer(id: CustomerId): Customer = ...

getCustomer(CustomerId(42))   # Works
getCustomer(42)                # Error: Num is not CustomerId
```

This lets you enforce domain boundaries:

```klein
type CustomerId = Num
type OrderId = Num

# Can't accidentally mix these up
fun process(cid: CustomerId, oid: OrderId) = ...
```

### Inferred Interface

When all constructors of a sum type share common fields, those fields are accessible without pattern matching:

```klein
type Light =
  Red { duration: Num, intensity: Num }
  | Yellow { duration: Num }
  | Green { duration: Num, direction: String }

# Inferred interface: { duration: Num }

fun getDuration(light: Light): Num = light.duration  # No match needed
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
result: Result(Num, String) = Ok { 42 }
```

### Pattern Matching

```klein
match color
  Red -> "stop"
  Yellow -> "slow"
  Green -> "go"

match result
  Ok { value } -> value
  Err { error } -> handleError(error)

match person
  Person { name, age } -> "Hello, ${name}"
```

## Tuples

Tuples use parentheses and have positional field names `_1`, `_2`, etc:

```klein
pair = ("Alice", 30)
triple = (1, 2, 3)

pair._1                   # "Alice"
pair._2                   # 30
```

Tuples are structurally typed. The tuple type `(String, Num)` is equivalent to `{ _1: String, _2: Num }`.

## Type Inference

Klein uses SimpleSub-style type inference—subtyping is integrated into unification rather than being a separate check. This gives us:

- **Principal types** — every expression has a most general type
- **No explicit subtype coercions** — subtyping happens automatically
- **Predictable inference** — similar to Hindley-Milner but with subtyping

Type annotations are optional in most cases:

```klein
fun double(x) = x * 2                  # inferred: Num -> Num
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

### Union and Intersection Types

SimpleSub infers union (`|`) and intersection (`&`) types as a way of representing constraints on type variables. These types are **restricted by polarity**—they can only appear in certain positions.

**Polarity restriction.** Union and intersection types can only appear in specific positions:

| Type | Allowed position | Meaning |
|------|------------------|---------|
| `A \| B` | Positive (return types, record fields) | "Produces either A or B" |
| `A & B` | Negative (function arguments) | "Must satisfy both A and B" |

**Union types** (`A | B`) appear in output positions:

```klein
fun pick(b) = if b then 42 else "hello"
# Inferred: (Bool) -> Num | String

object1 = { x = 42, y = |x -> x| }
object2 = { x = 17, y = false }
fun choose(b) = if b then object1 else object2
# Inferred: (Bool) -> { x: Num, y: Bool | ((a) -> a) }
```

**Intersection types** (`A & B`) appear in input positions:

```klein
fun selfApply(x) = x(x)
# Inferred: (a & ((a) -> b)) -> b
# x must be both a value (a) and a function that accepts it ((a) -> b)
```

**What you cannot do.** Because of the polarity restriction, you cannot write a function that accepts `Num | String` as input—unions are illegal in negative position. This means Klein (like MLsub) cannot express "a function that takes either a number or a string." If you need that, use a nominal sum type:

```klein
type NumOrString = N { value: Num } | S { value: String }

fun process(x: NumOrString) = match x
  N { value } -> ...
  S { value } -> ...
```

**Why this matters.** When you see an inferred union like `(Bool) -> Num | String`, you can produce such values but you cannot consume them in a type-safe way without knowing which variant you have. The type system tracks what flows where, but unions in outputs are essentially "information lost"—useful for record field polymorphism, less useful as return types.

**Simplification.** The type inferencer simplifies where possible:
- `Num | Num` becomes `Num`
- `a & a` becomes `a`
- `Num & String` in an input usually indicates a type error (no value satisfies both)

### Recursive Types

SimpleSub can infer recursive types—types that refer to themselves. These arise naturally from certain programming patterns:

```klein
fun produce(n) = { head = n, tail = produce(n + 1) }
# Inferred: (Num) -> { head: Num, tail: a } as a

fun consume(stream) = stream.head + consume(stream.tail)
# Inferred: ({ head: Num, tail: a } as a) -> Num
```

The `as a` syntax indicates that the type variable `a` refers back to the enclosing type, creating an infinite structure.

**These types exist for completeness of type inference, not for direct use.** They ensure the type system can assign principal types to all valid programs, including self-referential patterns like Y combinators or infinite stream processors.

For practical recursive data structures, use nominal types instead:

```klein
type List(a) = Cons { head: a, tail: List(a) } | Nil
type Tree(a) = Node { value: a, left: Tree(a), right: Tree(a) } | Leaf
```

Nominal types are clearer, provide better error messages, and support pattern matching. If you see inferred recursive types in your code (the `as` notation), consider whether a nominal type would better express your intent.

## Summary Table

| Construct | Syntax | Example |
|-----------|--------|---------|
| Primitive | `Name` | `Num`, `String`, `Bool` |
| Type variable | `name` | `a`, `t`, `key` |
| Record (structural) | `{ field: Type }` | `{ name: String, age: Num }` |
| Function type (1 param) | `A -> B` | `Num -> Num` |
| Function type (n params) | `(A, B) -> C` | `(Num, Num) -> Num` |
| Function type (0 params) | `() -> A` | `() -> Num` |
| Single-constructor type | `type Name = { ... }` | `type Person = { name: String }` |
| Wrapper type | `type Name = Primitive` | `type Money = Num` |
| Sum type | `type Name = A \| B` | `type Color = Red \| Green` |
| Constructor with payload | `Name { fields }` | `Ok { value: t }` |
| Type parameters | `Name(a, b)` | `Option(a)`, `Result(t, e)` |
| Type application | `Name(Type)` | `Option(Num)`, `List(String)` |
| Tuple type | `(A, B)` | `(String, Num)` |
| Tilde transform | `f~` | `process~ : { name: String } -> R` |
| Union type (inferred) | `A \| B` | `Num \| String` |
| Intersection type (inferred) | `A & B` | `Bool & Num` |
| Recursive type (inferred) | `T as a` | `{ head: Num, tail: a } as a` |

## References

- Parreaux, L. (2020). "The Simple Essence of Algebraic Subtyping"
- Dolan, S. & Mycroft, A. (2017). "Polymorphism, Subtyping, and Type Inference in MLsub"
