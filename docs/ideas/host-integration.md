# Host Integration

How Klein rules and a host application evolve independently and stay in agreement.
Companion: [diagnostic-severity.md](./diagnostic-severity.md) — how the checker's
findings are classified into errors and warnings across authoring and evolution.

## Klein is a small core with standalone components

The core is a small library: check, compile, run, and structured reports. Around it,
Klein ships optional standalone components — a web editor, storage connectors — but the
core never depends on them, and every one of them can be replaced or skipped. The
integration surface is the library's functions and report objects; the components are
just first-party consumers of that same surface:

- Want rules in S3? Use that connector or write your own. Transactional through
  Postgres? Sure. Single node? Disk or in-memory is fine.
- Don't want the shipped editor? Skip it — the same functions run over files from a CLI,
  or under whatever UI the org builds.
- Want rule review in git? Sync your store to a repo. Want it in your product's UI?
  Build approvals there.

The design below therefore describes *mechanisms and invariants*, not infrastructure.
Where a choice is the org's, it says so. One honest dependency note: some guarantees
(marked below) are only as strong as the store's transactions — an org that puts rules
in S3 keeps every mechanism but trades the transactional guarantees for eventual ones.

## Terminology

Running example: a lending application with two features exposing business rules — an
**eligibility check** and a **loan configuration form** — and a host function
`creditCheck` backed by a bureau API.

**Host** — the application that embeds Klein: the org's own software into which rules
are injected (here, the lending application). The host stores rules however it likes,
decides when they run, and provides everything a rule can reach beyond pure
computation. A **node** is one running instance of the host; fleets of nodes matter
later because, during a deployment, different instances temporarily run different
builds.

**Capability** — one named, typed thing the host provides for rules to use: either a
function the host answers every time a rule calls it (`creditCheck(c: Customer): Score`,
backed by the bureau API), or a value the host supplies once at the start of each run
(`maxRetries: Num`). A capability is declared to Klein as a signature only — a
declaration with no body, in a Klein source file the host hands over (the language and
checker side of that file is specified in [spec/contracts.md](../spec/contracts.md)); its
implementation is ordinary code inside the host. A capability version's identity is
**(name, revision)**, both declared — see below. The name is stable across versions,
which is the point: evolution never goes through renaming. The capability is the *only* thing a rule and
the host share; everything else in this doc is bookkeeping about who provides and who
needs which capability versions.

**Environment** — a place in the host application where rules run: one injection point,
one business context. An environment fixes three things: the **contract** rules there
must have (the eligibility check takes an `Application` and returns a `Decision`), the
**curated set** of capabilities — by name — that rules there may use, and the
**invocation site** (what event in the application triggers a run). Curation says what
is *appropriate* in this context; which *version* a name means is never curated — the
system resolves it (the newest served, non-retired version) at compile time, so version
state lives in one place. Environments are a product concept; capabilities are shared
across them (one `creditCheck`, curated by both features).

**Module** — *(out of scope for v1: types reach rules by riding capability signatures,
so the design below works with no module system; what v1 gives up is shared Klein code
between rules, hub-type version bridging, and module-mediated rule composition)* — a
Klein program exposing types and functions, versioned in the rule store,
evolved at editor speed. Modules may depend on other modules, and may call capabilities —
in which case the requirement travels: a rule using the module inherits its capability
needs. Pure modules are portable across environments; capability-using modules fit only
environments that curate what they need. Shared "hub" types (`Customer`) live in modules
and version like everything else; same-named type versions are compatible when
structurally compatible (the checker bridges them by shape, which is also exactly what
the machine does at runtime).

