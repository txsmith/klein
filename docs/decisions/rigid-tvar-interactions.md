# Rigid TVar interactions for union/intersection annotations

## Context

Klein's type system uses SimpleSub-style constraint-based inference where TVars carry mutable lower bounds (LBs) and upper bounds (UBs). Annotations for unions and intersections are encoded as rigid TVars (TVars that don't accumulate bounds during inference) carrying the structural components as bounds:

- **Union** `A | B` at positive polarity → rigid α with `LBs = {A, B}`
- **Intersection** `A & B` at negative polarity → rigid α with `UBs = {A, B}`

The "rigid" flag means the solver reads these bounds for checking but never adds to them. This document walks through how rigid TVars interact with each other and with concrete types under various constraint shapes.

## Polarity discipline

Klein enforces a polarity restriction at annotation time:

- Unions are only valid at **positive** polarity (output positions: return types, val types, function results that are observed)
- Intersections are only valid at **negative** polarity (input positions: function params, function results that are demanded)

Polarity composes through variance: `outer.compose(inner_variance)`. E.g., `Consumer<Num | String>` where `Consumer` is contravariant in its type arg: if the outer position is negative, the union ends up at negative ∘ contravariant = positive (allowed).

## The encoding and what the bounds mean

| Encoding | Interpretation when on LHS of `<:` (consumed) | Interpretation when on RHS of `<:` (demanded) |
|---|---|---|
| rigid α(LBs) — union | AND over LBs: every LB must satisfy RHS | OR over LBs: ∃ LB such that LHS <: LB |
| rigid α(UBs) — intersection | OR over UBs: ∃ UB such that UB <: RHS | AND over UBs: every UB must be satisfied by LHS |

The pattern: **intersection or union always behaves "all-of" on the side where its components are required, and "any-of" on the side where its components are offered.**

Concretely:
- A union value being *used* must be safe for every possible runtime form → AND-over-LBs.
- A union *target* accepts a value that fits any allowed form → OR-over-LBs.
- An intersection value being *used* offers many capabilities → pick one (OR-over-UBs).
- An intersection *target* demands every constraint be satisfied → AND-over-UBs.

## The full matrix of constraint shapes

Nine cases by LHS shape × RHS shape:

| Case | LHS | RHS | Rule | Quantifier pattern |
|---|---|---|---|---|
| 1 | concrete C | concrete C' | structural | — |
| 3 | concrete C | α(UBs inter) | ∀ ub, C <: ub | AND |
| 2 | concrete C | α(LBs union) | ∃ lb, C <: lb | OR |
| 4 | α(UBs inter) | concrete C | ∃ ub, ub <: C (with structural decomp on RHS) | OR |
| 5 | α(LBs union) | concrete C | ∀ lb, lb <: C | AND |
| 6 | α(UBs) | β(UBs) | ∀ ub' ∈ β, ∃ ub ∈ α: ub <: ub' | AND-of-OR |
| 7 | α(LBs) | β(LBs) | ∀ lb ∈ α, ∃ lb' ∈ β: lb <: lb' | AND-of-OR |
| 8 | α(UBs) | β(LBs) | ∃ ub, ∃ lb: ub <: lb | OR-of-OR |
| 9 | α(LBs) | β(UBs) | ∀ lb, ∀ ub: lb <: ub | AND-of-AND |

Reading aid:
- **Intersection on a side → OR over that side** (the side that "offers many capabilities, pick one").
- **Union on a side → AND over that side** when consumed; OR when demanded.
- The double-quantifier cases compose these.

### Structural decomposition

For records on the RHS in case 4, naive whole-record OR-trial fails: a value of type `Dog & Hero` against demand `{ name, movie }` shouldn't require *one* component to supply both fields — different components can supply different fields. The fix is to **decompose the RHS structurally first**, then apply the rule at each leaf:

```
{ name, movie } demand decomposes to two field demands:
  - α(UBs={Dog, Hero}) supplies name → OR-trial: Dog has name ✓
  - α(UBs={Dog, Hero}) supplies movie → OR-trial: Hero has movie ✓
```

This generalizes: function-RHS decomposes to param-and-result demands (each handled), TRef-RHS decomposes to each type-arg-with-variance, etc.

## Reachability

Pure rigid-vs-rigid constraints only fire when **both sides come from annotations in the same scope without instantiation between them**. After generalization and call-site instantiation, rigid TVars get freshened to flex TVars (via `freshenAbove`), and the existing flex propagation rules handle the work.

The "stays rigid" paths:

