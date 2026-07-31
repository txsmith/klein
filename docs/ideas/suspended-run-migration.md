# Migrating long-suspended runs

**Status:** Idea (position settled) · carved out of the Core IR plan

A rule program suspended for weeks may need to resume against a newer version of itself.
Migration is **host policy, not a Klein engine**: Klein's job is to provide the three
primitives that make every rung of the ladder buildable, and stop there.

The primitives (updated for
[persist-the-log-replay-the-run](../decisions/2026-07-31-persist-the-log-replay-the-run.md),
which makes replay the *primary* persistence mechanism, not a migration trick):

1. **A deterministic machine** — the suspended state is a pure function of the artifact,
   the extern vals, and the responses so far.
2. **The effect log** — every host interaction passes through the `Execution` API and is
   recorded; determinism makes the log a complete account of a run.
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

See also the host interface evolution notes (on the `host-interop` branch) for the adjacent
question (the *host* changed, not the program).
