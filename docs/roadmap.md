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

**Positional function calling** — Functions use standard positional arguments like most languages. The `~` operator transforms positional functions to accept records when needed.

See `calling-conventions.md` for details on the design rationale.

## Work Units

### Phase 1: Expression Features

| Item | Status | Notes |
|------|--------|-------|
| Tuple accessors `._1`, `._2` | TODO | New field access pattern |
| Ranges `..` and `..<` | TODO | Lexer done (`DOTDOT`), parser TODO |
| Arrays `[1, 2, 3]` | TODO | Lexer done, parser TODO |
| Match expressions | TODO | Value and condition matching |
| For comprehensions | TODO | `for x in xs yield expr` |
| Tilde operator `~` | TODO | Record-to-positional transform |

### Phase 2: Type Definitions

| Item | Status | Notes |
|------|--------|-------|
| Record types | TODO | `type R = { field: Type }` |
| Enum/sum types | TODO | `type E = A \| B { field: T }` |
| Type aliases | TODO | `type Money = Double` |
| Function types | TODO | `Int -> Int`, `(Int, Int) -> Int` |
| Generics `List(T)` | TODO | Parentheses, not angle brackets |
| Type annotations | TODO | `fun f(x: Int): Int` |

### Phase 3: Advanced Features

| Item | Status | Notes |
|------|--------|-------|
| Extension methods | TODO | `on` keyword for method receiver |
| Modules | TODO | `module Name` + imports |
| Function fields in records | TODO | `type T = { f: A -> B }` |

### Phase 4: Type System

| Item | Status | Notes |
|------|--------|-------|
| Type inference | TODO | See `type-system.md` |
| Type checking | TODO | |
| Kleene types (`?`, `*`, `+`) | TODO | See `kleene-types-experimental.md` |

### Phase 5: Execution

| Item | Status | Notes |
|------|--------|-------|
| Interpreter | TODO | |
| Effect system | TODO | See `error-handling-design.md` |
