# Klein Roadmap

Units of work to evolve Klein from current state to full language spec.

## Current State

Lexer and parser handle expressions, lambdas, record literals, if/then/else, and function definitions. No type system yet.

**Current syntax:**
- Comments: `#`
- Function definitions: `fun name(params) = body`
- Function calls: `name(args)`
- Record literals: `{ field = value }`
- Lambdas: `|x -> expr|`, `|.field|`, `|.|`

## Design Decisions

**SimpleSub-style type system** — Using subtyping with type inference (à la SimpleSub/MLSub) rather than row polymorphism. Records have width subtyping: `{ x, y }` is a subtype of `{ x }`.

**Positional function calling** — Functions use standard positional arguments. The `~` operator transforms positional functions to accept records when needed.

See `calling-conventions.md` for details on the design rationale.

## Work Units

### Phase 1: Type System

Build SimpleSub-style type inference before adding more syntax.

| Item | Status | Notes |
|------|--------|-------|
| Type representation | TODO | Internal types for inference |
| Subtyping | TODO | Width subtyping for records |
| Type inference | TODO | Bidirectional or constraint-based |
| Type annotations | TODO | `fun f(x: Int): Int`, `x: T = ...` |
| Primitive types | TODO | `Int`, `Double`, `String`, `Bool` |
| Function types | TODO | `Int -> Int`, `(Int, Int) -> Int` |
| Record types | TODO | `{ name: String, age: Int }` |

### Phase 2: Pattern Matching

Add pattern matching as the next syntax feature.

| Item | Status | Notes |
|------|--------|-------|
| Match keyword | TODO | `match expr` with arms |
| Literal patterns | TODO | `42`, `'hello'`, `true` |
| Variable patterns | TODO | `x` binds the value |
| Record patterns | TODO | `{ name, age }` destructuring |
| Wildcard | TODO | `_` matches anything |
| Guards | TODO | `pattern if cond -> expr` |
| Exhaustiveness | TODO | Warn on non-exhaustive matches |

### Phase 3: Type Definitions

| Item | Status | Notes |
|------|--------|-------|
| Record types | TODO | `type R = { field: Type }` |
| Enum/sum types | TODO | `type E = A \| B { field: T }` |
| Type aliases | TODO | `type Money = Double` |
| Generics `List(T)` | TODO | Parentheses, not angle brackets |

### Phase 4: Additional Syntax (deferred)

Lower priority. Add as needed.

| Item | Status | Notes |
|------|--------|-------|
| Arrays `[1, 2, 3]` | TODO | Lexer done, parser TODO |
| Ranges `..` and `..<` | TODO | Lexer done (`DOTDOT`), parser TODO |
| Tuple accessors `._1` | TODO | New field access pattern |
| For comprehensions | TODO | `for x in xs yield expr` |
| Tilde operator `~` | TODO | Record-to-positional transform |
| Record spread `...` | TODO | `{ ...r, x = 1 }` |

### Phase 5: Advanced Features

| Item | Status | Notes |
|------|--------|-------|
| Extension methods | TODO | `on` keyword for method receiver |
| Modules | TODO | `module Name` + imports |
| Kleene types | TODO | `T?`, `T*`, `T+` (experimental) |

### Phase 6: Execution

| Item | Status | Notes |
|------|--------|-------|
| Interpreter | TODO | |
| Effect system | TODO | Suspendable effects |
