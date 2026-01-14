# Implementation Status

**Current state:** Parser complete for core expression features. Type inference implemented (SimpleSub). No interpreter.

## Parser

### Complete

| Feature | Notes |
|---------|-------|
| Int literals | `42`, `123` |
| Double literals | `3.14` |
| String literals | `"hello"` with escapes `\"`, `\\`, `\n`, `\t` |
| Bool literals | `true`, `false` |
| Identifiers | `foo`, `myVar` |
| Binary operators | `+`, `-`, `*`, `/`, `%`, `==`, `!=`, `<`, `<=`, `>`, `>=`, `and`, `or` |
| Unary operators | `-x`, `not x` |
| Parenthesized expressions | `(a + b)` |
| Function definitions | `fun f(x, y) = ...` |
| Function application | `f(x, y)` |
| Lambdas | `\|x -> expr\|`, `\|x, y -> expr\|` |
| Implicit param | `\|.\|`, `\|.field\|`, `\|. > 100\|` |
| Field access | `record.field` |
| Record literals | `{ x = 1, y = 2 }` |
| Record shorthand | `{ name }` = `{ name = name }` |
| Trailing commas | `{ x = 1, }` |
| If-then-else | `if cond then a else b` |
| Val bindings | `x = expr` |
| Blocks | Indentation-based |
| Comments | `# comment` |

### Partial (lexer only)

| Feature | Token | Notes |
|---------|-------|-------|
| Arrays | `LBRACKET`, `RBRACKET` | `[1, 2, 3]` - parser TODO |
| Ranges | `DOTDOT` | `1..10` - parser TODO |
| Colon | `COLON` | For type annotations - parser TODO |

### Not Started

**Expression features:**

| Feature | Notes |
|---------|-------|
| Record spread | `{ ...r, x = 1 }` - no `...` token |
| Tuple literals | `(a, b)` - parens exist but parsed as grouping |
| Tuple accessors | `pair._1` |
| String interpolation | `"Hello ${name}"` |
| String concatenation | `++` operator |
| Match expressions | `match x` with arms |
| For comprehensions | `for x in xs yield expr` |
| Tilde operator | `f~(record)` |
| Operators as values | `nums.fold(0, (+))` |

**Type annotations:**

| Feature | Notes |
|---------|-------|
| Parameter types | `fun f(x: Num)` |
| Return types | `fun f(x): Num` |
| Val types | `x: Num = 1` |

**Definitions:**

| Feature | Notes |
|---------|-------|
| Type definitions | `type Person = Person { name: String }` |
| Sum types | `type Color = Red \| Green \| Blue` |
| Type aliases | `type Money = Num` |
| Extension methods | `fun f(on x: T)` |
| Modules | `module Name` |
| Imports | `import Module._` |

**Error handling:**

| Feature | Notes |
|---------|-------|
| Error keyword | `error "message"` |
| Recover | `\|expr\|.recover` |

## Type System

See [type-system.md](type-system.md) for design and [simplesub-type-inference.md](decisions/2026-01-14-simplesub-type-inference.md) for implementation decisions.

### Complete

| Feature | Notes |
|---------|-------|
| Type inference | SimpleSub algorithm with subtyping |
| Primitive types | `Num`, `String`, `Bool`, `Unit` |
| Function types | `(a) -> b`, `(a, b) -> c` |
| Record types | `{ name: String, age: Num }` |
| Width subtyping | `{ a, b } <: { a }` |
| Recursive types | `{ head: Num, tail: a } as a` |
| Union/intersection | `a \| b`, `a & b` |
| Type simplification | Canonicalization of recursive types |

### Not Started

| Feature | Notes |
|---------|-------|
| Sum types | `Ok { value: t } \| Err { error: e }` |
| Generics | `List<'A>`, `Option<'A>` |
| Nominal types | `type Person = Person { ... }` creates nominal type |
| Kleene types | `T?`, `T*`, `T+` (experimental) |

## Interpreter

Not started. See [dsl-project-summary.md](dsl-project-summary.md) for design.

| Feature | Notes |
|---------|-------|
| Expression evaluation | Pure interpreter |
| Effect system | Suspendable effects, yield/resume |
| State serialization | Pause and persist execution |
