---
task: make-capabilities-callable-from-rule-expressions
type: design-prd
repo: txsmith/klein
branch: host-interop
sha: 0d52a61b16b5eef19fb68463030a4756f9c7abb3
---

# Make Capabilities Callable From Rule Expressions

### Problem to Solve

A rule that type-checks against a contract release still cannot run. `klein check --contract lending.contract lending-rule.klein --release 2` says the rule is valid — but `klein run` with the same arguments prints "capabilities are not callable at runtime yet" and exits 1. The entire contract story stops at the verdict: an author can learn their rule *would* work, but nobody can ever see it produce a result.

The gap is one-sided. Per the research doc, both halves already exist and are tested: the checker projects a release into the rule's environment, and the machine can suspend on a host call and resume with the host's answer. What's missing is the middle — nothing translates a checked capability name into a runnable program, and nothing drives the suspend/resume loop against the host's registered handlers. The host-side handler registry (`implement { immediate("creditScore") { … } }`) is likewise complete but has never dispatched a single call.

### What does business success look like, and how can we measure it?

Success is demonstrable end-to-end capability execution, enforced by tests:

1. **The lending walkthrough runs, not just checks.** A rule calling `creditScore(customer)` against release 2 of `examples/lending.contract` executes and prints a real result — both through the Kotlin example host (handlers compiled in) and through the CLI's interactive mode (answers typed at a prompt).
2. **An end-to-end execution suite guards the new execution path in CI.** Rules that call capabilities, rules that only construct contract-declared values, and the bad-handler diagnostic are all covered — the way `LendingExampleTest.kt` guards the checking half today. Suite green in CI is the ongoing measure that the feature works and stays working.
3. **The check/run split survives.** Checking a rule still requires zero handlers anywhere; the `EnvironmentContract` / `Environment` boundary is unchanged.

### Proposed Solution

- **Lowering learns the release's vocabulary.** The three kinds of name a release puts in scope — exposed-type constructors, capability functions, capability values — become resolvable in a rule, ending the "type-checks but can't lower" gap.
- **The library drives the suspend/resume loop.** A public API takes a compiled rule plus a host `Environment`, runs the machine, answers each suspension from the registered handler, and returns the final value — the piece a real host embeds.
- **Two hosts demonstrate it.** A small Kotlin example host with handlers compiled in (exercising and honing the public embedding API), and an interactive mode in the `klein` CLI that prompts the user to answer each host call at the terminal.
- **Compilation produces a pin map alongside the IR.** The compiled rule stays revision-free; the pin map records which `(name, revision)` set it resolved against — what edition storage and reconciliation consume downstream.

### Alternative Solutions Considered

- **A bespoke stubs file for the CLI** (capability answers written in Klein, e.g. `creditScore = |c -> 700|`) — ruled out. It would mean building file-format, parsing, and checking machinery that no real host would ever use. A Kotlin example host exercises the actual public API instead, and doubles as living documentation of what embedding Klein looks like.

### Solution Details

#### The Kotlin example host: scripted execution through the real embedding API

One small Kotlin main class with handlers compiled in, taking the contract and rule files as runtime arguments:

- It does exactly what a production host would: `Klein.checkContract(...)`, `contract.implement { immediate("creditScore") { ... } }`, compile the rule against a release, drive the loop, print the result.
- It is the scripted/repeatable runner for demos and the natural fixture for the end-to-end test suite.
- Its second job is API feedback: any awkwardness in the embedding surface shows up here first, while the API is still cheap to change.

#### The CLI runs capability rules interactively: the user is the host

`klein run --contract lending.contract rule.klein --release 2` no longer refuses a capability-calling rule. When the machine suspends, the CLI prints the call and prompts for the answer:

```
creditScore(Customer(1, "gold")) = ?
> 700
rule = true
```

