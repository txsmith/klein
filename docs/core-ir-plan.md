# Plan: The Core IR

**Status:** In progress · **Started:** 2026-07-20 · **Branch:** `interpreter` (PR #23)

Klein gains a surface/core split (à la GHC Core): the surface AST stays what the parser
produces and the checker consumes; a new `lower` stage produces a **core IR** that the CESK
machine steps over. The pipeline becomes:

```
tokenize → parse → check → lower → interpret
```

The IR serves three masters at once:

1. **Speed** — slot-addressed variables (no name hashing in the hot loop), scope plans baked
   in (no per-call SCC recomputation). Targets the CESK machine's measured 1.8–2.1× overhead
   on call-heavy programs (`fib` 266→561µs, `sumTo` 37→65µs vs the tree-walker).
2. **Diagnostics** — closure names, call markers in K, real Klein stack traces on errors and
   on suspended states.
3. **Persistence** — the IR is the artifact stored in the DB. Authoring runs the full front
   end once at save; runtime loads IR and steps it (checking costs more than evaluation for
   most rule-shaped programs, so this halves cold-start).

## Design decisions (settled)

- **Lowering requires checker output.** There is no lower-without-check scenario; `check`'s
  output grows from a bare `Type` to a checked-program artifact (program + type + scope/SCC
  analysis) and `lower` consumes it exclusively. Unbound names at lowering are internal
  invariant violations, not user diagnostics.
- **Source is truth; IR is a cache with cheap integrity triggers.** Store source + IR + a
  lowerer-version stamp + a checksum. A version mismatch *or* a failed checksum both collapse
  to one response: re-derive from source, never migrate the IR schema. That is the whole
  integrity story — one mechanism (source is truth), two cheap triggers. (Deferred until the
  edge design exists: an edge fingerprint as a third trigger, same handler.)
- **Names dissolve into slots, survive as metadata.** `Ident` → `LocalRef(depth, slot)`.
  Reference nodes keep the source name, lambdas carry an optional inferred name (from `fun`
  or the binding) — the JVM `LocalVariableTable` pattern: indices for speed, names for humans.
- **Vocabulary (2026-07-20): never call a lexical env level a "frame".** A **scope** is a
  lexical env level (a lambda's params, a block's bindings) — the thing `(depth, slot)`
  addresses; a **frame** is a runtime activation record, one per call. The `(depth, slot)`
  walk is a *scope-chain* walk; frames (activations) and stack traces (which come from the
  K continuation, not the scope chain) are untouched by how names resolve.
- **v1 name resolution — deliberately minimal (2026-07-20).** Uniform `LocalRef(depth, slot)`
  for *all* references, top-level/host/constructors included (they sit in the outermost scope,
  reached by depth). **Keep the store** (scope holds slot→address `IntArray`, store holds
  address→`Value`) — same model as the current CESK machine, so the delta is just
  `Map<String,Int>` → `IntArray` and name-lookup → slot-index, killing the one real cost
  (per-read string hashing). Two optimizations are explicitly **deferred behind benchmarks**,
  not built in v1:
  - `GlobalRef(index)` — direct-index the outermost scope so global refs skip the walk.
  - Closure conversion / flat per-function scopes — collapse the scope-chain walk to O(1)
    local/captured/global addressing. Rejected for v1 because the walk is O(scope depth),
    which is 1–2 in practice (business rules don't nest deeply) and dwarfed by boxing/dispatch.
    Klein's immutability would make it the *easy* version (capture by value, no upvalue cells)
    if the numbers ever call for it; the one wrinkle is local recursive bindings (top-level
    recursion is fine).
- **Tail calls with trace policy, host-chosen per execution.** Call markers (callee name +
  call span) are pushed on entry and pop as identity frames; a tail call *replaces* the top
  marker. `begin()` takes a trace mode: `full` / `budgeted(n)` / `elided` — budgeted is the
  default (collapse elided history into one counted `(...×N tail calls...)` entry). The
  flag never changes results, only memory behavior and trace fidelity.
- **Metering.** `begin()` also takes an optional step budget (fuel) so untrusted rule
  authors can't loop forever — a counter in the machine loop.
- **Own machine, not a rented VM.** Compiling to JVM bytecode / WASM / Lua was considered
  and rejected: Klein's differentiators (suspension-as-data, deterministic replay, the
  migration ladder, metering, sandboxing) live below the bytecode line of any existing VM,
  and multiplatform embedding rules out JVM bytecode outright. WASM re-enters later only as
  a distribution channel for our runtime; native compilation only as an optimization tier
  over this IR.
- **No load-time verifier; the machine's per-op checks are just unboxing.** (Reversed after
  discussion 2026-07-20.) In a boxed machine `asNum` / field-miss / arity are the unavoidable
  tag tests needed to read a value at all — removing them buys no speed, only a worse error on
  an impossible branch — so they stay as free hygiene, not as a safety system. A shipped
  load-time verifier was rejected: a checksum beats it for corruption (a verifier misses
  value-preserving corruption and costs more), the version stamp already prevents skew, and a
  verifier fails *correlated* with lowerer bugs (same authors, same model — unlike GHC Core
  Lint, which checks type-preservation across a transform, an independent property). Kotlin
  already gives memory safety, which is the property the JVM verifier actually exists to
  protect. A real validator only earns its place if Klein ever runs IR from untrusted third
  parties — a someday concern, same shelf as a JIT. The only surviving form is an optional
  well-formedness assertion in the lowering pass's own tests: normal dev hygiene.
