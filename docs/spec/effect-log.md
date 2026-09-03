# The Effect Log

A run's record of every answer the host gave it. Replaying the log against the run's edition
rebuilds the run without asking the host anything; resuming a parked run needs nothing but the
log. The persistence model behind this is the
[persist-the-log ADR](../decisions/2026-07-31-persist-the-log-replay-the-run.md): machine state is
never serialized, the log is truth, replay rebuilds everything else.

The test suites enforce everything here. The surrounding terms (run, turn, edition, parked) are
defined in
[host-integration.md](./host-integration.md). The decision record is
[replay-is-ordinal-migration-is-host-policy](../decisions/2026-08-26-replay-is-ordinal-migration-is-host-policy.md).

## The log

A log is an ordered list of entries. It opens with a **start** entry: the run's **start values**,
keyed by name. A start value is a capability declared as a value rather than a function: it takes
no arguments and is read once, when the run starts. The start is one whole entry because those
reads are of a single moment. **Reply** entries follow in execution order, each recording a
**call**: an invocation of a capability function, mid-run, with its name, its arguments, and the
answer. A log may end with one **terminal** entry: the run's
**result**, or its **failure**, a runtime error inside the rule, deterministic given the replies.
A failure entry carries the diagnostics, messages and spans, because the log is what a person
reads about a run, failed runs included. A failure on the host's side is an unanswered ask and is never a log entry: the log
ends at the last accepted reply. Nothing follows a terminal entry; appending a reply to
an unterminated log yields a longer log.

Every start value is read during prelude bind evaluation, before any rule expression runs, so
the input phase is a real phase of every run and the map is order-free.

Call entries are positional. The answer recorded is the answer the machine resumed with, checked
at the boundary when the host gave it fresh. Replaying by position makes host-call order part of
Klein's semantics: an edition, given the same answers, asks the same calls in the same order, no
matter how or when it was compiled.

Nothing else is in a log. No revisions, no pins, no header: which edition a log belongs to is the
host's run record, and the edition owns the pins. A pending call is never in a log; the log holds
answers.

A log is immutable: appending yields a longer log, and a recorded entry never changes.

## Recording

Every run produces a log, and every outcome carries it. The input phase records the start entry,
atomically, when its last answer is accepted. A call records its reply when the answer is
accepted, before the machine resumes. A completed run records its result; a run that fails on a
runtime error inside the rule records its failure. A run failing that way after its third call
records the start, three replies, and the failure; a run failing on the host's side records
nothing past the last accepted reply.

## One entry point

Running takes an edition and, optionally, a log; it returns an outcome. There is no other way to
execute an edition, and no separate replay operation. A fresh start is the no-log case; a log,
when given, is replayed first, and the run continues live past it:

- A start value takes its answer from the start entry by name. The host is not asked. A start
  value the run never asks for is ignored: unread entries feed nothing and change no behavior.
- A call takes the next unconsumed reply's answer, after checking that its name and arguments
  equal the reply's. The host is not asked.
- When the log is exhausted, the host answers each further ask, and each newly answered ask is
  recorded as above.

Replaying a result-terminated log reaches the outcome without asking the host. Resuming a run is
the same operation with a longer log.

A run refuses to start unless the environment can answer every pin, whether or not the log would
have covered them. During replay the host is asked nothing; a partial log's run continues past
the log's end and asks the host like any live run.

A second pre-flight check covers the log itself: every recorded answer (each start value, each
reply's answer) is checked against the capability's declared answer type before the machine starts, the
same check the resume boundary applies to a live answer. A mismatch fails the run
naming the entry, what it holds, and what the contract declares. This is a claim about the log
against the contract, not against the edition's execution (corrupted storage, encoding skew, or
an in-place contract edit that was not as compatible as it claimed), which is why it is not a
divergence. Recorded arguments are not type-checked: they are only ever compared for equality
against machine-produced arguments, which the checker already guarantees.

A live ask is checked in both directions: the call's arity and each argument against the declared
parameter types before the handler runs, and the answer against the declared answer type. Start
values take no arguments, so only their answers are checked.

## Divergence

Divergence means the log was produced by a different edition, or it was changed on the host's
side. It is a mismatch during replay, raised before the host is asked anything and before any
reply is recorded. Each of these diverges:

- A start value whose name is not in the start entry.
- A call whose name differs from the next unconsumed reply's.
- A call whose arguments are not structurally equal to the next unconsumed reply's.
- An outcome reached with unconsumed replies remaining.
- A replay that reaches a result different from a recorded result entry.

A recorded ending is replayed like a reply: the run's own ending is compared against it, and any
mismatch diverges: a different result, a different failure, a run that completes where a failure
was recorded or fails where a result was, a call past the recorded ending. A matched
ending means the outcome mirrors the record, with nothing new recorded or persisted. Failures
match on their diagnostics; see future work.

## Outcomes

An outcome is one of three: **completed**, with the run's final value; **failed**, with the rule's
own diagnostics, the mirror of the log's failure entry; or **parked**, with the outstanding
question (see below). Every outcome carries the full log: the replayed entries plus every newly
recorded one.

Failure is an outcome only when the rule itself fails: a failed outcome is a normal result, the
thing a rule author sees in a UI. Everything else the run detects is the host's mistake and is an
error, not an outcome: a bad registration, a pin the environment cannot serve, a recorded answer
that does not fit the contract, a divergence, a call whose arguments do not fit the contract, a
handler answering the wrong type. An exception in
the host's own code (a handler, a deferred initiation, the persistence callback, the transaction
wrapper) passes through unwrapped, as it does everywhere. The host already holds the log it
passed in, and every newly recorded entry reached it through persistence before the error, so no
error loses log entries a host cares about.

## Parked

Capabilities may be answered during the lifetime of the host deployment (keeping the machine state in-memory),
or durably and asynchronously by parking the run. Parked runs are represented by the log entirely,
no machine state is ever persisted. The run returns the log so far and the one outstanding question, 
its name and arguments. The pending call is not in the log. When a capability is designated for parking 
is determined by the host.

Resuming a parked run mandates that the host first persist the new reply by appending it to the log, followed
by starting the run with the freshly updated log. Resuming needs nothing but the log: the same operation 
resumes in the same process or another one, and no other resume mechanism exists.

## Serialization

A log round-trips as bytes: encoding then decoding yields a log that replays identically to the
original. The encoding carries a version stamp. A log whose version
cannot be read is an error: a log is the one artifact that cannot be re-derived, so the
discard-and-re-derive rule for stored IR does not apply to it.

## What the host owns

Klein owns the schema, the log recording order, the divergence check, and the byte-encoding for serialization.
The host owns storage, durability, run identity, the run-to-edition association, and retention. The
log as a stored artifact exists only on the host's side; the library hands it over as a value and
takes it back as a value. Each accepted entry (start, call, and terminal) is offered to the host
before execution continues, so a crash loses at most the ask in flight, never a recorded entry.
How the host receives entries is the embedding API's design, not this spec's.

## Future work

- **Failure matching beyond message text.** A recorded failure matches on its diagnostics, so
  the message text is part of its identity: a release that rewords an error message makes older
  failure-ended logs diverge on replay. A stabler identity for failures, one that survives
  message edits, is a later improvement.
