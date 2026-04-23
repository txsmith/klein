# Rigid Type Variables and Bounded Polymorphism in Annotations

**Date:** 2026-04-12

## Context

Type annotations in Klein can contain type variables: `fun f(x: 'A): 'A = x`. The question is what `'A` means — is it a **flexible** inference variable that the body can constrain, or a **rigid** (skolem) variable that the body must work with abstractly?

## Decision

**Type variables in annotations are rigid (skolems).** The body of a function must be valid for *any* instantiation of the type variable. The annotation is a contract.

```klein
fun f(x: 'A): 'A = x        # ok: works for any 'A
fun f(x: 'A): 'A = x + 1    # error: can't assume 'A supports +
fun f(x: 'A): 'A = 42        # error: Num is not 'A
```

**Type variables in local annotations must be introduced in the enclosing function signature.** A type variable that appears in a local binding or ascription but not in the function's parameter or return type annotations is an error.

```klein
fun f(x: 'A) =
  xs: List<'A> = Cons(x, Nil)   # ok: 'A is from the signature
  xs

fun f(x) =
  xs: List<'B> = Nil             # error: 'B not introduced in signature
  xs
```

**At the top level**, type variables in annotations are universally quantified via let-generalization — there is no enclosing function to scope them to.

```klein
xs: List<'B> = Nil       # ok: 'B is generalized, xs : forall 'B. List<'B>
ys = xs ++ listOf(42)    # ok: 'B instantiated to Num
```

### Union and intersection types

Klein allows `A | B` and `A & B` in annotations, subject to a polarity restriction:

- **Union `A | B`** is allowed only in **positive** (output) positions.
- **Intersection `A & B`** is allowed only in **negative** (input) positions.

These rules apply uniformly regardless of whether the operands are concrete types, type variables, or a mix.

```klein
fun foo(): Num | String = ...                   # ok: union in output
fun foo(x: HasName & HasAge) = ...              # ok: intersection in input
fun dogCat(x: 'A): 'A | Dog = ...               # ok: union with TVar operand in output
fun groom(x: 'A & Dog) = ...                    # ok: intersection with TVar operand in input

fun foo(x: Num | String) = ...                  # error: union in input
fun foo(): Num & String = ...                   # error: intersection in output
```

Polarity is determined by walking the annotation and flipping at each function-arrow argument position. Tuple/record/applied-type parameters inherit the surrounding polarity (covariant by default).

### Bounded polymorphism via intersection

Bounded polymorphism — "a type variable constrained to some supertype" — is not a separate construct. It emerges naturally from an intersection involving a type variable in negative position:

```klein
fun feed(a: 'A & Animal) = ...    # 'A is some Animal
```

Algebraically, `'A & Animal` in input position is the meet of 'A and Animal. MLSub's lattice represents this identically to "TVar 'A with upper bound Animal" — the same TVar either way, carrying the same bound set. So writing `'A & Animal` in a parameter is equivalent to declaring 'A with an upper bound of Animal: the body can call Animal methods on `a`, and callers must supply a value that is a subtype of Animal.

No special parser carve-out is needed and no new type-system machinery is introduced — bounded polymorphism is what the intersection rules already imply when a TVar is one of the operands.

## Rationale

### Why rigid?

Annotations in Klein follow the principle "the annotation IS the type" — they establish contracts for both the body and callers. With flexible type variables, this contract breaks:

```klein
fun f(x: 'A, y: 'A) = x + y
```

If `'A` is flexible, inference silently constrains it to `Num`, giving `(Num, Num) -> Num`. The signature *looks* polymorphic but isn't — it lies to the reader. With rigid `'A`, this is a type error: you promised to work for any `'A` but assumed it supports `+`.

