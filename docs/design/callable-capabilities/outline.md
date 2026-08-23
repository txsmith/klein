---
task: make-capabilities-callable-from-rule-expressions
type: structure-outline
repo: txsmith/klein
branch: host-interop
sha: 0d52a61b16b5eef19fb68463030a4756f9c7abb3
---

# Make Capabilities Callable From Rule Expressions

Close the gap between contract checking and execution: lowering learns a release's vocabulary and
produces an `Edition` (Core IR + pins), and a new runner in `klein.host` drives the machine's
suspend/resume loop against registered handlers. Two hosts prove it — a Kotlin example host in
its own Gradle module (which keeps the public embedding surface honest by the build) and the CLI as
an interactive host that prompts the user for each answer.

## Desired End State

- `klein run --contract examples/lending.contract examples/lending-rule.klein --release 2` executes
  and prints a result, prompting for each host call.
- `EnvironmentContract.compileRule(source, release)` returns an `Edition` — revision-free Core plus
  the `(name, revision)` pin map the rule resolved against. Two releases, same rule, different pins.
- A rule that only constructs contract-declared values runs with no handler registered anywhere.
- `Environment.run(edition) { … }` is the public embedding API: handlers answer inline, blocking on
  the host's own concurrency if they need to.
- A handler answering with the wrong shape fails at the resume boundary with `handler creditScore
  returned Str, declared Num`, not deep in the machine; an edition this environment cannot serve
  fails before the machine starts.
- Checking a rule still needs no handlers anywhere: the `EnvironmentContract` / `Environment` split
  is unchanged.

## Implementation Overview

- [ ] Phase 1: Constructor-only rules run — `compileRule`, the edition prelude, and pins
  - [x] 1a: Mechanical renames — `RevisionNumber`, `FlattenedReleaseBlock`
  - [x] 1b: `ResolvedRelease` — the release materialised in two halves
  - [x] 1c: The used-capability pass
  - [ ] 1d: Prelude lowering — `PreludeBinding` and `lowerWithPrelude`
  - [ ] 1e: `compileRule` and the `Edition`
  - [ ] 1f: The CLI compiles and executes
- [ ] Phase 2: A capability-calling rule runs — the pin check, the runner, and an example host
- [ ] Phase 3: The CLI becomes an interactive host
- [ ] Phase 4: Handler answers are checked at the resume boundary

The result sink (formerly Phase 5) is extracted to its own roadmap item — see
"Not in this outline" below.

---

## Phase 1: Constructor-only rules run — `compileRule`, the edition prelude, and pins

The ticket's suggested first slice, widened by one step. Lowering gains an **edition prelude** — an
outer Core scope holding every contract name a rule uses, tailored to one edition — so all three
kinds — constructors, capability functions, capability values — resolve and emit IR. Constructors are pure, so a rule that only builds values runs today;
a rule that calls a capability now lowers successfully and fails honestly at `Klein.execute` with
`unhandled host call 'creditScore'` instead of an invariant violation. The `InvariantViolation` catch
and its "not callable at runtime yet" message are deleted in this phase.

**Settled here**, closing the ticket's open decision: a capability value is a nullary `HostCall`
bound at scope entry, not a reference written into the store before the machine starts. The existing
suspend/resume path serves it with no new machinery, `Core` / `Machine` / `Execution` stay untouched,
and a `Deferred` value handler works for free. Store pre-filling is recorded as a performance-debt
entry with its fix, not built.

Six sub-items, each landable on its own with the suite green: **1a → (1b ∥ 1c ∥ 1d) → 1e → 1f**.
The renames go first so nothing built after them churns; 1b and 1c are contract-side, 1d is
core-side, and the three share no code, so they can proceed in any order or in parallel; 1e is the
assembly point that consumes all three; 1f is the CLI payoff.

### 1a: Mechanical renames

- **`klein-lib/src/commonMain/kotlin/klein/Revision.kt`**: rename `Revision` to `RevisionNumber`,
  pairing it with the `ReleaseNumber` it already shares a file with — 182 occurrences across 23
  source files and 5 docs, including the phantom bound (`Type<out R : RevisionNumber?>`). Purely
  mechanical, and cheapest now, before this feature's code exists. The file itself is then named
  after one of the two types it holds; `Numbering.kt` or similar is worth taking at the same time.
