# Capability Contracts

A **contract** declares what a host application provides to Klein programs: type definitions and
signatures, with no implementations. Klein reads and checks a contract as its own language, and
what rules can see is governed by the contract's releases.

This spec describes the contract language as v1 must deliver it. Each section carries a status:
**implemented** means the test suites enforce it today; **target** means it is settled design the
implementation still has to reach. The system around the language — environments, editions, pins,
reconciliation — is [host-integration.md](./host-integration.md).

## The file

Status: implemented.

```klein
type Customer = Customer { id: Num, name: String, score: Num }

fun creditCheck(c: Customer): Num
maxRetries: Num
```

Declarations without definitions: a `fun` header with no `= body`, a binding with no `= value`.
The syntax is ordinary Klein with the definition removed — no keyword, no new tokens; see
[grammar.md](../grammar.md) for the productions and their disambiguation.

In spirit this is an OCaml `.mli` or a TypeScript `.d.ts`, but closer in role to an IDL: it is
not the interface *of* some implementation file, it is a standalone declaration of a boundary.
Any host language can therefore produce and consume one through the parser already in the
library, without a host-language-specific API.

## Two grammars, one lexer

Status: implemented.

A contract and a rule are disjoint languages. They share a lexer and a type grammar, and nothing
else — not a root, not an entry point. `Parser.parseProgram` reads the one that runs;
`Parser.parseContract` reads the one that only declares:

| Form | `parseProgram` (rule) | `parseContract` (contract) |
|-----------|-------------------------|----------------------------------|
| `type_def` | allowed | allowed |
| `fun_decl`, `val_decl` | parse error | allowed — this is the point |
| `fun_def`, `binding` | allowed | parse error |
| bare `expr` | allowed — it is the result | parse error |
| `release` | parse error | allowed |
| `/N` anywhere | parse error | allowed |

Each direction for one reason:

- A **declaration in a rule** has nothing to run. Only a contract's declarations are answered
  by something outside the rule.
- A **definition in a contract** computes, and a contract declares rather than computes.
- A **bare expression in a rule** is its result; a contract has no result.

Because the parser knows which language it is reading, each of these is caught at the place it is
written rather than diagnosed later. Until parser recovery lands they are **first-offence** errors:
a file with three mistakes reports one.

The one form that genuinely overlaps is a type reference, and it is parameterised rather than
moded — a rule's type expression has no slot to write a revision into, so `RevisionInProgram` is
not a diagnostic the checker carries but a state the AST cannot represent.

## Revisions

Status: implemented.

A declaration may carry a revision — `type Customer/2`, `fun creditScore/2`, `maxRetries/2` — so
two incompatible versions coexist in one file while the old one drains. The syntax is in
[grammar.md](../grammar.md); what the checker does with it is one rule:

**The key of a declaration is (name, revision), and a bare declaration is revision 1.**

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer): Num
fun creditScore/2(c: Customer/2): Num
```

Everything follows from that key. `Customer` and `Customer/2` are two entries in the environment,
so they coexist; declaring `Customer/2` twice, or `creditScore` alongside `creditScore/1`, is
`DuplicateBinding`. A revised type definition revises its constructors with it — `Customer/2`'s
constructor is `Customer/2`, and `type Shape/2 = Circle { … } | Square { … }` yields `Circle/2`
and `Square/2` — which is what lets both revisions of a type keep the same constructor names. Two
revisions of a type are unrelated nominal types: nothing is inherited, and neither is a subtype
of the other.

A revised signature is checked exactly like any other: its parameter and return types resolve
against the revisions they name (`Customer/3` with no such declaration is `UnboundVariable`), and a
revised capability may not carry a function.

**A built-in type has no revisions.** A revision names one version of *declared* vocabulary, and
`Num`, `String`, `Bool`, `Unit`, `Any` and `Nothing` are never declared — a contract cannot define
them at any revision. So `Num/2` is `RevisionOnPrimitive`, in every position a type may appear, and
so is `Num/1`: as with a rule writing `/N`, the offence is writing the syntax at all rather than the
number it names. Bare `Num` is of course unaffected.

Revision syntax is contract-only. A rule that writes `/N` — in a type position or anywhere else —
is a parse error, and a written `/1` is rejected the same as any other: the offence is the syntax,
not the number. What a rule sees is governed entirely by releases, below.

## Releases

Status: implemented.

A **release** is a numbered set of pointers. Each pointer aims a name that rules may write at one
declared revision. Rules never write a revision themselves; the release decides which revision
each name means.

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer/1): Num
fun creditScore/2(c: Customer/2): Num

release 1
  Customer
  creditScore

release 2
  Customer/2
  creditScore/2
```