**Revision** — a `/N` suffix declared in the contract, on capabilities and on the types
they carry, so incompatible versions coexist in one file while the old one drains:

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer): Num
fun creditScore/2(c: Customer/2): Num
```

Absent means revision 1. Because each declaration names the type revision it carries,
there is no implicit pinning rule to remember: `creditScore/2` means `Customer/2`
because it says so.

Revisions are **permanent and never recycled**. Renumbering after a drain is tempting —
it would keep the numbers small — but the reconciler cannot distinguish "revision 2 was
rolled back" from "revision 1 was retired and 2 promoted in its place", since both leave
one bare declaration. Accepting monotonic numbers costs a growing label; recycling costs
correctness.

**Three shapes of change**, which is what decides whether a revision is needed at all:

| Change | Revision? | What the reconciler does |
|---|---|---|
| Type change, compatible (new signature is a subtype) | no | detects it and migrates rules automatically |
| Type change, incompatible | yes | versions coexist; rules are re-checked and may be flagged |
| Semantic change, types unmoved | yes, plus `review` | versions coexist; every affected rule goes to the author |

**`review`** — a trailing marker declaring the third case, which nothing else can
detect:

```klein
fun underwrite/2(a: Application): Decision review
```

"The scores come from a different bureau now" is invisible to types, and the new
revision would otherwise look like an ordinary compatible one. `review` sends rules
using it to the maintenance queue rather than reconciling them automatically.

**Rule** — a Klein program written by a business author inside one environment, against
that environment's contract and curated capabilities.

**Edition** — one compiled form of a rule against one set of dependency versions. Rules
change along two axes: a **version** is the author editing the source; an **edition** is
the same source compiled for a changed world. Author edits move one axis, dependency
evolution the other (an edit also mints the new version's first edition). Editions are
**additive**: evolving a dependency adds a new edition next to the old one; existing
editions are never edited or deleted. A rule with editions for
v1 and v2 of a capability is servable in both worlds at once; an edition whose pins
no node advertises is simply unservable until that changes.

**Pin** — an exact dependency version (capability, module, or type) recorded in an
edition, by content hash. An edition is nothing more than compiled code plus its pins,
and the pin set answers every bookkeeping question there is: *what to assemble* (at run
time, pins are dereferenced to exactly what the rule was compiled against — **addresses,
not predicates**, never compared to decide compatibility), *what changed* (at evolution
time, comparing pinned hashes against current versions finds the affected rules and
skips the rest), and *who can serve it* (pins against node advertisements).
Compatibility itself was decided once, by the checker, when the edition was created;
both sides of that check are immutable, so the verdict never expires.

**Run** — one execution of one edition, from its triggering event to its final
value. A run carries its pins; a **parked run** (a run suspended on a capability call,
possibly for weeks) owns its pinned capability versions until it completes: the host may
not remove a handler while a parked run may still resume into it.

**Turn** — one uninterrupted stretch of a run: from start or resume until the rule parks
on a capability call, or finishes. The host gives the machine a turn; the machine gives
it back with a question or a result. Turns are the host's scheduling unit and the unit
replay re-executes; effect-log entries sit at turn boundaries; fuel limits bound a turn.
(Distinct from a machine **step** — one dispatch of the interpreter loop; a single turn
typically executes thousands to millions of steps.)

**Advertisement** — a node's declared capability set: every (name, revision) pair its
binary implements. A node can serve a run iff its advertisement covers the
run's pinned capabilities. A node mid-transition advertises both revisions of whatever
it is migrating.

**Reconciler** — the incremental background process that keeps editions in step with the
fleet. When the fleet converges on advertising a new contract (with hysteresis, so a
mid-rollout rollback doesn't cause flapping), it moves the corpus forward. Work sorts
into three tiers, by diffing the old contract against the new and comparing that to each
edition's recorded pins:

1. **Untouched** — the rule uses nothing that changed. Nothing to do.
2. **Compatibly changed** — what it uses changed, but the new signature is a subtype in
   the right direction, so it stays sound (see the monotonicity argument in
   [diagnostic-severity.md](./diagnostic-severity.md)). Re-pin now, recheck in the
   background for newly dead code, which is a warning rather than a blocker.
3. **Everything else** — run the checker. Compiles → new edition; doesn't → maintenance
   queue with the errors.

`review` is applied first and overrides all three: a rule using a marked capability goes
to the queue whatever tier it would land in. That ordering is what makes tiers 1 and 2
safe, since a semantic change leaves the signature untouched and would otherwise be
classified as needing no attention.

A note on hashing, because it looks more central than it is. A signature hash is a
useful *change detector* for pruning this work, and nothing more: it disagrees on
changes that are perfectly compatible, and agrees on semantic changes that are not. It
is neither necessary nor sufficient for compatibility, which is why identity is the
declared (name, revision) and the subtype check is what decides tier 2.

**Maintenance queue** — the flagged rules, routed to their authors with diagnostics
("your `Square` arm went dead when `Shape` narrowed"). Warnings gather here too: sound
rules whose re-check found newly dead code.

**Retire** — the deliberate, dev-initiated cutoff that makes removing a capability
version safe. Without it, the drain counts below can regress: a rollback or mid-deploy
dip makes old editions the newest *servable* again, and fresh events quietly start
new runs on the version being drained. Retiring a version means: no fresh run may start
on an edition pinning it, and no new edition pinning it may be created — but
parked runs still resume (they must be able to finish). Retiring spends the rollback
safety net (a rollback after it leaves affected rules with no servable edition,
loudly), which is why it is a manual act taken when the fleet is stable — and why it
stays reversible until the handler is actually removed. Atomic where the store is
transactional.

**Drain** — the countdown, after retirement, to removing a capability revision. Two
counts fall monotonically to zero: rules whose newest edition still pins it (each needs
a newer edition — the reconciler's job), and parked runs pinned to it (each must
finish). A run does not record capabilities itself; it records its edition, and the
edition carries the pins, so both counts are one join over data the host already stores.
At zero the revision is **removable**: delete its declaration from the contract, and the
next deploy drops its handler. Editions are never deleted — unservable ones are simply
routed around and remain as history. Removal has no deadline, so cleanup can be batched.

Note what is *not* recycled: the revision number. Deleting `creditScore` once it drains
leaves `creditScore/2` as `/2` forever, and the next breaking change is `/3`.

**Adapter** — how a host carries an old capability version during a transition without
maintaining two real implementations: the old handler is written as a translation onto
the new one, at change time, while the diff is fresh. When no adapter is expressible
(the new version needs data old requests lack), the change was semantically breaking for
the host too, and two implementations honestly coexist until drain.

## How the pieces relate

- The **host** implements capabilities and advertises them per node.
- **Environments** curate capabilities by name and fix rule contracts; they are how the
  product organizes rules, not how the runtime matches them.
- **Rules** are written against an environment; **compiling** one produces an
  **edition** whose **pins** freeze exactly which capability/module/type versions it
  touches.
- An **event** at an environment's invocation site targets a *rule*; the system runs the
  newest edition the current fleet can serve (by advertisement inclusion). Host code
  contains no version switch — routing is data.
- A **run** executes against exactly its pins; a **parked run** holds them until done.
- When the host evolves, the **reconciler** grows the corpus with new editions;
  **drain** shrinks retired versions to removal; the **maintenance queue** carries
  whatever needs a human.

Two independently evolving parties, one agreement discipline: the ruleset stays
compatible with the host **by construction** (every save is checked against curated,
currently-served capabilities — transactionally, where the store allows it); the host
stays compatible with the ruleset **by procedure** (serve both capability versions while
the corpus and parked runs drain).

## Evolution, concretely

**Adding a capability.** Deploy binaries that implement and advertise it. Nothing
references it; a half-rolled fleet is harmless. Once curated into an environment, rules
may use it. No new editions are created, no drain — additions are free.

**Changing or removing a capability.** The new binary serves old and new versions (the
adapter pattern keeps that cheap). As the fleet converges, the reconciler adds
editions against the new version for compatible rules and flags the rest; fresh
events shift per rule to the newest servable edition. Once the fleet is stable, the
dev **retires** the old version; its references drain, and at zero the handler can be
removed. There is no synchronization point: no deploy waits on authors, no author waits
on deploys, and the only clock is the drain query.

**Changing what a capability means.** Types cannot capture "the scores come from a
different bureau now", so no checker can verify such a change — but it can be
*declared*: publish a new revision with the capability marked `review`. The
mechanics are then the same as any other change, which is the point of having one
version axis: the new file's handler arrives alongside the old one, editions pinned to
the old revision genuinely keep the old semantics, and every affected rule reaches its
author through the maintenance queue asking the one question a machine cannot — "does
your threshold still make sense against the new meaning?" The old revision drains as
those reviews complete.

When the old semantics cannot be served at all (the bureau contract is gone),
coexistence is off the table and two honest treatments remain: **review-gate** (old
editions become unservable — parked runs stall and affected rules go dark until their
authors confirm) or **auto-reconcile** (every rule moves to the new meaning at once;
available, but drifting). Either way the marker earns its one word: pins and the effect
log record which meaning each run used, so "which decisions straddled the scoring
change" is a query rather than archaeology. Omitting it makes the system's history lie.

**How long does a transition take?** Two populations move at two speeds. Rules the
re-check clears migrate automatically — the reconciler compiles their new editions in
bulk, in seconds. Rules the re-check flags wait for their authors — each sits in the
maintenance queue until a human resolves it. The old capability version can be retired
once every affected rule has a newer edition and every parked run pinned to it has
finished, so the transition ends when the slowest of those happens. In practice: a
change that flags nothing is done the same day (one ordinary deploy, one cleanup deploy
later); a change that flags three hundred rules keeps both versions serving until the
queue empties, which may take weeks and is fine. The impact report — which rules, which
diagnostics, computed where the rules live — forecasts all of this before the change is
committed. Only two things in the procedure are decided rather than determined: when to
retire the old revision, and, when a capability is marked `review`, what
happens to rules still on the old meaning.

**Rollback.** Roll the fleet back and fresh events quietly fall back to old editions
(they were never deleted). Runs suspended on new-version pins are **stranded, not
broken**: pins make waiting safe, and they resume when the fleet rolls forward. "Parked
runs pinned to capabilities no node advertises" is the one state where work neither
progresses nor fails — alert on it.

**Rules invoking rules.** Two different needs, two existing mechanisms. Sharing *logic*:
a module — compiled in, pinned, frozen at the caller's compile. Invoking *a rule as the
product runs it* (a workflow step calling the eligibility check): a capability whose
handler starts the callee's run — own environment, own effect log, and **late binding**
(a workflow parked three weeks gets today's eligibility policy, because the host
dispatches at call time). Binding time is thereby a per-capability host choice, not a
language commitment.

## What checking costs, and when

- Every edition is checked **exactly once, at creation**. Its source and its pinned
  dependencies are all immutable, so the verdict is permanent; evolution creates and
  supersedes editions, it never invalidates one.
- Pin hash comparison skips everything untouched: a capability change re-checks
  only the rules whose editions pin it. This is memoization, not a compatibility
  oracle — a hash mismatch means "go run the checker", nothing more.
- The authoritative question is always "does this rule compile against these versions",
  answered by the real checker, run **where the rules live** (the org's store, the org's
  process). No subtype shortcut substitutes for it; the shortcuts only shrink how often
  it runs.

## Implementing capabilities

The host declares its capabilities and implements them in its own language. The
contract is always the same thing — the set of Klein-side signatures and their hashes —
but there are **two directions** to produce it, chosen per host language and team taste:

- **Contract-first**: write the signatures in a Klein file; a build step (`bindgen`)
  generates the host-language types and glue from it. CI fails if the checked-in
  generated code is stale, so changing a declaration stops the host compiling until
  someone updates the implementation. Right for languages without strong type
  derivation, and for teams that want the contract to lead.
- **Code-first**: declare capabilities directly in host code, and *derive* the Klein
  signatures from the host's own types — compile-time derivation in languages that
  support it (a serialization-framework descriptor on Kotlin, type-level derivation on
  Scala), never runtime reflection. The handler is written against real types with
  nothing generated; hosts that persist parked requests get their serialization from
  the same machinery for free. The obligation this direction carries is
  **non-optional**: the build must emit the derived Klein signatures to a checked-in
  contract file, diffed by CI — because in code-first, an innocent refactor (renaming a
  field on a shared type) *is* a signature change, a new capability version, and a
  flood of maintenance-queue entries. The emitted file is what makes that visible as a
  contract change in the PR, in Klein terms, before it ships.

Either direction, the derived-or-written signatures hash identically — advertisements
and pins never know which direction produced them. The sections below describe the
contract-first tooling concretely (Kotlin shown); code-first replaces the generation
step and keeps everything else.

### Generated types

Klein types in capability signatures become real host-language types (Kotlin shown):

| Klein | Kotlin |
|---|---|
| `Num` | `Double` |
| `String`, `Bool`, `Unit` | `String`, `Boolean`, `Unit` |
| `T?` | nullable `T?` |
| `type Customer = Customer {...}` | `data class Customer(...)` |
| `type Decision = Approve {...} \| Deny {...}` | `sealed interface Decision` with a `data class` per constructor |

The host works with `Customer(id = 3.0, name = "Ada", score = 710.0)`, not raw Klein
values; a `when` over a generated sealed interface covers constructors the way a Klein
`match` does. Conversion in both directions lives in generated code, so a handler cannot
produce a wrongly-shaped value — the host compiler won't let it. Records without a name
(`{ a: Num, b: String }` directly in a signature) get a generated class named from their
shape, shared wherever the shape recurs; declaring a named type is optional polish.

One restriction on capability signatures, checked when the contract is checked: **no
function types**. A Klein function's only meaning is "the machine can run it", and the
host is not the machine — a handler given a closure could never call it, so
`fun sortBy(xs: List<'A>, key: ('A) -> Num): List<'A>` is unimplementable by
construction. (If host-callable closures are ever wanted, effect handlers are the
principled answer, and relaxing a restriction is free where tightening one would break
contracts already written.)

