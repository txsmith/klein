# Klein Type System

Klein uses **local bidirectional type checking** with subtyping: you annotate signatures and the checker infers the interiors. Records have width subtyping, meaning a record with more fields is a subtype of one with fewer fields.

## Design Principles

- Subtyping checked locally and bidirectionally (synthesize / check) — no global inference
- Records are structurally typed with width subtyping
- All `type` definitions create nominal types with constructors
- Nominal types are subtypes of their structural equivalents
- Annotate signatures, infer interiors — the surface and the checker's output are one language

See [spec/bidirectional-checking.md](./spec/bidirectional-checking.md) for the full checking model.

## Primitive Types

Built-in types are capitalized:

```klein
Num
String
Bool
Unit
```

## Type Variables

Type variables use a tick prefix with uppercase letters:

```klein
'A, 'B, 'T, 'E, 'Key, 'Value
```

The rule: **uppercase = concrete type, tick + uppercase = type variable**.

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
Num -> Num                          # single parameter
(Num, Num) -> Num                   # multiple parameters
() -> Num                           # no parameters (thunk)
('A -> 'B, List<'A>) -> List<'B>    # higher-order
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

Type parameters use tick-prefixed variables:

```klein
fun identity(x: 'A): 'A = x
fun map(f: 'A -> 'B, xs: List<'A>): List<'B> = ...
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

All type definitions use the `type` keyword and create nominal types with constructors. Constructors are first-class functions with named parameters.

### Single-Constructor Types

```klein
type Money = Money { value: Num }
type Person = Person { name: String, age: Num }
type Point = Point { x: Num, y: Num }
```

Constructors are called positionally:

```klein
p = Person("Alice", 30)
m = Money(99.95)
pt = Point(10, 20)
```

`Person` and `Point` are distinct nominal types, even if their fields happen to match.

### Sum Types

Use pipe (`|`) to introduce multiple constructors:

```klein
type Color = Red | Green | Blue

type Option<'A> = Some { value: 'A } | None

type Result<'T, 'E> = Ok { value: 'T } | Err { error: 'E }
```

Constructors can have parameters or be bare:

```klein
type Light =
  Red { duration: Num, intensity: Num }
  | Yellow { duration: Num }
  | Green { duration: Num, direction: String }
```

### Type Parameters

Type definitions take explicit parameter lists with tick-prefixed variables:

```klein
type Option<'A> = Some { value: 'A } | None
type Result<'T, 'E> = Ok { value: 'T } | Err { error: 'E }
type Pair<'A, 'B> = Pair { first: 'A, second: 'B }
```

Apply type arguments with angle brackets:

```klein
x: Option<Num> = Some(42)
y: Result<String, Error> = Ok("hello")
```

### Constructors as First-Class Functions

Constructors are functions and can be passed around:

```klein
nums.map(Some)           # List<Num> -> List<Option<Num>>
nums.map(Money)          # List<Num> -> List<Money>
```

## Nominal vs Structural Interop

A nominal type is a subtype of its structural equivalent:

```klein
type Person = Person { name: String, age: Num }

# Person <: { name: String, age: Num } <: { name: String }
```

This means nominal types can be passed where structural types are expected:

```klein
fun greet(r: { name: String }): String = "Hello, ${r.name}"

person = Person("Alice", 30)
greet(person)  # works! Person <: { name: String }
```

### Nested Subtyping

Subtyping composes through nesting:

```klein
type Address = Address { city: String, zip: String }
type Person = Person { name: String, address: Address }

fun getCity(r: { address: { city: String } }): String = r.address.city

person = Person("Alice", Address("NYC", "10001"))

getCity(person)  # Works!

# Because:
# Person <: { name: String, address: Address }
# Address <: { city: String, zip: String } <: { city: String }
```

### One-Directional

Subtyping only works from nominal to structural, not the reverse:

```klein
type CustomerId = CustomerId { value: Num }

fun getCustomer(id: CustomerId): Customer = ...

getCustomer(CustomerId(42))   # Works
getCustomer(42)                # Error: Num is not CustomerId
```

This lets you enforce domain boundaries:

```klein
type CustomerId = CustomerId { value: Num }
type OrderId = OrderId { value: Num }

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

Constructors are called positionally:

