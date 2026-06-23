# The Polarity Wall, and the Three Ways Out

**Date:** 2026-06-23

**Status:** Accepted (what we are abandoning) · Deliberating (what we adopt)

**Supersedes / revisits:**
- [2026-01-14-simplesub-type-inference.md](./2026-01-14-simplesub-type-inference.md) — the foundation now under strain
- [2026-04-12-rigid-type-variables-in-annotations.md](./2026-04-12-rigid-type-variables-in-annotations.md) — the encoding this analysis retires

**Companion:** [rigid-tvar-interactions.md](./rigid-tvar-interactions.md) — the full derivation of the rigid-variable encoding and its 9-case constraint matrix. This ADR is the narrative and the decision; the companion is the granular mechanics.

---

## 1. TL;DR

Adding union/intersection **type annotations** forced a latent problem into the open: Klein's inference engine (SimpleSub) is built on a **polarity restriction** — unions may appear only in output positions, intersections only in input positions — and that restriction is not a stylistic choice. It is the precise boundary inside which a constraint-propagation solver stays deterministic, principal, and complete. User-writable `&`/`|` step *outside* that boundary by construction, and our current handling of them is already **incomplete** (it rejects programs that should typecheck) in code that ships today. This even holds when we apply the polarity restriction to these user-writable annotations.

The uncomfortable discovery is that there is **no stable resting point** between "SimpleSub without type annotations" and "MLstruct." Every patch toward complete and sound handling of user-annotated types is an installment payment on rebuilding the whole MLstruct boolean algebraic engine. So this document does one firm thing — **it rules out the path we are currently on** (SimpleSub + subtyping + hand-rolled patches) as a destination — and frames the three places we can actually stand:

1. **MLstruct** — adopt the full Boolean-algebra-of-types engine. Complete, principal, unrestricted; expensive, and keeps (a worse version of) our hardest code.
2. **Path G ("Go")** — drop polymorphism and user-facing type variables, keep subtyping, annotate signatures. Monomorphic, simple, no `&`/`|` machinery.
3. **Path ML ("Hindley–Milner + rows")** — drop *subtyping*, keep full inference and polymorphism via row-polymorphic records.

The choice between the three is a **capability call** — keep subtyping, keep full inference, or insist on both — not a question of how many intersections we want (those are a *consequence* of the call, not an input to it). That choice is deliberately left open here; this document exists so it is made with eyes open rather than re-discovered.

---

## 2. Background: what we chose, and why it is now under strain

Klein adopted **SimpleSub** (Parreaux, 2020) because it promised three things at once (see [2026-01-14-simplesub-type-inference.md](./2026-01-14-simplesub-type-inference.md)):

- **subtyping** (structural records with width subtyping, nominal hierarchies),
- **principal type inference** (every term has a single most-general type), and
- **ergonomic high quality simplification**

with a proven reference implementation to port from.

That bet has held well for *inference*. The strain shows up the moment users are allowed to **write** types — specifically union and intersection annotations ([2026-04-12-rigid-type-variables-in-annotations.md](./2026-04-12-rigid-type-variables-in-annotations.md)). To understand why a feature as ordinary as a type annotation destabilises the system, we have to be precise about what the polarity restriction *is* and what it buys. That is §3, and it is the load-bearing section of this document.

---

## 3. The polarity restriction, from first principles

### 3.1 SimpleSub has no `&`/`|` at all — only bounded variables

The fact that makes everything else click: **pure SimpleSub has no union or intersection nodes in its internal type language.** The whole vocabulary is primitives, functions, records, and *type variables carrying lower and upper bounds* — and a bound is a concept every Java programmer already has. An **upper** bound is `extends` (`A <: Num`); a **lower** bound is `super` (`Num <: A`, the relation you write as `? super Num`). In those terms `A & Num` is just a variable bounded by `extends Num`, and `A | Num` a variable bounded by `super Num` — with SimpleSub *inferring* the bounds instead of making you declare them.

The bounds fill up by one rigidly directional rule, and it is the whole engine: **demands accumulate as upper bounds, outputs as lower bounds — the reverse never happens.** A use of a variable (`'a <: U`) records `U` as an upper bound; a value flowing into it (`L <: 'a`) records `L` as a lower bound. Because the direction is fixed, reading a variable back at a position is unambiguous:

