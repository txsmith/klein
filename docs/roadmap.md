# Klein Roadmap

A look ahead — how Klein evolves from here toward the full language. The detailed
build/teardown plan for the type checker lives in
[plans/path-g-roadmap.md](./plans/path-g-roadmap.md).

## Done so far

- **Lexer & parser** — expressions, lambdas, records, if/then/else, function
  definitions, type annotations.
- **Type definitions** — constructors, sum types, type parameters, variance
  inference, nominal subtyping (`Money <: { value: Num }`).
- **Type checking** — a SimpleSub-style inferencer exists but is **being replaced**
  (see Phase 1 and [adopt-path-g](./decisions/2026-06-24-adopt-path-g.md)).

## Phase 1 — Type-checker rewrite (Path G) ← next

Replace global SimpleSub inference with **local bidirectional checking**: annotate
signatures, infer interiors; keep structural + nominal subtyping; delete the
constraint solver and simplifier. Generics by implicit quantification; joins
resolve to a nominal supertype or error; bounded polymorphism for "both"-ness.

Full milestones, test strategy, and doc updates:
[plans/path-g-roadmap.md](./plans/path-g-roadmap.md).

## Phase 2 — Pattern Matching

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
| First-class intersection | `A & B` everywhere — deferred Path G candidate (see spec §8) |

## Phase 5 — Execution

| Item | Notes |
|------|-------|
| Interpreter | |
| Effect system | suspendable effects |