Everything else crosses fine, including structural records, nested data, and **generic
signatures**: `fun first(xs: List<'A>): 'A` is legal. A polymorphic capability costs the
host nothing to declare when capabilities are declared as types rather than functions
(see below) — the type variable binds at the declaration, so there is no call site
needing a concrete instantiation — and parametricity guarantees such a handler can only
move values it cannot inspect.

### The base layer: requests and responses as data

A run that calls a capability parks and hands the host a suspension. The host may answer
it microseconds later — or persist everything and answer tomorrow, on another machine,
after a reboot. No host-language function signature can span that gap, so the generated
base layer is not callbacks but **data**:

```kotlin
// generated
sealed interface HostRequest
data class CreditCheckRequest(val c: Customer) : HostRequest

fun Execution.AwaitingHost.decode(): HostRequest
fun Execution.AwaitingHost.resumeCreditCheck(result: Double): Execution
```

A host that answers across restarts decodes the request into a plain value, persists it
next to the parked
run, and lets its normal infrastructure (queue, workflow engine, a human) produce the
answer; the typed resume feeds it back in. The exhaustive `when` over `HostRequest` is
the routing — add a capability, and the `when` stops compiling until it is handled.

These request/response classes are also the shape of the effect log: a recorded run is
the list of requests and answers, typed and serializable, one turn boundary per entry.

