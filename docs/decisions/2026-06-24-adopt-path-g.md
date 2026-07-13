# Adopt Path G: Local Bidirectional Type Checking

**Date:** 2026-06-24

**Status:** Accepted

**Follows from:** [2026-06-23-polarity-wall-and-type-system-direction.md](./2026-06-23-polarity-wall-and-type-system-direction.md) — which ruled out the middle and left the choice open; this records the choice.

**Supersedes:**
- [2026-01-14-simplesub-type-inference.md](./2026-01-14-simplesub-type-inference.md) — global constraint-based inference is replaced.
- [2026-02-02-lub-glb-type-simplification.md](./2026-02-02-lub-glb-type-simplification.md) — no inferred types left to simplify.
- [2026-04-12-rigid-type-variables-in-annotations.md](./2026-04-12-rigid-type-variables-in-annotations.md) + [rigid-tvar-interactions.md](./rigid-tvar-interactions.md) — the OR-trial encoding is retired (the `'T` skolem idea survives, the mechanism does not). Its "local `'T` must be introduced in the enclosing **signature**" rule is also dropped — superseded by the nearest-enclosing-binder rule below, which the old rule only needed because of top-level let-generalization (now gone).
- [2026-03-11-constructor-type-options.md](./2026-03-11-constructor-type-options.md) — partially revisited: its LUB/union-display machinery goes with the simplifier, but **Option 2's core is kept** — constructors keep their own type (`Dog : Dog`, no auto-upcast). What a heterogeneous join yields is reopened (no anonymous unions; spec §7).

> Early-stage record: the shape below is expected to change as the rewrite proceeds. It exists to capture the direction, not to freeze it.

## Context

- Demanding subtyping **and** principal global inference forces first-class `&`/`|`, which forces either incompleteness or MLstruct's negation engine (see the polarity-wall ADR).
- Klein's audience — non-engineers writing embedded rules — needs subtyping's forgiveness, not ML rigidity; but global inference's cost (and the lattice it drags in) buys little for them.

## Decision

Adopt **Path G**: keep structural + nominal subtyping, drop global/inferred polymorphism, check **locally and bidirectionally**.

- **Annotate signatures, infer interiors** — mutually-recursive `synth` / `check`; most rules need ~zero annotations because inputs are host-provided and host-typed.
- **No `&` / `|` type connectives** — "either" is a nominal sum, "both" is bounded polymorphism (a later feature); `Optional` (`T?`) stays as the one built-in tagged union.
- **Generics by implicit quantification, scoped to the nearest enclosing binder** — a `'T` *not already in scope* is ∀-quantified at the nearest enclosing annotated binder (a `fun`/lambda, or a type-annotated `val` — not a block); a `'T` *already in scope* is a reference. It is rigid in that binder's body, instantiated per use by structural matching; output-only vars are allowed (return-type polymorphism). This removes the top-level special case (top level is just "nearest binder = the top-level binding") and lets you write polymorphic helpers locally. Quantifying every `'T` at its binder dissolves the old escape / generalization-inconsistency / name-sharing hazards: no free skolem crosses a scope (it lives only during the binder's body-check, then the binder is a closed scheme), one mechanism replaces top-level-let-gen-vs-local, and lexical scope decides sharing (sibling `val`s get independent `'T`; an in-scope name can't be locally shadowed). Stays **rank-1** — a polymorphic value used *as an argument* (`id(id)`) is out of scope.
- **Constructors keep their own type** (`Dog("x") : Dog`) — upcasting happens via subtyping at use sites, never eagerly at construction. Heterogeneous-branch **joins** resolve to a nominal common supertype or error (never a synthesized union); the exact join policy is still open (spec §7).
- **Recursive definitions declare their return type** — breaks the cycle without a fixpoint.

## Consequences

- **Delete:** the constraint solver (`Subtyping`), the simplifier (`TypeComponents`, `TypeSimplifier`), and `TVar` bounds / levels / `freshenAbove`.
- **Build:** a `synth` / `check` walk + a concrete `isSubtype`; substitution-based generic instantiation.
- **Repurpose:** `ScopeGraph`'s SCC pass for synthesis ordering + recursion-annotation enforcement.
- **Incomplete-by-design corners** are accepted as deliberate "no"s, not bugs.
- **No empty record *type* `{}`** — the empty structural interface *is* the top type, so writing `{}` as a **type** is redundant with `Any` and is removed; annotate with `Any` instead. The empty record **value** `{}` stays — it doubles as the unit-like "no meaningful value" and has type `Any`. This keeps the "never infer Top" rule pointed the right way (Top has one spelling, `Any`; a no-common-structure join is an error, never a written `{}` type) without stranding the `{}` value idiom.
- **Branch joins (`lub`) are commutative but not associative** — `lub(a, b) == lub(b, a)`, but `lub(lub(a, b), c)` may differ from `lub(a, lub(b, c))`. Subtyping is only a *partial* order, not a lattice: two types needn't have a single least upper bound, so the join has to *choose* a representative, and which one it gets depends on how the branches are grouped. Concretely, two nominal siblings promote to their shared parent, but a nominal type joined with an unrelated structural record falls back to a structural field-intersection. Example: `if p then cat else if q then dog else someRecord` — grouping `cat`/`dog` first yields their nominal parent `Animal`, then meets `someRecord`; but the source's right-nesting meets `dog` with `someRecord` first (dropping `dog` to a structural record), then joins `cat` — a different type. This is invisible for a single binary `if`/`else`; it only bites when three or more branches fold (nested `if`, a future `match`), where **the source structure determines the association**. The checker must therefore fold in a defined, documented order (source order) and must not assume associativity when simplifying or reordering joins.

## References

- Spec: [../spec/bidirectional-checking.md](../spec/bidirectional-checking.md)
- Roadmap: [../plans/path-g-roadmap.md](../plans/path-g-roadmap.md)