- **`klein-lib/src/commonMain/kotlin/klein/check/contract/ContractChecker.kt`**: rename `Release`
  (:36) to `FlattenedReleaseBlock`, and `resolveReleases` (:79) to `flattenReleaseBlocks` — 8
  internal references plus the doc-comment mention at TypeEnv.kt:27. It is not the AST (that is
  `ReleaseBlock`, holding the delta entries as written) and it is not resolved either: it is the
  blocks folded into an absolute `name -> revision` surface, still pointers, nothing materialised.
  Keeping "resolve" for the stage that materialises makes the three readable in sequence —
  `ReleaseBlock` parsed, `FlattenedReleaseBlock` folded, `ResolvedRelease` materialised.

**Done when:** the suite is green with zero behaviour change; no new code.

### 1b: `ResolvedRelease` — the release materialised in two halves

- **`klein-lib/src/commonMain/kotlin/klein/check/contract/ResolvedRelease.kt`** (new, internal): the
  release materialised, in the two halves `strip()` splits it into — the file named after the type
  it declares. `environmentFor`
  (Projection.kt:69-76) moves here and gains a second output rather than being walked twice — its
  existing loop over `FlattenedReleaseBlock.surface` plus `constructorsOf` already performs every
  lookup both halves need.

```kotlin
internal fun ContractEnv.resolveRelease(release: FlattenedReleaseBlock): ResolvedRelease

internal class ResolvedRelease(
    val types: RuleEnv,                          // revision-free: what the checker checks against
    val revisions: Map<String, RevisionNumber>,  // every name the release exposes, incl. constructors
) {
    fun bindingFor(name: String): PreludeBinding?   // null for a name that lowering erases
}
```

  `types` is exactly what `environmentFor` builds today, unchanged. `revisions` is the one thing it
  cannot answer: `strip()` nulls every revision on the way in, and pins *are* revisions, so they can
  only come off the other side of the walk.

  Nothing else needs storing, because `types` already holds it — `ConstructorInfo.fields.keys` is the
  field list in order, a `TFun`'s `params.size` is the arity, and `lookupConstructor` separates a
  constructor from a capability function. `bindingFor` reads it back for one name at a time, which is
  all `compileRule` ever asks for; building a binding per exposed name here would mean 200 of them
  for a rule that uses three.

  `bindingFor` returns null exactly where lowering erases the name. A rule writing
  `fun process(s: Shape) = s.area` never touches a constructor, but its `FieldGet` compiles only
  because `Shape/2`'s interface has `area` — so `Shape` is in `revisions` and gets pinned, while
  binding nothing. Constructors are the converse case, and the reason `revisions` is needed at all:
  a release entry never names `Circle` or `Square`, so filtering against `surface` would miss them.

  Emitting both halves from one loop is the point: a name the checker can see but the lowerer cannot
  is the `InvariantViolation` this ticket exists to remove, and afterwards it cannot be written.
  `Projection.kt` keeps `strip`, `stripRecord` and `expose`, so the one signature that changes the
  phantom parameter stays put and `ProjectionTest.kt` is unaffected.

  Two details the prelude depends on. Constructor field names and their **order** come from
  `ConstructorInfo.fields`, which `bindConstructors` already reads as declaration order
  (TypeDefPreprocessor.kt:229-234) — reading it the same way is what makes the checker's `TFun` arity
  and the lowered `Lambda` arity agree by construction. And `PreludeBinding` is declared in
  `klein.core`, not here, so lowering never imports `klein.check.contract` (1b therefore consumes
  1d's one shared type; landing 1b first just means declaring `PreludeBinding` ahead of its lowering).

- **`klein-lib/src/commonMain/kotlin/klein/check/contract/EnvironmentContract.kt`**: widen the memo
  from `RuleEnv` to `ResolvedRelease`; `check` reads `.types` and is otherwise unchanged.

```kotlin
// the memo widens from RuleEnv to both sides; `check` reads .types, `compileRule` (1e) reads both
private val resolved = mutableMapOf<ReleaseNumber, ResolvedRelease>()
private fun resolved(release: ReleaseNumber): ResolvedRelease          // was: ruleEnvironment
```

  `ruleEnvironment`'s memo already exists because "a host recompiles many rules against the same
  one" — widening it to carry the `revisions` half as well means a second rule against the same
  release pays for neither walk, and `UnknownRelease` still throws from the one place it does today.

- **`klein-lib/src/commonTest/kotlin/klein/check/contract/ResolvedReleaseTest.kt`** (new):
  `bindingFor` on each kind — a constructor yields `Ctor` with field names in declaration order, a
  capability function yields `Function` with the `TFun`'s arity, a capability value yields `Value`,
  a type-only name yields null; a sum type's constructors appear in `revisions` though no release
  entry names them. Plus one sweep over the lending contract asserting `types` and `revisions`
  agree — every name `types` binds or registers has a revision — which is the drift the merged walk
  exists to prevent.

**Done when:** existing contract/rule-checking tests are green untouched, and `ResolvedReleaseTest`
passes. Nothing consumes `revisions` yet.

### 1c: The used-capability pass

- **`klein-lib/src/commonMain/kotlin/klein/check/contract/UsedCapabilities.kt`** (new, internal): one AST
  walk collecting names the program does not bind itself, in all **three** positions — expression
  identifiers, type references, and pattern mentions. Type positions are not optional: annotations on
  `fun` params and returns, on `val`s and lambda params, and inside the rule's own type definitions
  are all places a contract type is depended on without a constructor ever appearing. Pattern
  positions are not optional either: `match shape … Circle c -> …` and `Circle c = x` resolve
  `Circle` against the release without it appearing in any expression or annotation, and pins that
  miss it would tell reconciliation this rule is unaffected by a `Shape` revision — exactly wrong.

```kotlin
internal fun usedCapabilities(program: Program, exposed: Set<String>): Set<String>
```

  `exposed` is only a filter, so it takes the name set (`release.revisions.keys`) rather than the
  whole release. For value identifiers the filter is belt-and-braces — the rule already checked
  against `types`, so an unexposed name failed as `UnboundVariable` before `compileRule` got here —
  but type positions mention unbound names that are not contract names: the primitives in
  `TypeDefPreprocessor.PRIMITIVE_TYPE_NAMES`, and type parameters a rule's own definitions bind.
  Intersecting is cheaper and less brittle than enumerating what to exclude, and the pin map is a
  durable artifact worth being conservative about.

  The result drives both of 1e's outputs: `used.associateWith { release.revisions.getValue(it) }` is
  the edition's pins, `used.mapNotNull { release.bindingFor(it) }` the edition prelude.

- **`klein-lib/src/commonTest/kotlin/klein/check/contract/UsedCapabilitiesTest.kt`** (new), driving
  the function directly with a parsed program and a hand-written `exposed` set: a name used in call
  position; a bare value read; a name only in a type annotation (`fun` param, return, `val`, lambda
  param, a field of the rule's own type definition); a name the rule binds itself excluded; a
  primitive excluded; a type parameter of the rule's own definition excluded; a name not in
  `exposed` excluded; a program using nothing returning the empty set.

**Done when:** `UsedCapabilitiesTest` passes. Pure function, no integration; needs neither 1b nor 1d.

### 1d: Prelude lowering — `PreludeBinding` and `lowerWithPrelude`

- **`klein-lib/src/commonMain/kotlin/klein/core/Lowering.kt`**: a second entry that wraps the
  program's scope in a hoisted scope of contract binds. Every case in `lowerExpr` is untouched,
  including `Ident` and `SurfaceApply` — to the lowerer a contract name is a name already in scope.
  `lowerConstructor` is generalised to `(name, fieldNames, span)` so the prelude reuses it verbatim.

```kotlin
internal fun lower(program: Program): CoreExpr                                       // unchanged
internal fun lowerWithPrelude(program: Program, prelude: List<PreludeBinding>): CoreExpr // new

/** The three kinds a release binds at runtime, differing only in what their bind evaluates to.
 *  Declared here, in lowering's own vocabulary: it says nothing about revisions or contracts, so
 *  the IR stays revision-free and `klein.core` stays ignorant of `klein.check.contract`. */
internal sealed interface PreludeBinding {
    val name: String
    class Ctor(name, val fieldNames: List<String>) : PreludeBinding // Lambda over MakeData
    class Function(name, val arity: Int) : PreludeBinding           // Lambda over HostCall
    class Value(name) : PreludeBinding                              // bare nullary HostCall
}
```

  `lowerWithPrelude` sorts the prelude by name before laying out the scope, so slot layout is canonical
  — a fact about the rule and the release, not about the used-capability walk's traversal order.
  Golden IR dumps stay stable under refactors of the walk, and edition serialization later hashes a
  Core whose bytes cannot depend on iteration order. Normalizing in the consumer means no caller
  can construct a mis-ordered prelude even in principle.

  `Function` and `Value` are not the same case at arity 0: `parseFunParams` returns `emptyList()` on
  `RPAREN` (Parser.kt:522), so `fun tick(): Num` is a legal declaration, and it has to stay callable
  — binding it to the *result* of a host call at scope entry would fail the moment a rule writes
  `tick()`. `Ctor` differs from both by payload as well as body, since `MakeData` carries the field
  labels and not just a count.

  The emitted shape, with the rule's own scope nested inside so shadowing falls out of the chain:

```text
EnterScope                        # hoisted binds, evaluated on entry
  Customer     = fun/2 -> Customer{id: _0, tier: _1}
  creditScore  = fun/1 -> hostcall creditScore(_0[0;0])
  customer     = hostcall customer()               # nullary: asked once, here; later reads are Var
  EnterScope [the rule's own statements] …
```

- **`klein-lib/src/commonTest/kotlin/klein/core/EditionPreludeLoweringTest.kt`** (new): golden IR for
  the edition prelude, driving `lowerWithPrelude` directly with hand-built `PreludeBinding` lists —
  no contract machinery, so this sub-item stands alone. Cases: each of the three bind kinds; multi-field
  constructor parameter order matching declaration order; a nullary constructor binding a bare
  `MakeData`; a direct call resolving through the chain; `f = creditScore` (the capability twin of
  `constructorPassedAsArgument`); a rule shadowing a contract name resolving to its own slot; a
  name in the prelude the rule never mentions still binding (lowering does not filter — 1e's pass
  does); prelude slot layout identical
  however the bindings are handed in (the canonical-order guarantee). Plus the pair that pins the
  `Function`/`Value` split: `fun tick(): Num` binds `Lambda(0, HostCall("tick", []))` and a rule
  writing `tick()` applies it, while `customer: Customer` binds a bare `HostCall("customer", [])` and
  a rule writing `customer` reads it — three distinct shapes at arity 0, counting the nullary
  constructor above.
- **`docs/performance-debt.md`**: two new entries. The eta-lambda indirection on every capability
  call — a frame pushed and popped around a suspension — with its fix (resolve the callee first and
  match on the resolved binding rather than on syntax). And one suspension per capability *value* the
  rule uses, with its fix being the store pre-fill: the runner fills the store cell before
  `Machine.start` and no `HostCall` is emitted at all.
- **`docs/implementation-status.md`**: line 140's "lowering does not yet emit `HostCall`" is no
  longer true.

**Done when:** `EditionPreludeLoweringTest` passes against hand-built preludes and the existing
lowering suite is untouched. Needs neither 1b nor 1c.

### 1e: `compileRule` and the `Edition`

- **`klein-lib/src/commonMain/kotlin/klein/check/contract/Edition.kt`** (new): the artifact
  `host-integration.md` §Edition names — a compiled rule and its pins.

```kotlin
class Edition internal constructor(
    val core: CoreExpr,                     // revision-free: a HostCall carries a plain name
    val release: ReleaseNumber,
    val pins: Map<String, RevisionNumber>,  // every contract name the rule used, at its revision
)
```

  No hash of the release goes in. An in-place edit that keeps its revision is legal — the canonical
  example enforces it (`aCompatibleSignatureEditNeedsNoRevision`, LendingExampleTest.kt:129-133) — so
  a fingerprint mismatch under an unchanged revision would be expected behaviour, not a signal.
  Identity is `(name, revision)` and recompilation is the compatibility verdict (ADR 2026-08-06); a
  hash sits above neither. The per-pin signature hashes host-integration.md §Edition describes belong
  to reconciliation, at that granularity, when reconciliation exists.

  Nor does a second map of the capability pins. A pin alone does not say whether it needs an
  implementation — types and constructors do not — but `bindingFor` answers that from the
  `ResolvedRelease`, which the host reaches through the contract `implement` was called on.

- **`klein-lib/src/commonMain/kotlin/klein/check/contract/EnvironmentContract.kt`**: add
  `compileRule` beside `check`, throw-or-succeed like its neighbours. Both verbs share one private
  parse-and-check helper, so the CLI's current double-parse disappears.

```kotlin
fun check(ruleSource: String, release: ReleaseNumber): RuleType        // unchanged
fun compileRule(ruleSource: String, release: ReleaseNumber): Edition   // new
```

  `compileRule` is the assembly point — 1c's pass feeding 1b's halves into 1d's entry:
  `usedCapabilities` over the checked program, `used.associateWith { revisions.getValue(it) }` the
  pins, `used.mapNotNull { bindingFor(it) }` the prelude, `lowerWithPrelude`, then
  `Edition(core, release, pins)`.

- **`klein-lib/src/commonTest/kotlin/klein/core/CoreAssertions.kt`**: add
  `assertRuleLowersTo(contract, rule, release, expected)` — the contract-aware twin of
  `assertLowersTo`, expressible now that the pipeline exists end to end. One or two of 1d's shapes
  re-confirmed through it guard the assembly, not the lowering.
- **`klein-lib/src/commonTest/kotlin/klein/check/contract/CompileRuleTest.kt`** (new): the same rule
  compiled against release 1 and release 2 yields different pins; pins contain only names the rule
  used; a sum type's constructors (`type Shape = Circle | Square`) enter the prelude and are pinned
  even though no release entry names them; `fun process(s: Shape) = s.area` pins `Shape` at its
  revision and adds nothing to the prelude, and the same rule against a release pointing at `Shape/3`
  pins `/3`; a rule whose own type definition has a field of a contract type pins that type; a
  constructor-only edition executes to a value through `Klein.execute`; an unexposed name is still
  `UnboundVariable` rather than a prelude entry; `UnknownRelease` propagates.

**Done when:** `CompileRuleTest` passes; `check`'s behaviour and its tests are untouched.

### 1f: The CLI compiles and executes

- **`klein-lib/src/nativeMain/kotlin/klein/Main.kt`**: `runCmd` calls `contract.compileRule(...)`
  then `Klein.execute(edition.core)`. Delete the `InvariantViolation` catch and message (:183-193)
  and the second `tokenize`/`parse`; update `runCmd`'s doc comment. Rename the private
  `resolveRelease` (:214) to `parseReleaseNumber` — it reads the `--release` flag, defaulting to the
  contract's single release — leaving `resolveRelease` to mean the one thing it now means.
- **`examples/lending-value-rule.klein`** (new): a constructor-only rule, e.g.
  `Customer(1, "Acme", "gold").tier == "gold"` — the CLI's manual check for this phase.

**Done when:** the manual verification below passes.

### Validation — phase level

#### Automated Verification

- [ ] `./gradlew :klein-lib:jvmTest`
- [ ] `./gradlew :klein-lib:allTests`
- [ ] `./gradlew :klein-lib:linkDebugExecutableLinuxX64`

#### Manual Verification

- [ ] `./klein run --contract examples/lending.contract examples/lending-value-rule.klein --release 2`
      prints `true` with no handlers anywhere.
- [ ] `./klein run --contract examples/lending.contract examples/lending-rule.klein --release 2`
      reports `unhandled host call 'customer'` — lowering succeeded, the runner does not exist yet.
- [ ] `./klein check --contract examples/lending.contract examples/lending-rule.klein --release 2`
      is unchanged.

---

## Phase 2: A capability-calling rule runs — the pin check, the runner, and an example host

The heart of the feature. `klein.host` gains a runner that checks an edition's pins against the
registered implementations *before* the machine starts, then drives the suspend/resume loop to a
value. A new Gradle module outside `klein-lib` is the second half of the phase: `internal` is
invisible to it, so any gap in the public embedding surface stops the build.

One entry point, `run`, and handlers that answer inline. `Implementation.Deferred` stays undriven —
see "Not in this outline" at the end.

### File Changes

- **`klein-lib/src/commonMain/kotlin/klein/host/Environment.kt`**: per-run supply, and the contract
  reference the runner needs.

```kotlin
fun Registry.immediate(name: String, revision: RevisionNumber = RevisionNumber(1))  // no lambda: supplied per run

class Environment internal constructor(
    val capabilities: List<Capability>,
    internal val contract: EnvironmentContract,  // implement is an extension on it, so it has one
    …
)
```

  The lambda-less overload keeps `implement`'s completeness guarantee intact — the environment still
  names every declaration at boot — while marking an entry as "an implementation arrives with the
  run". `customer` is the case: per-request data an environment built once cannot hold.

  The contract reference is what lets `checkPins` classify a pin: `contract.resolved(edition.release)`
  gives the `ResolvedRelease`, and `bindingFor` says whether the name needs an implementation. Phase 4
  uses the same reference for the release's `RuleEnv`. `resolved(release)` is already `internal` —
  1b made it so, since `ResolvedReleaseTest` had no other way to reach a `ResolvedRelease`.

- **`klein-lib/src/commonMain/kotlin/klein/host/Runner.kt`** (new): the pin check, the loop, and the
  resume step, all extensions on `Environment` so the dependency arrow stays one-way.

```kotlin
fun Environment.run(edition: Edition, supply: Registry.() -> Unit = {}): Value
internal fun Environment.checkPins(edition: Edition, supplied: Registry)  // throws or returns

class UnservedPin(name: String, revision: RevisionNumber) : KleinError          // env cannot serve this edition
class MissingImplementation(name: String, declared: RevisionNumber) : KleinError // declared, never supplied
```

  `checkPins` walks `edition.pins`, classifies each through `bindingFor` on the release the edition
  names, and for the `Function` and `Value` ones requires an implementation: run-supplied →
  boot-registered → `MissingImplementation`. A pinned `(name, revision)` the environment does not
  declare at all is `UnservedPin` — an edition compiled earlier against a contract the host has since
  changed. It keeps nothing: once it passes, the loop's own lookups are guaranteed to succeed.

  What it buys is *when* the failure lands. A rule calling `creditScore` early and `sendSMS` late
  would otherwise run the whole way, perform the effects in between, and only then discover `sendSMS`
  has no handler; in a system whose persistence model is an effect log, that is the expensive kind of
  failure. It is to a run what `implement` is to boot, and the boot case set the principle when
  `Environment` was designed: requiring every declaration up front makes forgetting one "a boot
  error rather than a 3am surprise the first time a rule calls it".

  Pins being a flat map is what makes it possible: every capability the rule *could* reach is
  checkable without running it, and no walk of the Core tree is needed. It over-approximates — a
  capability behind an untaken `if` still needs an implementation — which is the same trade
  `implement` makes.
- **`settings.gradle.kts`**: `include(":klein-example-host")`.
- **`build.gradle.kts`** (root): add `kotlin("jvm") version "2.0.21" apply false` to the plugins
  block.
- **`klein-example-host/build.gradle.kts`** (new): `kotlin("jvm")` + `application`, depending on
  `project(":klein-lib")`.
- **`klein-example-host/src/main/kotlin/klein/example/LendingHost.kt`** (new): what a production host
  does, and nothing more — `Klein.checkContract`, `implement { … }`, `compileRule`, `run`, print.
  Contract path, rule path, and release are runtime arguments.
- **`klein-example-host/src/test/kotlin/klein/example/LendingHostTest.kt`** (new): a smoke test that
  it runs the lending example and produces the expected value.
- **`klein-lib/src/commonTest/kotlin/klein/host/RunAgainstReleaseTest.kt`** (new): the execution
  narrative, modelled on `LendingExampleTest.kt` — a rule calling a capability runs; a nullary
  capability is asked exactly once however many times the rule reads it; a rule calling through
  `f = creditScore` produces the same answer as the direct call; two editions of the same rule
  dispatch to the revision their release pinned; an unregistered pin is `UnservedPin` before the
  machine starts; a run that forgets a supplied capability is `MissingImplementation`; a rule that
  both constructs a contract type and calls a capability passes `checkPins`, the case that fails if
  a constructor pin is mistaken for a capability; an edition still runs against an environment built from a
  contract edited in place at the same revision, per `aCompatibleSignatureEditNeedsNoRevision`.
- **`klein-lib/src/commonTest/kotlin/klein/host/EnvironmentTest.kt`**: new cases for the lambda-less
  marker — it satisfies `implement`'s completeness check, and a run must then supply it.
- **`docs/host-integration-roadmap.md`**: §Execution wiring moves to Done (the roadmap already
  records the once-open `HostCall`/eta-expansion decisions as settled).

### Validation

#### Automated Verification

- [ ] `./gradlew :klein-lib:jvmTest`
- [ ] `./gradlew :klein-example-host:test`
- [ ] `./gradlew build` — the example host compiles against the public surface only
- [ ] `./gradlew :klein-example-host:run --args="examples/lending.contract examples/lending-rule.klein 2"`

#### Manual Verification

- [ ] Delete one `immediate(...)` registration from `LendingHost` and confirm the failure names the
      missing declaration at boot, before any rule is compiled.

---

## Phase 3: The CLI becomes an interactive host

The second host, serving a different user: a rule author poking at a rule in a terminal. The
CLI registers a prompting handler for every declaration and drives the same `run` the example host
uses. A typed answer is compiled as a Klein expression against the same release and checked against
the declared type, so `Customer(1, "gold")` works and a mismatch re-prompts with the checker's
message rather than entering the machine.

### File Changes

- **`klein-lib/src/commonMain/kotlin/klein/check/contract/EnvironmentContract.kt`**: a second compile
  verb sharing the private pipeline `compileRule` already uses. Their promises differ — an `Edition`
  is a rule compiled against a release with its pins; this is a pure expression of a demanded type.

```kotlin
fun compileValue(source: String, release: ReleaseNumber, expected: RuleType): CoreExpr
```

  Purity is what makes `Klein.execute` enough to evaluate an answer, and the used-capability pass
  enforces it: an answer may use the release's **types** but not its **capabilities**, so answering a
  prompt never triggers more prompts. Handing the demanded type to the checker gives the message the
  person typing needs — `argument 2 of Customer expects Str, got Num`, at the span they wrote.
- **`klein-lib/src/nativeMain/kotlin/klein/Main.kt`**: `runCmd`'s contract path builds a prompting
  environment and runs the edition.

```kotlin
val environment = contract.implement {
    declarations.forEach { d -> immediate(d.name, d.revision) { args -> prompt(contract, release, d, args) } }
}
println(Value.print(environment.run(edition)))
```

  `prompt` prints the call (`creditScore(Customer(1, "gold")) = ?`), reads a line, compiles it with
  `compileValue` against the capability's declared type, evaluates it with `Klein.execute`, and
  re-prompts on `KleinException`. With no terminal (piped input, or `--stdin` consumed by the rule
  source), a suspension is an error naming the capability so scripts fail loudly instead of hanging.
- **`klein-lib/src/commonTest/kotlin/klein/check/contract/CompileValueTest.kt`** (new): an answer of
  the demanded type compiles and evaluates; a constructor answer works; a wrong-typed answer carries
  the checker's message at the typed span; an answer naming a capability is rejected; an answer
  naming an unexposed type is `UnboundVariable`.
- **`CLAUDE.md`**: the "Check or run against a capability contract" section no longer says a rule
  that names a capability cannot execute; document interactive `run`.
- **`examples/lending-rule.klein`**: its header comment currently says the rule cannot be executed.

### Validation

#### Automated Verification

- [ ] `./gradlew :klein-lib:jvmTest`
- [ ] `./gradlew :klein-lib:linkDebugExecutableLinuxX64`
- [ ] `echo "" | ./klein run --contract examples/lending.contract examples/lending-rule.klein --release 2`
      exits non-zero naming the capability it could not ask about (the no-terminal path)

#### Manual Verification

- [ ] `./klein run --contract examples/lending.contract examples/lending-rule.klein --release 2`
      prompts for `customer`, then `creditScore(...)`, then prints the result.
- [ ] Typing `700` where a `Customer` is demanded re-prompts with the type error; typing
      `Customer(1, "Acme", "gold")` is accepted.
- [ ] Typing `customer` at a prompt is rejected — an answer may use types, not capabilities.

---

## Phase 4: Handler answers are checked at the resume boundary

A handler returns a raw `Value`, and nothing compares it to the declared return type. A handler for
`fun creditScore(c: Customer): Num` that answers `VStr("hi")` resumes fine, and the run fails later
wherever something unboxes it — an `InvariantViolation` at the span of the operation that *consumed*
the value, not the call that produced it.

This phase adds one check at resume: work out a type for the answer, and ask whether it fits what
was declared.

### File Changes

- **`klein-lib/src/commonMain/kotlin/klein/check/ValueTypes.kt`** (new, internal): a type for a
  runtime value, which does not exist today.

```kotlin
internal fun infer(value: Value, env: RuleEnv): RuleType
```

  Most values map straight across — `VNum` to `TNum`, `VStr` to `TStr`, and so on. `VStruct` is why
  it takes an environment: untagged it is a record, so the fields are inferred and wrapped in a
  `TRecord`; tagged, the tag is only a string like `"Customer"`, and turning that into the nominal
  type means looking the constructor up. The registry is the `types: RuleEnv` in `ResolvedRelease`,
  which the environment reaches through the contract it captured in Phase 2.

  Two answers cannot be typed, and both come back `TBottom` so they fail the check rather than
  crashing it: a `VClos`, which contracts.md:382 forbids from crossing the boundary at all, and a
  `VStruct` whose tag the release does not know — a host answering with a constructor from some other
  release.

- **`klein-lib/src/commonMain/kotlin/klein/host/Runner.kt`**: the check, in one place the loop calls.

```kotlin
class HandlerTypeMismatch(call: String, got: RuleType, declared: RuleType) : KleinError

// at resume, where `release` is the ResolvedRelease the edition names
val declared = release.types.lookup(call)                        // already stripped by expose
val answers  = if (declared is TFun) declared.result else declared
val got      = infer(answer, release.types)
if (!isSubtype(got, answers)) throw KleinException(HandlerTypeMismatch(call, got, answers))
```

  The declared type needs no work at the boundary: `expose` binds every name in the `RuleEnv` to
  `it.strip()`, so the release's own environment already holds it revision-free. Only the *result*
  is compared — an answer to `creditScore` must match `Num`, not the arrow — while a capability
  value's whole type is what an answer must match.

  Subtyping rather than equality, so a handler answering with a record that carries extra fields is
  accepted, as Klein's width subtyping says it should be. `HandlerTypeMismatch` carries
  `AwaitingHost.span`, the call site in the rule, which is the point of the whole phase.

- **`klein-lib/src/commonTest/kotlin/klein/check/ValueTypesTest.kt`** (new), for `infer` alone: each
  `Value` maps to its type; a tagged struct becomes its nominal type; an untagged one becomes a
  record; an unknown tag and a closure both come back `TBottom`.
- **`klein-lib/src/commonTest/kotlin/klein/host/RunAgainstReleaseTest.kt`**, through a real run: a
  handler returning a `VStr` where `Num` is declared names the capability, what it gave and what was
  declared; a `Customer` with a wrong field type is caught at the boundary; a record with extra
  fields passes.
- **`docs/performance-debt.md`**: `infer` walks the whole answer value and `isSubtype` then walks
  both types, so the cost grows with the answer size and is paid on every resume, to catch something
  a correct handler never does. The loop calls it in one place rather than spreading the logic
  around, so a host that trusts its handlers can turn it off — which is the entry's fix.

### Validation

#### Automated Verification

- [ ] `./gradlew :klein-lib:jvmTest`
- [ ] `./gradlew :klein-example-host:test`

#### Manual Verification

- [ ] Point a `LendingHost` handler at the wrong type and confirm the message names `creditScore`,
      not whatever operation consumed the value.

---

## Not in this outline: the result sink

Earlier drafts had a Phase 5: a release nominating one capability as where the rule's answer goes
(`result decide`, a `fun` returning `Nothing`; a rule concludes on every path or its trailing
expression is wrapped, and mixing the two is a compile error). It is out of this feature — it is
the one slice that changes the contract language, §Releases today says a release entry names a
declaration and nothing else, and nothing in Phases 1–4 depends on it. It is now its own roadmap
item ([host-integration-roadmap.md](../../host-integration-roadmap.md) §Result sink), to be taken
spec-first. The design draft stays in [tdd.md](./tdd.md) §"A release may nominate one capability
as where the rule's answer goes"; this outline's git history holds the phased file list.

---

## Not in this outline: driving `Implementation.Deferred`

`Implementation.Deferred(val take: (HostCall) -> Unit)` (Environment.kt:46-48) exists and has never
been driven. It stays that way, for two reasons that between them leave it nothing to do here.

**In-process, `immediate` already covers it.** `Immediate.answer` is an ordinary
`(List<Value>) -> Value`, so a handler waiting on I/O just blocks in whatever the host language
offers — virtual threads, green threads, an await. A separate hand-off mechanism buys nothing.
(Making `answer` suspendable is its own API question, and a later one.)

**Across a restart, a closure is the wrong tool.** The case `Deferred` is really for — a queue, a
durable workflow, a human approval — means the host node can die between the hand-off and the
answer, so anything the runner closed over is gone. ADR 2026-07-31 already settles what happens
instead: machine state is never serialized, the effect log is truth, and replay rebuilds the run up
to the suspension. That needs the effect log, which is downstream of edition serialization and out of
scope per the PRD.

So `Deferred` lands with the log, not with execution wiring. Everything the mode phantom was for —
`Registry<M>`, `Environment<M>`, `implementDeferrable`, `run` being absent where handlers may defer —
goes with it, and Phases 2–5 have one entry point.

---

## Open Questions

None outstanding.

Two things the roadmap once listed as open decisions need no decision: a capability outside call
position is an ordinary value of arrow type, so `f = creditScore` works unless a restriction is
*added*; and the Core IR carries no revisions anywhere, so `HostCall` not carrying one is simply
that. Both are recorded as settled in the roadmap's §Execution wiring.

Interactive mode's happy path is checked by hand, since piping input is an error by design.