Rigid type variables also mean that the return type annotation is a real contract. `fun f(x): 'A = 42` is an error because the body doesn't return an arbitrary `'A` — it returns `Num`. Without rigidity, annotations on type variables would be decorative.

### Why require signature introduction?

Allowing type variables in local annotations that aren't in the function signature creates scoping ambiguities:

- **Escape problem**: a local skolem that flows into the return type is either an error (too restrictive — blocks returning locally-annotated polymorphic values) or gets promoted to a function-level type parameter (surprising — a local annotation silently changes the function's signature).
- **Generalization inconsistency**: at the top level, a local type variable gets generalized via let-polymorphism. Inside a function, the same code would behave differently depending on whether the variable is scoped to the function body or generalized at the binding site.
- **Name sharing ambiguity**: if `'B` appears in multiple local annotations, should they share the same skolem (scoped) or each be independently generalized? Both answers are surprising in some cases.

Requiring signature introduction avoids all of these: the function signature is the single source of truth for type parameters, and local annotations can only reference what's already declared. This matches the Rust/Scala approach.

### Why not flexible (MLscript's approach)?

MLscript treats bare type variables in annotations as fresh inference variables. This works in MLscript because it has `forall` for explicit universal quantification:

```
def foo(f: forall 'a. 'a -> 'a) = (f 1, f true)
```

Without `forall`, bare `'a` is just shorthand for "some type I don't care about" — a partial annotation. The flexible approach is a side effect of MLscript's design, not a feature goal.

Klein does not have `forall`. If type variables were flexible, there would be no way to express "this must work for any type" — universal polymorphism in annotations would be impossible. Rigid by default is the only option that makes `'A` worth writing.

### What about partial annotations?

The one use case for flexible type variables is partial annotations — specifying some structure while leaving parts to inference:

```klein
xs: List<'A> = someExpression  # "I know it's a List, infer the element type"
```

With rigid semantics, this means "for any `'A`", which would fail if the expression produces `List<Num>`. If partial annotations prove useful, a wildcard syntax (`List<_>`) is a better fit — it clearly means "infer this part" without overloading the meaning of type variables.

### Prior art

Haskell's default (each annotation independently quantified, type variables are implicitly universally quantified) makes bare type variables rigid within their scope. The `ScopedTypeVariables` extension (enabled by essentially every project, included in GHC2021) extends the rigid scope to the function body — matching Klein's scoping decision. Haskell's experience shows that scoped type variables are the right default, and that local type variable introduction without explicit quantification leads to confusion.

Rust, Scala, and other languages require type parameters to be declared at the function signature level. Local annotations can only reference existing type parameters.

### Why bounded polymorphism is just intersection-in-negative-position

Bounded polymorphism in Klein is not a separate feature — it's what intersection-in-negative-position already means when a TVar is one of the operands.

Writing `'A & Animal` as a parameter type is literally an intersection: the parameter's type must be <: 'A and <: Animal. In the constraint solver, this is represented as a rigid TVar 'A carrying Animal in its upper-bound set. The body can use the parameter as Animal (the bound makes it so); callers must supply a value that is a subtype of Animal. That is exactly the behavior of a bounded type parameter — no special syntax, no separate construct.

The symmetric reasoning applies to `'A | T` in output position: it's just a union whose operands happen to include a TVar, permitted by the polarity rules. The `dogCat` example is an instance.

Intersection in output position (e.g. `fun makePuppy(): 'A & Dog = ...`) is rejected by the same polarity rule that rejects `Num & String` in output position. The reason is in the next section — it's a limitation of what MLSub's bound-based representation can faithfully encode, not a missing piece of surface syntax.

### Why the polarity restriction on anonymous unions and intersections?

The restriction falls directly out of what MLSub's bound-based representation can faithfully express.

Klein represents types with TVars that carry lower-bound *sets* (a join — things that flow in) and upper-bound *sets* (a meet — things that flow out). This cleanly encodes:

- **Input-position intersection** `A & B` → a TVar with upper bound set `{A, B}`. Any value flowing in must be a subtype of both, i.e. of the meet. The solver enforces this by requiring each flowing lower bound to satisfy each upper bound.
- **Output-position union** `A | B` → a TVar with lower bound set `{A, B}`. The value flowing out is at least one of A or B, i.e. their join. Consumers see the join of the lower bounds as what's produced.

