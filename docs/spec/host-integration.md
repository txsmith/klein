# Host Integration

Two independently evolving parties, one agreement discipline. The ruleset stays compatible with
the host by construction: every rule save is checked against its environment's contract. The host
stays compatible with the ruleset by procedure: serve both revisions of a changed capability while
rules and parked runs drain off the old one. This doc defines the terms and mechanisms that make
both halves work.

Companion: [diagnostic-severity.md](../ideas/diagnostic-severity.md) — how the checker's
findings are classified into errors and warnings across authoring and evolution.

## Klein is a small core with standalone components

The core is a small library: check, compile, run, and structured reports. Around it, Klein ships
optional standalone components — a web editor, storage connectors — but the core never depends on
them, and every one of them can be replaced or skipped. The integration surface is the library's
functions and report objects; the components are just first-party consumers of that same surface:

- Want rules in S3? Use that connector or write your own. Transactional through Postgres? Sure.
  Single instance? Disk or in-memory is fine.
- Don't want the shipped editor? Skip it — the same functions run over files from a CLI, or under
  whatever UI the org builds.
- Want rule review in git? Sync your store to a repo. Want it in your product's UI? Build
  approvals there.

The design below therefore describes *mechanisms and invariants*, not infrastructure. Where a
choice is the org's, it says so. One honest dependency note: some guarantees (marked below) are
only as strong as the store's transactions — an org that puts rules in S3 keeps every mechanism
but trades the transactional guarantees for eventual ones.

## Terminology

Running example: a lending application with two features exposing business rules — an
**eligibility check** and a **loan configuration form** — and a host function `creditCheck`
backed by a bureau API.

### Host

The application that embeds Klein: the org's own software into which rules are injected (here,
the lending application). The host stores rules however it likes, decides when they run, and
provides everything a rule can reach beyond pure computation. A host usually runs as several
instances, and during a deployment different instances temporarily run different builds. Where
that matters below, "a host" means one running instance.

### Environment

A single injection point in the host application where Klein rules can run. It is defined by a
contract file and an implementation of that contract. A contract file is a Klein file listing the
types, function declarations and value declarations that rules can access. Each declaration is
versioned, and the contract's releases decide which version each name means.

Environments are separate worlds. Every mechanism in this doc operates within a single
environment. Two environments may declare the same capability name, and no machinery ever
compares them. They can share the host code that implements a capability.

### Capability

One named, typed thing the host provides for rules to use. It is either a function the host
answers every time a rule calls it (`creditCheck(c: Customer): Score`), or a value the host
supplies once at the start of each run (`customerName: String`). A capability is declared in the
contract file as a signature with no body. The language and checker side of that file is
specified in [contracts.md](./contracts.md). Its implementation is ordinary code
inside the host. A capability is identified by **(name, revision)** within its environment. The
name is stable across versions.

### Revision

