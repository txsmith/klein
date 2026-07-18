# M0 — Surface Spec: Local Bidirectional Type Checking

**Status:** Drafting · **Milestone:** Operation Bidi / M0 · **Started:** 2026-06-24

This is the contract the rewrite (`rewrite/type-checker`) is built and tested
against. It specifies **how Klein decides types locally**, without global
inference. The *why* is in
[`../decisions/2026-06-23-polarity-wall-and-type-system-direction.md`](../decisions/2026-06-23-polarity-wall-and-type-system-direction.md);
the build/teardown sequencing is in
[`../plans/operation-bidi-roadmap.md`](../plans/operation-bidi-roadmap.md).

**In scope:** the *existing* surface — top-level `type` defs, `fun`/binding/param
annotations, records, tuples, applied types, function types, `if`, the `?`
optional, and `'T` type variables.

**Out of scope (deferred features, not M0):** `match` expressions, `where 'T <: …`
declared bounds. These are net-new and specced later.

---

## 1. The rules are mode-driven, not syntactic

Checking has two judgments. Which one an expression lands in depends on
**context**, not on its syntactic kind:

- **synth** — `e ⇒ T` — produce a type from `e` alone. Natural for *elimination*
  forms: variable reference, application, field access, operators.
- **check** — `e ⇐ T` — given an expected `T`, verify `e` conforms. Natural for
  *introduction* forms: lambdas, records, constructors, empty collections.

They connect by **subsumption**: to check `e ⇐ T` when `e` only has a synth rule,
synthesize `e ⇒ S` and require `S <: T`.

The **only** place an annotation is forced is the mirror image — when you must
**synthesize an introduction form and there is no expected type to seed it**:

> annotation required ⟺ *introduction form* ∧ *synth mode* ∧ *no annotation present*

This is the heart of the spec. Everything in §3 is a corollary of it. An
expression need not be a top-level "root" to be underdetermined: `[]` has no
element type, a bare polymorphic constructor has no type argument, a lambda in
synth position has no parameter types. Each gets stuck unless an expected type is
pushed in (check mode) or an annotation is written.

---

## 2. Annotations are never redundant

An annotation is never *rejected for being unnecessary*. Even where synthesis
would succeed on its own, you may write a type, and it acts as a **checking
ascription**: the expression is checked against it (`e ⇐ T`), and from that point
the *annotation* — not what synthesis would have produced — is the type that flows
onward. (So an ascription can also *widen*: `(dog : Animal)` synthesizes `Dog` but
flows on as `Animal`.)

Consequently the spec never asks "is an annotation allowed here?" — only "is one
**required** here?", per §1.

---

## 3. Where annotations are required (corollary)

The table below is a **derived cheat-sheet for synth mode with no expected type**
— i.e. a definition checked standalone (the CLI `infer` path). Under a host-supplied
expected type (§4), even these become optional.

| Form | Natural mode | Annotation required? |
|---|---|---|
| literal, variable ref, field access | synth | no |
| application, operators | synth (args checked vs. param types) | no |
| record literal | check; synth if all fields synth | no |
| `x = e` binding | synth from `e` | no |
| `if` (see §7) | check distributes; else synth + join | no |
| **lambda** | check (params pushed in); **synth ⇒ stuck** | **only when synthesized in isolation** |
| **top-level `fun` params** | synth root (standalone) | **yes, unless host-checked (§4)** |
| top-level `fun` return | inferred from body | no (but see §5: recursion) |

---

## 4. Host embedding supplies the root expected type

Klein is embedded; the host's contract for a rule (e.g. "this rule must be
`(Order) -> Bool`") is the **expected type at the root**. A rule checked against a
host contract is in *check* mode top-to-bottom: parameter types flow inward from
the contract, so **even top-level parameters need no annotation** in that mode.

The standalone CLI (`infer`) has no such context, so it falls back to the §3
table. Both are the same rule (§1) under different root contexts.

---

## 5. Top-level definitions

Top-level parameters are always declared (standalone) or host-supplied (§4), so
every signature's **input** is known up front. Processing:

1. Build the dependency graph among top-level definitions.
2. Topologically order it.
3. A **non-recursive** definition synthesizes its body *after* its callees — their
   return types are known by then.
4. A **recursive** strongly-connected component (a self-call, or a cycle of ≥ 2)
   **requires every member to declare its return type.** That makes all their
   signatures fully known before any body is checked, dissolving the cycle.

Rationale: inferring a return type from a body that calls itself would need a
fixpoint over a type variable — exactly the constraint machinery Operation Bidi removes. A
declared return type breaks the cycle locally.

