# The Result Sink

A release nominates one capability as where the rule's answer goes. Design draft, extracted
2026-08-24 from the callable-capabilities design (whose phased file list lives in that work's git
history). It changes the contract language — §Releases says a release entry names a declaration and
nothing else — so it goes spec-first: contracts.md and grammar.md, three new contract checks, and
an ADR for the real alternatives (implicit sink, trailing-expression-only, allowing mixed
conclusion). Builds on `compileRule` and the runner.

## A release may nominate one capability as where the rule's answer goes

A rule's answer is its trailing expression. That works, but it can't express "decide early", and it
gives the host no way to state what a rule must produce. Both fall out if the answer travels through
an ordinary capability:

```
type Decision = Approve { limit: Num } | Decline
fun decide(d: Decision): Nothing

release 2
  Decision
  result decide
```

`result` is a contextual prefix on a release entry, the same shape `remove` already has, so which
capability plays the role — and what it is called — is the contract author's choice, per release. A
host writes `decide`, `conclude`, `approve`, or `price` as its domain prefers.

The sink's parameter type *is* the release's declared result type. A rule delivers through it one of
two ways, and never half of each:

```klein
# it never mentions decide: the trailing expression is wrapped for it
customer.tier == "gold" and creditScore(customer) >= 620
    ->  decide(customer.tier == "gold" and creditScore(customer) >= 620)

# or it concludes on every path itself, and nothing is added
if score < 500 then decide(Decline) else decide(Approve(limit))
```

Mixing the two — concluding in one branch and falling out with a value in another — is a compile
error, not a silent wrap. That is the whole point of the rule: an author who writes `decide` in one
place has the concept in mind, so leaving the other path implicit is far more likely a forgotten
conclusion than an intention, and no diagnostic can tell which if we accept it.

A rule that never concludes and whose trailing expression doesn't fit is the same error from the
other side: `this rule produces Bool; decide declares Decision`. The sink's parameter type is what
makes that check possible, and ordinary argument checking is what performs it.

Early exit needs no type-system work: `Nothing` is `TBottom`, already surface-writable, already the
bottom of `isSubtype`, and `lub(TBottom, T)` already returns `T` — so an `if` arm that concludes
and one that carries on join cleanly.

Three new contract checks come with the entry: a release surface may mark **at most one** result, the
marked name must be exposed by that release, and it must be a `fun` returning `Nothing` — which is
what makes an explicit early `decide(...)` and the implicit trailing wrap mean the same thing.

Releases that mark no result are unchanged: the trailing expression is the run's value, as it is for
contract-free `klein run` today.

## A capability declared `Nothing` ends the run — a rule the runner enforces, not the machine

A call to a capability declared `: Nothing` suspends like any other, hands the host its argument, and
the run is **over**. Nothing in the machine or the IR knows this: the runner resolves every call to
its `Capability` anyway (it needs the declared type to shape-check answers), sees `Nothing`, and
simply never resumes — abandoning a suspension is legal, since one-shot semantics forbid using it
twice, not zero times. `Environment.run` answers with the value the rule concluded.

So `Core`, `Machine`, and `Execution` are untouched, and the terminating rule sits beside the other
policy decisions outside the machine. The IR stays contract-free — the same reason `HostCall`
carries no revision.

## The sink decision is a branch on two facts `compileRule` already has

Checking runs first, so the rule's type is known before anything is rewritten; the used-capability
pass already says whether the rule mentions the sink. The wrap is then a two-way branch, and every
other shape is a diagnostic rather than a guess:

```text
release marks no sink        -> lower as-is; the trailing expression is the run's value

sink referenced by the rule
    type is Nothing          -> concludes on every path; lower as-is
    otherwise                -> RuleConcludesInconsistently

sink not referenced
    isSubtype(type, param)   -> wrap: Apply(Ident(sink), [trailing]) before lowering
    otherwise                -> RuleDoesNotConclude
```

Wrapping at the surface level rather than in Core keeps one path for how a sink call is lowered — it
becomes an ordinary application of the prelude's eta-lambda, like any call the author wrote. The
synthesized node is lowered having been type-checked by that `isSubtype` rather than by a second pass
of the checker, which satisfies lowering's "assumes checked input" precondition for the one node we
built ourselves.

`RuleDoesNotConclude` specializes its message on the two shapes that mean "forgot", both of which are
visible in the trailing type:

```text
type is T?     "this rule concludes only when the condition holds"   # a missing else
type is Unit   "this rule never produces a decision"
otherwise      "this rule produces Bool; decide declares Decision"
```
