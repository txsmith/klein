# The Host Environment

How a host application declares what Klein programs may use, how rule authors see it, and
how the host implements it. Companion to [host-interface-evolution.md](./host-interface-evolution.md),
which covers what happens when the environment changes over time.

## The environment is a Klein module

Everything the host offers is declared in one Klein source file, written in plain Klein
plus one new form: a declaration without a body (`extern`), meaning "the host provides this".

```klein
# host.klein — lives in the host application's repo

type Customer = Customer { id: Num, name: String, score: Num }
type Decision = Approve { limit: Num } | Deny { reason: String }

extern val maxRetries: Num
extern fun creditCheck(c: Customer): Num

fun isVip(c: Customer): Bool = creditCheck(c) > 700
```

Three kinds of member, three different meanings:

- **`extern val maxRetries`** — a value the host hands over once, when a program starts.
  It is constant for the whole run: every read during one execution sees the same value.
- **`extern fun creditCheck`** — a question the host answers every time it is called.
  Two calls in one run may get two different answers. This is the only member kind that
  makes a running program stop and wait.
- **`fun isVip`** — ordinary Klein. It compiles into the programs that use it, exactly as
  if the author had written it themselves. The host never sees it.

The rule of thumb for authors: cheap ambient facts are `extern val`s; anything expensive,
per-call fresh, or side-effecting is an `extern fun`; anything you can compute from those
is a plain `fun` in the module.

A program is compiled as if the module's contents were an enclosing scope around it. That
is the whole semantics: module `fun`s work like any top-level `fun`s (mutual recursion and
all), module `val`s evaluate at program start in order, and there is nothing special to
learn. Everything is per-run — module values are not cached across runs, because nothing
in Klein is.

## The rule author's view

Rule authors never see a CLI. They write rules in a web editor that the host application
serves, and the host's backend — which embeds the Klein library anyway, since it runs the
programs — does the work:

- **While typing**: the backend calls `check` against the environment module and gets back
  structured diagnostics (position, message, severity) that the editor renders as
  squiggles. Check speed is therefore an interactive constraint, not a batch one.
- **Completion and docs**: the checked module's surface — what names exist, their types,
  what fields a `Customer` has — is available as data, so the editor's autocomplete and
  hover docs come straight from `host.klein`. The environment file is the editor's
  vocabulary.
- **On save**: the backend calls `compile` and stores the artifact: the compiled program
  (module code included), the list of extern names it uses with the types it assumed for
  them, the checksum of the `host.klein` it was compiled against, and the source (kept so
  the program can be re-checked when the environment changes later).

The library is the product surface; every function returns data, never printed text, so
the host's UI decides how things look. The CLI (`klein check`, `klein compile`) does the
same things on files — it exists for host developers and for working on Klein itself, not
for rule authors.

## The host developer's view

The host implements only the externs. To keep the Kotlin side and `host.klein` from
drifting apart, the Kotlin code the host writes against is generated from the file:

```
klein bindgen host.klein -o generated/HostEnv.kt
```

Regenerating is a build step; CI fails if the checked-in generated file is stale. The
consequence: change `host.klein` and the host application stops compiling until someone
updates the implementation. The declaration and the implementation cannot disagree,
because one is derived from the other.

### Generated types

Klein types in extern signatures become real Kotlin types:

| Klein | Kotlin |
|---|---|
| `Num` | `Double` |
| `String`, `Bool`, `Unit` | `String`, `Boolean`, `Unit` |
| `T?` | nullable `T?` |
| `type Customer = Customer {...}` | `data class Customer(...)` |
| `type Decision = Approve {...} \| Deny {...}` | `sealed interface Decision` with a `data class` per constructor |

So the host works with `Customer(id = 3.0, name = "Ada", score = 710.0)`, not with raw
Klein values, and a `when` over a generated sealed interface covers constructors the same
way a Klein `match` does. Conversion in both directions lives in generated code. A host
handler cannot produce a wrongly-shaped value — the Kotlin compiler won't let it.

Records without a name (`{ a: Num, b: String }` directly in a signature) get a generated
class name derived from their shape; the same shape appearing in several signatures gets
the same class. Declaring a named type instead is optional and just makes the generated
code prettier.

### The base layer: requests and responses as data

A running program that hits an `extern fun` stops and hands the host a suspension. The
host may answer it microseconds later — or serialize everything, put it on a queue, and
answer it tomorrow on a different machine after a reboot. No Kotlin function signature can
span that gap, so the generated base layer is not callbacks but **data**:

```kotlin
// generated
sealed interface HostRequest
data class CreditCheckRequest(val c: Customer) : HostRequest

fun Execution.AwaitingHost.decode(): HostRequest
fun Execution.AwaitingHost.resumeCreditCheck(result: Double): Execution
```

A durable host decodes the request into a plain value, persists it next to the parked
program, and lets its normal infrastructure (queue, workflow engine, a human) produce the
answer. When the answer exists, the typed resume feeds it back in. An exhaustive `when`
over `HostRequest` is the routing — add an extern, and the `when` stops compiling until
it is handled.

These request/response classes are also the shape of the recorded effect log: a recorded
run is the list of requests and their answers, in typed, serializable form. Replay and
scenario tooling read and write exactly these classes. (The `extern val`s a run started
with are recorded too, as the first entry — they never appear as requests, so they must
be captured at start or replay would be missing its inputs.)