**Likely direction — declare capabilities *as* these types** (pending; the sketch above
assumes the request classes are generated from function-shaped declarations). If a host
instead declares each capability as the request type itself:

```kotlin
data class CreditCheck(val c: Customer) : Capability<Score>
data class Head<A>(val xs: KList<A>) : Capability<A>
data object MaxRetries : LinkedCapability<Double>
```

then one type is the declaration, the wire message, and the log entry at once — nothing
is generated to twin it. Three things fall out. The declaration is a data class, which
is the only place a serialization annotation can go, so derivation reads it directly.
Type variables bind at the class, so polymorphic capabilities need no call-site
instantiation. And because a request carries no answering strategy, the host chooses
sync, in-memory async, or persist-and-resume per capability, per deployment — a
function-shaped declaration would have encoded that choice in the handler's signature.
Open: whether `Capability` is sealed (an exhaustive `when` makes adding one a host
compile error, at the cost of one module), how the Klein-side name is derived from the
class, and the separate supertype marking a value capability, whose promise is that it
is asked once at the start of each run and constant within it.
Replay and scenario tooling read and write exactly these classes. (Capability *values* —
the run-start constants — are recorded as the log's first entry; they never appear as
requests, so replay would otherwise be missing its inputs.)

### The convenience layer: a handler interface for resident hosts

