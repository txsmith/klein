# Klein Language

**A tiny, safe expression language for embedding customizable business rules.**

Klein is designed to let tech-savvy business users write rules, validations, and simple programs that your application executes. Programs are pure expressions with suspendable effects—they can't access the network or modify state directly, only use what the host application provides.

## Documentation

### Core Documentation

- **[grammar.md](./docs/grammar.md)** - Complete formal grammar for Klein expressions and types, including indentation rules, operator precedence, and parser method mappings
- **[reference.md](./docs/reference.md)** - Complete language reference with syntax, examples, and usage patterns for all Klein features
- **[type-system.md](./docs/type-system.md)** - Type system design: structural vs nominal typing, subtyping, records, and the tilde operator (inference sections being rewritten for Operation Bidi)
- **[spec/bidirectional-checking.md](./docs/spec/bidirectional-checking.md)** - The current type-checking model (the Operation Bidi surface spec)
- **[spec/pattern-matching.md](./docs/spec/pattern-matching.md)** - Pattern matching and destructuring bindings: pattern forms, match typing, exhaustiveness, refutability
- **[spec/host-integration.md](./docs/spec/host-integration.md)** - How rules and a host evolve independently: environments, capabilities, revisions, releases, editions, pins, reconciliation, drain
- **[spec/contracts.md](./docs/spec/contracts.md)** - The v1 contract language: declarations without definitions, revisions, releases, the two checking modes; sections marked implemented vs target
- **[spec/effect-log.md](./docs/spec/effect-log.md)** - The effect log and the unified `run`: the record's shape, replay, divergence, outcomes, `Parked`, the two codecs

Specs are **living contracts** — the current rules, updated in place as the language evolves, and what the test suites are written against. ADRs (below) are the immutable decision history.
- **[calling-conventions.md](./docs/calling-conventions.md)** - Function definitions, positional arguments, records, tuples, extension methods, and the tilde operator

### Implementation Guides

- **[implementation-status.md](./docs/implementation-status.md)** - Current implementation status across parser, type system, and execution
- **[performance-debt.md](./docs/performance-debt.md)** - Deliberate performance corners in the execution pipeline, each with its fix
- **[roadmap.md](./docs/roadmap.md)** - The global roadmap: syntax additions, advanced features, the migration toolkit, editor + tooling, the evaluation spec
- **[host-integration-roadmap.md](./docs/host-integration-roadmap.md)** - What is left to make the host-integration spec real, and what depends on what

### Design Decisions

See [docs/decisions/](./docs/decisions/) for the full set of ADRs. ADRs are immutable history; superseded ones carry a forward pointer to what replaced them.

**Current type-system direction:**

- **[2026-06-24-adopt-operation-bidi.md](./docs/decisions/2026-06-24-adopt-operation-bidi.md)** - **Current.** Local bidirectional checking — annotate signatures, infer interiors; drop global inference, keep subtyping.
- **[2026-06-23-polarity-wall-and-type-system-direction.md](./docs/decisions/2026-06-23-polarity-wall-and-type-system-direction.md)** - Why SimpleSub was abandoned: the polarity wall and the three ways out.

**Execution decisions (Core IR + CESK machine):**

- **[persist-the-log-replay-the-run.md](./docs/decisions/2026-07-31-persist-the-log-replay-the-run.md)** - **The persistence model.** Machine state is never serialized; the effect log is truth, replay rebuilds everything else
- **[own-machine-not-a-rented-vm.md](./docs/decisions/2026-07-20-own-machine-not-a-rented-vm.md)** - Why Klein runs its own machine instead of compiling to JVM/WASM/Lua (re-centered on embedding, walkable hot tier, fuel)
- **[source-is-truth-ir-is-a-cache.md](./docs/decisions/2026-07-20-source-is-truth-ir-is-a-cache.md)** - Stored IR integrity: version stamp + checksum, re-derive instead of migrate
- **[no-load-time-verifier.md](./docs/decisions/2026-07-20-no-load-time-verifier.md)** - Why per-op checks are just unboxing and a verifier was rejected

**Host integration decisions:**

