# Host Interface Evolution

Klein programs are compiled once and stored; the host environment they were compiled
against keeps evolving. This doc is about what happens to the stored programs when it does.

## The trigger program

The host provides `currentShape: Shape`. A stored rule matches on it:

```klein
type Shape = Circle { radius: Num } | Square { side: Num }

match currentShape
  Circle { radius } -> radius
  Square { side } -> side
```

The host upgrades and now declares `currentShape: Circle` — a *tightening*: `Circle <: Shape`.

Verdicts:

- The **compiled artifact keeps running, correctly, forever.** Compiled Core never re-consults
  the checker; it only needs the value at its imported name to have the shape it was compiled
  against, and every `Circle` has it.
- **Re-checking the source now fails.** The `Square` arm is unreachable, and unreachable arms
  are a hard error. The program was correct when written; the world improved underneath it.

Those are two different guarantees, and the design below keeps them separate.

## The environment declares its type

The host environment's type is a record: one field per binding it provides.

```
E = { currentShape: Shape, creditCheck: (Customer) -> Score, today: () -> Date }
```

Each new host version declares its environment type, and we keep the chain of versions.
Compatibility between versions is Klein's ordinary subtyping, nothing new:

- **Width**: adding bindings is always compatible.
- **Depth**: each binding may tighten (`Circle <: Shape`).
- **Functions**: a provided function may accept *wider* arguments and return *narrower*
  results (standard variance).

## The gate: a fast subtype check, no old file required

Each stored artifact records the extern names it uses and the types it assumed for them —
its own private slice of the environment it was compiled against. The gate for one
artifact is: does the new environment provide every assumed name at a compatible
(subtype) type? Computed from the artifact's own records, no source parsing, no old
`host.klein` — which matters because there is no single old environment anyway: a real
rule base contains artifacts compiled against many historical versions.

If the gate holds for an artifact, it stays sound as compiled. Checking the whole corpus
is one cheap pass over recorded assumptions. (`E_new <: E_old` between two module files
remains available as `env-diff`, a developer convenience for the editing loop — if it
holds, every artifact's gate holds too.)

The gate is a fast certificate, not a wall. A change that fails it for some artifact
(say, removing a binding that artifact never actually depends on in a breaking way) may
still re-check clean; the re-check below is the truth.

## The re-check: source-level truth, program by program

On upgrade, re-check every stored program's *source* against `E_new` —
`Klein.recheck(newEnv, artifacts)`, a library call made by whoever holds the corpus: the
host's backend for the production rule base (it owns the database and embeds the library
anyway), or the CLI over a directory of artifact files for CI and host-dev machines. This
answers the question the gate cannot: does the source still compile? It runs the real
checker, so it catches everything the checker knows — including strictness rules like arm
reachability that type-level subsumption cannot see (the trigger program above is exactly
such a case).

- Gate green for an artifact: its re-check can run lazily, in the background. By the
  classification below, it can only produce warnings.
- Gate red: the re-check is the impact analysis. Its flagged list — which programs, which
  errors — routes to the rule authors.

The artifact's recorded import list also serves as a prefilter (the database's
`pg_depend` move): only artifacts whose assumptions mention a changed name — or a type
reachable from one — need re-checking at all.

This requires storing the surface source (or something re-checkable) alongside compiled
Core for every rule. That is a hard requirement on the checked-program artifact.

## Errors and warnings: severity belongs to the context

At authoring time, a dead arm is the author's mistake — an error, caught while they can act.
At env-upgrade time, the same dead arm means the world changed under a correct program —
failing the rule punishes its author for the host's improvement. So:

- The **checker classifies** each diagnostic once, by what it means.
- The **caller assigns severity**: authoring treats everything as an error; the evolution
  re-check downgrades one class to warnings.

The classification criterion: *could this program misbehave at runtime?*

- **Soundness**: yes — wrong shapes reaching the machine, no arm matching, unfilled cells.
  Errors in every context.
- **Degeneracy**: no — the program is verified sound but provably pointless somewhere: an
  arm that can never fire, a comparison that is always false. Error when authoring, warning
  on evolution re-check.

Warnings need a destination: the re-check's per-program warning list is a maintenance queue
for rule authors ("your `Square` arm went dead in the March host upgrade"). Dead code that
tooling reports is debt; dead code that silently compiles forever is a lie in the rule base.

### The second face of degeneracy

Dead arms are not the only diagnostic that flips meaning under tightening:

```klein
# E_old: hostShape: Shape
hostShape == Square(1)
```

Legal under `E_old`. After tightening to `hostShape: Circle`, the equality rule rejects the
comparison (`Square` cannot be used as `Circle`) — but the artifact is perfectly sound: the
comparison returns `false` forever. "Provably always false" is the same moral fact as
"provably never fires".