Most hosts hold the suspension in memory and answer promptly. For them, bindgen derives
a handler interface plus a driver over the message layer:

```kotlin
// generated
interface HostEnv {
    suspend fun maxRetries(): Double
    suspend fun creditCheck(c: Customer): Double
}
suspend fun HostEnv.drive(start: Execution): Value
```

`suspend` covers handlers that await I/O and handlers that return immediately. What it
does **not** cover is surviving a process restart — a suspended `suspend fun` is an
in-memory thing. Hosts that need durability use the message layer directly; the
interface is sugar for the ones that don't.

### Sync capabilities: answering inside the machine

Some capabilities are so cheap that parking the machine to answer them is all overhead —
a feature flag, `log`. The host may register these as **sync**: the machine invokes the
handler directly inside its loop and continues; no suspension surfaces. Four rules, all
consequences of "the machine is waiting on this call":

- Program meaning is identical either way. Sync is a host implementation choice,
  invisible to rule authors and changeable per deployment; declarations don't mention it.
- Sync calls are still effects, recorded in the effect log like any other, so replay
  works regardless of which mode a deployment used.
- A sync handler must be fast and must not block; anything touching a network belongs on
  the async path.
- A sync call is not a turn boundary, so tooling that forks or snapshots runs at
  suspensions cannot fork there. For a debugging session where every call should be a
  fork point, run with nothing registered sync.

