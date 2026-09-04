# Capabilities Execute Through the Suspension Path

**Status**: Accepted, 2026-08-24. The living rules are in
[spec/host-integration.md](../spec/host-integration.md) (§Capability, §Edition, §Run). This ADR
records the execution-wiring decisions and what was rejected on the way to them.

## The decision

A rule that checks against a release also runs against it:

```klein
creditScore(customer) >= 620 and customer.tier == "gold"
```

Compiling this against release 2 yields an edition — a program of plain names plus a pin map
`{creditScore → /2, customer → /2, Customer → /2}`. Running it suspends on `customer` once at the
start, suspends on `creditScore` at the call, and the host answers each suspension through the
implementation its pin names.

The load-bearing choices:

- **Every capability interaction is a suspension.** A capability value is a nullary ask at the
  start of the run; a capability call is an ask at the call. One channel, the machine's existing
  suspend/resume path, with `Core`, `Machine` and `Execution` untouched by the feature.
- **The compiled program is revision-free; the pins carry the resolution.** One source compiled
  against two releases is the same program with different pins. Dispatch is a name-to-revision
  lookup at the boundary, never a fact inside the program.
- **The library never caches an answer.** Two identical calls are two questions. Only the host
  knows whether a capability is pure — `log` must be askable twice — so caching lives inside the
  host's implementation or nowhere. A value is the complement: asked once, every later read sees
  the same answer.
- **The run is guarded at both ends of the boundary.** It refuses to start unless every pin is
  answerable, and every answer is checked against the declared type as it arrives. A missing
  handler fails before the first effect; a wrong-shaped answer fails at the call that produced it
  and never enters the program.
- **An interactive answer is Klein.** The CLI's prompt compiles what the person types against the
  release's types — with its capabilities excluded, so answering a question can never raise
  another one.

## Semantics settled, mechanism deferred

Each choice above is intended as final semantics. Two mechanisms were deferred with their triggers
recorded, and nothing observable waits on either:

- **Store pre-fill for capability values.** The runner could fill the value's cell before the
  machine starts, saving one suspension per value. Same observable behaviour. The suspension path
  shipped first because it needed no new machinery; pre-filling revives if that suspension ever
  shows up in a profile.
- **The deferred hand-off.** `deferred(name) { call -> }` — the host owning the continuation — is
  commented out, to return with the effect log. In-process waiting is a blocking handler; across a
  restart the answer path is replay, which needs the log. (Settled since, in
  [spec/effect-log.md](../spec/effect-log.md) §Parked: deferral returns as a `Parked` outcome with
  the log alone resumes a parked run; the callback-and-token shape is deleted, not revived.)

## Considered and rejected

- **Revisions in the compiled program.** A `HostCall` carrying its revision would spare the
  boundary lookup, but the program would no longer be shared across releases, and revision
  knowledge would sink below the host layer into the IR. The pins already say everything once.
- **A library-side memo table, or a cacheability flag at registration.** Both put purity knowledge
  in the wrong party's hands. A flag at registration is also invisible to rule authors; if purity
  is ever declared, it belongs in the contract where authors can see it — the trigger for that is
  the effect log's entry granularity.
- **A stubs file for the CLI** — capability answers written in a bespoke file format. It would
  need parsing, checking and documentation that no real host would ever use. The example host
  exercises the real embedding API instead, and interactive mode serves the terminal user.
- **A typed seam for deferral now** — registries and environments parameterised by mode, so `run`
  is absent where handlers may defer. Designed, then deleted with the deferral it guarded: API for
  machinery that does not exist. It returns, in whatever shape the log demands, when the log does.
- **A result sink in this feature.** A release nominating where the answer goes changes the
  contract language, so it goes spec-first, on its own — extracted as future work rather than
  rejected.
