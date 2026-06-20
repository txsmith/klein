# Brief: Polymorphism leak across non-recursive function definitions

## The bug

Klein currently over-monomorphizes top-level function definitions that aren't actually mutually recursive. Repro via the CLI:

```
fun id(x) = x
fun f(x) = id(3)
fun g(x) = id("Hello")
```

Current output (`./klein infer`):
```
id : ('A) -> 'A | Num | String
f  : (Any) -> Num | String
g  : (Any) -> Num | String
```

Expected:
```
id : ('A) -> 'A
f  : (Any) -> Num
g  : (Any) -> String
```

`id`'s polymorphism leaks across `f` and `g` — both end up returning `Num | String` instead of being independently polymorphic in their callee.

## Root cause

`Typer.processFunDefs` (in `klein-lib/src/commonMain/kotlin/klein/types/Typer.kt`) treats every batch of FunDefs in a scope as one mutually-recursive group. The three-pass approach:

1. **Pass 1**: bind every function name to a fresh flex TVar (mono placeholder).
2. **Pass 2**: infer each body; recursive references resolve to placeholders directly (no instantiation, because they're mono-bound).
3. **Pass 3**: re-bind each function polymorphically.

When `f` and `g` call `id` during pass 2, both calls resolve to the same `τ_id` placeholder. Two UBs accumulate on `τ_id`: `(Num) -> result_f_τ` and `(String) -> result_g_τ`. End-of-pass-2 propagation flows `id`'s inferred type `(x_τ) -> x_τ` through both UBs, unifying `x_τ` with both `Num` and `String`. The polymorphism is lost — `id` is monomorphized to a single shared TVar.

The mono-binding is correct *for genuine mutual recursion* (where the recursive knot must be tied before any of the functions can be generalized). But applying it to all FunDefs in a scope over-conservatively monomorphizes independent functions.

## Fix: process by strongly connected component (SCC)

Build a directed dependency graph: nodes are FunDefs, edge `a → b` iff `a`'s body references `b`. Compute SCCs (Tarjan's algorithm is standard, ~30 lines, O(V+E)). Process SCCs in topological order. For each SCC:

- If size 1 and no self-edge: just infer + generalize the single function.
- If size 1 with self-edge (self-recursion): mono-bind during body inference, then generalize.
- If size > 1 (true mutual recursion): the existing 3-pass approach over just this SCC.

In the example: `{id}`, `{f}`, `{g}` are three separate SCCs (no edges into id, no edges between f and g). Process id first → `id : ('A) -> 'A`, generalize. Then process f: looks up id, instantiates → fresh flex copy of `('A) -> 'A` per call. f's body specializes its instance to `(Num) -> Num`, generalizes to `f : (Any) -> Num`. Same for g.

For genuine mutual recursion like:

```
fun a(x) = b(x)
fun b(x) = a(x)
```

SCC is `{a, b}`. Existing 3-pass applies to just this SCC. Behavior unchanged.

## Where to look in the code

- `klein-lib/src/commonMain/kotlin/klein/types/Typer.kt`, function `processFunDefs` (around line 40-73) — the current 3-pass implementation, should be split per SCC.
- `klein-lib/src/commonMain/kotlin/klein/types/TypeEnv.kt`, `bind` vs `bindPolymorphic` and `lookupAndInstantiate` — the binding mechanism this exploits. Already correct, no changes needed.
- `klein/Ast.kt` (FunDef definition) — needed to walk bodies for the dependency graph. References to free names appear as `Ident` expressions; check whether the name resolves to a sibling FunDef in the current batch.

## Reference graph construction

For each FunDef in the batch, walk its body collecting `Ident` references. A reference counts as a dependency edge only if the target name is another FunDef in the same batch. References to outer-scope names, primitives, and constructors don't form edges (they're not part of the group's polymorphism story).

Recommend: do this walk before pass 1 begins, on the raw FunDef AST, to determine the SCC partitioning. Then run the existing 3-pass on each SCC in topological order.

## Test cases worth adding

1. `id`+`f`+`g` exact repro above — verify `id` stays `('A) -> 'A`, f returns Num, g returns String.
2. Single self-recursive function — should still typecheck (existing behavior preserved).
3. True mutual recursion (a calls b, b calls a) — should still typecheck identically to today.
4. Mixed: helper function used by a mutual-recursion pair — verify the helper is generalized independently while the pair stays a unit.
5. Three-way SCC (a→b→c→a) — verify it's treated as one group.

## Out of scope

- Don't touch the constraint solver. The fix is purely in `processFunDefs`.
- Don't change `freshenAbove` or instantiation semantics. Those already do the right thing — the bug is that polymorphic-binding happens too late.
- Don't add a new TypeBinding variant. Mono and Poly are sufficient.

## Why this matters

This is a correctness bug for any program with more than one helper function. It silently produces over-restrictive inferred types and unwanted `|` unions in function signatures. It's also a prerequisite for the upcoming rigid-TVar-with-bounds work on unions/intersections — that work relies on instantiation freshening rigid bounds at use sites, which only fires when functions are properly generalized between SCCs.
