# Replay Stays In Order; Migration Is Host Policy

**Status**: Accepted, 2026-08-26. Closes the effect-log keying question
[spec/host-integration.md](../spec/host-integration.md) left open. The living rules are in
[spec/effect-log.md](../spec/effect-log.md); the migration toolkit sketch is in
[ideas/suspended-run-migration.md](../ideas/suspended-run-migration.md).

The open question: replay matches log entries to capability calls by position. That works while
the edition stays the same. Resuming on a *different* edition seemed to need a key that survives
edits, and without one, moving a parked run to a new edition looked impossible. Working through
the question lands somewhere else: the key was never the problem. **The log stays an ordered
list, and replay walks it by position. Migration is a function the host writes, and Klein's job
is the toolkit it is written against.**

## What keying would have bought

Matching by position means entry *k* answers the *k*th question. Insert a call anywhere before
*k* and every later entry shifts, so almost any edit to the rule reads as divergence, even when
the calls themselves did not change. Keying entries by `(name, arguments)` instead of by position
tolerates that: an unrelated insertion no longer shifts anything, and only calls with the same
key can be confused for each other.

That benefit is real, and it is the only one. Keying decides whether a question the new version
asks **matches** an answer already in the log. It says nothing about questions with no match, and
those are where migration actually fails.

## The example

A rule that timestamps an approval:

```klein
t1 = now()
a  = approve(loan)      # parks for days
t2 = now()
```

A run of it, logged:

| # | call | answer |
|---|---------------|------------|
| 1 | `now()` | Mon 09:00 |
| 2 | `approve(loan)` | yes (answered Thu) |
| 3 | `now()` | Thu 14:00 |

The rule is edited: a risk check is inserted before the approval.

```klein
t1 = now()
r  = riskCheck(loan)    # new
a  = approve(loan)
t2 = now()
```

Now replay the parked run against the new version. Entry 1 matches. `riskCheck` matches nothing.
The engine has three options: fail, ask the host now, or ignore the call. Asking now goes
through: the credit bureau is called, and possibly billed, on a Saturday.

Ignoring is what is actually wanted here, and the engine cannot do that either. Klein is
expression-oriented, so `r = riskCheck(loan)` needs a value whether or not the call happens;
skipping the call still leaves `r` to fill in. Someone has to say what `r` is. The engine cannot,
and this is the toolkit's job, reached again below from a different direction.

**That call should never have happened.** The loan was approved on Thursday. A risk check whose
purpose is to inform the approval is meaningless once the approval exists; the question's moment
has passed. And nothing the engine can see says so: to the engine this is just an unanswered
capability call, exactly like one at the start of a fresh run.

## Why no key scheme fixes this

The failure is not that the engine matched the wrong entry. It is that **"ask the host" and
"fail" are both wrong**, and the correct third answer, *this question is obsolete*, cannot be
derived from anything Klein holds.

The obvious repairs each fall over:

- **Refuse to ask the host until the whole log is replayed.** Too strict. An unmatched question
  after the last logged call is harmless, and this refuses it.
- **Refuse only while logged calls remain unreplayed.** This needs to know which entries had
  effects in the outside world, and the contract language cannot say. It also ignores the new
  call's own effect: a call with effects invalidates every logged entry behind it no matter what
  they were, because each of those recorded a world from before the call.
- **Allow it when neither side has effects.** The history still ends up out of cause-and-effect
  order: the new call reads the world of today, and its answer feeds decisions that also consume
  Thursday's answers. Whether that is acceptable is a judgment the engine cannot make.
- **Declare which capabilities are commit points**, past which insertion is forbidden. Closest to
  the truth, but it is knowledge about the host's own systems, not a property of a signature. And
  the general question (would this new call change what a logged read returned?) is a fact about
  the host's databases and third parties. No declaration can decide it.

So the honest statement: **whether a migration is safe is a claim about a specific change, made
by someone who understands both versions and what the host's capabilities do.** It is not a
property of the log, the edition, or the difference between editions.

