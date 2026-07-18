# Implementation Status

**Current state:** The type system runs on **Operation Bidi** — local bidirectional checking (see [plans/operation-bidi-roadmap.md](plans/operation-bidi-roadmap.md)). The checker (`klein.check`, behind both `Klein.check` and the `check` CLI command) covers the bidirectional core, concrete subtyping, rank-1 generics, and branch joins (roadmap M2–M5, M7 cutover and M8 teardown done). Type annotations (`fun f(x: Num)`, return types, `x: Num = 1`, `T?`) are parsed and checked. The legacy SimpleSub engine is deleted. No interpreter.

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
| Parameter types | `fun f(x: Num)` |
| Return types | `fun f(x): Num` |
| Val types | `x: Num = 1` |
| Type variables | `'T` in signatures |
| Nullable types | `T?` |

### Partial (lexer only)

| Feature | Token | Notes |
|---------|-------|-------|
| Arrays | `LBRACKET`, `RBRACKET` | `[1, 2, 3]` - parser TODO |
| Ranges | `DOTDOT` | `1..10` - parser TODO |

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

**Definitions:**

| Feature | Notes |
|---------|-------|
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

The type system runs on **Operation Bidi** (local bidirectional checking). See
[type-system.md](type-system.md) for design and
[plans/operation-bidi-roadmap.md](plans/operation-bidi-roadmap.md) for the milestone plan.
The `klein.check` checker is the only engine: cutover (M7) and teardown (M8) are done, and
the SimpleSub machinery is deleted. There is a single type hierarchy (`klein.check.Type`,
printed directly) and a single typed error hierarchy (`klein.check.TypeError`).

### Operation Bidi checker — complete (roadmap M2–M5)

| Feature | Notes |
|---------|-------|
| Bidirectional checking | `synth` / `check` over fully-annotated functions |
| Primitive types | `Num`, `String`, `Bool`, `Unit` |
| Function types | `(a) -> b`, `(a, b) -> c` |
| Record types | `{ name: String, age: Num }`, width/depth subtyping |
| Type annotations | Params, return types, `x: T = e`, ascription `(e : T)` |
| Rank-1 generics | `'T` in signatures → rigid skolem, instantiated at demand points |
| Type definitions | `type Option<'A> = Some { value: 'A } \| None` |
| Sum types | `type Color = Red \| Green \| Blue` |
| Nominal types | `type Person = Person { ... }` → `TRef`, variance inferred |
| Nominal → structural subtyping | `Dog <: { name: String }` |
| Constructor binding | First-class constructor functions |
| Inferred interfaces | Common fields across constructors (incompatible fields erased) |
| Optional types | `T?`, null safety, safe navigation `?.`, `NullNotAllowed` |
| Branch joins | `if`/`else` results join to a common supertype (`lub`), nominal join/meet by variance |

### Operation Bidi checker — ahead

| Feature | Notes |
|---------|-------|
| Declared bounds | `where 'T <: B` (roadmap M6 — deferred) |
| Pattern matching | `match x with \| Some v -> v \| None -> 0` |
| Kleene types | `T*`, `T+` (experimental; `T?` done) |

## Interpreter

Not started. See [dsl-project-summary.md](dsl-project-summary.md) for design.

| Feature | Notes |
|---------|-------|
| Expression evaluation | Pure interpreter |
| Effect system | Suspendable effects, yield/resume |
| State serialization | Pause and persist execution |
