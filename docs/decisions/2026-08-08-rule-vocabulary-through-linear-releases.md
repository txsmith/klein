# Rule Vocabulary Through Linear Releases

**Status**: Accepted, 2026-08-08. Supersedes the exposure half of
[2026-08-06-capability-evolution-through-revisions-and-tags.md](./2026-08-06-capability-evolution-through-revisions-and-tags.md)
— tags and `expose` are replaced by releases. Everything else in that ADR stands: permanent `/N`
revisions, invariant type definitions, recompilation as the compatibility verdict, optimistic
removal. The living rules are in [spec/contracts.md](../spec/contracts.md) and
[spec/host-integration.md](../spec/host-integration.md); this ADR records the decision and what was
rejected on the way to it.

## The decision

A contract declares capabilities with permanent `/N` revisions, as before. What rules can see is
decided by **releases**: numbered blocks of pointers, each aiming a plain name at one declared
revision.

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer/1): Num
fun creditScore/2(c: Customer/2): Num

release 1
  Customer
  creditScore

release 2
  Customer/2
  creditScore/2
```

The load-bearing choices:

- **A rule takes its whole vocabulary from one release.** Not a per-name choice a rule makes call
  by call. Inside a release every name means exactly one revision, so a rule can never meet two
  versions of one type, every name is spelled plainly, and no revision marker can reach a rule's
  source or its diagnostics.
- **Releases are numbered, and the numbers only increase.** The number is both the identity and
  the order, so "is this rule behind" is a comparison anywhere an edition is read, without
  consulting the contract.
- **A block states only what its release changes**, inheriting the rest from the release before
  it. Retiring a release folds it into its successor, which is a single step no matter how many
  releases exist.
- **Two acts carry the whole migration vocabulary.** *Editing* a release changes what the rules
  already on it will compile against; nobody moves, and each rule picks the change up at its next
  recompile. *Appending* a release creates something nobody is on until an author selects it. A
  mechanical change is an edit; a change no checker can see is an append.
- **No marker declares which kind of change it is**, because the act already does. There is
  nothing to forget, and forgetting to act leaves rules on what they had rather than moving them.
- **Any block may be edited, not only the newest.** An edit reaches every later release that did
  not name that capability itself. This is what lets an urgent repair land on the rules that need
  it — usually the ones on an older release — without dragging them through an unrelated
  migration first.
- **A rule's release travels with the compile request, not in its source.** Chosen once per rule
  by a person, reused by every recompile. Selecting a different one is the only thing that ever
  moves a rule, which keeps mechanical migration free of source edits.
- **Retiring a release is unconditional.** Deleting a block stops new compiles against it and
  nothing else, since everything already running dispatches through pinned revisions. It begins a
  drain rather than waiting for one.

## Considered and rejected along the way

- **Keeping per-name tags** (`Customer@legacy` in rule source, each name migrated separately): the
  extra freedom is what forces qualified spellings into rule text, printing precedence when
  several tags reach one revision, qualified constructor names, and diagnostics that must explain
  which version of a type a rule met. One release per rule makes all of that unrepresentable
  rather than handled. The migration signal tags carried for free is recovered by the edit-versus-
  append distinction.
- **Merging declarations into release blocks** (no separate declaration list; a new version of a
  capability appears by being written inside a release): collapses two numbering systems into one
  and removes `/N` from the language, which is genuinely tempting. Rejected because it destroys
  the middle tier of change. With declarations separate there are three acts of increasing risk —
  edit a declaration in place, re-point a release, append a release — and the middle one does real
  migration work at zero risk to parked runs, because they dispatch through pinned revisions
  rather than through releases. Merged, there is nothing to re-point at, leaving only the risky
  edit and the append.
- **Absolute blocks, each listing everything a release exposes**: makes a release readable in one
  place and makes omission mean removal, at the cost of duplication scaling with contract size
  rather than change size — the same objection that sank snapshot files in the previous ADR. Delta
  blocks keep the diff the size of the change; the fold on retirement keeps the chain short enough
  that reading a release stays cheap.
- **Arbitrary labels instead of numbers** (`release experian-migration`): workable, since blocks
  are only ever appended and file order could supply the ordering. Rejected because every ordering
  question then has to consult the contract — including from editions and reconcile reports, where
  the contract isn't at hand — and because meaningful names drift back toward the named-tag model,
  inviting reuse and confusing alphabetical order with time order.
- **A marker declaring a release safe to migrate automatically** (`release 8 auto reconcile`):
  needed only while both kinds of change looked alike. Once editing and appending became distinct
  acts, the marker restated what the act already said, and any marker can be forgotten.
- **Major and minor release numbers**: the same single fact in a costume, already rejected in the
  previous ADR as semantic versioning. It cannot default to the safe side, since half a version
  number can't be omitted, and it invites readers to infer compatibility promises that nothing
  verifies.
- **`add` and `upgrade` markers on block entries**: derivable, since the previous release already
  says whether a name was there and at which revision. Only `remove` carries information the
  checker cannot infer, because under delta blocks silence means unchanged. Kept `remove`, dropped
  the other two.
- **Allowing only the newest block to be edited**: intended to protect settled releases, but the
  protection was illusory — a rule's release never changes on its own, so editing an older release
  simply changes what the rules already there will compile against, which is exactly what a repair
  should do. The rule's real cost was severe: a bug fix reachable only from the newest release
  would force every affected author through a review-gated migration to receive it.
- **A `using` header in rule source naming the release**: puts the choice in the rule's own text,
  versioned and reviewed with it. Rejected because it makes mechanical migration a mass source
  edit across every rule, giving up the zero-source-edit property that the whole design exists to
  provide.
- **Silence in a block meaning removal**: only coherent with absolute blocks. Under delta blocks
  silence has to mean unchanged, so removal is written out.
- **An explicit predecessor chain** (`release mar2026 based on feb2026`): decouples ordering from a
  counter and so allows arbitrary names. It does not solve what motivated it — the permanence of
  the numbering comes from editions pinning a release forever, not from the numbers themselves, so
  a name is no more recyclable than a number once something has compiled against it. It costs a
  chain the checker must validate instead of two integers compared.
