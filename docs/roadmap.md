# Klein Roadmap

The global roadmap: the language and the features around it. Current status in detail is
[implementation-status.md](./implementation-status.md); the host-boundary machinery — editions,
the effect log, reconciliation — has its own dependency map in
[host-integration-roadmap.md](./host-integration-roadmap.md).

## Done so far

Lexer and parser; type definitions with variance inference and nominal subtyping; type checking (annotate signatures, infer interiors); pattern matching and destructuring
per [spec/pattern-matching.md](spec/pattern-matching.md), nested patterns deferred; Core IR and
the CESK machine, with suspendable host calls; capability contracts, releases, and execution
against a host — the host roadmap's Done section tells that half.

## Additional syntax

Lower priority; add as needed.

| Item | Notes |
|------|-------|
| Arrays `[1, 2, 3]` | lexer done, parser TODO |
| Ranges `..` / `..<` | lexer done (`DOTDOT`), parser TODO |
| Tuple accessors `._1` | new field-access pattern |
| For comprehensions | `for x in xs yield expr` |
| Tilde operator `~` | `f~` transforms to record-accepting |
| Record spread `...` | `{ ...r, x = 1 }` — also the basis for tag-preserving extension |

## Advanced features

| Item | Notes |
|------|-------|
| Extension methods | `on` keyword for method receiver |
| Modules | `module Name` + imports |
| First-class intersection | `A & B` everywhere — deferred Operation Bidi candidate (see spec §8) |
| Nested patterns | `Cons { head = Circle { radius } }` — needs usefulness-matrix exhaustiveness |

## Migration toolkit

What a host-written migration is written against, per the
[replay-order ADR](./decisions/2026-08-26-replay-is-ordinal-migration-is-host-policy.md) — Klein
ships primitives, the migration function is the org's. Four pieces: a policy seam on `run` — a
third answer source beside the log and the handlers, consulted for unmatched questions; a keyed
view over the ordered log, looking entries up by name and argument values instead of by counting;
the two lists — questions the new edition asked that the log could not answer, and answers the log
held that it never asked for — which is the divergence check generalised from an error into a
worklist; and derived answers with provenance, so an answer a migration synthesised from logged
data is distinguishable in the log from one the host actually gave — the one `Turn` schema
addition the toolkit requires. Needs `Parked` and edition serialization from the host map.

## Editor + tooling

The standalone-components tier: a rule editor embedding the checker — the library compiles to JS,
so check-as-you-type runs in the browser against the real engine — with the release picker
host-integration.md §Edition describes; diagnostics rendered from spans and severity classes; a
contract browser over declarations and releases; and an effect-log viewer, because the log is data
and a run's history deserves a UI. Components stay components per the library tenet: the core
never depends on any of this, and an org can replace or skip each piece. Lands after the library
surface settles; diagnostic severity is the one library-side prerequisite.

## Unphased

| Item | Notes |
|------|-------|
| A proper README / tutorial | A narrative "what is Klein, by example" against the real language — the lending walkthrough as the spine. Replaces the deleted dsl-project-summary.md, whose vision-document role it inherits; the old text is in git history. |
| Evaluation spec | docs/spec/evaluation.md — the machine's semantics in writing: value identity (`-0.0`, NaN), host-call order as program order (per the replay-order ADR), arithmetic. Replay's determinism test is gated on it. |