A `/N` suffix declared in the contract, on capabilities and on the types they carry, so
incompatible versions coexist in one file while the old one drains:

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer): Num
fun creditScore/2(c: Customer/2): Num
```

Absent means revision 1. Revisions are permanent, never recycled and are contract-only syntax.
`/N` never appears in a rule; what rules see is governed entirely by releases, so revision markers
never reach a rule author's screen or vocabulary.

### Release

A numbered set of pointers in the contract, each aiming a name rules may write at one declared
revision. A rule is compiled against exactly one release, and inside it every name means exactly
one revision — so rules write plain names and never see a revision marker:

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

The language side — delta blocks, the implicit first release, `remove`, self-containment, and the
fold that retires one — is specified in [contracts.md](./contracts.md). What matters here is that
a release is the unit an environment migrates in, and that there are exactly two ways to change
what rules can see:

**Editing a release** changes what the rules already on it will compile against. Nobody moves;
each rule picks the change up at its next recompile, still on the same release. This is how a
mechanical change travels — a shape that moved while the meaning behind it did not, or a repair to
a capability that was wrong. An edit also reaches every later release that did not name that
capability itself.

**Appending a release** creates something nobody is on. Rules stay where they are until an author
selects the new release for them. This is how a change the checker cannot see is declared — a
different credit bureau behind an unchanged signature.

That difference is the whole migration vocabulary. Nothing marks a release as needing review,
because the act already says it: an edit carries rules along, a new release waits for a person.

Tags, the earlier design this replaces, were per-name pointers a rule could mix freely, spelled
`Customer@legacy` in rule source. They are gone: a rule now takes its entire vocabulary from one
release.

### Rule

A Klein program written by a business author inside one environment, against that environment's
contract.

### Edition

A compiled artifact of one version of a rule. A version is what an author creates by editing rule
source. An edition is that version compiled: the program lowered to Klein Core, the release it was
compiled against, plus a map recording, for each capability and type the rule uses, the name it
wrote, the revision that release pointed at, and a hash of the declared signature. Each entry of
that map is a **pin**.

The release is not written in the rule. It travels with the compile request — in the shipped
editor, a dropdown beside the rule — so it is chosen once, per rule, by a person. Recompiling
reuses the release the previous edition recorded. Selecting a different one is the deliberate act
that moves a rule forward, and the only thing that ever does.

One version accrues editions as capability contracts evolve. Recompiling the same source against
a changed contract makes a new edition next to the old ones. Editions are never edited and only
deleted if they are not in use anymore. A host instance can serve an edition iff it implements
every revision the edition pins. An edition no instance can serve is unservable until that
changes.

Pins link each capability call in the compiled program to a host implementation. When a run
starts or resumes, every call is dispatched to the implementation of exactly the pinned revision.

Pins have two more jobs. During evolution, comparing pins against the current contract finds the
editions a change affects and skips the rest. During serving, a host checks the pins against the
revisions it implements to decide whether it can run the edition.

### Run

One execution of one edition, from its triggering event to its final result. A run may suspend on
a capability call and resume later, possibly weeks later. A suspended run is **parked**. A run
records which edition it executes and its effect log. Through the edition's pins, a parked run
keeps revisions alive: the host may not remove a revision while a parked run may still resume
into it.

### Turn

One uninterrupted stretch of a run: from start or resume until the rule parks on a capability
call, or finishes. The host gives the interpreter a turn. The interpreter gives it back with a
question or a result. The host decides when each turn runs. The effect log gains one or more
entries per turn.

### Effect log

A run's record of its interaction with the host. An entry holds which capability was called, at
which revision, with which arguments, and the answer the host gave. Entries are ordered. The log
is append-only, and a recorded entry never changes. Replaying the log against the run's edition
rebuilds a parked run without asking the host anything again.

How entries are keyed to call sites in the edition is undecided. Everything that would resume a
run on a *different* edition waits on that decision. See open questions.

### Reconciliation

The act of recompiling rules against an evolved contract. When a signature is edited in place or a
release is re-pointed, each affected rule's unchanged source is compiled again, against the same
release it recorded. A clean check is the new edition. A failed check produces a report with the
diagnostics, and the rule keeps running on its existing editions.

Reconciliation never changes a rule's release. It recompiles rules where they stand, so an
appended release affects nobody until an author selects it. How that report reaches the author is the org's
choice: an email, a chat message, a warning in the rule editor.

Reconciliation is incremental. Comparing each edition's pins against the current contract finds
the affected rules and skips the rest. The pinned hash detects that a declaration changed. It
never decides whether the change is acceptable. Recompilation decides that.

Reconciliation is safe to perform at any time. Editions only accrue, and each host serves only
editions whose pins it implements. Done early, the new editions sit unused until hosts catch up. Done
after a rollback, migrating back is a lookup, because the old editions still exist. Done twice,
pin comparison skips the finished work. In practice it happens when the fleet has converged on a
new contract.

A failed recompile has two urgencies. Under a re-pointed release the rule waits safely, because
the old revision is still declared and served. Under an in-place signature change there is no old
revision to stay on once the fleet flips. A CI check can detect this, or the host can choose to handle it as a runtime error.

The hash also enforces the revision discipline. An incompatible in-place edit, or any edit to a
type definition at an unchanged revision, appears as a hash mismatch and is reported as a
contract error.

### Drain

The countdown to deleting a capability revision. Two counts fall toward zero: rules whose newest
edition still pins the revision, and parked runs pinned to it. The first count falls through
reconciliation and author edits. The second falls as parked runs finish. A run records its
edition, and the edition carries both its release and its pins, so both counts are one join over
data the host already stores.

Retiring a release is not gated by any of this. Deleting a block is always allowed: it stops new
compiles against that release and nothing else, since everything already running dispatches
through pinned revisions. Retiring is what *begins* a drain rather than what waits for one — from
that moment, rules recorded on the retired release cannot be recompiled, and hold their editions
until someone moves them.

So the release count is a decision aid, not a permission check. Watching it fall to zero as
authors migrate is the patient path; retiring earlier is the blunt one, and it strands nobody —
it only means the remaining authors must move before their rules can change again. Either way,
the revisions that release was the last to reach become deletable once the counts above reach
zero.

Nothing bars fresh runs during a drain. On a stable fleet the births stop by themselves: every
host serves the new editions, and a fresh event always starts the newest edition its host can
serve. A rollback or a mid-deploy dip can start new runs on the draining revision. Those runs are
correct, and the counts simply rise until the fleet is stable again.

At zero the revision is removable. Delete its declaration from the contract, and the next deploy
drops its implementation. An implementation shared across environments outlives each
environment's drain until the last. Removal has no deadline, so cleanup can be batched. Editions
pinning the removed revision may be deleted once nothing uses them, or kept as history. Until
then they are simply routed around.

Removal can guess wrong. A host that still carried the revision may have parked a run on it just
before the fleet finished upgrading. That run strands: it waits for a host that serves the
revision, and none exists. Stranding loses no work. The alert on "parked runs pinned to
revisions no instance implements" names the run, restoring the revision lets it finish, and
removal ships again later. Revision numbers are permanent, so restoring one is well-defined.

Neither number is recycled. Deleting `creditScore` once it drains leaves `creditScore/2` as `/2`
forever, and the next breaking change is `/3`. Retiring release 1 leaves release 2 as release 2,
and the next release is 3.

## How the pieces relate

- Each **host** instance implements a set of capability revisions.
- **Environments** each declare their own contract and register implementations behind it; every
  other mechanism in this doc operates inside one environment.
- **Rules** are written against an environment and compiled against one of its **releases**, which
  is chosen per rule and travels with the compile request; **compiling** produces an **edition**
  that records that release and whose **pins** freeze exactly which capability/type versions it
  touches.
- An **event** at an environment's invocation site targets a *rule*; the serving host runs the
  newest edition whose pins it implements, which is what makes a heterogeneous mid-deploy
  fleet safe.
- A **run** executes against exactly its pins; a **parked run** holds them until done.
- When the host evolves, **reconciliation** grows the corpus with new editions, always against the
  release each rule already recorded; **drain** counts old releases down to retirement and old
  revisions down to deletion; failed recompiles become reports for their authors.

## Evolution, concretely

Each kind of contract change asks something different of the host.

| Change | What the host writes | What happens to rules |
|---|---|---|
| Compatible signature change, types unmoved | edit the declaration in place | affected rules are recompiled; green is the new edition |
| Type change, or incompatible signature change, meaning preserved | new revision(s), re-point the release | rules recompile against the new revision with zero source edits, staying on their release; red waits safely on the old revision |
| A capability was wrong and needs repairing | re-point the release the affected rules are on | the repair reaches them where they are, without a migration |
| Meaning changed | new revision, new release | nobody is on the new release until an author selects it for a rule; the old release keeps serving meanwhile |
| A capability retires from rule vocabulary | `remove` it in a new release | rules on earlier releases carry on; no rule compiled against the new release can name it |

Editing a release is the claim that meaning was preserved. The claim is made by the act itself, in
a diff, reviewed when it happens. This polarity fails safe. Forgetting to edit leaves rules on
what they had, which keeps being served and shows up as staleness. Nothing silently changes
meaning.

The two acts also decide who is disturbed. An edit reaches everyone on that release, and every
later release that did not name the capability itself — which is what makes a repair land on the
rules that need it rather than on whoever has already migrated. A new release reaches nobody until
chosen.

Type definitions are invariant. Any edit to a `type` requires a new revision. Only a capability's
own signature, over unmoved types, may change in place. A changed type definition admits no
subtype question, because old and new share one nominal name and cannot coexist in one
environment to be compared. Compiled editions also bake definitions in, as constructor arity and
match exhaustiveness, which no check of the new definition alone can see. This rule is what makes
in-place signature changes well-founded. A signature comparison means something only when the
types it mentions hold still.

One environment, followed through a year. The eligibility environment has three rules. We follow
`eligibility-standard`, whose whole source is `creditScore(customer) >= 620`. The CLI shown is
illustrative. Whether these actions run as a CLI, a host API, or library calls in the host's
tooling is the org's choice.

### 1. A new capability

The contract on day one:

```klein
type Customer = Customer { id: Num, name: String }

