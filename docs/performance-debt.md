# Performance Debt

Deliberate performance corners cut in favor of simplicity, correctness, or serializable
state. None of these change results — they are all "the simple version works, optimize once
a profiler says it matters." This is the list to revisit once there is a benchmark baseline,
roughly ordered within each section by expected impact.

Each entry: what we do now (and how it bites), why we do it, and the fix.

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
per-comparison `VBool`s — the entries below — are the whole story. The old *recursive*
evaluator's recorded `fib` was 266 µs; the IR machine's 646 µs is the retained price of
suspension-as-data.

## The log-persistence rethink (2026-07-31)

A suspended program no longer needs its machine state persisted at all. The machine is
deterministic by construction — nondeterminism enters only through the extern boundary —
so the state at any suspension is a pure function of three recorded things: the compiled
artifact (pinned by checksum), the extern vals it was linked with, and the extern-fun
responses so far. Persisting a suspension means persisting that log; resuming means
re-running the program from the start, feeding logged responses back until the log runs
out, then continuing live. (Temporal works this way; Klein gets the determinism it has to
fight for from the language itself.)

Two tiers follow: the in-memory machine is the hot tier (resume is O(1) while the process
holds it); the log is the durable tier (crash, eviction, migration — resume costs one
replay). During replay, each re-issued request must match the logged one — name and
arguments — so any semantic drift fails loudly at resume, not as silent divergence.

Consequences for this list: serializability was the *why* behind the two most expensive
entries below. With it gone, they are unblocked — marked inline. The trade: `clone()`
becomes fork-by-replay (O(replay), fine for its only customer, what-if debugging), and
the evaluation-order semantics pinned by the eval test suite become a compatibility
contract — replay of old logs depends on them, so the suite guards parked suspensions in
production, not just correctness today. Sequencing: land the benchmark baseline first,
then make these changes as measured before/afters.

## Representation

**Boxed values.** Every `Value` is a heap object; `VNum` wraps a `double`, so `a + b + c`
boxes the intermediate `a+b` and unboxes it again for `+c`. This is the defining cost of a
boxed interpreter.
- *Why:* a uniform, walkable, serializable `Value` — "states are data." Value classes unbox
  only in monomorphic slots (every slot here is `Value`-typed) and NaN-boxing needs memory
  layout control portable KMP doesn't give, so neither helps.
- *Fix:* an unboxed representation in a native/optimizer tier that runs where the machine
  isn't suspending; superinstructions that keep doubles unboxed across a `PrimApp` subtree.
- *Free adjacent win, not yet taken:* `VBool` is allocated per comparison; it has two
  inhabitants and should be two singletons (`VNull`/`VUnit` already are). Every `if`/guard
  boxes a bool only to match on it and discard it.

**Persistent control/operand stacks.** Each push allocates a `Cons`; the CESK step loop
churns small, short-lived nodes.
- *Why:* O(1) snapshots — a host-call suspension/clone just shares the spine.
- *Fix:* mutable `ArrayDeque` stacks + copy-on-snapshot. Cheap steps; pay the O(n) copy only
  when a host call actually clones (rare), which is exactly when you can afford it.
- *Unblocked by the log rethink:* no snapshot consumer remains — suspension just stops the
  loop (nobody else holds the stacks) and forking is replay-based. Go straight to mutable
  `ArrayDeque`, no copy-on-snapshot needed.

## Machine execution

**Sealed-`when` dispatch.** `when (expr) { is Literal … }` compiles to an `instanceof` chain
(the JVM has no type-based jump table), so dispatch is O(arms) per step — the fundamental
tree-interpreter tax.
- *Fix (cheap):* order the arms by dynamic frequency; the ANF change below shifts the hot set
  to `Apply`/`PrimApp`/`EnterScope`/`Match`.
- *Fix (structural):* a flat node table addressed by `Int` (kills pointer-chase) with a dense
  integer opcode (enables a real `tableswitch`). This is most of what a bytecode tier is for.

**A control frame per subexpression (no ANF).** Every leaf — `Literal`, `Var`, `Lambda` —
gets its own control frame and a full dispatch-loop trip, though it produces a value in one
step with no continuation to return to. Leaves are ~half the nodes on `fib`.
- *Fix:* evaluate atomic operands inline in `collectOperands` (no frame); or lower to ANF so
  every argument position is atomic by construction and a frame is pushed only to enter a
  function body or suspend.

**An empty scope frame per arm.** `armScope` gives *every* matched arm a fresh `BindingScope`,
including `lit`/`_`/variable arms that bind nothing — an empty-slots `BindingScope(parent)`
allocated on each dispatch. It also costs every out-reference from that arm's body or guard an
extra parent hop: the scrutinee reads as `s[1;0]`, not `s[0;0]`, and a variable arm aliases the
scrutinee one level deeper. Only constructor arms actually need slots.
- *Why:* uniform lowering — treating all arms as one scope deeper lets the lowerer assign arm
  depths mechanically, without a per-arm "does this bind anything" special-case.
- *Fix:* reuse the parent scope for arms with no field bindings (`else -> parent` in `armScope`)
  and keep those arm bodies at the match's own depth in the lowerer; the empty alloc and the
  extra hop then survive only on constructor arms, which genuinely bind.

