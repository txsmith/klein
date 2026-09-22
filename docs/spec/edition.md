# Editions at Rest

How an edition is stored, loaded, and rebuilt. An edition is a compiled rule: its Core and its pins (see
[host-integration.md](./host-integration.md) §Edition). At rest it is a build artifact: the
compiled output together with a verbatim record of the inputs it was compiled from. Artifacts are
immutable. Nobody edits one; a change to a rule produces a new edition. The persistence model
behind this is the
[source-is-truth ADR](../decisions/2026-07-20-source-is-truth-ir-is-a-cache.md): the source is
truth, the stored Core is a cache, and a cache that cannot be trusted is discarded and re-derived
from the source, never migrated.

The test suites enforce everything here. Storage itself (where artifacts live, how they are
keyed) is the host's, as it is for the effect log.

## What is stored

An artifact holds:

- the rule **source**, verbatim, and the **language version** it was written in;
- the **pins**: every declaration the edition depends on, the names the source wrote and every
  declaration their signatures reach, each at the revision it was compiled against, with a hash
  of that declaration as it was compiled against (a capability's signature, a type's
  definition);
- the **Core**, opaque, with the **compiler version** that produced it;
- a **checksum** over the whole.

The source, the language version, and the pins are the inputs: everything the loader needs to
rebuild the edition. The Core is the output, stored so that loading does not have to compile.
Nothing else is in an artifact. In particular the release the author compiled against is not:
it is author metadata, kept by the host beside the rule with the author and the time, and the
editor and the reconciler read it from there.

The Core stays opaque. A host reads source and pins for inspection; it never reads the Core, and
the Core's layout is not part of this spec.

## Two versions

The artifact carries two versions, each answered differently:

- **The language version**, on the source. The source cannot be re-derived from anything, so a
  version the parser no longer reads is not something loading can recover from: decoding
  returns the artifact stale, reason **language changed**, and rewriting the source is the
  migration toolkit's job (see [roadmap.md](../roadmap.md) §Migration toolkit).
- **The compiler version**, on the Core. It changes when lowering produces different Core for the
  same source. A stored Core whose compiler version is not the current one is discarded and
  re-derived.

## The checksum

The checksum ties the stored Core to the inputs it was compiled from. It is a hash over the whole
artifact: the language version, the source, the pins, the compiler version, and the Core. A
mismatch means the Core and the inputs no longer agree. Because the source is truth, the response
is to re-derive from the recorded inputs; a re-derivation that fails is the sign that something
was actually damaged. The stored Core is never run after a mismatch.

The hash is computed from the artifact's contents, so it does not depend on how the artifact is
encoded: the same edition has the same checksum on every platform and in every encoding.

## The pin hash

Each pin carries a hash of the declaration it was compiled against, so that an edit to a
signature or a type definition at an unchanged revision is visible to whatever reads the pins.
The hash detects a change. It never decides whether the change was acceptable: recompiling the
rule decides that. So it is not a cryptographic hash, and a collision costs one recompile that
was skipped.

The hash is computed from the checked declaration, walked as a tree, never from printed text.
What is in it:

- for a capability, its whole type: each parameter's name and type, in order, and the result;
  for a value capability, its type;
- for a type, its parameters and every constructor's name and fields, whatever the rule uses;
- every type the declaration reaches by name, with the revision it reaches it at, but not that
  type's own definition: a change there moves that type's own pin.

Parameter names are in the hash even though a call is positional today. The tilde operator
already turns a function's parameters into record fields by name (see
[calling-conventions.md](../calling-conventions.md) §The Tilde Operator), and calling by name is
where that leads. When it arrives, a renamed parameter is a changed signature, and the hash must
already say so rather than be widened then.

What is not in it: the declaration's own revision, which the pin records beside the hash; the
order of record fields and of constructors; the names of type variables, which are numbered by
first appearance. So `fun pick(x: 'A, y: 'A): 'A` and the same declaration written with `'T`
hash the same, while renaming a parameter or adding a constructor does not.

## Decoding

Decoding takes an encoded artifact and the contract, and answers one of two things: the
edition with its stored Core; or the recorded inputs without Core, with the reason the
stored Core is stale and can not be used. Re-compiling a stale edition is the host's
next step and may fail when the source no longer checks (see Errors). In order:

- The artifact is read; one that cannot be read is an error and yields nothing.
- The reader recomputes the checksum from what it read, before the Core is opened. If it differs
  from the stored one, the artifact is stale: reason **checksum mismatch**.
- Otherwise, if the source's language version is one the parser no longer reads, the artifact is
  stale: reason **language changed**. Re-derivation cannot follow; the source must be migrated
  first.
- Otherwise, if the Core's compiler version is not the current one, the artifact is stale: reason
  **compiler changed**.
- Otherwise each recorded pin is looked up in the contract at its revision. A pin the contract
  does not declare makes the artifact stale, reason **unknown pins**: the reason names each
  such pin, and the inputs returned leave them out, since nothing can compile against them. A
  pin whose recorded hash differs from the contract's declaration at that revision means a
  signature or a type was edited in place: the artifact is stale, reason **declaration
  changed**.
- Otherwise the artifact is intact, and the edition is returned with the stored Core as is.

A stale artifact still yields its recorded inputs, so a rule whose source no longer checks can
still be read, shown, and migrated. Whatever the reason, the source stays readable: the rule
whose pins the contract no longer knows is the one that most needs migrating. A re-derived edition is a fresh compile from those
inputs, and the artifact it came from is out of date or damaged. The host should store the
re-derived edition's artifact in its place. Until it does, every decode answers stale again.
Decoding never writes anything itself.

