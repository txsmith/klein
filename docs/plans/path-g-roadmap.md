# Path G Roadmap — Local Bidirectional Type Checking

**Status:** Planning · **Started:** 2026-06-24 · **Branch:** `rewrite/type-checker`

This roadmap sequences Klein's move off SimpleSub-style global inference and onto
**Path G**: drop inferred polymorphism, keep structural + nominal subtyping, and
replace constraint-based inference with **local bidirectional checking** (annotate
signatures, infer interiors).

The *why* lives in the decision record —
[`2026-06-23-polarity-wall-and-type-system-direction.md`](../decisions/2026-06-23-polarity-wall-and-type-system-direction.md)
— and is not re-argued here. In one line: demanding subtyping **and** principal global inference forces first-class `&`/`|`, which forces either incompleteness or MLstruct's full Boolean-algebra engine; Path G keeps subtyping and gives up the global inference that created the wall. This document is the build/teardown plan.

## Guiding invariant

The surface language and the checker's output are **one language**: whatever the
checker prints must be writable, and whatever is writable must be checkable. Under
Path G this is easy to honor — types are *written and gated per site*, not emergent
from a solver — but every milestone should be checked against it.

## Two streams

The work is one part **build** (a new bidirectional checker) and one part
**teardown** (retiring the SimpleSub machinery). They interleave; the teardown is
gated entirely on the build proving itself.

- 🔨 **Build** — `synth`/`check`, concrete subtyping, generics, bounds, joins.
- 🧹 **Teardown** — delete the constraint solver (`Subtyping.kt`), the
  polarity-unaware flattening (`TypeComponents.kt`), the simplifier
  (`TypeSimplifier.kt`), and the `TVar`-bounds / coalescing / let-poly machinery.

The teardown target is ~1640 lines of the hardest code in the project, plus the
bound-propagation parts of `SimpleType.kt`. Most of the roadmap's risk is in the
*build*; most of its relief is in the *subtraction*.

## What we keep vs. supersede from the annotation work

The `rewrite/type-checker` branch starts from main, which now carries the
type-annotation work. Not all of it survives:

- ✅ **Keep (foundation):** parser/AST for `'T`, union/intersection type syntax,
  `where`-clauses; `TSkolem` / rigid type variables; the `TypeError` additions;
  the ADRs and companion docs; `AnnotationInferTest` (becomes the migration
  ledger — see M1).
- ♻️ **Supersede (scaffold):** the `Subtyping.kt` rigid-`TVar` OR-trial constraint
  logic and the `TVar`-bounds smuggling. These are deleted in M8. They remain in
  git history (and on the archived `type-annotations` branch), so removing them
  loses nothing.

---

## Milestones

Dependency spine: **M0 → M1, M0 → M2 → M3 → {M5 (joins), M4 → M6 (bounds)} → M7 →
M8**, with the checking-tests track trailing M2–M6. Joins (M5) is sequenced ahead
of bounds (M6) by priority — it gates usable `if`/`else` and depends on neither
generics nor bounds.