The rule underneath, stated once: a log's answers may span days (a parked run's always do), but
they must stay in cause-and-effect order. Inserting a call puts it before Thursday's answers in
the program and after them in the world. Replaying in order cannot create that state; only a
migration can, which is why the decision belongs to a person.

## Where this leaves the engine

An ordered log, replayed by position, one path, no modes. The log stays a faithful ledger of what
the run asked and what the host answered, in order, which is what makes it an audit record.

Replaying by position makes **host-call order part of Klein's semantics**: an edition, given the
same answers, asks the same calls in the same order, no matter how or when it was compiled.
Compilation may therefore never reorder host calls. Calls already run in program order; this
makes that a permanent language rule rather than an implementation accident.

The engine's replay handles exactly the case it already handles: the same edition. Everything
else is a host decision, which is the position
[ideas/suspended-run-migration.md](../ideas/suspended-run-migration.md) already takes: migration
is host policy, Klein provides primitives and stops.

## The toolkit

What Klein ships instead of a migration engine. A migration is a host-written function from a
parked run's log and a new edition to a decision, and it needs:

- **The static vocabulary to write against**: capability names and their declared types, known
  from the contract without executing anything. Capability names survive source edits by
  construction, which call-site identity does not.
- **A keyed view over the ordered log**: look up entries by name and by argument values, rather
  than by counting. Which entries to look for is known up front; which entries match is not,
  because argument values only exist once replay has produced them.
- **The two lists**: questions the new version asked that the log could not answer, and answers
  the log held that the new version never asked for. The second list is what needs a person:
  real effects with no place in the current history.
- **The ability to answer a question without asking the host**: derive an answer from logged
  ones and hand it back. Klein's part is the seam and the provenance: a derived answer must be
  distinguishable in the log from one the host actually gave.

Because a question's arguments exist only once replay reaches it, the migration function is
necessarily interactive: replay until an unmatched question, consult the host's policy, continue.
That is the unified `run` with a third answer source beside the log and the handlers: a seam on
the engine the spec already has, not a second engine.

Note what disappears with the engine's version. No idempotency declaration in the contract, no
partition function, and no two-way read of the log for capabilities that cannot be keyed by
their arguments. Those existed to let the *engine* decide when a match was legitimate. A
migration author decides per change, and simply does not key the capability where keying would
be wrong.

## Considered and rejected

- **`hash(name, arguments)` as the engine's replay key.** Tolerates shifted entries, which is the
  common and boring case. Rejected as an engine feature because it only widens what counts as a
  match; the failures above are all about non-matches, so it buys nothing where migration
  actually breaks. It survives as a toolkit tool, where the host chooses when it applies.
- **An `idempotent` marker in the contract.** Its migration use is the above; its within-run use
  (serve repeats from the log) was always redundant with the settled stance that a host caches
  inside its own handler. Nothing else consumed it.
- **Including the revision in the key.** Would have made re-pointing a release change every key
  for that capability, which is the one migration that already works for free, since one source
  compiled against two releases is the same program. The revision belongs in the entry as data,
  never in identity.
- **Bucketing the log per capability**, replayed in order within each bucket. Isolates each
  capability from insertions elsewhere, and throws away the ordering *between* capabilities,
  which recorded when an answer was given. In the example the second `now()` is only meaningful
  relative to `approve`; per-capability buckets cannot express that.
- **A watermark**: the highest position replayed so far, forbidding entries below it. Fixes going
  backwards, never forbids jumping forward over skipped entries, so two adjacent lines can still
  receive answers three days apart. Weaker than it looks.
- **Dedup identical calls at record time.** Would assert purity for every capability, against the
  suspension-path ADR's ruling that two identical calls are two questions. It also destroys the
  audit record, which is the log's other job.
- **Static call-site keys**: identity from the program text, so eligibility could be classified
  across a corpus of parked runs without replaying any. Genuinely cheaper for analysis, and the
  wrong stability: a call site is exactly what a source edit moves, and migration is the case
  where the source changed.
