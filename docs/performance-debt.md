# Performance Debt

Deliberate performance corners cut in favor of simplicity, correctness, or serializable
state. None of these change results — they are all "the simple version works, optimize once
a profiler says it matters." This is the list to revisit once there is a benchmark baseline,
roughly ordered within each section by expected impact.

Each entry: what we do now (and how it bites), why we do it, and the fix.

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

**The store never frees.** `Store.alloc` only appends; the store grows O(total allocations)
and never shrinks — a straight-line leak for long-running or loop-heavy programs, which is the
rule-engine workload.
- *Fix:* reclaim stack-disciplined scopes (a high-water mark rewound on scope exit), or a
  bounded store.

**Every binding gets a store cell.** `stepApply` cells all arguments. But the store
indirection is only *needed* for allocate-before-fill bindings — recursive and forward-
referenced (hoisted) `fun`s, where the cell must exist before the value that references it.
Arguments, `val`s, and scrutinee/whole-value binders are never recursive and, being immutable,
capture by value — so they need no cell. Celling them is pure uniformity.
- *Fix:* non-recursive bindings live as direct values in the `BindingScope`; reserve the store
  for `fun`s (it is really "the recursive-function heap"). This is the single biggest source of
  the store growth above.

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