customer: Customer
fun creditScore(c: Customer): Num
```

The host implements both capabilities and deploys. Saving `eligibility-standard` compiles it into
its first edition. The edition pins `customer`, `creditScore` and `Customer`, each at revision 1.
Adding a capability later looks the same: deploy the implementation, declare it, done. Nothing
references a new capability yet, so a half-deployed fleet is harmless and no edition changes.

### 2. The meaning changes

The host switches credit bureaus. The types do not move, so no checker can see the change. The
host declares it by adding a revision and putting that revision in a new release. The whole
contract now reads:

```klein
type Customer = Customer { id: Num, name: String }

customer: Customer
fun creditScore(c: Customer): Num
fun creditScore/2(c: Customer): Num

release 1
  Customer
  customer
  creditScore

release 2
  creditScore/2
```

The day-one declarations are untouched. Release 1 is written out here for the first time: it was
implicit while nothing was versioned, and versioning something is what makes it worth stating.
Release 2 says only what it changes.

```
$ klein reconcile plan eligibility.klein
3 rules checked against the release each recorded, 0 affected
```

Nothing migrates, and that is the point. Every rule is on release 1, and stays on the old bureau
until its author decides otherwise. The author of `eligibility-standard` selects release 2 and
saves `creditScore(customer) >= 640`, reconsidering the threshold against the new bureau's scale.
That is the judgment no machine could make. The rule source barely changed; what changed is which
release it is compiled against. Its first edition records release 2 and pins `creditScore/2`.

```
$ klein drain release 1
rules: 2 (eligibility-premium, eligibility-student)
parked runs: 7
```

The host could retire release 1 today — nothing forbids it — but that would leave both authors
unable to change their rules until they moved. So it waits, and both bureaus serve while the
count falls.

### 3. The shape changes

`Customer` drops `name` for privacy and gains `tier`. A type edit requires a new revision, and
the revision cascades: every declaration whose signature mentions `Customer` needs a revision
that mentions `Customer/2`. The bureau behind release 2 is unchanged, so the meaning is preserved
and release 2 is re-pointed rather than superseded:

```klein
type Customer = Customer { id: Num, name: String }
type Customer/2 = Customer { id: Num, tier: String }

