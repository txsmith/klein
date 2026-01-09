# Klein Roadmap

Units of work to evolve Klein from current state to full language spec.

## Current State

Lexer and parser handle expressions, lambdas, record literals, if/then/else, and function definitions. No type system yet.

**Already using new syntax:**
- Comments: `#`
- Record literals: `{ field = value }`
- Lambdas: `|x -> expr|`, `|.field|`, `|.|`

## Work Units

### Phase 1: Syntax Migration

| Item | Status | Notes |
|------|--------|-------|
| Function params `()` → `{}` | TODO | `fun f(x)` → `fun f { x }` |
| Function calls: juxtaposition | TODO | `f(x)` → `f x` or `f { field = x }` |

See `syntax-migration-plan.md` for detailed implementation plan.

** NEW INSIGHT ** We'll keep function syntax the same and instead instroduce new syntax for unrolling a record into function application arguments

### Phase 2: New Expression Features

| Item | Status | Notes |
|------|--------|-------|
| Tuple accessors `._1`, `._2` | TODO | New field access pattern |
| Ranges `..` and `..<` | TODO | Lexer done (`DOTDOT`), parser TODO |
| Arrays `[1, 2, 3]` | TODO | Lexer done, parser TODO |
| Match expressions | TODO | Value and condition matching |
| For comprehensions | TODO | `for x in xs yield expr` |

### Phase 3: Type Definitions

| Item | Status | Notes |
|------|--------|-------|
| Record types | TODO | `type R = { field: Type }` |
| Enum/sum types | TODO | `type E = A \| B { field: T }` |
| Type aliases | TODO | `type Money = Double` |
| Function types | TODO | `{ x: Int } -> Int` |
| Generics `List(T)` | TODO | Parentheses, not angle brackets |
| Type annotations | TODO | `fun f { x: Int }: Int` |

### Phase 4: Advanced Features

| Item | Status | Notes |
|------|--------|-------|
| Extension methods | TODO | `on` keyword for method receiver |
| Modules | TODO | `module Name` + imports |
| Function fields in records | TODO | `type T = { fun f { x }: R }` |

### Phase 5: Type System

| Item | Status | Notes |
|------|--------|-------|
| Type inference | TODO | See `type-system.md` |
| Type checking | TODO | |
| Kleene types (`?`, `*`, `+`) | TODO | See `kleene-types-experimental.md` |

### Phase 6: Execution

| Item | Status | Notes |
|------|--------|-------|
| Interpreter | TODO | |
| Effect system | TODO | See `error-handling-design.md` |