1. **Inside the function body that wrote the annotation** — before the function is generalized.
2. **Recursive references within a function's own body** — the function is mono-bound to a placeholder during its own inference.
3. **Mono-bound callback params** — calling a callback inside the enclosing function uses the callback's annotated type directly.
4. **Mutual recursion within an SCC** — all functions in a mutually-recursive group are mono-bound during joint pass-2 inference (this requires the SCC fix described separately).

Note that rigid-vs-rigid constraints don't always fire *directly*. They often arise via propagation through flex TVar intermediaries. E.g., in a recursive call `f(x)`, the call site sees `τ_f` (a mono placeholder, not f's actual rigid annotated type). After f's body is inferred and `inferred <: τ_f` propagates, function-subtyping fires rigid-vs-rigid constraints downstream.

## Example for each case

The following examples assume types `Dog`, `Hero`, `Both`, etc. are pre-defined as constructors. Numerically passing/failing examples are noted.

### Case 1: concrete vs concrete

```
x: Num = 42
```

Constraint `Num <: Num`. Trivial structural match.

### Case 2: concrete <: α(LBs)

Body value flowing into a union annotation, inside the body where the annotation is rigid:

```
fun f(): Num | String = 42
```

Constraint: `Num <: α(LBs={Num, String})`. OR-over-LBs: try `Num <: Num` ✓. Passes.

### Case 3: concrete <: α(UBs)

Concrete value flowing into intersection annotation, inside a scope where the annotation stays rigid. Reachable via self-recursion (the function is mono-bound within its own body, so the param's intersection annotation isn't instantiated):

```
type Both = Both { name: String, age: Num }
fun f(x: { name: String } & { age: Num }): Num = f(Both("Paco", 5))
```

Inside f's body, recursive `f(Both(...))` doesn't instantiate. Constraint: `Both <: α(UBs={Record(name), Record(age)})`. AND-over-UBs: Both has name ✓ AND Both has age ✓. Passes.

Note: this only stays rigid because self-recursion keeps f mono-bound. Calling f from *outside* its body would instantiate the intersection to a flex β, and the equivalent constraint would fire under the flex-RHS propagation rule (which gives the same AND semantics naturally).

### Case 4: α(UBs) <: concrete

Using an intersection-typed parameter inside the function body:

```
fun lookup(x: Dog & Hero) = x.name
```

Inside the body, `x.name` generates `α(UBs={Dog, Hero}) <: Record(name: T_fresh)`. The RHS-structural decomposition applies: for the field demand `name`, OR-trial over UBs — `Dog <: Record(name)` ✓ (Dog has name). T_fresh gets bound to String via the field type.

### Case 5: α(LBs) <: concrete

Reachable via self-recursion, where the recursive function's union return is mono-bound through the placeholder TVar mechanism:

```
fun f(x): Num | String = f(x) + 1
```

Inside f's body, `f(x)` returns through τ_f (mono placeholder). After f's body inference, propagation eventually delivers `α(LBs={Num, String}) <: Num` (the `+ 1` operator demands Num). AND-over-LBs: Num <: Num ✓, String <: Num ✗ → **error**. The check correctly rejects: f might return String, but `+` requires Num.

### Case 6: α(UBs) <: β(UBs)

Two different intersection annotations in the same recursion group:

```
fun a(x: A & B & C) = b(x)
fun b(x: A & B) = b(x)   // b self-recursive, doesn't call a
```

Inside a's body, `b(x)` mono-binds b and passes α(UBs={A,B,C}) as arg. After b's body inference, propagation delivers `α(UBs={A,B,C}) <: β(UBs={A,B})`. AND-of-OR: every β.UB must be hit by some α.UB. A finds A ✓, B finds B ✓. Passes.

Semantically: a value of `A & B & C` is also a value of `A & B` (intersection is monotone — more constraints means a subtype).

Reverse direction fails:
```
fun a(x: A & B) = b(x)
fun b(x: A & B & C) = b(x)
```

Here `α(UBs={A,B}) <: β(UBs={A,B,C})`. AND-of-OR: C ∈ β.UBs has no satisfier in α.UBs → error.

### Case 7: α(LBs) <: β(LBs)

Two union annotations interacting via mutual recursion:

```
fun a(x): Num | String = b(x)
fun b(x): Num | String = a(x)
```

Constraint propagation through both bodies eventually fires `α(LBs={Num,String}) <: β(LBs={Num,String})`. AND-of-OR: every LHS LB must be in some RHS LB. Num finds Num ✓, String finds String ✓. Passes.

Widening case (passes):
```
fun specific(x): Num | String = ...
fun broader(x): Num | String | Bool = specific(x)
```

