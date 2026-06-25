# Adopt Path G: Local Bidirectional Type Checking

**Date:** 2026-06-24

**Status:** Accepted

**Follows from:** [2026-06-23-polarity-wall-and-type-system-direction.md](./2026-06-23-polarity-wall-and-type-system-direction.md) — which ruled out the middle and left the choice open; this records the choice.

**Supersedes:**
- [2026-01-14-simplesub-type-inference.md](./2026-01-14-simplesub-type-inference.md) — global constraint-based inference is replaced.
- [2026-02-02-lub-glb-type-simplification.md](./2026-02-02-lub-glb-type-simplification.md) — no inferred types left to simplify.
- [2026-04-12-rigid-type-variables-in-annotations.md](./2026-04-12-rigid-type-variables-in-annotations.md) + [rigid-tvar-interactions.md](./rigid-tvar-interactions.md) — the OR-trial encoding is retired (the `'T` skolem idea survives, the mechanism does not).
- [2026-03-11-constructor-type-options.md](./2026-03-11-constructor-type-options.md) — partially revisited: its LUB/union-display machinery goes with the simplifier, but **Option 2's core is kept** — constructors keep their own type (`Dog : Dog`, no auto-upcast). What a heterogeneous join yields is reopened (no anonymous unions; spec §7).

> Early-stage record: the shape below is expected to change as the rewrite proceeds. It exists to capture the direction, not to freeze it.

## Context

- Demanding subtyping **and** principal global inference forces first-class `&`/`|`, which forces either incompleteness or MLstruct's negation engine (see the polarity-wall ADR).
- Klein's audience — non-engineers writing embedded rules — needs subtyping's forgiveness, not ML rigidity; but global inference's cost (and the lattice it drags in) buys little for them.

## Decision

Adopt **Path G**: keep structural + nominal subtyping, drop global/inferred polymorphism, check **locally and bidirectionally**.

- **Annotate signatures, infer interiors** — mutually-recursive `synth` / `check`; most rules need ~zero annotations because inputs are host-provided and host-typed.
- **No `&` / `|` type connectives** — "either" is a nominal sum, "both" is bounded polymorphism (a later feature); `Optional` (`T?`) stays as the one built-in tagged union.
- **Generics by implicit quantification** — a `'T` in a signature is ∀-quantified, rigid in the body, instantiated by structural matching; output-only vars allowed (return-type polymorphism).
- **Constructors keep their own type** (`Dog("x") : Dog`) — upcasting happens via subtyping at use sites, never eagerly at construction. Heterogeneous-branch **joins** resolve to a nominal common supertype or error (never a synthesized union); the exact join policy is still open (spec §7).
- **Recursive definitions declare their return type** — breaks the cycle without a fixpoint.

## Consequences

- **Delete:** the constraint solver (`Subtyping`), the simplifier (`TypeComponents`, `TypeSimplifier`), and `TVar` bounds / levels / `freshenAbove`.
- **Build:** a `synth` / `check` walk + a concrete `isSubtype`; substitution-based generic instantiation.
- **Repurpose:** `ScopeGraph`'s SCC pass for synthesis ordering + recursion-annotation enforcement.
- **Incomplete-by-design corners** are accepted as deliberate "no"s, not bugs.

## References

- Spec: [../spec/bidirectional-checking.md](../spec/bidirectional-checking.md)
- Roadmap: [../plans/path-g-roadmap.md](../plans/path-g-roadmap.md)