*Why every member, not the minimum?* A single declared return can technically
break a simple cycle (it's a feedback-vertex-set problem), but "annotate enough
functions to make the rest acyclic" is unpredictable and yields awkward errors
("annotate at least one of `f`, `g`, `h`"). The rule is therefore the simple,
predictable one: **recursive ⟹ declare your return.** (Self-recursion forces it
regardless.) Generalizes to lambda-valued recursive bindings: they declare the
binding's type, since params + return live inside the lambda.

(Whether a given cycle is *legal* in the first place — recursion must be guarded,
not a direct recursive value — is an orthogonal, pre-existing rule, out of scope
here.)

*Implementation note:* this **repurposes `ScopeGraph`** rather than deleting it.
Its SCC pass stops serving polymorphic generalization (a global-inference concern)
and starts serving cycle detection + synthesis ordering. Update the roadmap
teardown list accordingly.

---

## 6. Generics — implicit quantification

- A `'T` appearing in a signature is **implicitly ∀-quantified** over that
  definition. `fun id(x: 'T): 'T = x` *is* `∀T. T -> T`. No `<T>` declaration on
  functions. (Type **definitions** keep explicit `<'T>` — `type Result<'T,'E>` —
  because a named type constructor must declare its parameters to be applied.)
- Inside the body, `'T` is a **rigid skolem**: passable, not inspectable. Reuses
  `TSkolem`.
- **Instantiation is by structural matching, not unification.** At a call
  `f(args)` with `f : (P₁…Pₙ) -> R`: synthesize each argument `⇒ Aᵢ`, then match
  each `Pᵢ` against `Aᵢ` (one-sided: find σ with `Aᵢ <: σ(Pᵢ)`) to build the
  substitution σ over the quantified vars. Substitute σ into `R` for the result.
  No constraint variables, no occurs-check — a structural walk.
- **Output-only variables are allowed** (return-type polymorphism). A `'T` that
  occurs in no parameter (e.g. `fun parse(s: String): 'T`) is left free by
  argument matching and **resolved from the expected type** pushed in by check
  mode. Used in synth mode with nothing to fix it, it is the §1 stuck case → error
  there, not a signature error. This is the same principle as inferred lambda
  params, in return position.
- **Scope — nearest enclosing binder.** When resolving an annotation, a `'T`
  *not already in scope* is introduced (∀-quantified) at the **nearest enclosing
  annotated binder** — a `fun` (params + return + body) or a type-annotated
  `val` (its type + initializer), **not** a plain block and **not a lambda**. A `'T` *already in scope*
  is a reference; nothing new is introduced. Each binder's annotation resolves as
  one unit (repeats within it share one skolem); each *use* of the binder
  instantiates its variables fresh. There is **no top-level special case** — top
  level is just "the nearest enclosing binder is the top-level binding," so
  `xs: List<'B> = Nil` and a `'T` inside a function body follow the identical rule.
  - Consequences: sibling `val`s get **independent** `'T`; an in-scope type-var
    name **cannot be locally shadowed** to mean a fresh one (use another letter —
    Haskell `ScopedTypeVariables` behavior); a binder-quantified `'T` never escapes
    its scope, so the old escape / generalization / name-sharing hazards do not
    arise. **Rank-1 only** — passing a polymorphic value *as an argument* (`id(id)`)
    is out of scope.
- **Lambdas and record fields are not binders.** A `'T` introduces *only* at a
  `fun` or a type-annotated `val`. A fresh `'T` inside a lambda that is not already
  in scope is an **error** (`UnboundVariable`), not a fresh quantifier. Implicit
  quantification at a *nested* site is the ambiguous case: a `'T` spanning a record-
  field boundary (`fun f(r: { g: ('X)->'X }): 'X`) would bind in two places at once.
  A quantifier at a nested site — a **polymorphic record field** or a **rank-2
  parameter** — requires explicit `forall`, which is **deferred**; records stay
  monomorphic for now. (Model: Haskell GHC2021 — implicit prenex, explicit `forall`
  only to reach a quantifier under a constructor or left of an arrow.)
- **`TForall` quantifies any body, not just functions.** A generic nullary
  constructor is a polymorphic *non-function* (`Nil : ∀A. List<A>`, `None : ∀A.
  Option<A>`), so the quantifier wraps arbitrary types. Instantiation is
  shape-agnostic — it fires at a check/apply demand whatever the body's shape.
- **No `∀` reaches the subtyper or solver.** Instantiation happens *only* at demand
  points; `isSubtype`/`lub`/`glb`/constraint-generation assert against a `∀` input.
  A still-polymorphic argument meeting a parameter that still mentions a type
  variable (`use(id)` where `use : ∀X. ((X)->X, X) -> X`) is two unknowns at once —
  unification, not the one-sided matching above — and is rejected, not floated.

---

## 7. Joins (`if`)

- In **check mode**, `if c then a else b ⇐ T` distributes: check `a ⇐ T` and
  `b ⇐ T`. No join is computed.
- In **synth mode**, synthesize both branches and resolve their results to a
  **common supertype, or error — never a silent `Any`.**

(`match` is deferred; when added it follows the same check-distributes /
synth-joins discipline over its arms.)

**Open — exact supertype computation (defer detail to M6):** for nominal sums the
join is the declared parent (`Ok` ⊔ `Err` = `Result`); for records the natural
candidate is the common-field (width) record; incompatible branches (e.g.
`Num` vs `String`) error. Whether record branches width-join silently or must
match is the one real sub-decision, left to M6. **Polymorphic branches** (both
`∀`-typed) are a further open sub-case — α-equal → that scheme, otherwise reject.
*Status:* `synthIfThenElse` does not yet compute a join at all (it only accepts a
branch that is a subtype of the other) and crashes on a `∀`-typed branch; red
targets in `IfThenElseLubTest`.

---

## 8. Unions, intersections, and `no-anonymous-unions`

Klein's standing decision is
[no-anonymous-unions](../decisions/2026-01-09-no-anonymous-unions.md): unions are
expressed as **nominal sums** via `type` declarations, which narrow cleanly to
their remaining members without negation.

**Decision for the rewrite: neither `&` nor `|` is a type connective in the core
language.**

- **`Optional` (`T?`)** stays — the one built-in tagged union, with its own
  discriminator.
- **Anonymous union (`A | B`)** — **rejected**. `UnionTypeExpr` parses today (from
  the annotation experiment); the checker rejects it, directing the author to a
  nominal `type`. The "either" capability lives in nominal sums, which supply the
  three things an anonymous union lacks: a **tag** (the constructor), an
  **elimination form** (`match`, later), and **exhaustiveness**.
- **Intersection (`A & B`)** — **deferred, not rejected on principle.** The
  "both" capability is served first by **bounded polymorphism** (`where 'T <: A,
  'T <: B`, a later milestone), which is the form that keeps every value type
  *concrete* and so stays complete and negation-free (see §6 and the rationale
  below). First-class structural intersection (a returnable `A & B`) remains a
  viable *future* feature; `IntersectionTypeExpr` is rejected for now.

### Why intersection ≠ union, and why bounds come first

This was worked out at length (2026-06-24); the durable conclusions, so they need
not be re-derived:

- **They are variance-duals.** Inferred contravariance (`computeVariance` in
  `TypeDefPreprocessor`) means a denotable *meet* forces a denotable *join*:
  `Sink<Dog> & Sink<Fish>` ≡ `Sink<Dog | Fish>`. You cannot have first-class
  structural intersection without union semantics coming along.
- **`A & B` is *not* sugar for a bound.** They coincide only at a parameter
  position (∀-negative = ∃-at-the-call); in return position they diverge —
  structural `A & B` is existential ("I have a value that is both"), the bound is
  universal ("for any both-type the caller picks"). So intersection can't be
  desugared away.
- **The hard direction is intersection-on-the-left** (`A & B <: C`), which
  OR-decomposes and is incomplete in the *combine-case*. Bounded poly sidesteps it
  entirely: `'T` instantiates to a **concrete** type, so no `&` node ever reaches
  the left. That is why bounds are complete and come first.
- **First-class intersection is nonetheless feasible** (it is *not* an
  MLstruct/negation requirement), via **Scala-2-style target-shape dispatch**:
  intersection-on-left against a **nominal** target → OR-trial (complete, because
  nominal subtyping can't combine); against a **structural** target → **merge**
  member sets (complete). Contravariant merged members resolve their parameter to
  a nominal LUB or **error** — exactly the §7 join policy — never a denoted union.
- **Klein would construct intersections better than Scala.** Scala's class-instance
  runtime makes intersection *introduction* painful (mix-in subclassing, illegal
  for case classes). Klein's structural records make it **tag-preserving,
  add-only record extension** (`{ ...dog, fur: "brown" }` : `Dog & {fur}`), which
  also collapses the everyday case to a single concrete tagged record (subtyping =
  width + tag). The bill is *value semantics* (equality, serialization, `match`
  exhaustiveness over records with extra fields), not type soundness.

**Sequencing:** build the monomorphic core → generics → **bounded polymorphism**
(the near-term "both" mechanism); revisit first-class structural intersection as a
later feature once bounds exist and most of its value is already covered.

---

## Open questions

1. **Record-branch joins** — silent width-join vs. must-match. (§7; → M6)
2. **First-class structural intersection** — deferred candidate (§8). Revisit after
   bounds: target-shape-dispatch subtyping + tag-preserving record extension. Carries
   a value-semantics bill (equality / serialization / `match` over extended records).
3. **Polymorphic `if` branches** — how to join two `∀`-typed branches (α-equal → that
   scheme, else reject). (§7; `IfThenElseLubTest`)

*Resolved:* anonymous `A | B` rejected (nominal sums); `A & B` deferred in favour of
bounded polymorphism (§8).