```klein
# Bare constructors
color = Red
result = None

# With parameters
person = Person("Alice", 30)
result = Ok(42)
```

### Pattern Matching

```klein
match color
  Red -> "stop"
  Yellow -> "slow"
  Green -> "go"

match result
  Ok(value) -> value
  Err(error) -> handleError(error)

match person
  Person(name, age) -> "Hello, ${name}"
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

## Type Checking

Klein checks types **locally and bidirectionally**: every expression is either *synthesized* (its type produced bottom-up) or *checked* against an expected type pushed in from context. There is no global inference and no constraint solver.

The rule of thumb: **annotate signatures, infer interiors.** Function parameters carry types; the body, local bindings, and branch results are inferred from there.

```klein
fun double(x: Num) = x * 2             # return inferred: Num
fun identity(x: 'A) = x                # return inferred: 'A
fun greet(p: { name: String }) = "Hi, ${p.name}"
```

A parameter's annotation may be omitted only when the expected type comes from context — e.g. a lambda passed where the function type is already known, or a rule checked against a host-provided signature. See the spec for exactly where annotations are required.

### Generics

A type variable in a signature is **implicitly universally quantified** over that definition — no `<T>` declaration on functions:

```klein
fun identity(x: 'A): 'A = x            # ∀A. A -> A
fun map(f: 'A -> 'B, xs: List<'A>): List<'B> = ...
```

Inside the body a type variable is **rigid** (opaque — passable, not inspectable). At a call site it is instantiated by matching the arguments against the declared parameters. A variable appearing only in the return type is resolved from the expected type at the call (return-type polymorphism).

### Recursion

A recursive function (self- or mutually-recursive) must **declare its return type** — there is no fixpoint to infer it from:

```klein
fun fib(n: Num): Num = if n < 2 then n else fib(n - 1) + fib(n - 2)
```

### No anonymous unions or intersections

Klein has **no `&` / `|` type connectives.** The two needs they would serve are met differently:

- **"either A or B"** → a nominal sum (`type`), which carries a tag for pattern matching.
- **"both A and B"** → bounded polymorphism (planned): a type variable with declared bounds.

```klein
type NumOrString = N { value: Num } | S { value: String }

fun process(x: NumOrString): Result = match x
  N(value) -> ...
  S(value) -> ...
```

`Optional` (`T?`) is the one built-in tagged union. See [spec §8](./spec/bidirectional-checking.md) for why this split is principled — and why a first-class `A & B` is a *deferred candidate*, not a flat no.

### Recursive data

There are no inferred recursive types (no `as 'A` notation). Recursive data structures use nominal types:

```klein
type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
type Tree<'A> = Node { value: 'A, left: Tree<'A>, right: Tree<'A> } | Leaf
```

## Summary Table

| Construct | Syntax | Example |
|-----------|--------|---------|
| Primitive | `Name` | `Num`, `String`, `Bool` |
| Type variable | `'Name` | `'A`, `'T`, `'Key` |
| Record (structural) | `{ field: Type }` | `{ name: String, age: Num }` |
| Function type (1 param) | `A -> B` | `Num -> Num` |
| Function type (n params) | `(A, B) -> C` | `(Num, Num) -> Num` |
| Function type (0 params) | `() -> A` | `() -> Num` |
| Single-constructor type | `type N = N { ... }` | `type Money = Money { value: Num }` |
| Sum type | `type N = A \| B` | `type Color = Red \| Green` |
| Constructor with params | `Name { fields }` | `Ok { value: 'T }` |
| Bare constructor | `Name` | `None`, `Nil` |
| Type parameters | `Name<'A, 'B>` | `Option<'A>`, `Result<'T, 'E>` |
| Type application | `Name<Type>` | `Option<Num>`, `List<String>` |
| Positional construction | `Name(args)` | `Person("Alice", 30)` |
| Tuple type | `(A, B)` | `(String, Num)` |
| Tilde transform | `f~` | `process~ : { name: String } -> R` |

## References

- Dunfield, J. & Krishnaswami, N. (2021). "Bidirectional Typing" — the checking model Klein now uses
- Parreaux, L. (2020). "The Simple Essence of Algebraic Subtyping" — the prior (SimpleSub) approach, since retired
- Dolan, S. & Mycroft, A. (2017). "Polymorphism, Subtyping, and Type Inference in MLsub"
