# TODO

Work to make the host-integration spec (docs/spec/host-integration.md) real.

## Next up — get the pipeline working end to end

The shortest path to `klein run` with real capabilities. Everything here is
dynamically typed at the host seam (`Handler = (List<Value>) -> Value`); the typed API,
derivation, wire format and platform-agnostic `Num` all follow *after*, wrapping this
rather than replacing it.

- [x] **Declarations without definitions** — `fun name(params): T` with no `= body`,
      `name: T` with no `= value`. No new keyword, no new tokens. `FunDecl`/`ValDecl`
      as distinct AST nodes; `DeclarationWithoutBody` in program mode; grammar.md
      documents both. (Exposed and fixed a latent bug: `parseTypeUnion` had no
      indentation awareness, so a `|`-lambda on the next line was swallowed as a union
      type.)
- [x] **Contract checking mode** — `Klein.checkContract(program, env): StageResult<TypeEnv>`
      as its own stage: declarations bind, definitions and bare expressions are errors
      (`DefinitionInContract`, `ExpressionInContract`), duplicate declared names
      caught. Composes as
      `checkContract(contract).andThen { env -> check(program, env) }`.
- [ ] **CLI surface for contracts** — `checkContract` is library-only today. Decide:
      a `--contract <file>` flag on `check`/`run` (composes; contract alone when no
      program given), versus a separate `klein contract` command, versus a
      file-extension convention.
- [x] **`Environment`: contract + implementations** —
      `klein.host.Environment.load(contractSource) { … }`: parse, check the contract,
      register implementations, validate, **throw** `EnvironmentError` carrying every
      error (boot-time, unrecoverable — unlike the pipeline stages, which return
      `StageResult` because an editor renders their diagnostics). Exposes `typeEnv`
      (seeds `Klein.check` for rules), `capabilities` (declarations + ids; also the
      set of revisions this host implements), and handler lookup by id. Settled points:
      - **Two registration forms, one block**, so a capability's whole strategy is
        visible on its own line and no capability is defined by its absence:
        `immediate(name, revision = 1) { args -> Value }` answers inline;
        `deferred(name, revision = 1) { call -> Unit }` takes ownership and answers via
        `call.resume(v)` (in-process, any thread) or by persisting `call.token` and
        replaying elsewhere. In-process async and durable both use `deferred` — the
        split is "answer inline" vs "I own the continuation", not sync vs async.
      - **Registration is required**: every declaration needs an implementation, so
        forgetting one is a boot error rather than a 3am surprise the first time a rule
        calls it. A host that dispatches by hand says so with `deferred(name) { }`.
        Also validated: every registration names a declaration, and no name twice.

      Revisions are declared in the contract (`/N`); registration names an existing
      declared revision and each declared revision needs its own implementation.
      Remaining delta: `CapabilityId` still hashes the signature — under the settled
      design identity is the declared (name, revision) and the hash is only a
      reconciler change-detector.
      - **Handler errors: we don't care.** Exceptions escape unwrapped; no `Result`, no
        `Failed` state, no Klein-level representation. The log is truth, so a failed
        turn simply did not happen and the host retries or abandons by its own policy.
        `call.fail(...)` is additive later if wanted.
- [ ] **Capabilities reachable from Klein source** *(needs: contract + handler map)* —
      seed the checker's TypeEnv from the contract; lowerer resolves capability names
      (calls -> suspensions, capability vals -> link references) and records pins;
      `Machine.start(program, contract, handlers)` links capability vals and writes the
      effect log's first entry. End state: `klein run` with capabilities end to end;
      the parked legacy HostCallTest gets its successor suite. Cheap add when wanted: a
      shape check at resume against the declared return type, so a bad handler says
      "handler `creditCheck` returned Str, declared Num" instead of failing deep in the
      machine.

- [x] **Revision syntax in contracts** — `/N` on declared names and type references
      (`fun creditScore/2(c: Customer/2): Num`), absent meaning 1; duplicate key is
      (name, revision); a revised type revises its constructors. grammar.md and
      spec/contracts.md updated.
- [x] **Remove the `review` marker** — done at `3584a31`, which stripped it from the
      parser, AST, grammar.md and tests. The release design has no marker at all: an
      edited release carries rules along, a new release waits for a person.
- [x] **Release design and documentation** — tags replaced by numbered releases.
      spec/contracts.md §Releases rewritten, spec/host-integration.md retermed
      throughout (including the worked example), grammar.md given the release
      productions, and ADR 2026-08-08 records the decision plus what was rejected.
- [x] **Release test suites** — exhaustive, spec-driven, written before the
      implementation:
      - Parser: `klein/parser/ReleaseTest.kt`.
      - Contract checking: `klein/check/contract/` — `ReleaseTypeCheckTest` (entries,
        inheritance, `remove`, numbering, the fold on retirement),
        `SelfContainmentTest`, `ContractTypeCheckTest`, and `LendingExampleTest`, which
        walks host-integration.md's worked example end to end.
      - Rule checking: `klein/check/contract/RuleAgainstReleaseTest.kt`, including the
        leak sweep over every diagnostic channel.

      One spec ruling changed on the way in: there is **no implicit first release**. A
      contract's releases are exactly its written blocks, so one that writes none simply
      has nothing a rule can be checked against.
