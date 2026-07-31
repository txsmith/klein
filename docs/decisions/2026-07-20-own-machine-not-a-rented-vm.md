# Own machine, not a rented VM

**Status:** Current · **Date:** 2026-07-20

Klein programs execute on our own CESK machine over our own Core IR. Compiling to an existing
VM — JVM bytecode, WASM, Lua — was considered and rejected.

The reason is that Klein's differentiators all live *below* the bytecode line of any existing
VM: suspension-as-data (a paused run is a value the host can store and resume), deterministic
replay, the migration ladder for long-suspended runs, step metering, and sandboxing. On a
rented VM each of those becomes a fight against the platform — continuations aren't data on
the JVM, fuel isn't a WASM primitive, and none of them serialize. Multiplatform embedding
(Kotlin native/JS targets) additionally rules out JVM bytecode outright.

WASM may re-enter later, but only as a distribution channel *for our runtime* — the machine
compiled to WASM, not Klein compiled to WASM. Native compilation likewise only as an
optimization tier over the Core IR, applied where the machine is not suspending.