customer: Customer/1
customer/2: Customer/2
fun creditScore(c: Customer/1): Num
fun creditScore/2(c: Customer/1): Num
fun creditScore/3(c: Customer/2): Num

release 1
  Customer
  customer
  creditScore

release 2
  Customer/2
  customer/2
  creditScore/3
```

Declaring `Customer/2` also forced the older bare references to be spelled `Customer/1`. The new
declarations, their implementations, and the re-pointed release travel in one deploy, so a
pointer can never take effect before its target is served.

Release 1 is left alone, and that is a choice rather than a constraint. Moving it to `Customer/2`
would mean declaring an old-bureau capability that takes a `Customer/2`, which the host has no
reason to build. So release 1 keeps the whole old world coherently — old shape, old bureau — and
the rules on it carry on untouched.

```
$ klein reconcile plan eligibility.klein
3 rules checked against the release each recorded, 1 affected
  eligibility-standard   release 2   recompile ok   would pin Customer/2, customer/2, creditScore/3
2 rules untouched: release 1 did not change
$ klein reconcile apply eligibility.klein
1 edition created
```

`eligibility-standard` picked up the new shape with zero source edits, still on release 2.
`eligibility-premium` reads `customer.name`, which `Customer/2` dropped — and it is entirely
unaffected, because it sits on release 1 where `Customer` still has a `name`. It is not red, not
waiting, and not stale in any way that needs attention yet.

Its reckoning comes when someone selects release 2 for it. At that moment its author faces both
changes at once: the dropped field and the new bureau's scale. That is the right time for a person
to look, and until then nothing forced the question.

If the fleet rolls back mid-deploy, the releases roll back with it, because they live in the
contract. Fresh events fall back to the old editions, which still exist. Runs already parked on
new pins are stranded, not broken. They wait, and resume when the fleet rolls forward again.

### 4. An edit in place

The scorer behind `creditScore/3` stops needing the whole customer. The host widens the parameter
in place. No revision, because the change is compatible: any argument the old signature accepted,
the new one accepts.

```klein
fun creditScore/3(c: { id: Num, tier: String }): Num
```

```
$ klein reconcile plan eligibility.klein
3 rules checked against the release each recorded, 1 affected
  eligibility-standard   release 2   recompile ok