### M0 · Surface spec 🔨📄
The contract, code-free. Pins down:
- annotation grammar;
- **where annotations are required vs. inferred** — the line that sizes everything
  downstream (it determines the size of M1's REWRITE pile);
- the `'T` implicit-quantification rule (lexically-marked vars are ∀-quantified;
  unmarked-unknown names are errors);
- join policy for `if` (branch results resolve to a declared common supertype, or
  error — no silent `Any`);
- the no-anonymous-unions stance (unions are nominal/closed sums; no `&`/`|`
  connectives in core).

(`where`-bounds and `match` are deferred features, **not** part of the M0 surface
spec — they arrive with M6 and a later milestone respectively.)

*Depends on: nothing. This is the root.*
*Output: a short spec doc the rest of the roadmap checks against.*

### M1 · Test classification 🧹📄
Triage every existing test against the M0 spec into three buckets:
- **KEEP** — verdict/error tests (program → accept/reject/error). Engine-agnostic;
  these become the **M7 acceptance contract**.
- **REWRITE** — inferred-string tests. Each is a *decision*: does Path G still
  infer this, or does it now require an annotation? This is the migration ledger,
  led by `AnnotationInferTest` and the `*InferTest` files.
- **DELETE-AT-TEARDOWN** — machinery-internal suites that test the solver/simplifier
  directly: `TypeSimplifierTest`, `LubGlbSimplificationTest`, `LevelConstraintTest`,
  `ScopeGraphSccTest`. These die in M8; deleting them is correct, not lost coverage.

*Depends on: M0 (can't classify required-vs-inferred without the spec).*
*Output: per-file worklist + the precise definition of the M7 green bar →
[test-porting.md](./test-porting.md).*

### M2 · Bidirectional core 🔨
`synth(expr): Type` and `check(expr, expected): Unit`, mutually recursive.
Monomorphic, fully-annotated functions only — no type variables yet. Literals,
records, application, let, `if`.

*Depends on: M0. Can start alongside M1.*

### M3 · Concrete subtyping 🔨
`isSubtype(a, b)` over the **ground** type grammar: primitives, records
(width/depth), functions (variance), `TRef` nominal, `Optional`. No bounds, no
constraint propagation — this is what `check` calls.

*Depends on: M2 shape; parallelizable with M2.*

### M4 · Generics: implicit quantification 🔨
`'T` in a signature → rigid skolem (reuse `TSkolem`). Instantiation at call sites
by **structural matching** against arguments, not unification. Unmarked-unknown
names are errors (no typo footgun).

*Depends on: M2, M3.*

### M5 · Joins & sums 🔀
Finalize branch joins (`if` results resolve to a declared common supertype or
error). Nominal/closed-sum unions narrow cleanly to remaining members (no
negation). Note: **constructors keep their own type** — this is a *join-time*
policy, not eager upcasting; the exact policy (resolve to parent vs.
require-annotation) is open. **Prioritized ahead of bounds** — this is what makes `if`/`else` usable,
the bread-and-butter need, and it depends on neither generics nor bounds.

*Depends on: M3; touches both the new checker and existing nominal machinery.*

### M6 · Declared bounds 🔨
`where 'T <: B`: skolem-with-upper-bound, checked locally at instantiation.
Mergeable (record/interface) bounds merge at declaration; nominal-intersection
bounds reject. This subsumes the entire carrier/`as` bounded-polymorphism saga —
trivial once *declared* rather than inferred. The near-term "both" mechanism (see
the M0 spec §8); the advanced feature, so it lands last among the feature
milestones.

*Depends on: M4.*

### Checking-tests track 🔨
A test category Path G *adds* and that does not exist today: annotation
accept/reject against a body, generic instantiation, `where`-bound
satisfaction/violation, join-or-error. Grows alongside M2–M6.

### M7 · Cutover 🔀
Route `Typer.kt` through the new checker. Build a **differential harness**: run
every program snippet from the existing tests through old-engine and new-checker,
diff the *verdicts* (string diffs are expected noise; verdict diffs are real
behavioral changes to scrutinize). The M1 KEEP set must go green before any
deletion.

*Depends on: M2–M6.*

### M8 · Teardown 🧹
Delete `Subtyping.kt`, `TypeComponents.kt`, `TypeSimplifier.kt`, and the
DELETE-AT-TEARDOWN suites. Strip `TVar` lower/upper bounds, coalescing, and
`freshenAbove` let-poly from `SimpleType.kt`. Collapse `Type.kt`'s `Union`/`Inter`
to whatever M5 (joins & sums) actually keeps. Retire the differential harness once
the old engine is gone.

*Depends on: M7 green.*

---

## Test suite strategy

The type-test suite is ~10.3k lines and overwhelmingly asserts **exact inferred
type strings** — the output of the machinery being deleted. "Keep the suite green
through the cutover" is therefore impossible by construction; the suite encodes
inference behavior we are deliberately removing. The four strata and their fates:

| Stratum | Examples | Fate |
|---|---|---|
| Lexer + most parser | all `lexer/`, most `parser/` | 🟢 green throughout — free safety net |
| Grammar tests | `UnionIntersectionTypeTest`, `AnnotationTest`, `ImplicitParamTest`, `TypeDefTest` | change *with* the M0 surface decisions |
| Verdict / error tests | `TypeDefErrorTest`, `OptionalTypeErrorTest`, `NominalStructuralTest`, much of `SubtypingTest` | ✅ portable — the M7 acceptance contract |
| Inferred-string tests | `SimpleSubTest`, `InferredInterfaceTest`, `AnnotationInferTest`, `*InferTest` | ⚠️ the migration ledger (M1 REWRITE) |
| Machinery-internal | `TypeSimplifierTest`, `LubGlbSimplificationTest`, `LevelConstraintTest`, `ScopeGraphSccTest` | 🧹 die with M8 teardown |

Consequences, baked into the milestones above:
1. **Triage is M1, not M7** — deciding which tests stay *is* part of the spec.
2. **Verdict tests are the M7 green bar** — engine-agnostic, so they're the contract
   the new checker must satisfy before deletion.
3. **Inferred-string tests are a ledger, not a port** — a worklist for discovering
   the spec's edge cases empirically.
4. **The differential harness** turns "did I break something" into a reviewable
   list of verdict diffs.

---

## Documentation updates

The docs split three ways. **ADRs are immutable history** — never edit a body; record
supersession forward (in the new ADR) plus a one-line `Status` pointer.

**Decision records**
- [x] **New ADR** `2026-06-24-adopt-path-g.md` — the keystone; records the choice the
  polarity-wall ADR left open.
- [x] **Superseded-by pointers** added to: `simplesub-type-inference`,
  `lub-glb-type-simplification`, `rigid-type-variables-in-annotations`,
  `rigid-tvar-interactions`, `constructor-type-options`.

**Living docs — transform to Path G**
- [x] `type-system.md` — rewrote the inference half → bidirectional; kept subtyping / records /
  variance / nominal-structural / inferred-interface / tuples.
- [x] `roadmap.md` — reshaped into a forward-looking plan; Path G is the next phase.
- [ ] `implementation-status.md` — living; update as the rewrite lands.
- [x] `CLAUDE.md` — design decisions, CLI (`check` + `--ir`), project structure, index lines.
- [x] `reference.md` — flagged `match` as not-yet (no inference claims to scrub).
- [x] `grammar.md` — noted `A|B`/`A&B` rejected as types; `where`/`match` deferred.
- [x] `README.md` — scrubbed "no annotations required"; `dsl-project-summary.md` had no inference claims.

**Speculative / ideas — relabel, don't rewrite**
- [x] `ideas/type-simplification-future.md` → marked moot (simplifier deleted).
- [x] `ideas/2026-01-27-constraint-context-tracing-design.md` → marked stale (solver-era).
- [x] `kleene-types-experimental.md` → marked pre-Path-G / orthogonal.
- [x] `reading-list.md` → added bidirectional-typing (Dunfield & Krishnaswami); reframed SimpleSub/MLstruct as background.
- (`ideas/constructors-produce-parent-type.md` is the *not-taken* Option 1 — leave as an
  alternative; `ideas/pattern-match-synthetic-type.md` still live — no change.)

---

## Open questions

Tracked here so they don't get silently defaulted:

1. **M7 cutover style** — flag/parallel-path (stand up the new checker alongside
   the old, prove it, then delete) vs. in-place rewrite. Decide at M7.
2. **How much of `TSkolem` / rigid-`TVar` survives into M4** — reuse as-is, or does
   dropping the solver let us replace bounded `TVar` with something simpler?

*Resolved:* M0's required-vs-inferred line — settled in
[`../spec/bidirectional-checking.md`](../spec/bidirectional-checking.md).
