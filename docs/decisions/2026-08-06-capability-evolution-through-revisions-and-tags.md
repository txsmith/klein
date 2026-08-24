# Capability Evolution Through Revisions and Tags

**Status**: Accepted, 2026-08-06. **Partly superseded, 2026-08-08**: tags and `expose` were
replaced by numbered releases — see
[2026-08-08-rule-vocabulary-through-linear-releases.md](./2026-08-08-rule-vocabulary-through-linear-releases.md).
The rest of this ADR stands: permanent `/N` revisions, invariant type definitions, recompilation
as the compatibility verdict, and optimistic removal. Everything below is preserved as written.

The living rules are in [spec/host-integration.md](../spec/host-integration.md) and
[spec/contracts.md](../spec/contracts.md); this ADR records the decision and what was rejected on
the way to it.

## The decision

A host declares capabilities in a contract file. Declarations carry permanent `/N` revision
markers, and `expose` lines aim rule-facing names (tags) at declared revisions:

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer/1): Num
fun creditScore/2(c: Customer/2): Num

expose Customer/2 as Customer
expose creditScore/2 as creditScore
expose Customer/1 as Customer@legacy
```

The load-bearing choices:

- **Revisions are permanent, monotonic, contract-only syntax.** Renumbering after a drain is
  indistinguishable from a rollback, so labels never move. Rules never see `/N`.
- **Tags are the entire rule vocabulary.** Re-aiming a tag is the migration act and the claim
  that meaning was preserved; not moving it is how a semantic change is declared. A pointer may
  move precisely because what it points at never does.
- **Type definitions are invariant**; only a capability's signature, over unmoved types, may
  change in place.
- **Recompilation is the compatibility verdict.** A rule's unchanged source is compiled against
  the evolved contract; a clean check *is* the new edition. The pinned signature hash only
  detects that something changed and enforces the revision discipline.
- **Removal is optimistic.** Nothing bars fresh runs during a drain; a wrong removal strands
  parked runs rather than refusing events, and stranded runs are recoverable because revision
  numbers are permanent.

## Considered and rejected along the way

- **Environment-version lifecycle tables** (draft/active/retired rows): collapsed into
  reconciliation + per-instance serving checks; lifecycle states are now derived queries, not
  stored flags.
- **A whole-surface version as the unit of matching**: coupled rules to changes they never used;
  per-capability granularity via pins was strictly finer at no cost.
- **Usage lockfile / assumption export for offline CI**: its green duplicated the real check, its
  red couldn't localize; replaced by running the real check against a corpus snapshot where
  needed.
- **Per-definition content addressing** (Unison-style) for invalidation: everything it prunes, a
  member-wise diff of two small module versions already prunes; renames are better served by
  alias definitions.
- **Inferred principal env requirements** (SimpleSub at the boundary): dies at the first binding
  without full constraint inference, and bidirectional acceptance isn't a lattice condition
  anyway; the checker remains the only oracle.
- **Migrate-all-before-deploy as the only mode**: makes fleet deploys hostage to rule authors;
  kept only as one end of the per-change knob.
- **Semantic versioning numbers** on capabilities: the major/minor distinction is an unverifiable
  claim about impact. The only declared fact worth having is "the meaning changed", which an
  unmoved tag says, and the treatment of the old version is decided per change rather than
  encoded in a number.
- **Host-side revisions, with superseded signatures supplied at registration**: made ids
  human-facing — you would have to know the hash of a version whose signature had left the
  contract — and lost the name and number that drain reporting needs. Humans always say
  (name, revision).
- **Environment-scoped revisions — numbered snapshot files** (`001.klein`, `002.klein`, each a
  whole self-consistent surface): elegantly dissolves type pinning, since a file's `creditScore`
  means that file's `Customer`. Rejected because duplication scales with contract *size* rather
  than change size — a thousand-line contract copied to bump one capability, and diffs that show
  everything as changed. Per-declaration `/N` costs syntax and pays for itself in localized
  change.
- **Recycling revision numbers after a drain** (renumbering `/2` back to bare so numbers stay
  small): breaks reconciliation, which cannot distinguish "revision 2 was rolled back" from
  "revision 1 retired and 2 promoted" — both leave a single bare declaration. Numbers stay
  monotonic.
- **Identity by signature hash alone**: a hash disagrees on compatible changes and agrees on
  semantic ones, so it is neither necessary nor sufficient for compatibility. Kept as a change
  detector for pruning reconciliation work, not as identity.
- **Revision numbers in rule code** (`creditScore/2(...)` written by rule authors): sound —
  revisions are distinct nominal types and the checker keeps them coherent — but it exposes
  authors to plumbing, defaults bare names to the *oldest* revision forever, is grammatically
  ambiguous with division in expression position (`maxRetries/2`), and makes every migration a
  rule-source edit. Tags give rules version-free vocabulary while keeping revisions as the
  identity underneath.
- **Trailing markers on declarations** (`review` marking semantic changes; its inversion
  `mechanical` attesting safe ones): both put the attestation in permanent syntax on the
  declaration, far from the moment it matters. Replaced by exposure — *moving a tag* is the
  meaning-preserved attestation, made and code-reviewed at change time; *not moving it* is the
  semantic-change treatment. Same fail-safe polarity as `mechanical` (forgetting to act leaves
  rules on the served old revision), with no marker to forget.
- **Signature subtyping as the compatibility verdict**: it only ever predicted what a recompile
  would say, and unreliably — it cannot see compiled-in facts (constructor arity, match
  exhaustiveness), and type definitions admit no subtype question at all. Since minting an
  edition requires the compile anyway, the prediction saved nothing on the success path.
  Recompilation is the verdict; the hash prunes how often it runs.
- **Machine-authored rule versions** (reconciliation rewriting `/1` to `/2` in rule source to
  migrate it): raised "who authored this version" questions in the store and churned rule history
  with mechanical diffs. Tag re-pointing migrates rules with zero source edits, which is what
  made rule-side revision syntax unnecessary at all.
- **A retire flag** (a store-resident, dev-initiated bar on fresh runs for a draining revision,
  meant to make the drain counts provably monotone before removal): the flag lives in the store
  precisely so it binds every build, which makes it the one thing a rollback cannot roll back —
  an emergency rollback to old builds meets the bar on exactly the editions those builds can
  serve, and every affected rule refuses every fresh event until a human clears the flag
  mid-incident. Refusal loses events; stranding preserves work. Dropped for optimistic removal:
  births stop by themselves on a stable fleet, a wrong removal strands rather than refuses, the
  stranded-run alert names the victims, and permanent revision numbers make restoring the
  revision well-defined.
- **A store-resident expose map** (moving tags at runtime, without a deploy): faster policy
  changes, but the mapping can then disagree with the deployed contract — a tag pointing at a
  revision no host serves, or a binary rollback leaving the map aimed forward. In-file tags make
  those states unrepresentable and get code review of every move for free.
- **An advertisement protocol** (nodes publishing declared capability sets as a matching
  mechanism): vacuous as a concept — the host controls the whole serving decision, and the check
  is just "does this instance implement every revision the edition pins". Kept as a per-instance
  check, dropped as a term.