- **positive** (output): the **union** of its lower bounds — `'a | L₁ | L₂` — "what could flow out."
- **negative** (input): the **intersection** of its upper bounds — `'a & U₁ & U₂` — "what is demanded of it."

A source combines by union ("could be any of these"); a sink by intersection ("must satisfy all of these"). Unions appear positively and intersections negatively **because that is what a bounded variable means at each polarity** — the polarity "restriction" is just the observation that `&`/`|` are display artifacts of inferred bounds, never inputs to the solver.

Put bluntly: the type-combination vocabulary of SimpleSub is **no more expressive than Java's bounded generics** — `extends`/`super`, merely inferred rather than written. Its real novelty lies elsewhere (the inference itself, and structural/recursive types). Which is the first hint that letting users *write* `&`/`|` asks the machinery for something it was never built to carry — a point §3.2 makes precise.

### 3.2 What the discipline buys: every constraint decomposes to AND

The payoff is operational. When the solver decomposes a constraint involving the connectives, the *direction* of the connective decides whether it splits into a conjunction (deterministic) or a disjunction (not):

| Constraint | Decomposes to | Kind |
|---|---|---|
| `(A \| B) <: C` | `A <: C` **and** `B <: C` | AND ✓ |
| `C <: (A & B)` | `C <: A` **and** `C <: B` | AND ✓ |
| `(A & B) <: C` | `A <: C` **or** `B <: C` | **OR** ✗ |
| `C <: (A \| B)` | `C <: A` **or** `C <: B` | **OR** ✗ |

Under the polarity discipline, the connectives only ever land on the AND rows: a positive union appears as the *subtype* side of a flow (`producedValue <: expectation`), a negative intersection as the *supertype* side (`argument <: param`). Both decompose to conjunctions, the solver just runs both sub-goals and keeps propagating bounds, and the algorithm stays **deterministic, terminating, principal, and complete**.

### 3.3 Why the OR rows are poison for a bound-propagation engine

Step onto an OR row and a bound-propagation engine — whose only move is "record a bound and propagate" — has no way to cope. The cause is representational; two failures follow from it:

1. **The disjunction has no representation.** The engine only records bounds, and a bound set is always a pure *conjunction* — every recorded `L <: 'a` and `'a <: U` holds simultaneously. There is no slot for a *disjunction* ("either this bound or that one"); equivalently (per §3.1, where a lower bound is read as a *union*), no way to store an *intersection at a positive position*. Faced with an OR-row constraint, the engine can only commit to a single member.
2. **It rejects valid programs (incompleteness).** Assuming disjunction would be representable, decomposing `A & B <: C` into `A <: C ∨ B <: C` is only *sufficient*, never *necessary*: an intersection can satisfy `C` by *combining* its members when neither does alone — `A = {f: Num}`, `B = {g: Str}`, `C = {f: Num, g: Str}` gives `A & B = C` while both `A <: C` and `B <: C` fail. Trying each member misses every such case, so well-typed programs are turned away. This isn't about *how* the engine searches — the decomposition rule is simply weaker than the relation it stands for.
3. **Principality dies.** Take `(A & B) <: 'c`. The principal answer gives `'c` the lower bound `A & B` — but per (1) that is an intersection at a positive position, which the representation cannot express. The answers it *can* express, `A <: 'c` and `B <: 'c`, are **not** principal; they are merely two equally valid, incomparable answers, and the solver must favor one over the other based on which branch it happens to try first. So the inferred type depends on solve order — which is exactly what *not principal* means, and exactly the uid-ordering flakiness we hit during the annotations work.

None of this makes the OR rows *uncomputable*. A disjunctive constraint is perfectly decidable — backtracking search, SMT solvers, and normal-form (DNF/CNF) engines handle "either/or" as a matter of course. The claim is narrower and sharper: OR rows are poison for a *bound-propagation* engine specifically, whose entire method is the conjunctive bound store of §3.1. So lifting the restriction is not a feature you bolt onto that engine — it is a *different* engine. And the genuinely hard part is not the disjunctive search (that is the easy, well-understood half) but re-earning **principality** on top of it: a backtracking solver can happily *decide* a constraint while still failing to produce a single most-general type. Recovering unrestricted `&`/`|` *with principality intact* is the real cost.

