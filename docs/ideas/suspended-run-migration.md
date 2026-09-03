# Migrating long-suspended runs

**Status:** Idea (position settled) · carved out of the Core IR plan

A rule program suspended for weeks may need to resume against a newer version of itself.
Migration is **host policy, not a Klein engine**: Klein's job is to provide the primitives that
make every rung of the ladder buildable, and stop there. The decision record, including why no
key scheme makes migration automatic and the toolkit a migration function is written against, is
[replay-is-ordinal-migration-is-host-policy](../decisions/2026-08-26-replay-is-ordinal-migration-is-host-policy.md).

The primitives, in current vocabulary:

1. **A deterministic machine** — a suspended run's state is a pure function of its edition and
   its effect log.
2. **The effect log** — every host answer is recorded ([spec/effect-log.md](../spec/effect-log.md));
   determinism makes the log a complete account of a run.
3. **The edition** — source, release, and pin map, so a log knows exactly what it replays
   against.

The host-side ladder, cheapest first:

- **Pin** — resume on the old edition; versions coexist.
- **Replay-based migration** — re-run the new version against the log through the toolkit.
  Divergence is exact, and detection was never the hard part; the disposition of unmatched
  questions is, and that is the migration author's judgment (see the replay-order ADR).
- **Authored transforms** — Erlang `code_change`-style hand-written state migrations, the
  last resort.

The adjacent question — the *host* changed, not the program — is
[spec/contracts.md](../spec/contracts.md) and [spec/host-integration.md](../spec/host-integration.md).