- **[2026-08-26-replay-is-ordinal-migration-is-host-policy.md](./docs/decisions/2026-08-26-replay-is-ordinal-migration-is-host-policy.md)** - **Current.** The log stays an ordered list and replay matches it by position; no key scheme makes migration automatic, so migration is a host-written function against a Klein toolkit. Pins host-call order as language semantics.
- **[2026-08-24-capabilities-execute-through-the-suspension-path.md](./docs/decisions/2026-08-24-capabilities-execute-through-the-suspension-path.md)** - **Current.** Execution wiring: every capability interaction is a suspension, the compiled program is revision-free with pins at the boundary, the library never caches, the run is guarded at both ends.
- **[2026-08-08-rule-vocabulary-through-linear-releases.md](./docs/decisions/2026-08-08-rule-vocabulary-through-linear-releases.md)** - **Current.** Numbered releases decide what rules can see; editing one carries rules along, appending one waits for a person. Supersedes the tag half of the ADR below.
- **[2026-08-06-capability-evolution-through-revisions-and-tags.md](./docs/decisions/2026-08-06-capability-evolution-through-revisions-and-tags.md)** - **Current apart from tags.** Permanent `/N` revisions, invariant type definitions, recompilation as the compatibility verdict, optimistic removal — with the full rejected-alternatives list.

**Foundational language decisions (still current):**

- **[records-as-interfaces.md](./docs/decisions/2026-01-09-records-as-interfaces.md)** - Records with function fields as structural interfaces
- **[no-anonymous-unions.md](./docs/decisions/2026-01-09-no-anonymous-unions.md)** - Why unions are nominal sums, not anonymous `A | B`
- **[optional-types-null-safety.md](./docs/decisions/2026-01-14-optional-types-null-safety.md)** - `T?` and null safety
- **[type-definition-syntax.md](./docs/decisions/2026-01-14-type-definition-syntax.md)** - The `type` keyword, constructors, sum types
- **[positional-function-syntax.md](./docs/decisions/2026-01-09-positional-function-syntax.md)** - Positional arguments instead of record-based calling
- **[fail-fast-error-handling.md](./docs/decisions/2026-01-09-fail-fast-error-handling.md)** - Fail-fast by default with opt-in recovery via `.recover`
- **[modules-vs-records.md](./docs/decisions/2026-01-09-modules-vs-records.md)** - Module system design

**Superseded by Operation Bidi (kept as history):** `simplesub-type-inference`, `lub-glb-type-simplification`, `rigid-type-variables-in-annotations`, `constructor-type-options`.

### Other Resources

- **[README.md](./README.md)** - Project overview and examples

## Running the CLI

### Build the native binary

```bash
./gradlew :klein-lib:linkDebugExecutableMacosArm64
```

This automatically creates a `./klein` symlink to the binary for convenience.

The actual binary is at: `klein-lib/build/bin/macosArm64/debugExecutable/klein-lib.kexe`

For other platforms:
- Linux: `linkDebugExecutableLinuxX64`
- macOS Intel: `linkDebugExecutableMacosX64`

### Tokenize (Lex)

```bash
# From a file
./klein tokens example.klein

# From stdin
echo "x = 1 + 2" | ./klein tokens --stdin

# Short form
./klein t example.klein

# Raw output (just tokens, no formatting)
./klein tokens --raw example.klein
```

### Parse

```bash
# From a file
./klein parse example.klein

# From stdin
echo "f = |x -> x + 1|" | ./klein parse --stdin

# Short form
./klein p example.klein

# Raw output (AST only)
./klein parse --raw example.klein
```

### Check Types

The primary command under Operation Bidi: run the `klein.check` bidirectional checker. Prints the type of
each top-level binding (and the trailing expression), then a pass/fail verdict. Exits non-zero on any
type error, so it works as a gate in scripts.

```bash
# From a file
./klein check example.klein

# Short form
./klein c example.klein

# From stdin
echo "x = 1 + 2" | ./klein check --stdin

# Machine-readable errors (Error: <msg> at <span>)
./klein check --raw example.klein
```

`check` has no IR/format flags — the Operation Bidi type is a plain structural tree with nothing to dump.

### Check or run against a capability contract

`--contract <file>` on `check` and `run` checks a rule against a [capability contract](./docs/spec/contracts.md)
instead of the empty environment. It composes with the plain form: give `check` a contract and
nothing else to check the contract alone.

```bash
# Contract alone: prints its declarations and releases, exits non-zero on error
./klein check --contract examples/lending.contract

# A rule against one release of that contract
./klein check --contract examples/lending.contract examples/lending-rule.klein --release 2

# --release may be omitted when the contract has exactly one release
./klein run --contract examples/lending.contract examples/lending-rule.klein --release 2
```

`run` with a contract is interactive: the CLI is the host, and you answer its capability calls.
Each suspension prints the call — `creditScore(Customer(1, "Acme", "gold")) = ?` — and reads one
Klein expression, compiled against the capability's declared answer type (a fun's result type; a
value's whole type). A wrong-typed answer re-prompts with the checker's message; an answer may use
the release's types (`Customer(1, "Acme", "gold")` works) but not its capabilities, so answering
never triggers more prompts. A distinct question is asked once. Prompting needs a terminal: with
piped input, or with `--stdin` consumed by the rule source, a suspension is an error naming the
capability, so scripts fail loudly instead of hanging.