That is why the restriction is theorem-shaped rather than conservative taste — *for this class of solver*. Without complement (negation), the type lattice is merely *distributive*, and in a distributive lattice principal inference requires the polar discipline; getting unrestricted `&`/`|` back with principal inference means upgrading to a full *Boolean* algebra of types and a solver built to reason about it. That is exactly the bargain MLstruct strikes, and what §8 returns to.

> **See the companion** [rigid-tvar-interactions.md](./rigid-tvar-interactions.md) for the rigorous version: the "all-of where required, any-of where offered" reading of the bounds, and the complete nine-case matrix of constraint shapes (concrete×rigid and rigid×rigid). Cases 4 and 6–8 there are precisely the OR rows above; cases 6–9 (rigid-vs-rigid) are the combinatorial cliff in miniature.

### 3.4 The same discipline is what makes simplification work

§3.2 showed the discipline buys deterministic *solving*. The same structure also buys the third promise from §2 — ergonomic, high-quality *simplification* — and for the same reason. Klein's simplifier rests on two reductions:

- **single-polarity elimination** — a variable occurring only positively can be replaced by its lower bounds (`⊥` at the limit), or negatively by its upper bounds (`⊤`): a variable nobody constrains from one side carries no information there.
- **co-occurrence unification** — variables that always appear together at the same polarity are merged into one.

Both are *theorems of the polar, distributive lattice*. They hold precisely because a type factors cleanly into positive parts (built from `∨`) and negative parts (built from `∧`), which makes "at which polarity, and alongside what, does this variable occur?" a well-defined, side-aware question — answerable directly from the directional bound-reading of §3.1. And the canonical form the simplifier runs on, `TypeComponents`, is itself only *sound* because at any position a single bound-direction matters: it is a flat, one-connective projection of the constraint graph, faithful only under the polar reading.

So the restriction and the simplifier are **one phenomenon seen from two sides** — the distributive normal form is what makes solving *and* simplification tractable at once. This has a consequence the later sections cash out, and it is easy to get backwards: lifting the restriction does not *improve* the simplifier, it *removes the ground its reductions stand on*. You would not be tuning `TypeComponents`; you would be discarding it for MLstruct's Boolean normalization — harder, and (§8) buggy even in its reference implementation. The radical directions (§9) escape the other way: by dropping polymorphism or subtyping they remove the *need* for this class of simplification altogether.

---

## 4. The obstacle: union and intersection annotations

Adding type annotations is about the most ordinary thing a language can do, and most of it costs nothing here. `x: Num`, `x: Animal`, `x: { name: String }` are concrete demands that live entirely inside the fragment of §3 — they never hand the solver a connective it has to ingest. The obstacle is narrow and specific: `&` and `|`.

### 4.1 Writing a connective manufactures the OR rows

§3.1 established that `&`/`|` exist only as the *reading* of inferred bounds, never as inputs to the solver. A user-written `&`/`|` annotation does exactly the thing the engine was built never to face — it feeds a connective *in*. And the moment the annotated value is used in the only ways values are ever used, the constraint lands on an OR row of §3.2:

- an **intersection** annotation, as soon as the parameter is *used*: `fun f(x: A & B) = g(x)` applies `g`'s demand `C` to `x`, i.e. `(A & B) <: C` — intersection on the left, the OR row;
- a **union** annotation, as soon as the result is *produced*: `fun f(): A | B = body` checks `body <: (A | B)` — union on the right, the OR row.

So writing either connective, then using the value, manufactures precisely the disjunctive constraint §3.3 says a bound-propagation engine can solve neither completely nor principally. Plain annotations never do this: a concrete `x: Num` only ever yields `source <: Num` and `Num <: demand` — concrete against single, always AND-decomposable.

### 4.2 Why pure SimpleSub never hit this

An un-annotated parameter `x` is just a *variable*. Its uses accumulate as upper bounds (§3.1), and the constraints that actually fire are `flowedInValue <: eachDemand` — a concrete source against a single demand, all AND-decomposable. The *intersection* of `x`'s demands is only ever **read off** at the end; it never appears on the left of a constraint. An annotation is exactly what changes this: `x: A & B` asserts that the intersection itself *is* the value's type, so the moment `x` is used the intersection flows onto the left of `<:`. The annotation converts a passive display artifact into an active input — and that is the whole difference.

