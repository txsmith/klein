# Rigid Type Variables in Annotations

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

## Implementation

Skolems are implemented as opaque base types in the constraint solver, similar to nominal types:

- **`TSkolem(id, name)`** — A new `SimpleType` variant. Two skolems are compatible only if they're the same skolem.
- **Constraint solving** — Uses existing machinery. `constrain(TSkolem, TNum)` fails (incompatible types), `constrain(TSkolem, TVar)` adds the skolem as a bound (same as any base type).
- **Annotation resolution** — `resolveTypeExpr` creates skolems for `'A` instead of fresh tvars. A name-to-skolem map is maintained per function scope so `'A` in params, return type, and body annotations share the same skolem.
- **Local annotation validation** — when resolving a type variable in a local annotation, it must already exist in the enclosing function's skolem scope. If not, report an error.
- **Top-level bindings** — type variables in top-level annotations create skolems that are free and get generalized via let-polymorphism.
- **Type printing** — Skolems print as `'A`, `'B`, etc.

## Consequences

**Positive:**
- Annotations are real contracts — body and callers can trust them
- Consistent with "the annotation IS the type" principle
- Simple mental model: `'A` in an annotation means "for any type"
- No scoping ambiguity — signature is the single source of type parameters
- No need for `forall` keyword

**Negative:**
- No partial annotation support for type variables (wildcards could fill this gap later)
- Departure from MLscript's approach — can't directly follow their implementation for this part
- Can't introduce new type variables in local annotations (explicit quantification syntax could lift this restriction later)

## Future

If the restriction on local type variables proves too limiting, explicit quantification syntax could be added to introduce locally-scoped type variables — analogous to Haskell's `ScopedTypeVariables` with explicit `forall`.
