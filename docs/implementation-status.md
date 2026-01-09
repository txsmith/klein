# Implementation Status

**Current state:** Parser complete for core expression features. No type system or interpreter.

## Parser

### Complete

| Feature | Notes |
|---------|-------|
| Int literals | `42`, `123` |
| Double literals | `3.14` |
| String literals | `'hello'` with escapes `\'`, `\\`, `\n`, `\t` |
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
| String interpolation | `'Hello ${name}'` |
| String concatenation | `++` operator |
| Match expressions | `match x` with arms |
| For comprehensions | `for x in xs yield expr` |
| Tilde operator | `f~(record)` |
| Operators as values | `nums.fold(0, (+))` |

**Type annotations:**

| Feature | Notes |
|---------|-------|
| Parameter types | `fun f(x: Int)` |
| Return types | `fun f(x): Int` |
| Val types | `x: Int = 1` |

**Definitions:**

| Feature | Notes |
|---------|-------|
| Type definitions | `type Person = { name: String }` |
| Sum types | `type Color = Red \| Green \| Blue` |
| Type aliases | `type Money = Double` |
| Extension methods | `fun f(on x: T)` |
| Modules | `module Name` |
| Imports | `import Module._` |

**Error handling:**

| Feature | Notes |
|---------|-------|
| Error keyword | `error 'message'` |
| Recover | `\|expr\|.recover` |

## Type System

Not started. See [type-system.md](type-system.md) for design.

| Feature | Notes |
|---------|-------|
| Type inference | Hindley-Milner |
| Type checking | Validate expressions against types |
| Record types | `{ name: String, age: Int }` |
| Function types | `Int -> Int`, `(Int, Int) -> Int` |
| Sum types | `Ok { value: t } \| Err { error: e }` |
| Generics | `List(a)`, `Option(a)` |
| Row polymorphism | `{ name: String, ...r }` |
| Nominal vs structural | `type Person = { ... }` creates nominal type |
| Kleene types | `T?`, `T*`, `T+` (experimental) |

## Interpreter

Not started. See [dsl-project-summary.md](dsl-project-summary.md) for design.

| Feature | Notes |
|---------|-------|
| Expression evaluation | Pure interpreter |
| Effect system | Suspendable effects, yield/resume |
| State serialization | Pause and persist execution |