Today this surfaces as a generic `TypeMismatch`, which is a problem: after a *breaking* env
change, the re-check also produces genuine soundness `TypeMismatch`es, so the same variant
would need opposite verdicts in the same context. Classification must therefore be decided
at the emission site: the equality rule gets its own variant (`IncomparableEquality`),
classified degeneracy; all remaining `TypeMismatch` emissions are soundness.

### Classification of every current diagnostic

**Soundness — error in every context.** Each maps to a concrete machine failure.

| Diagnostic | Runtime failure it prevents |
|---|---|
| `UnboundVariable` | no cell to read |
| `TypeMismatch` (all sites except equality) | wrong shape reaching a machine operation |
| `CannotJoinBranches`, `CannotJoinMatchArms` | untyped value flowing downstream |
| `MissingField`, `NotARecord` | field access on the wrong shape |
| `NotAFunction`, `CallArityMismatch` | application of a non-closure / wrong arity |
| `NullNotAllowed` | null reaching a non-optional position |
| `RecursiveVal` | reading a cell that can never fill |
| `RefutableBinding` | destructure of a non-matching value |
| `NonExhaustiveMatch` | no arm matches |
| `CannotMatchOn` | matching a value with no match semantics |

**Verification failure / well-formedness — also error everywhere.** These say "cannot be
certified" or "malformed declaration", not "will misbehave"; warnings are only for verified
code, so these stay fatal: `MissingParamAnnotation`, `RecursiveFunctionNeedsReturnType`,
the four `ImplicitParam*` errors, `DuplicateField`, `DuplicateParameter`,
`DuplicateBinding`, `TypeArityMismatch`, `UndeclaredTypeParam`, `ShadowsBuiltinType`,
`AnonymousUnionType`, `AnonymousIntersectionType`.

None of these can be produced by an env change at all — if the evolution re-check ever
emits one, the pipeline itself is broken. (For severity policy they behave like soundness;
the separate bin exists for that diagnostic-hygiene property.)

**Degeneracy — severity assigned by context.**

| Diagnostic | Why it is sound to run anyway |
|---|---|
| `UnreachableMatchArm` | the arm never fires; the machine compiles and skips it |
| `NotAConstructorOf` | an arm naming a constructor the (narrowed) scrutinee cannot be — it never fires |
| `IncomparableEquality` (to be split from `TypeMismatch`) | the comparison evaluates, always to `false` |

`NotAConstructorOf` is dual-natured — at authoring time it is usually a typo — but needs no
split: the authoring context maps degeneracy to error anyway, so typos still fail.

New diagnostics must declare their class at birth; making the classification an abstract
property of the sealed `TypeError` forces this.

### Monotonicity, verified variant-by-variant

Walking the soundness list asking "can *tightening* newly trigger this in previously-clean
source?" comes up empty: fields cannot vanish (width subtyping adds fields going down),
arity is preserved by function subtyping, provided-function params only widen (so
`NullNotAllowed` cannot newly fire), narrower scrutinees need fewer arms (so
`NonExhaustiveMatch` cannot), and joins survive narrowing. So under a green gate the
re-check can only produce degeneracy diagnostics — which is exactly what makes the
downgrade-to-warning safe. A soundness error appearing under a green gate means a bug in
the gate, not in the rule.

Caveat: "joins survive narrowing" is an argument about the current lattice, not a proof.
First-class unions (committed but deferred) could disturb it; revisit when they land.

## Link-time mechanics

Independent of evolution, two pieces make stored artifacts robust to *any* host change:

- **The import table.** The lowerer assigns root-scope slots only for the host names the
  program actually references, in its own order, and the artifact carries the name list in
  slot order. At start, the runtime walks the table and fills each cell by name from the
  host's bindings. Slots are program-relative: the host adding or reordering bindings can
  never corrupt a read, and a missing name fails by name, at link time, before any
  evaluation.
- **The env version stamp.** Each artifact records which environment version it was
  compiled against. The runtime refuses to link an artifact whose version chain to the
  current env ever went red without a recompile in between.

## Upgrade policy, in tiers

1. **Gate green** — ship immediately; artifacts untouched; background re-check may add
   maintenance warnings to the queue.
2. **Gate red, source re-checks clean under `E_new`** — recompile in place, no author
   involvement.
3. **Gate red, source fails** — route to the rule's author with the type errors. The
   re-check's flagged list *is* the impact analysis ("which of our stored rules does this
   signature change break?" is a query, not archaeology).

## Open questions

- Whether the gate should distinguish "red because a binding was removed" from "red because
  a type loosened" — the re-check makes the distinction observable, so probably not.
- A proof or test suite for join-monotonicity under narrowing.

Resolved since first draft: how the environment is declared and implemented — it is a
Klein module with `extern` members; see [host-environment.md](./host-environment.md).
Host-provided data enters as `extern val`s linked at start (the import table mechanism
above, applied to the `extern val` subset).