- The answer is parsed as a Klein expression and checked against the capability's declared return type before resuming — a wrong-shape answer is re-prompted with the expected type, not fed into the machine.
- This serves the rule author poking at a rule in a terminal — a different user than the integrator the Kotlin host serves.
- When there is no terminal to prompt (piped input, CI), a suspension is an error naming the capability, so scripts fail loudly instead of hanging.

#### Capability functions are first-class values, via eta-expansion

The checker already types a capability function as an ordinary value of arrow type — so `f = creditScore`, passing `creditScore` to a higher-order function, or storing it in a record all type-check today. Execution honors that promise rather than revoking it:

- A capability name in call position (`creditScore(customer)`) compiles to a direct host call.
- A bare capability reference eta-expands to a lambda (`|c -> creditScore(c)|`) — a real value that performs the host call when applied, wherever it traveled in the meantime.

No new diagnostic, no "some names aren't values" carve-out. The host call happens at application either way, so memoization and interactive prompting behave identically for direct and indirect calls.

#### Constructors of exposed types run without any handler

A rule that only builds contract-declared values — `Customer(1, "gold")` — is pure: constructors compile to ordinary data construction, exactly like a rule's own type definitions, with no handler, no suspension, no prompt. Per the ticket, this slice is independently valuable: it makes `run --contract` work for constructor-only rules before the host-call path lands.

#### A misbehaving handler gets a named diagnostic, not a machine crash

When a handler's answer doesn't match the capability's declared return type, the run fails at the resume boundary with a diagnostic that names the culprit — `handler creditScore returned Str, declared Num` — instead of an invariant violation deep in the machine, where the blamed span would be whatever operation happened to consume the bad value. Interactive mode applies the same check to typed-in answers, but re-prompts instead of failing.

#### The compiled rule is revision-free; a pin map alongside it carries the resolution

A rule's text never mentions revisions, and neither does its compiled form — `HostCall` carries only the plain capability name, the same way the checker's `RuleType` never shows `/N`. The resolution lives in one place: a **pin map** produced alongside the IR at compile time, mapping each name the rule used to the revision its release pointed at — `{creditScore → /2, customer → /2, Customer → /2}`.

- Compiling the same rule against two releases yields the same shape of IR with different pin maps — exactly what an edition stores and what reconciliation later compares.
- At run time, dispatch reads the pin map: the driver looks up `creditScore` at the pinned revision in the host `Environment`, so a host serving two revisions side by side answers each rule with the revision its release promised.
- Because the pin map is flat, the driver validates it against the registered handlers **before starting the run** — a pre-flight pin check, where a missing or mis-revisioned handler is an error naming the capability, not a mid-run surprise, and no walk of the Core tree is ever needed.

#### Capability values are a snapshot; call caching is the host's business

The two kinds part ways here:

- **A capability value never changes within a run.** It is asked once, at scope entry; every later reference reuses the answer. A rule sees one consistent snapshot of the world.
- **Capability functions are asked every time.** Some capabilities must be askable twice — `log`, anything with an effect — so the library imposes no memoization, in any form. A host that knows a capability is pure caches inside its own handler.

The example host and the interactive CLI still answer each distinct question once per run — because their handlers cache, not because the library guarantees it: the example host memoizes its lookups, and interactive mode's prompting handler remembers its own answers. Declaring purity — at registration, or better, in the contract where rule authors can see it — is the eventual fix, and is what the effect log's entry granularity will have to be decided against.

### Out of Scope

- **A CLI stubs-file format** — see Alternative Solutions Considered.
- **A result sink** — a release nominating one capability as where the rule's answer goes. Extracted to its own roadmap item ([host-integration-roadmap.md](../../host-integration-roadmap.md) §Result sink): it is the one slice that changes the contract language, so it goes spec-first, on its own. The design draft stays in [tdd.md](./tdd.md).
- **Edition serialization, the effect log, reconciliation, drain** — downstream work that consumes the pin map this feature produces.
- **The typed derivation API and wire format** — the host boundary stays dynamically typed (`List<Value> -> Value`) for now.
- **Trace modes and fuel.**
