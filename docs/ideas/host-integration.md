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
(`maxRetries: Num`). A capability is declared to Klein as a signature only; its
implementation is ordinary code inside the host. Capabilities are versioned individually
by their signature: change the signature and it is a new capability version. The
capability is the *only* thing a rule and the host share; everything else in this doc is
bookkeeping about who provides and who needs which capability versions.

**Environment** — a place in the host application where rules run: one injection point,
one business context. An environment fixes three things: the **contract** rules there
must have (the eligibility check takes an `Application` and returns a `Decision`), the
**curated set** of capabilities — by name — that rules there may use, and the
**invocation site** (what event in the application triggers a run). Curation says what
is *appropriate* in this context; which *version* a name means is never curated — the
system resolves it (the newest served, non-retired version) at compile time, so version
state lives in one place. Environments are a product concept; capabilities are shared
across them (one `creditCheck`, curated by both features).

**Module** — a Klein program exposing types and functions, versioned in the rule store,
evolved at editor speed. Modules may depend on other modules, and may call capabilities —
in which case the requirement travels: a rule using the module inherits its capability
needs. Pure modules are portable across environments; capability-using modules fit only
environments that curate what they need. Shared "hub" types (`Customer`) live in modules
and version like everything else; same-named type versions are compatible when
structurally compatible (the checker bridges them by shape, which is also exactly what
the machine does at runtime).

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

**Advertisement** — a node's declared capability set: every capability version its
binary implements, as (name, signature hash) pairs. A node can serve a run iff its
advertisement covers the run's pinned capabilities. Nodes never serve "an environment
version"; they serve capability sets.

**Reconciler** — the incremental background process that keeps the editions in step 
with the fleet. When the fleet converges on advertising a new capability version (with
hysteresis, so a mid-rollout rollback doesn't cause flapping), the reconciler checks the
affected rules — only those whose editions pin the changed capability — adds
editions for the compatible ones, and flags the rest.

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

**Drain** — the countdown, after retirement, to removing the version's handler. Two
counts fall monotonically to zero: rules whose newest edition still pins the version
(each needs a newer edition free of it — the reconciler's job), and parked runs
pinned to it (each must finish). At zero the version is **removable**: the next host
deploy may remove its handler. Editions are never deleted — unservable ones are
simply routed around; they remain as history. Removal has no deadline; old handlers are
generated code that costs nothing to carry, so cleanup can be batched quarterly.

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
may use it. No editions, no drain — additions are free.

**Changing or removing a capability.** The new binary serves old and new versions (the
adapter pattern keeps that cheap). As the fleet converges, the reconciler adds
editions against the new version for compatible rules and flags the rest; fresh
events shift per rule to the newest servable edition. Once the fleet is stable, the
dev **retires** the old version; its references drain, and at zero the handler can be
removed. There is no synchronization point: no deploy waits on authors, no author waits
on deploys, and the only clock is the drain query.

**How fast?** Dealer's choice per change. All-compatible corpus: editions bulk-
compile in seconds and the old version drains as fast as its parked runs finish — the
"three-phase dance" collapses to an ordinary deploy plus one cleanup. Wide breakage: the
maintenance queue does its slow human work while both versions serve indefinitely. The
impact report (which rules, which diagnostics — computed where the rules live) tells you
which case you're in before you commit.

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

- Every (edition, pinned versions) pair is checked **exactly once, at creation**.
  Immutability on both sides makes the verdict permanent; evolution creates and
  supersedes pairs, it never invalidates one.
- Pin hash comparison skips everything untouched: a capability change re-checks
  only the rules whose editions pin it. This is memoization, not a compatibility
  oracle — a hash mismatch means "go run the checker", nothing more.
- The authoritative question is always "does this rule compile against these versions",
  answered by the real checker, run **where the rules live** (the org's store, the org's
  process). No subtype shortcut substitutes for it; the shortcuts only shrink how often
  it runs.

## Implementing capabilities

The host declares its capabilities (a signature-only file in the host repo) and
implements them in its own language. To keep declaration and implementation from
drifting, the code the host writes against is **generated** from the declarations —
`bindgen`, a build step; CI fails if the checked-in output is stale. Change a
declaration and the host stops compiling until someone updates the implementation.

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

Two restrictions on capability signatures, both mechanical, both checked at declaration:
**no type variables** (generated code needs concrete types to convert) and **no function
types** (a Klein function's only meaning is "the machine can run it", and the host is
not the machine). Everything else crosses fine, including structural records and nested
data.

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

A durable host decodes the request into a plain value, persists it next to the parked
run, and lets its normal infrastructure (queue, workflow engine, a human) produce the
answer; the typed resume feeds it back in. The exhaustive `when` over `HostRequest` is
the routing — add a capability, and the `when` stops compiling until it is handled.

These request/response classes are also the shape of the effect log: a recorded run is
the list of requests and answers, typed and serializable, one turn boundary per entry.
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
