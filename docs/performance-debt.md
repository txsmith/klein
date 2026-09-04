# Performance Debt

Deliberate performance corners cut in favor of simplicity, correctness, or serializable
state. None of these change results — they are all "the simple version works, optimize once
a profiler says it matters."

The entries themselves live in the backlog under the `perf` label
(`backlog task list -l perf --plain`), one task per corner, each carrying what we do now,
why it is deliberate, and the fix. The framing they share stays here: these are not bugs,
and a task read without its "why" will get fixed at the wrong time. Revisit them as
measured before/afters against the baseline below.

## The baseline (2026-07-31)

The IR machine vs the AST machine it replaced, `eval` stage (pre-compiled input for both),
JMH avgt on the same JVM — the last same-run comparison before the AST machine's deletion:

| program | AST machine | IR machine | ratio |
|---|---|---|---|
| `arith` | 14.2 µs | 1.78 µs | 8.0× faster |
| `fib` | 582 µs | 646 µs | 0.90× |
| `sumTo` | 63.4 µs | 70.7 µs | 0.90× |
| `closures` | 240 µs | 38.6 µs | 6.2× faster |
| `records` | 12.3 µs | 2.03 µs | 6.0× faster |
| `rules` | 48.2 µs | 4.00 µs | 12.0× faster |

`lower` costs 0.5–2 µs/program. The big wins are setup work moved to compile time (the AST
machine rebuilt scope/SCC analysis and name-keyed envs per execution); the two slightly-red
cells are the pure stepping loop, where boxing, operand-cons churn, per-`if` arm scopes, and
per-comparison `VBool`s — now `perf` tasks — are the whole story. The old *recursive*
evaluator's recorded `fib` was 266 µs; the IR machine's 646 µs is the retained price of
suspension-as-data.

## The log-persistence decision (2026-07-31)

Graduated to an ADR:
[decisions/2026-07-31-persist-the-log-replay-the-run.md](decisions/2026-07-31-persist-the-log-replay-the-run.md).
Machine state is never serialized; the effect log is the durable tier and replay rebuilds
the hot tier. Consequence for this list: serializability was the *why* behind the most
expensive entries — they are now unblocked (walkability for the hot-tier debugger is
the surviving constraint, and it gates only host-resident interiors, which debug replays
run unfused). The affected `perf` tasks carry an "unblocked by the log rethink" note.