Narrowing case (fails):
```
fun broader(x): Num | String | Bool = ...
fun narrower(x): Num | String = broader(x)
```

In narrower's body, `α(LBs={Num,String,Bool}) <: β(LBs={Num,String})`. Bool finds nothing → error.

### Case 8: α(UBs) <: β(LBs)

Intersection param flowing into union return, in the same function:

```
fun coolDog(x: Dog & Hero): Dog | Hero = x
```

Inside the body, the return constraint is `α(UBs={Dog,Hero}) <: β(LBs={Dog,Hero})`. Double-OR: ∃ ub ∈ α, ∃ lb ∈ β such that ub <: lb. Dog <: Dog ✓ on first try. Passes.

Semantically: `Dog & Hero` is a subtype of `Dog | Hero` (a value that's both is at least one).

### Case 9: α(LBs) <: β(UBs)

Union LHS into intersection RHS. Reachable via mutual recursion with propagation through flex intermediaries:

```
type A = A
type B = B
fun g(x): A | B = A
fun f(x: A & B) = f(g(3))
```

Inside f's body, `g(3)` returns a flex `result_g_τ`. f's recursive call passes it: `result_g_τ <: α_f(UBs={A,B})`. Propagation through both placeholder TVars eventually fires `γ_g(LBs={A,B}) <: α_f(UBs={A,B})`. Double-AND: ∀ lb, ∀ ub, lb <: ub. A <: A ✓ but A <: B ✗ → error.

Semantically: a value that's `A or B` can't be required to be `both A and B` (unless A = B by subtyping). Most natural Case 9 instances are failures.

## A note on propagation: indirect rigid-vs-rigid

Many rigid-vs-rigid constraints don't fire at the syntactic call site — they emerge from propagation across flex intermediaries (placeholder TVars from `processFunDefs`, fresh result TVars from `Apply` rules, etc.).

Example trace for case 8 (`fun coolDog(x: Dog & Hero): Dog | Hero = x`):

1. x has type α (rigid UBs={Dog, Hero}), the param annotation.
2. Return annotation: β (rigid LBs={Dog, Hero}).
3. Body type: x's type = α. Body return constraint: α <: β. **Direct rigid-vs-rigid Case 8.**

Example trace for case 9 (`fun f(x: A & B) = f(g(3))` with `fun g(x): A | B = A`):

1. In f's body, `g(3)`: g is mono → τ_g (flex placeholder). Apply: `τ_g <: (Num) -> result_g_τ`.
2. `f(g(3))`: f is mono → τ_f. Apply: `τ_f <: (result_g_τ) -> result_f_τ`.
3. End of pass 2 for g: `(g_param_τ) -> γ_g <: τ_g`. Propagation function-subs against τ_g's UB: `γ_g <: result_g_τ`. Flex-RHS adds γ_g to result_g_τ.LBs.
4. End of pass 2 for f: `(α_f) -> result_f_τ <: τ_f`. Propagation: `result_g_τ <: α_f`. Flex-LHS adds α_f to result_g_τ.UBs.
5. result_g_τ now has γ_g in LBs and α_f in UBs. Propagation: **γ_g <: α_f**. Rigid-vs-rigid Case 9.

The propagation chain is where the work happens. The constraint solver doesn't see Case 9 emitted directly from the source — it derives it from the closure of accumulated bounds.

## Implementation implications

For implementing the rigid-TVar-with-bounds encoding:

1. **Rigid LHS branch** in `Subtyping.constrain`:
   - If α has UBs only (intersection): OR-trial over UBs, with the RHS structurally decomposed before each trial.
   - If α has LBs only (union): AND-iterate LBs against RHS.
   - If α has neither: it's a skolem; succeed on identity, error otherwise.

2. **Rigid RHS branch**:
   - If β has LBs only (union): OR-trial over LBs.
   - If β has UBs only (intersection): AND-iterate UBs against LHS.
   - If β has neither: skolem rules.

3. **OR-trial mechanics**: try each candidate in turn. On failure, the only mutation that needs to be reverted is the errors list (use a count-snapshot pattern: save `errors.size` before each trial, truncate on failure). Flex TVar bounds may leak from failed trials in pathological cases — this is the same hazard MLscript faces, generally acceptable in practice.

4. **freshenAbove behavior**: when instantiating a polymorphic value containing a rigid TVar with bounds, the rigid TVar should become flex with the bounds copied. The existing flex propagation rules then deliver correct semantics naturally for use-site constraints — no need to preserve rigidity through instantiation.

5. **Bare skolems** (rigid TVar with no bounds): still freshen to flex on instantiation (this is what makes `id(42)` work via HM polymorphism).