### Run

Execute a program on the Core machine (parse → check → lower → run) and print the final value.
Without a contract there are no capabilities to call, so programs must be pure; with `--contract`,
capability calls are answered interactively — see above.

```bash
# From a file
./klein run example.klein

# Short form
./klein r example.klein

# From stdin
echo "1 + 2 * 3" | ./klein run --stdin
```

### Dump the Core IR

Print the lowered Core IR of a program — slot-addressed `name[depth;slot]` refs, hoisted-first
scopes, desugared `match`es.

```bash
./klein core example.klein
echo "if x > 1 then x else 0" | ./klein core --stdin
```

## Project Structure

```
klein-lang/
├── klein-lib/
│   ├── src/
│   │   ├── commonMain/kotlin/klein/
│   │   │   ├── SourceSpan.kt     # Source location tracking (cross-cutting; stays at root)
│   │   │   ├── Klein.kt          # Library entry: pipeline stages (tokenize → parse → check → lower → execute)
│   │   │   ├── Checked.kt        # Uniform stage result: output plus diagnostics; compose stages with andThen
│   │   │   ├── Numbering.kt      # RevisionNumber and ReleaseNumber value classes
│   │   │   ├── surface/          # Surface syntax: what the parser produces, the checker consumes
│   │   │   │   ├── Lexer.kt        # Tokenization
│   │   │   │   ├── Parser.kt       # Parsing
│   │   │   │   ├── Ast.kt          # Surface AST definitions
│   │   │   │   ├── Token.kt        # Token types
│   │   │   │   └── PrettyPrint.kt  # AST pretty-printing
│   │   │   ├── core/             # The compile-time half: Core IR + lowering
│   │   │   │   ├── Core.kt         # IR nodes (Control, CoreExpr, Match arms, constants)
│   │   │   │   ├── Lowering.kt     # Surface → Core: slot resolution, hoisting, desugaring
│   │   │   │   ├── Invariant.kt    # invariant() + InvariantViolation (malformed-IR errors)
│   │   │   │   └── PrettyPrint.kt  # Core IR printer (backs the `core` CLI command)
│   │   │   ├── interp/           # The run-time half: the CESK machine over Core
│   │   │   │   ├── Machine.kt      # Two-stack machine + Execution (Done | AwaitingHost), one-shot resume/clone
│   │   │   │   ├── Store.kt        # The store: write-once cells behind integer addresses
│   │   │   │   ├── Value.kt        # Runtime values (VStruct for records and data, VClos closures)
│   │   │   │   └── KleinRuntimeError.kt
│   │   │   ├── check/            # The Operation Bidi bidirectional checker
│   │   │   │   ├── Checker.kt              # synth / check driver (checkProgram facade)
│   │   │   │   ├── Type.kt                 # The type tree (skolems, foralls, revision witness) + printer
│   │   │   │   ├── TypeError.kt            # Typed error hierarchy
│   │   │   │   ├── Subtyping.kt            # Ground subtyping, lub/glb
│   │   │   │   ├── Constraint.kt           # Instantiation constraint solving
│   │   │   │   ├── TypeEnv.kt              # Environment / scopes (ContractEnv vs RuleEnv)
│   │   │   │   ├── ScopeGraph.kt           # Top-level dependency SCCs
│   │   │   │   ├── TypeDefPreprocessor.kt  # Variance inference, nominal setup
│   │   │   │   ├── Variance.kt             # Variance lattice
│   │   │   │   ├── ValueTypes.kt           # infer(Value): the runtime answer's type, for the resume boundary
│   │   │   │   └── contract/     # Contracts, revisions, releases
│   │   │   │       ├── ContractChecker.kt      # Contract checking, release folding, self-containment
│   │   │   │       ├── EnvironmentContract.kt  # check / compileRule / compileValue per release
│   │   │   │       ├── ResolvedRelease.kt      # A release materialised: types + revisions, bindingFor
│   │   │   │       ├── UsedCapabilities.kt     # The used-capability pass (expression, type, pattern positions)
│   │   │   │       ├── Edition.kt              # Compiled rule: revision-free Core + pin map
│   │   │   │       └── Projection.kt           # strip(): the one ContractType -> RuleType crossing
│   │   │   └── host/             # The embedding surface a host calls
│   │   │       ├── Environment.kt  # implement { }, Registry, Handler (immediate and deferred), Capability
│   │   │       └── Runner.kt       # Environment.run: pre-flight pin check, suspend/resume loop, answer check
│   │   ├── commonTest/kotlin/klein/
│   │   │   ├── lexer/
│   │   │   ├── parser/
│   │   │   ├── check/
│   │   │   │   └── contract/     # Release, self-containment, compileRule/value suites + lending walkthrough
│   │   │   ├── core/             # Lowering golden tests + IR printer tests
│   │   │   ├── interp/           # Machine unit tests + per-feature eval suites (full pipeline)
│   │   │   └── host/             # Environment + runner suites (RunAgainstReleaseTest)
│   │   └── nativeMain/kotlin/klein/
│   │       └── Main.kt           # CLI entry point (incl. interactive contract run)
│   └── build.gradle.kts
├── klein-example-host/           # JVM module outside the library: what embedding Klein looks like
├── klein-bench/                  # kotlinx-benchmark corpus over the pipeline stages
├── docs/                         # Specs, ADRs, ideas, roadmaps, guides
├── examples/                     # Sample .klein programs + lending.contract
└── README.md                     # Project overview
```

