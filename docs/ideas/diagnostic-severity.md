# Diagnostic Severity

How the checker's findings become errors or warnings. The checker **classifies** each
diagnostic once, by what it means; the **caller assigns severity** by context. Two
contexts exist today:

- **Authoring** — a rule is being written or edited (editor, CLI). Everything is an
  error: the author is present and can act.
- **Evolution re-check** — reconciliation recompiles an existing rule because a
  capability it uses changed (see
  [host-integration.md](../spec/host-integration.md)). One class is downgraded to warnings.

## The trigger program

A capability provides `currentShape: Shape`. A stored rule matches on it:

```klein
type Shape = Circle { radius: Num } | Square { side: Num }

match currentShape
  Circle { radius } -> radius
  Square { side } -> side
```

The capability tightens to `currentShape: Circle` — a compatible narrowing
(`Circle <: Shape`). Existing editions keep running, correctly, forever. But re-checking
the source now finds the `Square` arm unreachable — a hard error at authoring time. The
program was correct when written; the world improved underneath it. Failing the rule
would punish its author for someone else's upgrade; saying nothing would let dead code
accumulate silently. Hence: warning, reported to the author.

## The criterion

*Could this program misbehave at runtime?*

- **Soundness** — yes: wrong shapes reaching the interpreter, no arm matching, unfilled
  cells. Errors in every context.
- **Degeneracy** — no: the program is verified sound but provably pointless somewhere —
  an arm that can never fire, a comparison that is always false. Error when authoring,
  warning on evolution re-check.

## The second face of degeneracy

Dead arms are not the only diagnostic that flips meaning under a tightening:

```klein
# capability: hostShape: Shape
hostShape == Square(1)
```

Legal today. After tightening to `hostShape: Circle`, the equality rule rejects the
comparison (`Square` cannot be used as `Circle`) — but the edition is perfectly sound:
the comparison returns `false` forever. "Provably always false" is the same moral fact
as "provably never fires".

Today this surfaces as a generic `TypeMismatch`, which is a problem: after a genuinely
breaking change, the re-check also produces real soundness `TypeMismatch`es, so the same
variant would need opposite verdicts in the same context. Classification must be decided
at the emission site: the equality rule gets its own variant (`IncomparableEquality`),
classified degeneracy; all remaining `TypeMismatch` emissions are soundness.

## Classification of every current diagnostic

**Soundness — error in every context.** Each maps to a concrete runtime failure.

| Diagnostic | Runtime failure it prevents |
|---|---|
| `UnboundVariable` | no cell to read |
| `TypeMismatch` (all sites except equality) | wrong shape reaching an interpreter operation |
| `CannotJoinBranches`, `CannotJoinMatchArms` | untyped value flowing downstream |
| `MissingField`, `NotARecord` | field access on the wrong shape |
| `NotAFunction`, `CallArityMismatch` | application of a non-closure / wrong arity |
| `NullNotAllowed` | null reaching a non-optional position |
| `RecursiveVal` | reading a cell that can never fill |
| `RefutableBinding` | destructure of a non-matching value |
| `NonExhaustiveMatch` | no arm matches |
| `CannotMatchOn` | matching a value with no match semantics |

**Verification failure / well-formedness — also error everywhere.** These say "cannot be
certified" or "malformed declaration", not "will misbehave"; warnings are only for
verified code, so these stay fatal: `MissingParamAnnotation`,
`RecursiveFunctionNeedsReturnType`, the four `ImplicitParam*` errors, `DuplicateField`,
`DuplicateParameter`, `DuplicateBinding`, `TypeArityMismatch`, `UndeclaredTypeParam`,
`ShadowsBuiltinType`, `AnonymousUnionType`, `AnonymousIntersectionType`.

None of these can be produced by a dependency change at all — if the evolution re-check
ever emits one, the pipeline itself is broken. (For severity policy they behave like
soundness; the separate bin exists for that diagnostic-hygiene property.)

**Degeneracy — severity assigned by context.**

| Diagnostic | Why it is sound to run anyway |
|---|---|
| `UnreachableMatchArm` | the arm never fires; the interpreter compiles and skips it |
| `NotAConstructorOf` | an arm naming a constructor the (narrowed) scrutinee cannot be — it never fires |
| `IncomparableEquality` (to be split from `TypeMismatch`) | the comparison evaluates, always to `false` |

`NotAConstructorOf` is dual-natured — at authoring time it is usually a typo — but needs
no split: the authoring context maps degeneracy to error anyway, so typos still fail.

New diagnostics must declare their class at birth; making the classification an abstract
property of the sealed `TypeError` forces this.

## Monotonicity under tightening, verified variant-by-variant

Claim: when a dependency's type *tightens* (narrows compatibly) and the rule's source is
unchanged, the re-check can only produce degeneracy diagnostics — never a new soundness
error. Walking the soundness list asking "can tightening newly trigger this in
previously-clean source?" comes up empty: fields cannot vanish (width subtyping adds
fields going down), arity is preserved by function subtyping, provided-function
parameters only widen (so `NullNotAllowed` cannot newly fire), narrower scrutinees need
fewer arms (so `NonExhaustiveMatch` cannot), and joins survive narrowing. This is what
makes the downgrade-to-warning safe: a soundness error appearing after a
compatible-looking change means a bug in the compatibility reasoning, not in the rule.

Caveat: "joins survive narrowing" is an argument about the current lattice, not a proof.
First-class unions (committed but deferred) could disturb it; revisit when they land.