A release block is the word `release`, a positive number, and one indented line per pointer. Each
line names a declaration and nothing else. Every rule is checked against exactly one release.
Inside a release each name means exactly one revision, so a rule can never meet two versions of
the same type, and no `/N` marker ever appears in a rule or in a diagnostic. Which release a given
rule uses is recorded by the host rather than written in the rule; see
[host-integration.md](./host-integration.md).

Release numbers only increase, and a number is never reused once it has been used.

### Each release states only what changed

The oldest block in the file states its release completely. Every later block states only what
that release changes, and inherits everything else from the release before it. So `release 2`
above exposes `Customer` at revision 2 and `creditScore` at revision 2, and would inherit any name
it did not mention.

This keeps a block the size of the change rather than the size of the contract, and it makes the
one line that moved the obvious thing to review in a diff.

Reading a release means starting at the oldest block and applying each one in turn. That stays
short because retiring a release folds it into its successor — see below.

### A contract's releases are exactly its written blocks

There is no implicit release. A contract that writes no block has no release: it is a perfectly
good contract, and it simply has nothing a rule can be checked against. A newly declared revision
likewise cannot be reached by any rule until a block points at it.

### Adding and removing names

Naming a declaration a release did not previously expose adds it. So a brand new capability
reaches rules by being named in a block, and a declaration named in no block at all is simply not
available to rules — which is how a capability can be declared and implemented ahead of the
release that will expose it.

Taking a name away is written out, because saying nothing means "unchanged":

```klein
release 4
  Customer/3
  remove creditScore
```

From release 4 on, no rule can write `creditScore`. This does not delete anything: the
declarations remain, the host keeps serving them, and rules on earlier releases carry on using
them. It removes the name from the vocabulary of new work only.

### A release must be self-contained

Both halves of "reachable from anything it exposes" are wider than a capability's parameter and
result types. The root is **anything the release exposes**, capabilities and types alike, because
an exposed type is vocabulary in its own right: a rule can annotate with it whether or not a
capability mentions it. And the walk is **transitive through constructor fields**, because a field
one level down is just as reachable and just as unspellable — a rule holding a `Customer` can read
`.addr`, and match a `Shape` to reach a `Circle`'s. Constructors travel with their type, so they
need no entry of their own; a recursive type terminates on a visited set.

Every type reachable from anything the release exposes must itself be exposed by that release, at
that same revision. Anything else is a check error. This is judged on what the release exposes once
its block has been applied to the ones before it, not on the block alone.

The walk starts at every entry in the release's surface — capability declarations and type
definitions alike — and follows:

- a capability's parameter and result types;
- a record's field types, an optional's inner type, and every type argument of a generic;
- for a named type, the field types readable through the type itself and the field types of each
  of its constructors.

The walk is transitive, and terminates on a visited set of `(name, revision)`, so a recursive type
is not an error. A type's constructors are exposed by the entry that exposes the type and take no
entry of their own. Built-in types need no entry. Each reachable `(name, revision)` the release
does not expose is reported separately.

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer/1): Num
fun creditScore/2(c: Customer/2): Num

release 1
  Customer
  creditScore

release 2
  creditScore/2        # error: this release exposes a capability taking Customer/2,
                       # but it still inherits Customer pointing at revision 1
```

Adding `Customer/2` to the block fixes it. Without that, a rule on release 2 could call
`creditScore` and be handed a value whose type it has no way to write down, and a diagnostic about
that value would have no legal spelling to print for it.

### Constructors travel with their type

A type reaches rules through the release that points at it, and its constructors come with it.
Constructors are never pointed at individually.

```klein
type Shape = Circle { r: Num } | Square { side: Num }
type Shape/2 = Circle { r: Num } | Square { side: Num } | Triangle { base: Num, height: Num }

