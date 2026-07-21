# Klein Roadmap

A look ahead — how Klein evolves from here toward the full language.

## Done so far

- **Lexer & parser** — expressions, lambdas, records, if/then/else, function
  definitions, type annotations.
- **Type definitions** — constructors, sum types, type parameters, variance
  inference, nominal subtyping (`Money <: { value: Num }`).
- **Type checking** — **Operation Bidi**, local bidirectional checking: annotate
  signatures, infer interiors; structural + nominal subtyping; generics by
  implicit quantification; joins resolve to a nominal supertype or error. The
  SimpleSub inference engine is deleted. Deferred: declared bounds
  (`where 'T <: B`) for "both"-ness. See
  [adopt-operation-bidi](./decisions/2026-06-24-adopt-operation-bidi.md) and
  [implementation-status](./implementation-status.md).

## Phase 2 — Pattern Matching

**Done** (2026-07-18, `pattern-matching` branch) — parser + checker per
[spec/pattern-matching.md](spec/pattern-matching.md). Notable deltas from the table
below: arms are bare (no `|` marker), destructuring is record-only (`Ok { value }`,
no positional `Some(x)`), a matched value is named with a constructor binder
(`Dog d`) or variable pattern — there is no scrutinee flow-narrowing — and
exhaustiveness is a hard type error. Nested patterns deferred. Evaluator lands
with the interpreter.

| Item | Notes |
|------|-------|
| Match keyword | `match expr` with arms |
| Literal patterns | `42`, `"hello"`, `true` |
| Variable patterns | `x` binds the value |
| Record patterns | `{ name, age }` destructuring |
| Constructor patterns | `Some(x)`, `None` |
| Wildcard | `_` matches anything |
| Guards | `pattern if cond -> expr` |
| Exhaustiveness | over a sum's constructors (closed, no negation) |

## Phase 3 — Additional Syntax

Lower priority; add as needed.

| Item | Notes |
|------|-------|
| Arrays `[1, 2, 3]` | lexer done, parser TODO |
| Ranges `..` / `..<` | lexer done (`DOTDOT`), parser TODO |
| Tuple accessors `._1` | new field-access pattern |
| For comprehensions | `for x in xs yield expr` |
| Tilde operator `~` | `f~` transforms to record-accepting |
| Record spread `...` | `{ ...r, x = 1 }` — also the basis for tag-preserving extension |

## Phase 4 — Advanced Features

| Item | Notes |
|------|-------|
| Extension methods | `on` keyword for method receiver |
| Modules | `module Name` + imports |
| First-class intersection | `A & B` everywhere — deferred Operation Bidi candidate (see spec §8) |

## Phase 5 — Execution

| Item | Notes |
|------|-------|
| Interpreter | |
| Effect system | suspendable effects |
