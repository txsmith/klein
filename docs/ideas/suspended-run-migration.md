# Migrating long-suspended runs

**Status:** Idea (position settled) · carved out of the Core IR plan

A rule program suspended for weeks may need to resume against a newer version of itself.
Migration is **host policy, not a Klein engine**: Klein's job is to provide the three
primitives that make every rung of the ladder buildable, and stop there.

The primitives:

1. **Serializable state** — the CESK machine's suspended states are plain data.
2. **Effect transcripts** — every host interaction passes through the `Execution` API and
   can be recorded; determinism makes a transcript a complete account of a run.
3. **Version-stamped IR with stable identity** — a stored program knows exactly which
   compiled artifact a suspension belongs to.

The host-side ladder, cheapest first:

- **Pin** — resume on the old artifact; versions coexist.
- **Replay-based migration** — re-run the new version against the recorded transcript;
  purity makes divergence *exact* (the first differing host call identifies precisely where
  the versions disagree).
- **IR diffing** — prove a local edit doesn't move any suspension point, then rebind.
- **Authored transforms** — Erlang `code_change`-style hand-written state migrations, the
  last resort.

See also [host-interface-evolution.md](host-interface-evolution.md) for the adjacent
question (the *host* changed, not the program) and the log-persistence rethink in
[performance-debt.md](../performance-debt.md), which makes replay the primary persistence
mechanism rather than a migration trick.