- **Migration of long-running suspended runs is host policy, not a Klein engine.** Klein's
  job is the three primitives that make every rung of the ladder buildable: serializable
  state (CESK), effect transcripts (`HostCall` in/out through the `Execution` API), and
  version-stamped IR with stable node identity. Host-side options then include: pinning,
  replay-based migration (re-run the new version against the recorded transcript — purity
  makes divergence exact), IR diffing to prove local edits don't move suspension points,
  and Erlang `code_change`-style authored transforms as the last resort.

## Open design flags

- **Tree vs flat node table.** Current lean: define the IR as a Kotlin tree (readable
  machine code, easy lowering) but keep it acyclic, kotlinx-serializable, and trivially
  flattenable; a flat indexed node table — which suspended-state persistence and IR diffing
  ultimately want, and which is most of the way to bytecode — is a later mechanical step.

## Task list

- [ ] **1. Define the core IR and its metadata** — spans everywhere; types/`TypeDef`s/
  `Ascription` erased (constructors become scope-bound values); slot refs with names as
  metadata; lambda names; numeric literals flattened at lowering (IEEE double for now);
  version stamp. Node decisions whiteboarded 2026-07-20:
  - `Lam(arity, body, name?)` — multi-arg (positional-function ADR forbids currying); no
    `isImplicit` (the `.` param lowers to an ordinary slot-0 ref); thunk = arity 0.
  - `Match(scrutinee, arms)` — **no scrutinee binder field**: the lowerer hoists non-trivial
    scrutinees into an ordinary scope slot (evaluate-once), so the scrutinee is always a
    trivial ref. Constructor arms carry `tag` + `fieldKeys: List<String>` (semantic: which
    fields to extract into the arm's slots — position = slot; variable names are gone).
    Full desugar of field extraction into `FieldGet`+`Scope` blocked by guards
    (first-match fallthrough needs the pattern-match compiler); revisit with nested patterns.
  - `Scope(items, slotCount, result)` — items **in textual order**, interleaved
    `Bind(expr, name?)` (fills next slot) / `Run(expr)` (evaluated, discarded — statements
    don't waste slots); slot = bind-ordinal, implicit; `result` is a distinguished field
    because it evaluates in **tail position** (pop ScopeK, then evaluate on the parent K —
    this also fixes a latent bug: the current machine leaks one ScopeK per call for
    block-bodied tail recursion). No `Rec`/`NonRec` encoding — recursion structure has no
    runtime consumer; re-derivable if an optimizer ever wants it. No "store to slot" node —
    fills are frame behavior implied by structure (that instruction materializes only at a
    future linear/bytecode tier).
  - **Evaluation order is strictly textual** (funs hoisted — their creation is unobservable;
    everything else as written). Verified 2026-07-20: the checker *already* rejects forward
    `val` references ("Unbound variable") and already hoists `fun`s, so this is not a
    semantics change — the binds-before-statements phasing in the current machine was an
    implementation artifact (observable as effects running out of written order) that the
    IR machine simply drops.
  - **Final node set (settled 2026-07-20, in Core.kt):** `Literal`, `Var`, `Lambda`, `Apply`,
    `PrimApp` (with `Prim` a plain enum — operators only, all strict; `and`/`or` lower to
    `Match`), `MakeData(tag?, fieldNames, args)` (nullable tag unifies records and
    constructor data — the tag is all that survives erasure of nominal-vs-structural; `VRecord`
    dies at runtime too), `FieldGet`, `Suspend(name, args)`, `Scope`, `Match` (guards on all
    arms; no scrutinee binder — non-trivial scrutinees hoist to a slot; no `If` node — it's a
    `Match` on Bool). Lowering rule: Core form follows *surface syntax class* — operator
    syntax → `PrimApp`, call syntax → `Apply` (uniformly), data literals → `MakeData`.
  - **Every callable is a closure — uniform `Apply`, one dispatch case.** Constructors are
    eta-expanded lambdas over `MakeData`; host natives are wrapper lambdas over `Suspend`
    (manufactured at `begin()` from host bindings — the lowerer never emits `Suspend`);
    future named builtins (`sqrt`) are pre-bound lambdas over prim bodies. `VNative` and
    `VConstructor` both leave the `Value` hierarchy. `Suspend` is the effect-operation
    (`perform`) node the future effects design needs, arrived at via uniformity.
  - **No saturated-call fast paths in v1.** Compile-time β-reduction of known callees is
    *one* future optimization applied by policy, not per-kind carve-outs: constructors —
    can and should; natives — can but must not (inlining `Suspend` would bake host-ness
    into the stored artifact and break mock/replay host swapping via slot rebinding);
    user closures — the optimizer-tier question. Wrapper spans are synthetic; host-call
    error locations recover the call site via the task-6 markers.
- [ ] **2. Checked-program artifact from `check`** — program + type as the stage's output.
  (Shrunk 2026-07-20: no SCC/scope deliverable needed — textual order is correct because
  forward val refs are already check errors and funs hoist; SCC remains checker-internal
  for its recursive-fun-group rules.)
- [ ] **3. Lowering pass** — checked artifact → IR: slot resolution, erasure, name
  inference. Unit tests: slot layout, name inference, erasure. Update host-call tests to
  check first (helper that binds native types).
- [ ] **4. Pipeline + CLI wiring** — `Klein.lower` as a total stage; `run` composes all
  five stages; a CLI command to dump the IR.
- [ ] **5. Machine onto the IR** — frames reference IR nodes; IntArray env frames; scope
  entry follows baked plans. All existing tests stay green.
- [ ] **6. Call markers, trace modes, error traces, metering** — marker frames with
  tail-replacement; `begin()` config (trace mode + fuel); `KleinRuntimeError.trace`;
  CLI renders Klein stack traces; `Suspended` exposes its location.
- [ ] **7. IR serialization round-trip** — kotlinx.serialization + version stamp + checksum;
  test: lower → serialize → deserialize → interpret ≡ direct interpretation, and a corrupted
  blob is rejected by the checksum (→ re-derive from source).
- [ ] **8. Benchmark and document** — full JMH run vs the CESK-on-AST numbers (expect
  `fib`/`sumTo` to close most of the gap; price the markers); update CLAUDE.md and
  implementation-status; push.

## Explicitly not in scope

`NumAlgebra`/rational Num (waits on the numerics spec work) · the persistence DB layer and
replay tooling (host territory; the primitives suffice) · edge fingerprinting and the
load-time verifier (need the interop edge design) · flat node table & bytecode tier
(later, mechanical) · pattern matching (own branch).