2 rules untouched: they pin creditScore/1, which did not change
```

The pinned hash of `creditScore/3` no longer matches, so whoever pinned it recompiles. The two
rules on release 1 never pinned `/3`, so they are not checked further. Had a recompile failed
here, the change was not compatible after all, and the report gates the deployment: unlike a
revision bump, an in-place change leaves no old world to wait on once the fleet flips.

### 5. Removal

Months later, the authors of `eligibility-premium` and `eligibility-student` have rewritten their
rules onto release 2, and the last parked run on the old bureau has finished.

```
$ klein drain release 1
rules: 0
parked runs: 0
```

Nobody is left on it, so retiring costs nothing. Retiring release 1 means folding it into release
2 and deleting its block. Release 2 already
stated all three names, so the fold adds nothing and the file simply loses its first block:

```klein
release 2
  Customer/2
  customer/2
  creditScore/3
```

Now no release reaches the revision-1 declarations, and nothing pins them, so the host deletes
`Customer/1`, `customer/1`, `creditScore/1`, `creditScore/2` and their implementations. The next
deploy drops them. `creditScore` has now had revisions 1 through 3, and the next one is `/4`.
Release 2 stays release 2, and the next release is 3. Neither number ever resets.

Removal can guess wrong: a host that still carried revision 1 may have parked one last run just
before the fleet finished upgrading. The run strands, the alert on unservable parked runs names
it, restoring the revision lets it finish, and the removal ships again later.

## What checking costs, and when

- Every edition is checked **exactly once, at creation**. Its source and its pinned dependencies
  are all immutable, so the verdict is permanent; evolution creates and supersedes editions, it
  never invalidates one.
- Pin hash comparison skips everything untouched: a capability change re-checks only the rules
  whose editions pin it. This is memoization, not a compatibility oracle — a hash mismatch means
  "go run the checker", nothing more.
- The authoritative question is always "does this rule compile against these versions", answered
  by the real checker, run **where the rules live** (the org's store, the org's process). No
  subtype shortcut substitutes for it; the shortcuts only shrink how often it runs.

## Host responsibilities

The "by procedure" half of the agreement, gathered in one place. The host:

- Stores rules, versions, editions, runs and effect logs. Guarantees are only as strong as this
  storage.
- Decides when turns run, and appends effect-log entries as turns complete, before the next turn
  starts.
- Retries or abandons a turn whose handler failed. The log holds only completed turns, so a
  failed turn is one that never happened.
- Serves every revision an unfinished run still pins, and runs each edition only on an instance
  that implements its pins.
- Delivers failed-recompile reports to rule authors.
- Watches for parked runs pinned to revisions no instance implements, and restores a removed
  revision when one appears.

## What the org decides

Storage and its guarantees (transactional activation needs a transactional store); governance
(git sync, product approvals, or nothing); editor or files; deploy pacing and cleanup cadence;
how reports reach authors. Klein's side of the contract is the invariants above and structured
reports at every seam.

## Open questions

- **Value capability scope.** `customerName` is per run; `maxRetries` is really per environment.
  The contract syntax does not distinguish the two, and the effect log records only the per-run
  kind. Decide whether scope is declared, and how.
- **Parked-run migration.** Moving a parked run to a newer edition is unsolved and out of scope.
  Parked runs finish on the edition they started on.
- **Effect log keying.** Replay matches log entries to capability calls in the edition. Position
  works while the edition is fixed. Resuming on a different edition needs a stabler key. Blocks
  parked-run migration.
- **Illegal in-place edits.** The recompile discovers an incompatible in-place edit, but when: in
  CI before the deploy, at boot, or at first reconciliation? Alternatively, ban in-place edits
  entirely — every change needs a revision, and the detection machinery for small edits
  disappears.

## Decision history

Two ADRs. The revision substrate — permanent `/N`, invariant type definitions, recompilation as
the verdict, optimistic removal — and its rejected alternatives (snapshot files, semantic version
numbers, `review`/`mechanical` markers, subtyping as the verdict, a retire flag) are in
[decisions/2026-08-06-capability-evolution-through-revisions-and-tags.md](../decisions/2026-08-06-capability-evolution-through-revisions-and-tags.md).

Releases replaced the tags that ADR proposed; that decision, and what was rejected on the way to
it, is in
[decisions/2026-08-08-rule-vocabulary-through-linear-releases.md](../decisions/2026-08-08-rule-vocabulary-through-linear-releases.md).
