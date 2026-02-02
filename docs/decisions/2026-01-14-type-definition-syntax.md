# Type Definition Syntax

**Date:** 2026-01-14

## Context

Klein needs syntax for defining nominal types with constructors. The design must:
- Support single-constructor types (wrapper types, domain types)
- Support sum types (enums with data)
- Allow type parameters (generics)
- Make constructors first-class functions
- Reflect that nominal types subsume their structural record equivalents


## Decision

### Type Definitions

Type definitions use braces for constructor fields:

```klein
type Money = Money { value: Num }
type Person = Person { name: String, age: Num }
type Option<'A> = Some { value: 'A } | None
type Result<'T, 'E> = Ok { value: 'T } | Err { error: 'E }
type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
```

### Type Parameters

Type parameters use angle brackets `<>`:

```klein
type Option<'A> = ...
type Result<'T, 'E> = ...
```

Type application also uses angle brackets:

```klein
x: Option<Num> = Some(42)
y: List<String> = Nil
```

### Type Variables

Type variables use a tick prefix with uppercase letters:

```klein
'A, 'B, 'T, 'E, 'Key, 'Value
```

The rule: **uppercase = concrete type, tick + uppercase = type variable**.

### Construction

Constructors are called positionally with parentheses:

```klein
person = Person("Alice", 30)
result = Ok(42)
color = Red
```

## Rationale

### Why braces for constructor fields?

Nominal types subsume their structural record equivalents (`Person <: { name: String, age: Num }`). Using braces in the definition mirrors this relationship:

```klein
type Person = Person { name: String, age: Num }
```

The braces signal that a `Person` value can be used wherever a `{ name: String, age: Num }` record is expected.

### Why angle brackets for type parameters?

| Option | Example | Why Not |
|--------|---------|---------|
| Parentheses `()` | `Option(a)` | Conflicts with function calls: `Some(42)` is construction, `Option(Num)` would be type application |
| Brackets `[]` | `Option[a]` | Reserved for list literals: `[1, 2, 3]` |
| **Angle brackets `<>`** | `Option<'A>` | **Clear, no conflicts, familiar from Java/TypeScript/Rust** |

### Why tick-prefixed type variables?

Distinguishes type variables from concrete types at a glance:

| Syntax | Meaning |
|--------|---------|
| `String` | Concrete type |
| `'A` | Type variable |
| `List<'A>` | Generic type with type variable |
| `List<String>` | Concrete instantiation |

This is similar to OCaml/SML's `'a` but uses uppercase to match Klein's convention that types are capitalized.

### Why positional construction?

Klein uses positional function arguments (see [positional-function-syntax.md](./2026-01-09-positional-function-syntax.md)). Constructors are first-class functions, so they follow the same convention:

```klein
nums.map(Some)           # List<Num> -> List<Option<Num>>
nums.map(Money)          # List<Num> -> List<Money>
```

The tilde operator (`~`) can transform constructors to accept records when needed (not yet implemented).

## Alternatives Considered

### Parentheses for constructor fields

```klein
type Person = Person(name: String, age: Num)
```

Rejected because it doesn't visually connect to the structural record type that the nominal type subsumes.

### Lowercase type variables

```klein
type Option<a> = Some { value: a } | None
```

Rejected because Klein capitalizes all types. Using `a` for a type variable when `String` is a concrete type creates inconsistency.

### ML-style type parameters

```klein
type 'A Option = Some { value: 'A } | None
```

Rejected in favor of the more common trailing parameter list style used by most modern languages.

## Future: Structural Calling Syntax

Currently, constructors (and all functions) are called positionally:

```klein
person = Person("Alice", 30)
process("Alice", 30)
```

A future enhancement will allow **structural calling** using braces:

```klein
person = Person { name = "Alice", age = 30 }
process { name = "Alice", age = 30 }
```

### How It Would Work

Braces after a function/constructor name spread a record into positional arguments:

```klein
fun process(name: String, age: Num): Decision = ...

process("Alice", 30)              # positional
process { name = "Alice", age = 30 }  # structural (sugar)
```

Both forms are equivalent. The structural form desugars to positional calling with fields matched by name.

### The Tilde Operator (~)

The `~` operator transforms a positional function into one that accepts a record:

```klein
process  : (String, Num) -> Decision
process~ : { name: String, age: Num } -> Decision
```

This is useful with higher-order functions:

```klein
people.map(process~)    # process~ accepts records directly
people.filter(isValid~)
```

### Why Defer This?

1. **Positional calling works now** — No blocking need for structural syntax
2. **Parser complexity** — Need to distinguish `f { ... }` (structural call) from `f` followed by `{ ... }` (two expressions)
3. **Type inference implications** — Need to ensure it integrates cleanly with SimpleSub
4. **Tilde not implemented** — The `~` operator is the principled solution; structural call syntax is sugar on top

### Design Symmetry

When implemented, the syntax will be symmetric:

| Definition | Positional Call | Structural Call |
|------------|-----------------|-----------------|
| `type Person = Person { name: String, age: Num }` | `Person("Alice", 30)` | `Person { name = "Alice", age = 30 }` |
| `fun greet(name: String, age: Num) = ...` | `greet("Alice", 30)` | `greet { name = "Alice", age = 30 }` |

The braces in the type definition hint that structural construction is possible.

## Consequences

**Positive:**
- Clear visual distinction between type definitions and expressions
- Braces reflect nominal-structural subtyping relationship
- Angle brackets avoid ambiguity with calls and lists
- Tick-prefixed variables are immediately recognizable
- Constructors work naturally as first-class functions

**Negative:**
- Two bracket styles (`<>` for types, `{}` for fields) adds syntax to learn
- Tick prefix requires extra character for type variables

### Validation Rules

The following are enforced at type definition time:

- **No shadowing primitives.** `type Num = Num { x: Num }` is an error. Constructor or type names cannot reuse `Num`, `String`, `Bool`, or `Unit`.
- **No duplicate constructors.** Constructor names must be unique across all type definitions. Two types cannot both define a `Foo` constructor.
- **No duplicate type names.** Each `type` name must be unique.

## Grammar

```
TypeDef        = 'type' TypeName TypeParams? '=' Constructors
TypeParams     = '<' TypeVar % ',' '>'
TypeVar        = '\'' UpperIdent
Constructors   = Constructor % '|'
Constructor    = UpperIdent ConstructorParams?
ConstructorParams = '{' FieldDecl % ',' '}'
FieldDecl      = LowerIdent ':' Type

TypeArgs       = '<' Type % ',' '>'
AppliedType    = TypeAtom TypeArgs?
```

See [grammar.md](../grammar.md) for the complete type grammar.
