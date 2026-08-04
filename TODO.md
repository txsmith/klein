# TODO

Work to make the host-integration design (docs/ideas/host-integration.md) real.

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
- [ ] **Contract + handler map** *(needs: bodiless declarations)* — a contract is a
      parsed contract file; the host supplies handlers dynamically, **and supplies the
      revision alongside each handler** — the revision declares that the
      implementation's meaning changed, so it belongs next to the implementation, not
      in the file. Consequences: the file carries signatures only, identity is assembled
      at load from signature + registered revision, and a contract file alone is enough
      to *check* a rule but not to compile pins or run one (running needs handlers,
      which are code). Two rules to get right now because changing them later touches
      every call site:
      - **Identity is opaque.** A `CapabilityId` type, computed as `name@rev` for now
        and as the canonical signature hash later. Name+revision alone is *not* the
        identity: changing a signature without bumping the revision leaves name+rev
        unchanged, and a name-keyed handler would then silently serve the new
        implementation to runs pinned to the old signature.
      - **Register by name, dispatch by id.** Hosts register handlers by name
        (+revision); loading resolves them against the parsed contract into an
        id-keyed dispatch table, failing loudly on either side missing. The node's
        advertisement is that table's key set.
- [ ] **Capabilities reachable from Klein source** *(needs: contract + handler map)* —
      seed the checker's TypeEnv from the contract; lowerer resolves capability names
      (calls -> suspensions, capability vals -> link references) and records pins;
      `Machine.start(program, contract, handlers)` links capability vals and writes the
      effect log's first entry. End state: `klein run` with capabilities end to end;
      the parked legacy HostCallTest gets its successor suite. Cheap add when wanted: a
      shape check at resume against the declared return type, so a bad handler says
      "handler `creditCheck` returned Str, declared Num" instead of failing deep in the
      machine.

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
      and scenario/migration tooling stand on this.
- [ ] **Runtime lifecycle library** *(needs: editions, severity)* — pure functions +
      report objects over org-supplied data: reconcile (pin-hash prefilter,
      severity-classified reports), retire (cutoff flag: no fresh runs, no new
      editions, resumes allowed, reversible), drain queries, maintenance-queue
      reports.
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