The current encoding does not dodge this. Klein lowers `A & B` to a rigid type variable carrying `{A, B}` as upper bounds (see the [companion](./rigid-tvar-interactions.md)). That rigid variable *is* an intersection node in a variable's clothing — constraining it against a demand is constraining `(A & B) <: C`, the same OR row. The encoding relocates the disjunction into the bound store; it does not remove it.

### 4.3 The polarity gate does not save us

This is the load-bearing subtlety, and the one most likely to be assumed away. Klein *does* restrict where these connectives may be written: intersection only in negative position, union only in positive (`fromTypeExpr`). It is tempting to conclude that this confines them to the safe AND rows of §3.2. It does not — because **the gate governs where a connective may be *written*, not where it *flows* in a constraint.**

A negative-position intersection is the parameter's *type*. But a parameter exists to be *used*, and every use places that intersection on the *left* of a `<:` — the OR row. The gate stops you from *writing* a positive intersection; it cannot stop a legally-negative intersection from flowing leftward the instant the value is touched. (Dually for a positive union and the body that must produce it.) This is exactly why the TL;DR claims the incompleteness holds *even with* the polarity restriction enforced: the restriction was never the relevant fence. The connective is unsafe not because it sits at the wrong polarity, but because *using the value* moves it to the disjunctive side of a constraint regardless.

That is the obstacle in full: an utterly ordinary feature, restricted exactly as the theory prescribes, still drives the solver onto a constraint it cannot handle — because the feature's *essence* is to feed the solver a connective, and §3 is the proof that this engine cannot ingest one.

## 5. Aside: Optional was the same boundary, already closed

Annotations are not the first connective Klein admitted. `T?` is `T | Null` — a union — and it appears freely in *negative* position (`fun f(x: Num?)`), the very arrangement §3.2 forbids. It works only because `Null` is a **total discriminator**: "is it null or not" is decidable with certainty, so a few bespoke rules in `constrain` settle the disjunction deterministically *and* completely (see [2026-01-14-optional-types-null-safety.md](./2026-01-14-optional-types-null-safety.md)). Optional is therefore the degenerate, fully-tamed slice of the forbidden region — evidence that Klein's polar core already runs one sanctioned escape.

But it is **closed**: nothing about it is open, it generalises to nothing, and it imposes no ongoing cost. It is precedent, not obstacle — included here only so the annotation problem is not mistaken for the *first* time Klein touched the polarity boundary.

## 6. Aside: the incompleteness already ships — it is the witness, not a new argument

§3.3 argued the OR rows are incomplete in principle. That is not hypothetical; it is observable in the tree today. The program behind

```
{ a: { x: Num } } & { a: { y: Str } }   <:   { a: { x: Num, y: Str } }
```

is **rejected** by the current solver, even though the intersection's `a` field is exactly `{ x, y }` and the program is well-typed. It fails for precisely the §3.3 reason: the OR-trial checks each member against the demand, and neither member alone supplies both fields. The field-by-field record decomposition in `Subtyping.kt` patches the simplest shape (distinct top-level fields from distinct members); shared-field, nested, nominal-member, and function-member cases remain rejected — and some such intersections (nominal meets) have no uniquely-defined answer to begin with, which later sections revisit.

This section records only the *witness*. The analysis is entirely §3's; the code merely confirms that the theory bites in practice, on ordinary-looking programs, right now.

## 7. The trap: implementing type annotations *is* rebuilding MLstruct

At first, the natural response to the above was to fix it: deepen the record decomposition so it recurses, then handle the next shape that fails, and the next. This feels like ordinary bug-fixing. It is not: it is a march with no floor, toward a destination fixed in advance. The outline of SimpleSub and how it restricts the solver give us all the answers as to why his is not just a round of bugs to fix.

### 7.1 Completeness dismantles the restriction that motivates it

