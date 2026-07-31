# Own machine, not a rented VM

**Status:** Current · **Date:** 2026-07-20, rationale re-centered 2026-07-31

Klein programs execute on our own CESK machine over our own Core IR. Compiling to an existing
VM — JVM bytecode, WASM, Lua — was considered and rejected.

The original argument leaned on suspension-as-*serializable*-data. That leg is gone —
[2026-07-31-persist-the-log-replay-the-run.md](2026-07-31-persist-the-log-replay-the-run.md)
makes the effect log the persisted form, and replay-persistence demonstrably works on rented
VMs (it is Temporal's architecture, on the JVM). The conclusion stands on the legs that
remain:

- **Multiplatform embedding** — decisive on its own. Klein embeds in JVM, JS, and native
  hosts as a plain Kotlin library with zero runtime dependencies; JVM bytecode is JVM-only,
  and "every host embeds a WASM engine" is a heavy ask against "every host links a
  ~600-line interpreter."
- **The hot tier must be walkable.** The replay debugger, traces, and what-if forking
  inspect the live machine's frames and slots — data a rented VM hides inside opaque host
  frames.
- **Metering** — a fuel counter in our own loop, versus bytecode instrumentation; and under
  log-replay, fuel doubles as the structural bound on replay cost.
- **Sandboxing** survives compilation in principle (Klein has no FFI to emit), but the
  interpreter makes it a non-question.

WASM may re-enter later, but only as a distribution channel *for our runtime* — the machine
compiled to WASM, not Klein compiled to WASM. Native compilation likewise only as an
optimization tier over the Core IR, applied where the machine is not suspending.
