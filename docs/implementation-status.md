# Implementation Status

**Current state:** The type system runs on **Operation Bidi** — local bidirectional checking (see [decisions/2026-06-24-adopt-operation-bidi.md](decisions/2026-06-24-adopt-operation-bidi.md)). The checker (`klein.check`, behind both `Klein.check` and the `check` CLI command) covers the bidirectional core, concrete subtyping, rank-1 generics, and branch joins. Type annotations (`fun f(x: Num)`, return types, `x: Num = 1`, `T?`) are parsed and checked. The legacy SimpleSub engine is deleted. No interpreter.

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
[type-system.md](type-system.md) for design,
[spec/bidirectional-checking.md](spec/bidirectional-checking.md) for the checking contract, and
[decisions/2026-06-24-adopt-operation-bidi.md](decisions/2026-06-24-adopt-operation-bidi.md) for the decision record.
The `klein.check` checker is the only engine — the SimpleSub machinery is deleted. There is a
single type hierarchy (`klein.check.Type`, printed directly) and a single typed error
hierarchy (`klein.check.TypeError`).

### Operation Bidi checker — complete

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
| Pattern matching | `match` with bare arms, record destructuring, constructor binders (`Dog d`), exhaustiveness + reachability as hard errors — see [spec/pattern-matching.md](spec/pattern-matching.md) |
| Destructuring bindings | `{ name, age } = person`, irrefutable-only — see [spec/destructuring-bindings.md](spec/destructuring-bindings.md) |

### Operation Bidi checker — ahead

| Feature | Notes |
|---------|-------|
| Declared bounds | `where 'T <: B` — deferred |
| Nested patterns | `Cons { head = Circle { radius } }` — deferred (needs usefulness-matrix exhaustiveness) |
| Kleene types | `T*`, `T+` (experimental; `T?` done) |

### Known gaps (deferred checker bugs)

To fix under a dedicated "polymorphism bugs" pass:

1. **Branch join over-rejects when neither branch subsumes the other.** `if c then q else { tag = 1, extra = true }`, with `q : Phantom<'A>` (interface `{ tag: Num }`), rejects — yet the join `{ tag: Num }` plainly exists (the all-monomorphic version gives it). `synthIfThenElse` grounds a polymorphic branch by instantiating it to a *subtype of the whole* other branch (`groundPolyBranch` → `solveQuantified`), so it only produces joins where one branch subsumes the other, never a genuine third common supertype. Fix: match the poly branch against the *field intersection* — a lenient `generate` that skips fields the poly lacks, kept separate from the strict call-site `generate` where a missing field is a real error — solve the variables (defaulting untouched ones), then `lub`. The supertype comes from `lub`, not the solver.

2. **A phantom / unpinned type variable collapses to `Nothing` in synth mode.** `Phantom(7)` with no demand → `Nothing` plus a misleading `'Nothing' cannot be used as 'Any'`. `inferApply` always instantiates the callee scheme and, when a variable is left unconstrained, defaults it to failure rather than propagating a scheme (propagating would be let-generalization at an application result, which Operation Bidi declines). Needs a decision on intended behavior — a clear "cannot infer `'A`" error, or a benign default — not silent collapse. The annotated form already works: `q: Phantom<'A> = Phantom(4)` generalizes at the binder.

## Interpreter

Not started. See [dsl-project-summary.md](dsl-project-summary.md) for design.

| Feature | Notes |
|---------|-------|
| Expression evaluation | Pure interpreter |
| Effect system | Suspendable effects, yield/resume |
| State serialization | Pause and persist execution |
