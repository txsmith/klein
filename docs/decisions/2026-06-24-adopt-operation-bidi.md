# Adopt Operation Bidi: Local Bidirectional Type Checking

**Date:** 2026-06-24

**Status:** Accepted · Implemented 2026-07-18 (declared bounds deferred)

> **Naming:** this direction was called "Path G" while it was one of the polarity-wall
> ADR's candidate ways out (option G, "Go"); on completion it was renamed **Operation
> Bidi**. This ADR and its neighbors keep the old name as history.

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

Adopt **Operation Bidi**: keep structural + nominal subtyping, drop global/inferred polymorphism, check **locally and bidirectionally**.

- **Annotate signatures, infer interiors** — mutually-recursive `synth` / `check`; most rules need ~zero annotations because inputs are host-provided and host-typed.
- **No `&` / `|` type connectives** — "either" is a nominal sum, "both" is bounded polymorphism (a later feature); `Optional` (`T?`) stays as the one built-in tagged union.
- **Generics by implicit quantification, scoped to the nearest enclosing binder** — a `'T` *not already in scope* is ∀-quantified at the nearest enclosing annotated binder (a `fun`/lambda, or a type-annotated `val` — not a block); a `'T` *already in scope* is a reference. It is rigid in that binder's body, instantiated per use by structural matching; output-only vars are allowed (return-type polymorphism). This removes the top-level special case (top level is just "nearest binder = the top-level binding") and lets you write polymorphic helpers locally. Quantifying every `'T` at its binder dissolves the old escape / generalization-inconsistency / name-sharing hazards: no free skolem crosses a scope (it lives only during the binder's body-check, then the binder is a closed scheme), one mechanism replaces top-level-let-gen-vs-local, and lexical scope decides sharing (sibling `val`s get independent `'T`; an in-scope name can't be locally shadowed). Stays **rank-1** — a polymorphic value used *as an argument* (`id(id)`) is out of scope.
- **Constructors keep their own type** (`Dog("x") : Dog`) — upcasting happens via subtyping at use sites, never eagerly at construction. Heterogeneous-branch **joins** resolve to a nominal common supertype or error (never a synthesized union); the exact join policy is still open (spec §7).
- **Recursive definitions declare their return type** — breaks the cycle without a fixpoint.

## Consequences

- **Delete:** the constraint solver (`Subtyping`), the simplifier (`TypeComponents`, `TypeSimplifier`), and `TVar` bounds / levels / `freshenAbove`. *(Done — the whole `klein/types` engine, its test suites, and the `infer` CLI command are deleted.)*
- **Build:** a `synth` / `check` walk + a concrete `isSubtype`; substitution-based generic instantiation. *(Done — lives in `klein.check`.)*
- **Repurpose:** `ScopeGraph`'s SCC pass for synthesis ordering + recursion-annotation enforcement. *(Done.)*
- **One type hierarchy, one error hierarchy.** With the engine gone there is a single type tree (`klein.check.Type`, printed directly — a `∀` prints as its body, skolems by name) and a single typed error hierarchy (`klein.check.TypeError`, no untyped catch-all: branch-join failures, recursive-return, and anonymous `A | B` / `A & B` annotations each have their own error). The old printable surface tree (`klein.Type`) and its `Union`/`Inter` variants are deleted; the parser still parses `A | B` / `A & B` so the checker can reject them with a typed error rather than a parse error.
- **Incomplete-by-design corners** are accepted as deliberate "no"s, not bugs.
- **No empty record *type* `{}`** — the empty structural interface *is* the top type, so writing `{}` as a **type** is redundant with `Any` and is removed; annotate with `Any` instead. The empty record **value** `{}` stays — it doubles as the unit-like "no meaningful value" and has type `Any`. This keeps the "never infer Top" rule pointed the right way (Top has one spelling, `Any`; a no-common-structure join is an error, never a written `{}` type) without stranding the `{}` value idiom.
- **Branch joins (`lub`) are commutative but not associative** — `lub(a, b) == lub(b, a)`, but `lub(lub(a, b), c)` may differ from `lub(a, lub(b, c))`. Subtyping is only a *partial* order, not a lattice: two types needn't have a single least upper bound, so the join has to *choose* a representative, and which one it gets depends on how the branches are grouped. Concretely, two nominal siblings promote to their shared parent, but a nominal type joined with an unrelated structural record falls back to a structural field-intersection. Example: `if p then cat else if q then dog else someRecord` — grouping `cat`/`dog` first yields their nominal parent `Animal`, then meets `someRecord`; but the source's right-nesting meets `dog` with `someRecord` first (dropping `dog` to a structural record), then joins `cat` — a different type. This is invisible for a single binary `if`/`else`; it only bites when three or more branches fold (nested `if`, a future `match`), where **the source structure determines the association**. The checker must therefore fold in a defined, documented order (source order) and must not assume associativity when simplifying or reordering joins.
- **An `if`/`match` whose branches share no writable type is an error, not an inferred type.** `if c then dog else cat` has type `Animal` — both branches are `Animal`, fine. `if c then 1 else "x"` is a type error: `Num` and `String` have no common supertype you can write in Klein, so there is no type to give the expression. Every way to make that second one type-check needs a type we've ruled out: an anonymous union `Num | String` (what TypeScript and Scala 3 infer — Klein has no union types), or a shared supertype the user can't write down (Kotlin infers `Comparable<*> & Serializable` for `if (c) "s" else 1` — our surface=writable invariant forbids inferring a type nobody can name). With both off the table, the branches must already share a named type or the program is rejected.
- **A polymorphic branch is instantiated against the other branch.** `if c then id else g`, with `id : ('T) -> 'T` and `g : (Num) -> Num`, has type `(Num) -> Num`: the polymorphic branch is instantiated against the monomorphic one with the same `solveQuantified` that instantiates a polymorphic function against its arguments — so synth mode needs no annotation (previously this required one). It's rejected when no instantiation of the polymorphic branch fits the other — e.g. a branch whose interface is `{ value: 'A }` against `{ value: Num, extra: Bool }`, since no `'A` supplies the `extra` field. **When both branches are polymorphic** — `if c then id else id`, each `('T) -> 'T` — the join is the more general scheme, kept polymorphic (`('T) -> 'T` here): each scheme is checked against a *rigid skolemization* of the other (that check is scheme subtyping) and the one that subsumes the other wins; if neither does, it's rejected. Note this can't lub the two instantiated bodies — independent skolemizations don't align — so the result is the surviving scheme itself.

## References

- Spec: [../spec/bidirectional-checking.md](../spec/bidirectional-checking.md)
- The build/teardown roadmap was a working document, deleted on completion (git history: `docs/plans/operation-bidi-roadmap.md`).