### The host sees exactly the declared shape — and that's fine

Klein lets a rule pass a *wider* record than a signature asks for; inside Klein the
extra fields physically travel along. Crossing to the host, the generated class has only
the declared fields — echo the value back and the extras are gone:

```klein
type Wrap = Wrap { tags: { a: Num } }
w = Wrap({ a = 1, b = 99 })      # legal: { a, b } is usable as { a }
echo(w).tags == w.tags           # false: the round trip dropped b
```

This is not a broken promise, because the type never made one — a plain Klein function
with the same signature may do exactly the same thing (`fun echo(w: Wrap): Wrap =
Wrap({ a = w.tags.a })`), and no caller can tell the two apart: undeclared fields are
unreachable through the narrowed type (no casts exist to get them back), so the only
instrument that notices is `==`, and both versions give the same `==` results. The
boundary promises what the types promise. Authors who need a field to survive a host
round trip put it in the declared type — where it had to be anyway for the host to be
allowed to look at it.

## What the org decides

Storage and its guarantees (transactional activation needs a transactional store);
governance (git sync, product approvals, or nothing); editor or files; per-capability
binding time; deploy pacing and cleanup cadence; how the maintenance queue reaches
authors. Klein's side of the contract is the invariants above and structured reports at
every seam.

## Considered and rejected along the way

- **Environment-version lifecycle tables** (draft/active/retired rows): collapsed into
  the reconciler + advertisements; lifecycle states are now derived queries, not stored
  flags.
- **A whole-surface version as the unit of matching**: coupled rules to changes they
  never used; per-capability granularity via pins was strictly finer at no cost.
- **Usage lockfile / assumption export for offline CI**: its green duplicated the real
  check, its red couldn't localize; replaced by running the real check against a corpus
  snapshot where needed.
- **Per-definition content addressing** (Unison-style) for invalidation: everything it
  prunes, a member-wise diff of two small module versions already prunes; renames are
  better served by alias definitions.
- **Inferred principal env requirements** (SimpleSub at the boundary): dies at the first
  binding without full constraint inference, and bidirectional acceptance isn't a
  lattice condition anyway; the checker remains the only oracle.
- **Migrate-all-before-deploy as the only mode**: makes fleet deploys hostage to rule
  authors; kept only as one end of the per-change knob.
- **Semantic versioning numbers** on capabilities: the major/minor distinction is an
  unverifiable claim about impact. The only declared fact worth having is "the meaning
  changed", which is what `review` says, and the treatment of the old version is decided
  per change rather than encoded in a number.
- **Host-side revisions, with superseded signatures supplied at registration**: made ids
  human-facing — you would have to know the hash of a version whose signature had left
  the contract — and lost the name and number that drain reporting needs. Humans always
  say (name, revision).
- **Environment-scoped revisions — numbered snapshot files** (`001.klein`, `002.klein`,
  each a whole self-consistent surface): elegantly dissolves type pinning, since a
  file's `creditScore` means that file's `Customer`. Rejected because duplication scales
  with contract *size* rather than change size — a thousand-line contract copied to bump
  one capability, and diffs that show everything as changed. Per-declaration `/N` costs
  syntax and pays for itself in localized change.
- **Recycling revision numbers after a drain** (renumbering `/2` back to bare so numbers
  stay small): breaks the reconciler, which cannot distinguish "revision 2 was rolled
  back" from "revision 1 retired and 2 promoted" — both leave a single bare declaration.
  Numbers stay monotonic.
- **Identity by signature hash alone**: a hash disagrees on compatible changes and agrees
  on semantic ones, so it is neither necessary nor sufficient for compatibility. Kept as
  a change detector for pruning reconciler work, not as identity.
