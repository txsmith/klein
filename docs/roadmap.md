# Klein Roadmap

Units of work to evolve Klein from current state to full language spec.

## Current State

Lexer and parser handle expressions, lambdas, record literals, if/then/else, and function definitions. SimpleSub type inference complete with recursive types and canonicalization.

## Work Units

### Phase 1: Type System ✓

SimpleSub-style type inference with subtyping.

| Item | Status | Notes |
|------|--------|-------|
| Type representation | Done | `SimpleType`, `CompactType`, `Type` |
| Subtyping | Done | Width subtyping for records |
| Type inference | Done | SimpleSub with bisubstitution |
| Primitive types | Done | `Num`, `String`, `Bool`, `Unit` |
| Function types | Done | `(a) -> b`, `(a, b) -> c` |
| Record types | Done | `{ name: String, age: Num }` |
| Recursive types | Done | `{ head: Num, tail: a } as a` |
| Union/intersection | Done | `a \| b`, `a & b` |
| Type annotations | TODO | `fun f(x: Int): Int`, `x: T = ...` |

### Phase 2: Type Definitions

Constructors are first-class functions with named parameters, called positionally or structurally.

```klein
type Money = Money(value: Num)
type Color = Red(intensity: Num) | Green(intensity: Num) | Blue(intensity: Num)
type Option('A) = Some(value: 'A) | None
type List('A) = Cons(head: 'A, tail: List('A)) | Nil
```

Nominal types subsume their structural equivalents: `Money <: { value: Num }`.



| Item | Status | Notes |
|------|--------|-------|
| Constructor definitions | TODO | `type T = C(field: Type)` |
| Sum types | TODO | `type T = A(...) \| B(...)` |
| Bare constructors | TODO | `None`, `Nil` (no params) |
| Type parameters | TODO | `type Option('A) = ...` |
| First-class constructors | TODO | `nums.map(Some)` |
| Nominal subtyping | TODO | `Money <: { value: Num }` |

### Phase 3: Pattern Matching

| Item | Status | Notes |
|------|--------|-------|
| Match keyword | TODO | `match expr` with arms |
| Literal patterns | TODO | `42`, `"hello"`, `true` |
| Variable patterns | TODO | `x` binds the value |
| Record patterns | TODO | `{ name, age }` destructuring |
| Constructor patterns | TODO | `Some(x)`, `None` |
| Wildcard | TODO | `_` matches anything |
| Guards | TODO | `pattern if cond -> expr` |
| Exhaustiveness | TODO | Warn on non-exhaustive matches |

### Phase 4: Additional Syntax (deferred)

Lower priority. Add as needed.

| Item | Status | Notes |
|------|--------|-------|
| Arrays `[1, 2, 3]` | TODO | Lexer done, parser TODO |
| Ranges `..` and `..<` | TODO | Lexer done (`DOTDOT`), parser TODO |
| Tuple accessors `._1` | TODO | New field access pattern |
| For comprehensions | TODO | `for x in xs yield expr` |
| Tilde operator `~` | TODO | `f~` transforms to record-accepting |
| Structural calls | TODO | `f { x = 1 }` desugars to `f~({ x = 1 })` |
| Record spread `...` | TODO | `{ ...r, x = 1 }`, `f { ...r }` |

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