release 2
  Shape/2
```

A rule on release 1 matches `Circle` and `Square` and is exhaustive. The same rule on release 2 is
not, because `Shape` there has three constructors — and it says so using the plain names, since
`Triangle/2` is not something a rule can write.

### Changing a release

The newest block may be edited in place, and that is the ordinary way to carry a mechanical
change — one where the shape moves but the meaning does not. `Customer` gains a field, so a new
revision is declared and the newest block is re-pointed at it:

```klein
type Customer/3 = Customer { id: Num, tier: String, region: String }

release 2
  Customer/3           # was Customer/2
  creditScore/2
```

No rule moves. Every rule on release 2 is still on release 2; what changed is what release 2
means, and each rule picks that up when it is next recompiled, with no source edit. Rules that no
longer fit report an error and keep the editions they already have. Parked runs are untouched,
because they dispatch through the revisions they pinned rather than through the release.

A new block is appended instead when the meaning behind a capability changes even if its shape does
not — a different credit bureau answering the same signature:

```klein
fun creditScore/3(c: Customer/3): Num      # same shape, different bureau

release 3
  creditScore/3
```

No check can see that kind of change, so it is declared by putting it somewhere nobody is yet.
Every rule stays on release 2, and reaching release 3 means someone selecting it for that rule.

That is the difference between the two acts. Editing a release changes what the rules already on
it will compile against. Appending one creates something nobody is on until they are put there.

Any block may be edited, not only the newest. An edit reaches every later release that did not
state that name itself, which is what those releases were saying by leaving it out.

This matters when most rules are on an older release. A capability behind release 2 turns out to
be wrong; release 3 already exists because the bureau changed. Editing release 2 fixes it for
everyone still there, and release 3 inherits the fix unless it named that capability itself. If
only the newest block could be edited, the fix would be reachable only by moving every rule to
release 3 — putting an urgent mechanical repair behind a migration that needs a person per rule.

An edit that leaves a later release incoherent is caught before it deploys: re-pointing `Customer`
under a release that exposes a capability taking the old revision fails the self-containment check
below. What the diff does not show is how far the edit reaches, so read the reconcile plan, which
lists the affected rules on every release.

### Retiring a release

Only the oldest release can be retired, and retiring it means folding it into the one that follows.
The successor absorbs everything it was inheriting, becomes the complete statement in its own
right, and the old block is deleted:

```klein
release 1                    release 2
  Customer                     Customer/2
  creditScore                  creditScore     # absorbed from release 1

release 2             →      release 3
  Customer/2                   creditScore/3

release 3
  creditScore/3
```

Release 2 was inheriting `creditScore` without naming it; after the fold it states it outright.
Release 3 is untouched, because every block only ever refers to the one immediately before it. So
however many releases exist, retiring one is this single step.

This never changes what the surviving release means, so editions compiled against it stay valid.
The fold restates a release rather than changing one, which is why it is safe even though it
rewrites a block in the middle of the file.

Retiring and adding are independent, so both can travel in one deploy. The fold rewrites the
second-oldest block, a new release appends at the end, and nothing reads across the two.

Nothing that is already running stops. Existing editions and parked runs dispatch through the
revisions they pinned, not through a release, so those revisions keep being served. What changes
is that no rule can be compiled against the retired release again.

Retiring never moves a rule. A rule whose release has been retired keeps the editions it has and
waits for someone to move it forward deliberately.

This matters most when a release is retired and its successor appended in the same deploy, which
leaves a very small diff. A contract with one release, moving to a new credit bureau:

```klein
fun creditScore/2(c: Customer/2): Num

release 2
  Customer/2
  creditScore/2
```

becomes

```klein
fun creditScore/2(c: Customer/2): Num
fun creditScore/3(c: Customer/2): Num     # same signature, different bureau

release 3
  Customer/2
  creditScore/3