Decoding resolves the recorded pins into the edition's surface: the types and signatures the
rule was checked against, as the contract declares them at the pinned revisions. Compiling
builds the same surface from the pins it computes. The edition carries it, and the run reads it
from there: the run resolves nothing and asks the contract for nothing about the pins. An intact
artifact's edition is ready to run without compiling, as any edition is.

## Re-derivation

When the stored Core cannot be used, the host compiles the recorded source against the recorded
pins. This is the same as a fresh compilation except it uses the recorded pins instead of a pre-determined release. The result is a new edition, with its pins computed again
from what the source reaches; a recorded pin the source no longer reaches is dropped. Two things
can go wrong, both because the contract changed since the artifact was written:

- A pin names a revision the contract no longer declares: thrown per pin. Decoding already
  named such pins in its reason, so this is a host re-deriving where it should migrate.
- A declaration was edited in place at a pinned revision: the source is compiled against it as
  it now stands. Either the new edition carries the new hash, or the diagnostics say why the
  source no longer fits.

Because no artifact records a release and re-derivation never reads one, removing a release from a contract changes nothing
about any existing edition. Loading, running, resuming a parked run, and re-deriving all go
through the pins. Removing a release is a compile-time act: it stops new editions from being
compiled against it, so the next time an author edits a rule on that release, they must choose
another. This is what keeps releases and revisions on their separate lifecycles: a release can go
as soon as no author needs it, while a revision must stay until nothing pins it.

This rests on one invariant: everything a release contributes to compilation is captured in the
artifact. Today a release contributes only the name-to-revision surface, which the pins capture.
A feature that lets a release shape compilation in another way must record that contribution in
the artifact too, or re-derivation will silently produce a different program.

## Migration

An artifact is never migrated. A migration produces a **new edition** from an old one's recorded
source, possibly transformed, compiled against a release: the release the rule is moving to. A
release is always the goal, because a rule is migrated when the release it was on is retired,
and the pins of the new edition are computed from that release like any other compile. Moving a
rule from `creditScore/1` to `creditScore/2` is compiling its source against a release that
points at `creditScore/2`: the source is checked against revision 2's signature, and either it
fits or the diagnostics say what must change. Compiling against recorded pins, with no release,
is re-derivation only. How the toolkit transforms source, and what the host keeps as the editable
form of a rule, is the migration toolkit's concern ([roadmap.md](../roadmap.md) §Migration
toolkit), not this spec's. The artifact records the source it was compiled from, verbatim,
whatever produced it.

## Encodings

An artifact round-trips through an encoding: encoding then decoding yields the same edition, with
the same checksum. Every encoding carries its own version stamp; bytes whose encoding or version
cannot be read are an error naming what was wrong, never a re-derivation, since nothing about
the artifact is known until it is read. An encoding may keep the source and pins inspectable
without tooling; the Core it carries as an opaque unit. Which encodings exist, and their layout,
is the embedding API's, not this spec's.

As an illustration only, not a required or preferred form, a JSON encoding of the artifact above
might read:

```json
{
  "format": "klein-edition",
  "version": 1,
  "language": 1,
  "pins": {
    "Customer": { "revision": 2, "hash": "3c8d1f0a9b7e6d54" },
    "creditScore": { "revision": 1, "hash": "b41e0c9d2a7f5e63" }
  },
  "source": "score = creditScore(customer)\nif score > 600 then Approve else Decline",
  "core": "AQgAAAABAA...",
  "checksum": "9a3f0c1e77b2d4a8"
}
```

Every part of the artifact is visible: the inputs as they are, the Core as an opaque
unit carrying its compiler version, and the checksum. A binary encoding would carry exactly the
same contents and produce exactly the same checksum.

## Errors

Errors follow [host-integration.md](./host-integration.md) §Errors: a host error is thrown, a
diagnostic is returned.

Host errors, thrown, one per fault:

- **Unreadable**: the artifact cannot be read. Names what was expected and where.
- **Unknown pin**: compiling against recorded pins, and a pin names a revision the contract
  does not declare. One per pin, from the one step that resolves pins. Decoding does not throw
  it, and a run never does: an edition exists only because the contract compiled or decoded it,
  and its pins were resolved then.

Whatever decoding can explain is not an error: it returns the artifact stale with the reason,
as §Decoding lists them.

Diagnostics, returned beside no edition: as from any compile. With matching declarations the
recorded source checks as it did when the artifact was written, so diagnostics from
re-derivation mean the compiler itself changed what it accepts, or a declaration was edited in
place in a way the source does not fit. They carry spans into the recorded source.

Nothing here is an outcome: decoding returns an edition, or stale inputs, or throws. A host that
gets a host error has an artifact that is damaged, or re-derived where it should have migrated;
a host that gets diagnostics has a rule that needs its author.

## What the host owns

Klein owns the artifact's contents, the two versions, the checksum, decoding, re-derivation, and
the encodings. The host owns storage, keys, which edition a rule currently runs, the association
between runs and editions, and storing a re-derived edition in place of a stale artifact. The
library hands an edition over as an encoded artifact and takes it back the same way.

## Future work

- **Naming what changed.** A checksum mismatch says the artifact changed, not which part. If
  hosts want that, per-field hashes could say so; the single checksum is enough for integrity.
