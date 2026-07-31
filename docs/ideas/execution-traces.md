# Execution traces, trace modes, and metering

**Status:** Idea (design settled in outline, not built) · carved out of the Core IR plan

The machine's tail-call discipline is pop-before-push: after a thousand tail calls the
control stack is two frames and the call history is gone. That is the feature working —
constant space *is* the erasure of history. Tracing is the knob that lets a host buy some
history back.

## The design

**Call markers.** On closure entry the machine records `(callee name, call span)` — both
already in the IR (`Lambda.name`, `Apply.span`). A marker pops as an identity frame when its
call returns; a tail call *replaces* the top marker instead of stacking a new one.

**Trace modes, host-chosen per execution.** `start()` takes a mode; it never changes
results, only memory behavior and trace fidelity:

- `full` — keep every marker. O(calls) memory (on the heap, not the control stack); perfect
  traces. Right for development and small rule programs.
- `budgeted(n)` — ring buffer of the last n markers; elided history collapses into one
  counted `(...×N tail calls...)` entry. The default.
- `elided` — record nothing; errors show only the live control stack. Production mode.

**Metering.** `start()` also takes an optional step budget (fuel) — a counter in the machine
loop — so untrusted rule authors can't loop forever. Deterministic step count doubles as a
reproducible cost metric (quotas, regression alerts) independent of wall clock.

**Error traces.** `KleinRuntimeError` grows a `trace`; the CLI renders Klein stack traces;
a suspension exposes its location.

## Machine-side, not IR-side — for now

GHC's tick lesson (annotations survive only if they're terms the rewrite rules must respect)
does not yet apply to Klein: nothing rewrites Core after lowering, so term-level markers
would pay the every-pass-must-skip-ticks tax without collecting the benefit. Trace machinery
therefore stays in the machine, keyed by the IR's existing names and spans. The moment an
optimizer over Core exists, this decision must be revisited — and Klein has an escape GHC
never had: elided mode plus deterministic replay (see
[persist-the-log-replay-the-run](../decisions/2026-07-31-persist-the-log-replay-the-run.md))
can recover an exact trace offline, so soft markers don't have to mean lost history.

## Open question

How a trace entry retires on an ordinary (non-tail) return: a side log needs a "call
returned" event the machine doesn't naturally have; marker frames on the control stack get
retirement for free but make `budgeted` eviction awkward on a persistent cons stack. Decide
when building.