```

No signature changed anywhere, and the file still shows a single block. Read as a diff it is a
version bump and nothing else, and it looks exactly like an in-place edit of release 2.

It is not one, and the difference is visible where it counts: release 2 no longer exists. Rules
that pinned it cannot be recompiled, so they hold their editions and wait for a person to move
them to release 3. Had release 2 been edited in place instead, those same rules would have stayed
on release 2 and picked up the new bureau at their next recompile, without anyone deciding.

A declaration may be deleted once no remaining release exposes it and no edition or parked run
still pins it.

### What the checker rejects

Everything above is enforced at contract check time. The remaining rules are small and mechanical:

- **A pointer to a revision that is not declared.** `Customer/9` in a block, with no such
  declaration in the file, is an error — the same diagnostic a signature would get for naming it.
- **The same name twice in one block.** Two entries for `Customer` state two revisions for one
  name in one release, and there is no rule for picking between them. An error rather than a
  precedence.
- **`remove` for a name the release does not expose.** Either it was never there or an earlier
  release already removed it; both mean the author is working from a picture the contract does not
  match. Reported rather than ignored.
- **A repeated or out-of-order release number.** Blocks are read in file order and each one builds
  on the last, so the numbers must increase down the file. Gaps are legal: a gap is a release that
  has been retired.
- **A release in a rule.** Like every other contract-only form, this is a parse error at the line
  that wrote it.

An empty block is legal. As the oldest block it states a release that exposes nothing; anywhere
else it states a release identical to the one before it.

Nothing else gates an edit. A contract that checks can be deployed, and whether the change was a
good idea shows up as rules that do or do not recompile — which is the reconciler's report, not
the checker's.

## Restriction: no functions cross the boundary

Status: implemented.

A capability may not carry a Klein function — not as a parameter, not as a result, and not
nested inside a type it mentions:

```klein
fun sortBy(xs: List<Num>, key: (Num) -> Num): List<Num>   # FunctionTypeInCapability
fun adder(n: Num): (Num) -> Num                           # FunctionTypeInCapability
callback: (Num) -> Num                                    # FunctionTypeInCapability

type Handler = Handler { run: (Num) -> Num }
fun register(h: Handler): Num                             # FunctionTypeInCapability
```

A Klein function's only meaning is that the interpreter can run it, and whatever answers a
capability is not the interpreter — a handler holding a closure could never call it. The check
follows type references into their constructors' fields, so hiding a function one level down does
not evade it; recursive types terminate on a visited set. Records of functions remain perfectly
legal *inside* Klein; the restriction is only on what crosses to the host.

Polymorphic signatures, by contrast, are fine: `fun first(xs: List<'A>): 'A` is a legal
capability. A handler for it cannot inspect what it receives, which is exactly what the type
promises.

## The host sees exactly the declared shape

Status: ruling — enforced by marshalling when the derivation API lands, which is where a host's
view of a value is constructed. Until then the raw `(List<Value>) -> Value` seam is unmediated:
a handler receives the value as the rule built it, extra fields included, and the round trip
below does not yet drop them. Whether the ruling ultimately binds the raw seam too, or only the
derived one, is derivation's decision.

Klein lets a rule pass a *wider* record than a signature asks for; inside Klein the extra fields
physically travel along. Crossing to the host, only the declared fields cross — echo the value
back and the extras are gone:

```klein
type Wrap = Wrap { tags: { a: Num } }
w = Wrap({ a = 1, b = 99 })      # legal: { a, b } is usable as { a }
echo(w).tags == w.tags           # false: the round trip dropped b
```

This is not a broken promise, because the type never made one — a plain Klein function with the
same signature may do exactly the same thing (`fun echo(w: Wrap): Wrap = Wrap({ a = w.tags.a })`),
and no caller can tell the two apart: undeclared fields are unreachable through the narrowed type,
so the only instrument that notices is `==`, and both versions give the same `==` results. The
boundary promises what the types promise. Authors who need a field to survive a host round trip
put it in the declared type — where it had to be anyway for the host to be allowed to look at it.

## Not covered here

Capability identity, implementation registration, serving, editions and pins,
reconciliation, and the CLI surface for contracts all live outside the checker. See
[host-integration.md](./host-integration.md).