## Running Tests

```bash
# All tests
./gradlew :klein-lib:allTests

# JVM only (faster for development)
./gradlew :klein-lib:jvmTest

# Specific test class
./gradlew :klein-lib:jvmTest --tests "klein.parser.LambdaTest"

# Lexer tests only
./gradlew :klein-lib:jvmTest --tests "klein.lexer.*"

# Parser tests only
./gradlew :klein-lib:jvmTest --tests "klein.parser.*"
```

## Running Benchmarks

The benchmark corpus lives in `klein-bench` (kotlinx-benchmark / JMH): a suite of named
sample Klein programs (`Programs.kt`), each measured at every pipeline stage
(`parse`, `typecheck`, `eval`, `endToEnd`). Compare per (program, stage) cell before and
after a change to see whether it actually made things faster.

```bash
# Full statistical run (use this for real before/after comparisons; takes minutes)
./gradlew :klein-bench:benchmark

# Quick smoke pass (sanity only, high variance)
./gradlew :klein-bench:smokeBenchmark
```

JSON reports land in `klein-bench/build/reports/benchmarks/`. To track a new program, add
an entry to `Programs.suite` and its name to the `@Param` list in `ProgramSuiteBenchmark`.

## Implementation Status

See **[implementation-status.md](./docs/implementation-status.md)** for current status across parser, type system, and interpreter.

<!-- BACKLOG.MD GUIDELINES START -->
<!-- backlog.md-instructions-version: 1.51.0 -->
<CRITICAL_INSTRUCTION>

## Backlog.md Workflow

This project uses Backlog.md for task and project management.

**At the beginning of each conversation in this project, run `backlog instructions overview` before answering or taking action. Re-read it only if you have not read it yet in the current conversation.**

Use the overview to decide whether to search, read, create, or update Backlog tasks.

Before task lifecycle actions, read the matching detailed guide:
- `backlog instructions task-creation` before creating or splitting tasks
- `backlog instructions task-execution` before planning, changing status or assignee, adding a plan or implementation notes, or implementing task work
- `backlog instructions task-finalization` before checking acceptance criteria, writing final summaries, or moving tasks to terminal statuses

Use `backlog <command> --help` before running unfamiliar commands. Help shows options, fields, and examples.

Do not edit Backlog task, draft, document, decision, or milestone markdown files directly. Use the `backlog` CLI so metadata, relationships, and history stay consistent.

</CRITICAL_INSTRUCTION>
<!-- BACKLOG.MD GUIDELINES END -->

## Orientation and where things go

Read [MAP.md](./MAP.md) at session start; move its single `← you are here` marker at
session end. The map holds areas and open questions, never work items; keep it under
20 lines.

Each kind of writing has one home:

- **ADRs** → `docs/decisions/` — hand-written prose with supersession discipline.
- **Design essays** → `docs/ideas/` — settled positions, not drafts or captured one-liners.
- **Specs** → `docs/spec/` — living contracts, updated in place.
- **Bounded, PR-sized work** → `backlog task create` (prefer the `backlog` CLI over
  hand-editing files under `backlog/`).

`backlog/decisions/` and `backlog/docs/` stay empty **by design**: ADRs and docs live under
`docs/`, which predates Backlog.md here and is stronger. Do not populate them.

Labels are the seam between the map and the backlog — a map leaf names a label, and
`backlog task list -l <label> --plain` returns everything hanging off that node. The label
set is fixed: `language`, `parser`, `checker`, `host-boundary`, `contracts`, `perf`,
`execution`, `lowering`, `tooling`. Do not invent labels outside it without asking.
