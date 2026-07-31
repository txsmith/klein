# Persist the log, replay the run

**Status:** Current · **Date:** 2026-07-31

A suspended run is persisted as exactly three things: the compiled artifact (pinned by
checksum), the extern vals it was linked with, and the log of extern-fun responses so far.
**Machine state is never serialized.** Cold resume re-runs the program from the start,
feeding logged responses back until the log runs out, then continues live. The machine is
deterministic by construction — nondeterminism enters only through the extern boundary — so
the state at any suspension is a pure function of those three records. (Temporal works this
way; Klein gets the determinism Temporal has to fight for from the language itself.)

Two tiers follow. The in-memory machine is the **hot tier**: resume is O(1) while the
process holds it, and inspection, traces, and forking happen there. The log is the
**durable tier**: crash, eviction, migration — resume costs one replay. During replay each
re-issued request is matched against the logged one — name and arguments — so any semantic
drift fails loudly at resume, never as silent divergence.

## What this deletes

- The machine-state serialization format, its schema, and its versioning story — never built,
  now never needed. IR serialization remains (the artifact is still stored).
- Stored stack snapshots: the persisted form of a run is a readable ledger of what it asked
  the host and what the host answered — a business artifact, not a core dump.
- Most of the migration ladder: pin, or replay against the new version (divergence detection
  makes incompatibility exact). IR diffing and authored transforms stay as last resorts.

## What this promotes

- **Determinism becomes correctness-critical.** A nondeterminism bug is no longer a flaky
  test; it is persistence corruption — a suspended run that cannot resume. Cross-platform
  replay (a log written by a JS host, resumed on the JVM) leans on IEEE double semantics
  being identical, which makes open numeric rulings (e.g. `-0.0` equality) replay-visible.
- **The pinned evaluation semantics become a compatibility contract.** Replay of old logs
  depends on evaluation order, strictness, and equality staying exactly as the eval suites
  pin them — the suite guards suspended runs in production, not just correctness today.
- **Fuel bounds replay structurally.** A run that lived under a step budget of F replays in
  at most F steps. The known limitation — unbounded pure compute between extern calls
  inflates replay — is mitigated by the same mechanism that already exists for abuse.

## Derived views

The log is truth; everything else stored about a run is a disposable cache, re-derivable by
replay: persist-time trace snapshots for dashboards, full traces for forensics (replay with
full tracing on — exact, because deterministic), and debugging itself (attach = replay to
any event, then inspect the live machine: named locals via the IR's name metadata, exact
error states, what-if forks). Retraction never edits the log: truncate and reconcile —
re-execution matches retracted write events as an idempotency ledger; unmatched orphans are
the compensation list — never splice, because a spliced log is a history no execution could
have produced.

## What the hot tier still requires

The in-memory machine stays **walkable data**: the debugger reads locals off frames, traces
render from the control stack, and forking copies machine state. This is the surviving form
of the machine-as-data decision — coroutine-style machines remain rejected because opaque
host frames cannot be inspected or forked even in memory. But walkable no longer means
persistent: with snapshots gone as a storage concern, mutable stacks are unblocked and
`clone` degrades gracefully to an O(depth) copy or a fork-by-replay. Host-resident
optimization interiors (fused superinstructions, unboxed locals) are gated by *debug
fidelity*, not storage: debug replays run unfused, as a JIT deopts under a debugger.

## Supersessions

- [2026-07-20-own-machine-not-a-rented-vm.md](2026-07-20-own-machine-not-a-rented-vm.md) —
  conclusion stands; its serializability leg is replaced by this decision (see its updated
  rationale).
- The boxed-values ADR is retired: with serializability out of the argument, it recorded
  platform facts and a debt schedule, not a decision. Its escape-pricing lives in
  [performance-debt.md](../performance-debt.md).
- [2026-07-20-source-is-truth-ir-is-a-cache.md](2026-07-20-source-is-truth-ir-is-a-cache.md) —
  extended, not changed: the same principle now governs runs (log is truth, snapshots are
  caches) as governs programs (source is truth, IR is a cache).

Sequencing for the unblocked performance entries: the benchmark baseline is recorded in
[performance-debt.md](../performance-debt.md); make the changes as measured before/afters.