The wrong-polarity cases *cannot* be faithfully encoded:

- **Input-position union** `Dog | Cat` would mean "the parameter equals `Dog | Cat` exactly" — an *upper* bound that is itself a join. MLSub cannot express `α <: A ∨ B` as a single bound; it's a disjunctive constraint. The closest encoding (TVar with lower bounds `{Dog, Cat}`) only says "α is at least `Dog | Cat`," which fails to exclude values outside the union — a `Fish` passed to `fun f(x: Dog | Cat)` would silently slip through.
- **Output-position intersection** `Num & String` symmetrically: without a matching lower bound, nothing forces the body to actually produce a value in the intersection. The claim `fun f(): Num & String` would type-check for bodies the user never intended.

In short: intersections and unions in their natural polarities are first-class citizens of the bound algebra; in the wrong polarities they would require disjunctive constraints (outside SimpleSub's solver) or silently widen the user's type (unsound relative to user intent). Rejecting wrong-polarity uses at annotation resolution is the principled choice.

## Implementation

Skolems are rigid TVars — ordinary TVars with a `rigid` flag that freezes their bounds against extension by inference. The constraint solver reuses its existing bound-propagation machinery:

- **Rigid TVars** — A TVar with `rigid = true`. Its bounds are set at annotation-resolution time and are not extended by constraint accumulation. Constraints involving a rigid TVar *check* against its existing bounds rather than *extend* them. This subsumes the earlier `TSkolem` design: rigid-with-no-bounds ≡ a skolem, rigid-with-upper-bound ≡ a bounded skolem.
- **Annotation resolution** — `fromTypeExpr` creates rigid TVars for each unique `'A` name. A name-to-TVar scope is maintained per enclosing function so `'A` in params, return type, and body annotations share the same rigid TVar. Intersections involving a TVar in negative position accumulate the non-TVar operand(s) as upper bounds of the TVar; unions with a TVar in positive position analogously for lower bounds.
- **Polarity check** — `fromTypeExpr` tracks polarity through the annotation walk. `A | B` in negative position and `A & B` in positive position are rejected with a clear error, regardless of whether the operands are TVars or concrete types. The rules are uniform.
- **Local annotation validation** — when resolving a type variable in a local annotation, it must already exist in the enclosing function's scope. If not, report an error.
- **Top-level bindings** — type variables in top-level annotations create rigid TVars that are free and get generalized via let-polymorphism.
- **Type printing** — Rigid TVars print as `'A`, `'B`, etc. The printer reconstructs intersection/union surface syntax when a TVar has bounds that came from user-written operators.

## Consequences

**Positive:**
- Annotations are real contracts — body and callers can trust them.
- Bounded polymorphism emerges naturally from the polarity rules — no separate construct, no F<: complexity.
- Uniform polarity rules with no special cases: `|` in positive position, `&` in negative position, regardless of operand shape.
- Consistent with "the annotation IS the type" principle.
- Simple mental model: `'A` means "for any type"; `'A & T` in input means "for any type satisfying T"; `'A | T` in output means "either an 'A or a T."
- No scoping ambiguity — signature is the single source of type parameters.
- No need for `forall` keyword.

**Negative:**
- No partial annotation support for type variables (wildcards could fill this gap later).
- Departure from MLscript's approach — can't directly follow their implementation for this part.
- Can't introduce new type variables in local annotations (explicit quantification syntax could lift this restriction later).

## Future

- **Explicit quantification syntax** — if the restriction on local type variables proves too limiting, add explicit `forall` analogous to Haskell's `ScopedTypeVariables`.
- **Pattern matching** — unlocks the `Dog | Cat` input case via branch-narrowing rather than annotation widening.
- **Wildcard type syntax** (`List<_>`) — partial annotations without overloading type variables.