**Every `Var` walks the scope chain.** `(depth, slot)` resolution follows parent pointers,
O(scope depth) per read.
- *Why:* depth is 1–2 in practice (business rules don't nest deeply) and the walk is dwarfed
  by boxing/dispatch; the simple version wins until a profiler disagrees.
- *Fix (cheap):* `GlobalRef(index)` — direct-index the outermost scope so top-level refs
  (funs, constructors, host bindings) skip the walk.
- *Fix (structural):* closure conversion / flat per-function scopes — O(1)
  local/captured/global addressing. Klein's immutability makes it the easy version (capture
  by value, no upvalue cells); the one wrinkle is local recursive bindings.

**The store never frees.** `Store.alloc` only appends; the store grows O(total allocations)
and never shrinks — a straight-line leak for long-running or loop-heavy programs, which is the
rule-engine workload.
- *Fix:* reclaim stack-disciplined scopes (a high-water mark rewound on scope exit), or a
  bounded store.
- *Unblocked by the log rethink:* the global store exists so closures capture integer
  addresses instead of object references — an acyclic, serializable shape. With nothing
  serializing machine state, dissolve it: `BindingScope` holds `Array<Value?>` directly
  (slot *i* of the scope *is* the cell — empty until filled, same early-read error, same
  letrec behavior since closures share the scope object). Unreachable scopes become
  ordinary garbage; the leak stops being a problem to solve.

**Every binding gets a store cell.** `stepApply` cells all arguments. But the store
indirection is only *needed* for allocate-before-fill bindings — recursive and forward-
referenced (hoisted) `fun`s, where the cell must exist before the value that references it.
Arguments, `val`s, and scrutinee/whole-value binders are never recursive and, being immutable,
capture by value — so they need no cell. Celling them is pure uniformity.
- *Fix:* non-recursive bindings live as direct values in the `BindingScope`; reserve the store
  for `fun`s (it is really "the recursive-function heap"). This is the single biggest source of
  the store growth above.
- *Unblocked by the log rethink:* subsumed by the scope-array change above — every binding
  becomes a direct entry in its scope's array, so the recursive/non-recursive split stops
  mattering for cost.

## Lowering

**Construction goes through a lambda call (uniform `Apply` in v1).** Constructors are
eta-expanded to closures, and v1 lowers *every* saturated construction like any other call —
`Circle(1)` → `Apply(Circle, [1])` — so building one record runs the full call protocol (a
`BindingScope`, a param cell per field, a frame) around a `MakeData` body. A 1000-element list
is 1000 closure calls + 1000 param cells (which never free). The lowerer *could* emit `MakeData`
directly — it statically knows the callee is a saturated constructor, so it's just picking a
node, not β-reducing — but that, dead-bind elimination, and general linear-redex inlining are
all kept out of v1 to keep lowering uniform.
- *Fix (optimizer):* rewrite `Apply(<known ctor>, saturated-args)` →
  `MakeData(tag, fieldNames, args)` (safe: constructor bodies are linear and pure); drop
  constructor binds with no value-position use; and, more generally, inline any saturated
  linear redex (each param used at most once) — of which the constructor fold is the degenerate
  case that needs no scope/depth fixup.

**Hoisting cheap/trivial receivers, scrutinees, and destructure RHSs.** Three lowerings bind a
value to a temp so it is evaluated once, more aggressively than needed:
- Scrutinee and `?.`-receiver hoisting wraps a fresh `scope` and allocates a cell even when the
  receiver is a cheap pure expression (`a + 1`), where re-evaluating costs less than the hoist.
- Destructuring binds the RHS to a temp whenever it extracts **≥2 fields**, even when the RHS is
  already a slot (a trivial var) — a redundant slot-to-slot copy (`bind _rhs = person`).

A hoist buys one saved re-evaluation at the cost of a `BindingScope` + a store cell + reads.
- *Why worth it sometimes:* an **effectful** receiver (contains a `HostCall`) *must* hoist —
  re-evaluating fires the effect twice. A pure-but-expensive or heavily-reused receiver also
  wins.
- *Fix:* rematerialization vs spilling (as register allocators do) — inline-duplicate cheap
  pure receivers instead of hoisting, and skip the destructure temp when the RHS is already a
  slot (or cheap); hoist only effectful or expensive/reused values. Purity is trivial here:
  effectful == contains a `HostCall`.

**Wrap-in-fresh-scope for the hoist.** Even when a hoist is warranted, it allocates a new
`BindingScope` around the match.
- *Fix:* inject the bind into the enclosing scope when the match sits at a scope tail — flatter,
  no new scope object.

**Always-emit-match for a redundant `?.`.** `p?.x` on a *non-optional* `p` still lowers to
`match p { null -> null; _ -> p.x }`, with a dead `null` arm and a needlessly optional result
type.
- *Why:* keeps `?.` a purely syntactic desugar (types are erased, so the lowerer can't tell
  optional from non-optional without checker input).
- *Fix:* type-directed lowering — a plain `FieldGet` when the checker says the receiver is
  non-optional.
