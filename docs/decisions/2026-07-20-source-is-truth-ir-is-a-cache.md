# Source is truth; stored IR is a cache

**Status:** Current · **Date:** 2026-07-20

Persisted programs store source + compiled Core IR + a lowerer-version stamp + a checksum.
The IR is never migrated: a version mismatch *or* a failed checksum both collapse to one
response — re-derive from source. That is the entire integrity story: one mechanism (source
is truth), two cheap triggers.

Consequences:

- There is no IR schema-migration machinery to write, test, or trust. Upgrading the lowerer
  invalidates stored IR wholesale and recompilation is the recovery path — acceptable
  because checking + lowering run once per save, not per execution.
- The checksum subsumes a load-time verifier for the corruption case (see
  [2026-07-20-no-load-time-verifier.md](2026-07-20-no-load-time-verifier.md)).
- Deferred until the host-interface design lands: an interface fingerprint as a third
  trigger with the same handler (see [ideas/host-interface-evolution.md](../ideas/host-interface-evolution.md)).
- The same principle now governs *runs* as governs programs: the effect log is truth and
  stored traces/snapshots are disposable caches — see
  [2026-07-31-persist-the-log-replay-the-run.md](2026-07-31-persist-the-log-replay-the-run.md).
