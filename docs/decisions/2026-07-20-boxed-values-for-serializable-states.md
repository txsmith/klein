# Boxed values are the price of serializable states

**Status:** Current · **Date:** 2026-07-20

Every runtime `Value` is a heap object — `VNum` wraps a `double`, so `a + b + c` boxes the
intermediate only to unbox it again — and machine frames are plain data dispatched by type,
not function pointers. Both costs are paid on purpose: a uniform, walkable `Value`
representation and inspectable data frames are what make suspension-as-data, snapshots, and
replay work. "States are data" is the product; boxing is its price.

The same constraint rules out each fast escape:

- **Value classes** unbox only in monomorphic slots; every slot here is `Value`-typed, so
  they buy nothing.
- **NaN-boxing** needs memory-layout control that portable Kotlin Multiplatform doesn't give.
- **Threaded / closure-passing dispatch** (a function pointer per node) cannot be serialized —
  the same reason the coroutine-based machine was rejected: coroutine frames are opaque to
  the host, and an opaque frame can't be persisted, inspected, or replayed.

Unboxed representations and superinstructions belong to a future native/optimizer tier that
trades serializability for speed and runs only where the machine isn't suspending. The
concrete debt entries and their fixes live in [performance-debt.md](../performance-debt.md).
