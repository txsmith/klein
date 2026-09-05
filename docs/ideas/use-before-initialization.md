# Idea: Catching Use-Before-Initialization at Check Time

**Status:** Idea — not yet implemented · **Found:** 2026-07-27, while testing the Core machine

## The program

This program passes the type checker today:

```klein
x = g(1)
y = 2
fun g(n: Num): Num = y
x
```

Klein evaluates top to bottom, except that function definitions are available
before their own line. So `x = g(1)` runs first, and `g`'s body reads `y` —
whose slot exists but has not been filled yet. Three different outcomes exist
for this one program:

- **The old interpreter** evaluated bindings in dependency order (`y` before
  `x`, because `x` needs `g` needs `y`) and returned `2`.
- **The new machine** evaluates in written order and raises a runtime error:
  `'y' used before its binding was evaluated`, pointing at the read.
- **This proposal**: the checker rejects the program, pointing at the chain:
  *`x` calls `g`, and `g` reads `y`, which is not yet initialized at that point.*

The checker misses it today because it checks each reference against what is
textually above it — and `g`'s reference to `y` *is* fine at `g`'s definition
site. What it doesn't account for is that a call can move that read to an
earlier moment.

## What other languages do with this program

| Language | Behavior |
|---|---|
| Scala, Kotlin (fields) | Runs; the early read silently sees `0` / `null`. Wrong answers, no error. |
| C# (class fields) | Same: silently sees the default value. |
| JavaScript (`let`) | Runs; the early read raises a runtime error. |
| Haskell | Order doesn't matter — nothing evaluates until used. True cycles still fail, at runtime. |
| Rust | The program cannot be written: functions can't use local variables, and global initializers are evaluated at compile time with cycles rejected. |
| C# (local variables) | **Rejected at compile time.** The compiler tracks which variables each local function reads and requires them to be assigned at every call — and, when a function is passed around as a value, at the point it's passed. |

C#'s treatment of local variables is the strongest design that doesn't ban
useful programs, and it is essentially what's proposed below. Rust is stronger
still, but only by forbidding functions from reading outer bindings at all —
which would kill ordinary Klein rules like:

```klein
limit = 100
fun withinLimit(x: Num): Bool = x < limit
```

## Proposal

For every function, the checker computes its **read set**: the value bindings
its body might read — directly, or through other functions it calls by name.
This is a whole-program computation, but Klein makes it small: there is no
mutation, and calls to named functions are visible in the source.

Then, walking the bindings of each scope in written order, tracking which are
initialized so far:

1. **Where a binding's expression calls a function by name**, everything in
   that function's read set must already be initialized. Otherwise: check
   error, naming the chain (*"`x` calls `g`, which reads `y` before `y` is
   initialized"*).
2. **Where a function is used as a value instead** — passed as an argument,
   stored in a record — apply the same requirement at that point. Once the
   function travels as a value, we can no longer see where it will be called,
   so the safe assumption is that it might be called immediately.

Rule 2 is borrowed directly from C#, and its known cost is a small class of
false alarms: an initializer that stores a function without calling it (say,
building a record of callbacks before all bindings are done) gets rejected
even though it was safe. C#'s experience suggests this almost never bites in
practice; if it bites in Klein, the rule can be revisited.

## What remains at runtime

No rule above catches every case without also rejecting safe programs, so the
runtime error stays as the last line of defense, and its classification stays
as-is: it is a program error (a `RuntimeError` with the offending read's
location), not an internal one, because a checked program can reach it. The
machine never computes with an uninitialized slot — the alternatives in the
table above that silently produce `0`/`null` are the outcome this design
refuses.

The machine-level behavior is pinned by
`MachineTest.callBeforeBindThroughAFunctionIsARuntimeError`.

## Rejected alternatives

- **Evaluate in dependency order** (what the old interpreter did): makes this
  program work, but it means statements no longer run in the order they are
  written — a `log(...)` line can run after a later line already crashed.
  Written-order evaluation was chosen deliberately; this stays.
- **Lazy evaluation** (Haskell's answer): dissolves ordering entirely, but
  Klein bindings can contain host calls, and lazy bindings would fire those
  at hard-to-predict moments.
- **Forbid functions from reading outer value bindings** (Rust's answer):
  removes the problem and the language's usefulness with it.

## Where this lands

Checker work, sized small (Klein is pure and first-order in its call graph).
No machine changes. Natural home: alongside the checker-artifact work, or as
its own follow-up once the lowering pipeline exists.