The polarity restriction exists for exactly one reason: a bound-propagation engine cannot handle an OR row (§3.3). So any machinery that makes `&`/`|` *complete* is, by definition, machinery that handles OR rows — and once you have that, the reason for the restriction is gone. There is no coherent state "complete on intersections, but still forbidding them at positive position," because completeness over `&`/`|` *is* the ability to handle them at any polarity. Finishing the feature and lifting the restriction are not adjacent goals; they are the same goal. And handling arbitrary OR rows soundly, completely, *and* principally has a name — it is MLstruct (§3.3's Boolean-algebra upgrade). Every patch toward completeness is an installment payment on that engine.

### 7.2 "Inference drives annotations" forbids every halfway stop

Both ways of stopping short run into the same wall: Klein's invariant that *whatever inference produces must be writable, and whatever is writable must be inferable* — the surface language and the inferred output are one language, with no membrane between them. That single fact blocks the two tempting half-measures, from opposite directions:

- **You cannot give annotations more power than inference.** If the engine learns to accept a positive intersection in order to typecheck an annotation, inference will also *produce* positive intersections — and you cannot forbid users from writing the very type the checker prints back at them. Capabilities added "just for annotations" leak into the inferred output; lifting the restriction for annotations lifts it everywhere.
- **You cannot give annotations less power than inference either.** The hope of admitting only a finishable fragment (record merges) and rejecting the rest founders because **SimpleSub already produces general intersections with no annotation at all**: use a value as both a `Dog` and a `Cat` and inference types the parameter `Dog & Cat` — a negative variable carrying both as upper bounds (§3.1). That ordinary inferred type must, by the invariant, be writable, so it cannot be rejected at elaboration.

And the second case carries the sting that fixes the whole picture. Producing `Dog & Cat` *passively* — as the read-off of upper bounds (§4.2) — is **complete**; the intersection never reaches the left of a constraint. *Writing* it is what makes it **active**: now it is the value's type, and using the value drives it leftward (§4) onto the incomplete OR row. So the invariant forces you to admit exactly the annotations that break completeness, and completeness then forces the Boolean engine.

The upshot: the only coherent way to shrink the intersection surface is to stop inference from *producing* general intersections in the first place — which is not a SimpleSub patch but a different engine. Restriction is a genuine destination; it simply lives at the engine level (§9), never as a fragment carved out of SimpleSub.

### 7.3 The middle is a transient, not a resting place

So the comfortable middle — *keep* general union/intersection annotations, *stay* simple, *and* be complete — does not exist. "SimpleSub + hand-rolled patches" is MLstruct on layaway: the same total cost paid in installments, plus the penalty of living in a partially-incomplete state between every installment, each patch silently rejecting whatever the next one would fix. The stable grounds are the endpoints and the restrictions: admit no `&`/`|` at all, restrict them to a finishable fragment, or adopt the Boolean engine outright. Those are the three destinations of §9. The path we are on today is none of them — it is the slope between them, and it runs only downhill, toward MLstruct.

## 8. Why not just build MLstruct?

MLstruct is the honest, complete destination (§3.3, §7.1), so the question deserves an answer. It spends Klein's complexity budget exactly where Klein can least afford it:

**It keeps our hardest code, and makes it worse.** The simplifier is already by far the most complex part of the system (§3.4). MLstruct does not retire it; it replaces our polar `TypeComponents` with Boolean normalization (DNF/CNF plus the disjunctive kernel Parreaux's implementation literally names `annoying`). And the tell is plain: even MLstruct's *own* reference implementation has a visibly buggy simplifier. For a research language that is cosmetic; for Klein — whose premise is *readable types for non-engineers* — a layer whose failure mode is "print an incomprehensible type" attacks the mission. We would inherit the weakest, and for us most meaningful, component.

A coherent destination, just not ours. §9 asks whether the alternatives reach completeness more cheaply — by *removing the need* for this machinery rather than building more of it.

## 9. The alternatives: three stable destinations

Here is the axis the whole document turns on. All the expensive machinery — the polarity discipline, the OR-trial, MLstruct's Boolean engine — exists for exactly one purpose: **to make subtyping and inferred polymorphism coexist.** That is what SimpleSub is *for*, and §3–§8 are the story of how much that coexistence costs once users can write `&`/`|`. So the three stable destinations are the three answers to "how do we resolve that tension?":

- **keep both** — and pay the full price (MLstruct);
- **drop inferred polymorphism** — keep subtyping, annotate signatures (Path G);
- **drop subtyping** — keep full inference and polymorphism (Path ML).

The two "drop" paths are not consolation prizes. Each is complete, principal, negation-free, and — not coincidentally (§3.4) — **deletes the simplifier**, the component that has cost us the most. They buy what MLstruct buys by removing the *need* for the machinery rather than building it.

| | **MLstruct** | **Path G** ("Go") | **Path ML** (HM + rows) |
|---|---|---|---|
| Parameter inference | full | annotated (sigs explicit) | full |
| Polymorphism / generics | yes | none — stdlib generics as host built-ins | yes (parametric) |
| Subtyping | yes | yes (structural + nominal) | none (row polymorphism instead) |
| User-written `&` / `\|` | full, any polarity | none | none |
| Complete + principal | yes (hard-won) | trivially | yes (classic HM) |
| Negation exposed | yes — leaks to surface | no | no |
| Our simplifier | replaced by a *harder* one | ~deleted | ~deleted |
| Records model | structural subtyping | structural subtyping | row-polymorphic (extensible) |
| Fit with Klein's mission | fragile core + `¬` UX | explicit sigs, no generics | infer-everything, no hierarchies |

**MLstruct** keeps the coexistence intact and complete — at the cost laid out in §8: it keeps and worsens our hardest code. (It also leans centrally on type negation, which surfaces in inferred types — an extra ergonomic cost for Klein's audience.) The most expressive option, and the one that spends Klein's complexity budget where Klein can least afford it.

**Path G ("Go")** drops *inferred polymorphism*: types are monomorphic, signatures are annotated, and everything else (locals, returns, expression types) is inferred by simple bottom-up synthesis with subtype checks at the boundaries. Structural and nominal subtyping stay; there is no `&`/`|` machinery and nothing to coalesce, so the simplifier evaporates. The costs are real and specific: no user-written generics — the stdlib's polymorphic operations become host-provided built-ins the typer special-cases — and heterogeneous branches (`if … then circle else square`) must resolve to a declared common supertype or error, which *settles* the open constructor-type question ([2026-03-11-constructor-type-options.md](./2026-03-11-constructor-type-options.md)) in favour of "produce the parent, or reject." For a rules language aimed at non-engineers, explicit signatures are arguably a feature, not a tax.

**Path ML ("Hindley–Milner + rows")** drops *subtyping* instead. Full inference returns — parameters included — with principal types and real parametric polymorphism (`id`, a genuine `map`). "Duck-typed" records come back as **row polymorphism** (`{ name: String | r }`), which gives `|x -> x.name|` a principal type with no subtyping at all; nominal hierarchies become tagged variants / ADTs with explicit construction rather than implicit upcasting. No subtyping means no `&`/`|` ever arises, and the simplifier again reduces to "unify, generalise, print." The cost is a genuine change to Klein's type *model* — though notably the one [type-system.md](../type-system.md) already gestures at (rows + Hindley–Milner).

So the decision is not "which intersections do we want" but **"which coexistence do we give up"** — a capability call: keep subtyping, keep full inference, or insist on both. Intersections are a *consequence* of that call, not an input to it — they survive only if we insist on both and adopt MLstruct. You cannot have both cheaply; §3 is the proof of why.

## 10. Decision

This ADR makes a deliberately *partial* decision, and both halves matter.

**Decided.** The path Klein is on — SimpleSub plus subtyping plus hand-rolled patches for general user-written `&`/`|` — is **ruled out as a destination** (§7). It is not a stable system but the downhill slope toward MLstruct: continuing to patch it buys incompleteness in the interim and MLstruct's bill in the end. The general union/intersection annotation effort in its current form is therefore **stopped**, not paused for more patching. The rigid-variable encoding ([companion](./rigid-tvar-interactions.md), [2026-04-12-rigid-type-variables-in-annotations.md](./2026-04-12-rigid-type-variables-in-annotations.md)) is understood as a dead end, retained only as the record of what was tried.

**Not decided.** *Which* of the three destinations (§9) Klein adopts — MLstruct, Path G, or Path ML — is left open. The analysis establishes that these are the only stable grounds and what each costs; it does not, by itself, select one. That selection is primarily a product-level capability judgement — subtyping, full inference, or both — that is the maintainers' to make, gated at most by a narrow check for any use-case that would *force* the Boolean engine — realistically, only nominal meets, function overloading, or variable-laden intersections do, and a business-rules language likely needs none of them.

The document's job is done when the next person reaching for "let's just finish the intersection feature" finds, here, the reason not to — and the three real options laid out in its place. When the capability call is made, a follow-up ADR will record the chosen destination and supersede the *Deliberating* half of this one.