### The convenience layer: a handler interface for hosts that stay resident

Most hosts hold the suspension in memory and answer promptly. For them, bindgen derives a
handler interface plus a driver over the message layer:

```kotlin
// generated
interface HostEnv {
    suspend fun maxRetries(): Double
    suspend fun creditCheck(c: Customer): Double
}

suspend fun HostEnv.drive(start: Execution): Value
```

```kotlin
// the host writes only this
class ProductionEnv(private val db: Db) : HostEnv {
    override suspend fun maxRetries() = 3.0
    override suspend fun creditCheck(c: Customer) = db.scoreFor(c.id)
}
```

`suspend` covers both cases: a handler that awaits I/O and one that returns immediately.
What it does **not** cover is surviving a process restart — a suspended `suspend fun` is
an in-memory thing. Hosts that need that use the message layer directly; the interface is
sugar for the ones that don't.

### Sync externs: answering inside the machine

Some externs are so cheap that leaving the machine to answer them is all overhead:
reading a feature flag, a pure helper the host exposes, `log`. The host may register
these as **sync** — the machine invokes the handler directly inside its run loop and
continues, and the outer loop never sees a suspension:

```kotlin
val env = ProductionEnv(db)
    .sync("log") { msg -> logger.info(msg); VUnit }
```

Rules for sync handlers, all consequences of "the machine is waiting on this call":

- The meaning of the program is identical either way. Sync is an implementation choice of
  the *host*, invisible to rule authors, and changeable per deployment — `host.klein`
  does not mention it.
- Sync calls are still effects: they are recorded in the effect log like any other, so
  replay works regardless of which mode a deployment used.
- A sync handler must actually be fast and must not block — a slow one stalls the machine.
  Anything that touches the network belongs in the async path.
- A sync call is not a stopping point, so tooling that forks or snapshots executions at
  suspensions cannot fork there. If you need every call to be a fork point (a debugging
  session), run the same env with nothing registered sync.

## What extern signatures may not contain

Two restrictions, both mechanical, both checked when the module is compiled:

- **No type variables.** `extern fun first(xs: List<'A>): 'A` is rejected — the generated
  Kotlin needs concrete types to convert to and from.
- **No function types.** `extern fun retry(f: () -> Num): Num` is rejected — a Klein
  function's only meaning is "the machine can run it", and the host is not the machine.

Everything else crosses fine, including structural records and nested data.

## The host sees exactly the declared shape — and that's fine

Klein lets a program pass a *wider* record than a signature asks for:

```klein
type Wrap = Wrap { tags: { a: Num } }
w = Wrap({ a = 1, b = 99 })     # legal: { a, b } is usable as { a }
```

Inside Klein, that `b = 99` physically travels along inside `w`. But when `w` crosses to
the host, the generated `data class` has only the declared fields — the host receives
`a = 1` and nothing else, and if it echoes the value back, `b` is gone:

```klein
extern fun echo(w: Wrap): Wrap
echo(w).tags == w.tags           # false: the round trip dropped b
```

This is not a broken promise, because the type never made one. A plain Klein function
with the same signature is allowed to do exactly the same thing:

```klein
fun echo(w: Wrap): Wrap = Wrap({ a = w.tags.a })   # also "drops" b
```

No caller can tell the host version and this Klein version apart: the undeclared fields
are unreachable through the narrowed type (Klein has no casts to get them back), so the
only way to notice is `==` — and both versions give the same `==` results. The boundary
promises what the types promise, no more. Authors who need a field to survive a host
round trip put it in the declared type, which is where it had to be for the host to be
allowed to look at it anyway.

## Re-checking after an environment change

Re-checking needs no "old" environment file, because there is no single old environment:
rules compiled at different times were compiled against different versions. Instead, each
stored artifact carries its own past — the extern names it uses and the types it assumed
for them. So the call is:

```kotlin
Klein.recheck(newEnv, artifacts)
```

Per artifact it can answer the cheap question first — does the new environment still
provide everything this artifact assumed, at compatible types? — from the artifact's own
records, without touching the source. The full source re-check (which also finds the
newly-dead arms and always-false comparisons, reported as warnings) runs on top.

Where the artifacts come from depends on who is asking:

- **Production truth**: the host's backend owns the database and embeds the library, so
  the real re-check is a library call in the host's own admin or deploy path — their
  command, their credentials, their report rendering.
- **CI and host-dev machines**: `klein recheck host.klein rules/` over a directory of
  artifact files. The serialized artifact format doubles as the interchange format — a
  host that wants CI to test the real corpus dumps artifacts to files and points the CLI
  at the directory. A repo-resident fixture corpus works the same way.

`klein env-diff <old> <new>` remains as a two-file convenience for the editing loop
(tighten vs loosen, before any corpus is involved), with git supplying the old file.

## The workflow, end to end

1. Edit `host.klein` — the only place the contract lives.
2. Build regenerates the Kotlin; the compiler now points at every handler that must change.
3. CI re-checks a corpus of artifacts against the edited module (see above); the PR shows
   the module diff and any affected rules. The full evolution story — what may change
   freely, what needs recompiles, what routes to authors — is in
   [host-interface-evolution.md](./host-interface-evolution.md).
4. Deploy. `Machine.start(artifact, env)` refuses to link if the artifact's environment
   checksum is incompatible with the running host — version skew is a startup error with
   a clear message, never a wrong answer.