- [x] **Release implementation** — `release` recognized contextually at statement head;
      `ReleaseBlock`/`ReleaseEntry` on `ContractExpr`; contract checking folds the blocks
      in file order and projects one into the plain-name `RuleEnv` a rule is checked
      against, through `EnvironmentContract.check(ruleSource, ReleaseNumber(n))`.
      Self-containment is what makes that projection lossless, and the revision witness
      on `Type` (`ContractType` vs `RuleType`, crossed only by `strip`) makes a revision
      reaching a rule a compile error.
      Out of scope here, and still open above: execution wiring (nothing makes a
      capability callable from a rule yet), the CLI surface for contracts, and the
      release travelling with a compile request to be recorded on an edition.

## After that

- [ ] **Capability derivation API** — code-first typed host API: derive Klein types
      from kotlinx.serialization descriptors (data class -> record, sealed -> sum,
      `Double` -> `Num`, nullable -> `T?`) via `inline`/`reified` so the handler's
      Kotlin types and the Klein signature cannot drift; marshalling both directions
      (Klein `Value` as a kotlinx serialization format); emit the checked-in contract
      file (mandatory for code-first). Boundary rules: no type variables, no function
      types. Per-language work; the mapping/encoding *spec* it binds to is shared.
- [ ] **Canonical form + numeric spec** — the written spec behind hashing and the wire:
      node tag + fields in declared order, semantic/trivia field classification, and
      the numeric contract (a host type may bind to `Num` iff every value round-trips
      without silent loss — Kotlin `Long` fails today; reject at derivation rather than
      coerce). Ties to the open `Num` design arc: exact rationals would change what
      binds.
- [ ] **Edition serialization** *(needs: canonical form spec)* — compiled Core + pin
      set + source + version stamp/checksum (per the source-is-truth ADR), round-trip
      tested. Spans need source provenance. The stored unit everything below queries.
      Direction: CBOR-shaped tree encoding over the canonical form — storage encodes
      every field, hashes walk only the semantic ones (spans, comment/whitespace
      trivia and capability param names excluded; revision marker included). Same
      encoding backs capability hashes and, later, the FFI wire.
- [ ] **Diagnostic severity classification** — per docs/ideas/diagnostic-severity.md:
      abstract class property on sealed `TypeError` (soundness vs degeneracy); split
      `IncomparableEquality` out of `TypeMismatch` at the equality emission site;
      callers map class -> severity. New diagnostics declare their class at birth.
- [ ] **Effect log + replay runtime** — per the persist-the-log ADR: record turns
      (requests + answers; link record as entry one); replay a log against an edition
      to rebuild a parked run; resume from replayed state. Parked runs, drain counts,
      and scenario/migration tooling stand on this. Two invariants settled while
      designing `Environment`: the log is **host-held and appended per turn**, never a
      value extracted from a returned `Execution` — `advance` runs several turns per
      call, and a handler throwing on turn 3 must leave turns 1–2 durably recorded, so
      appends happen between `resume` and the next handler invocation and must not be
      batched. And a **pending call is never stored**: replaying (edition + log)
      deterministically arrives back at the same suspension, so the log holds completed
      (request, answer) pairs only.
- [ ] **Runtime lifecycle library** *(needs: editions, severity)* — pure functions +
      report objects over org-supplied data: reconcile (pin-hash prefilter,
      severity-classified reports), drain queries (edition and parked-run counts per
      revision; no retire flag — removal is optimistic, stranded runs alert and the
      revision is restorable), failed-recompile reports (delivery to authors is
      org-side).
- [ ] **Call markers, trace modes, error traces** — the older #15: tail-call trace
      modes (full/budgeted/elided), fuel/metering per turn, runtime error traces, CLI
      rendering.

## Out of scope for v1

- Module system (types ride capability signatures; costs: shared Klein code, hub-type
  version bridging, module-mediated rule composition — see the Module entry in
  host-integration.md).
- Editor and storage connectors (standalone components, after the library surface
  settles).
- **Non-JVM hosts via a C facade** (JVM languages — Java, Scala — consume the Kotlin
  library directly) — Kotlin/Native can emit a dynamic/static library
  with a generated C header, but the real surface is a deliberate flat facade over the
  message layer: start / pending-request / resume / result / check, everything crossing
  as serialized bytes, one FFI call per turn (the coarse suspension boundary amortizes
  FFI cost by design). Guest languages use contract-first with per-language generated
  stubs. A standalone component per the library tenet. Design note now, so the
  derivation API's serialized forms are chosen knowing they become the FFI wire format.
  (WASM is the other eventual avenue; Kotlin/WASM-WASI is not mature yet.)
