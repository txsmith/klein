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
- the **pins**: each name the rule uses, at the revision it was compiled against;
- the **Core**, opaque, with the **lowerer version** that produced it;
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
  version the parser no longer reads is not something loading can recover from: it is a
  migration trigger, the migration toolkit's job (see [roadmap.md](../roadmap.md) §Migration
  toolkit).
- **The lowerer version**, on the Core. It changes when lowering produces different Core for the
  same source. A stored Core whose lowerer version is not the current one is discarded and
  re-derived.

## The checksum

The checksum guards the artifact's integrity. It is a hash over the whole artifact: the language
version, the source, the pins, the lowerer version, and the Core. An artifact is immutable, so a mismatch means the artifact is not what was written:
a damaged or partial write, or a copy that was altered. The stored Core is then not to be trusted,
because the machine runs Core unverified by decision (the
[no-load-time-verifier ADR](../decisions/2026-07-20-no-load-time-verifier.md)), and the checksum
is what stands in for that verifier.

The hash is computed from the artifact's contents, so it does not depend on how the artifact is
encoded: the same edition has the same checksum on every platform and in every encoding.

## Decoding

Decoding takes an encoded artifact and answers the edition, together with whether the stored
Core was used or the edition was re-derived, and if re-derived, why. Like compiling, it answers
diagnostics instead of an edition when re-derivation finds the source no longer checks (see
Errors). In order:

- The artifact is read; one that cannot be read is an error and yields nothing.
- The checksum is recomputed from the decoded contents. If it differs from the stored one, the
  stored Core is ignored and the edition is re-derived: reason **checksum mismatch**.
- Otherwise, if the Core's lowerer version is not the current one, the stored Core is ignored and
  the edition is re-derived: reason **lowerer changed**.
- Otherwise the stored Core is used as is.

A re-derived edition is a fresh compile from the recorded inputs, and the artifact it came from is
stale: its Core is out of date or damaged. The host should store the re-derived edition's
artifact in its place. Until it does, every decode re-derives again. Decoding never writes
anything itself.

Decoding needs the contract only on the re-derivation path. An intact, current artifact yields an
edition without compiling; its pins are still checked against the contract when the edition is
run, as for any edition.

## Re-derivation

Re-derivation compiles the recorded source against the recorded pins, not against a release.
The pins are exactly the names the source uses, each at one revision; together with everything
those declarations' signatures reach, they form a complete typing surface, the same one the run
uses to check answers. The source is parsed and checked against that surface, lowered, and its
pins are computed again: the names it uses, at the recorded revisions. A recorded pin the source
does not use is dropped, not an error. For an intact artifact the pins come out as recorded,
since they were computed from that source; re-derivation fails only when the contract has
changed underneath:

- A pin names a revision the contract no longer declares: thrown per pin, the same host error
  the run's pre-flight check throws.
- The source no longer checks against the pinned signatures, because a declaration was edited in
  place: the checker's diagnostics.

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
inputs: the source, possibly transformed, compiled against pins that may point at other
revisions. Compiling against pins is the same operation as re-derivation: the pins given may cover more
than the source uses, and the new edition pins exactly what it used. Moving a rule from `creditScore/1`
to `creditScore/2` is compiling its source against pins that say `creditScore/2`: the source is
checked against revision 2's signature, and either it fits or the diagnostics say what must
change. How the toolkit transforms source, and what the host keeps as the editable form of a
rule, is the migration toolkit's concern ([roadmap.md](../roadmap.md) §Migration toolkit), not
this spec's. The artifact records the source it was compiled from, verbatim, whatever produced
it.

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
  "pins": { "Customer": 2, "creditScore": 1 },
  "source": "score = creditScore(customer)\nif score > 600 then Approve else Decline",
  "core": "S0NPUgEAAAAG...",
  "checksum": "9a3f0c1e77b2d4a8"
}
```

Every part of the artifact is visible: the inputs as they are, the Core as an opaque
unit carrying its lowerer version, and the checksum. A binary encoding would carry exactly the
same contents and produce exactly the same checksum.

## Errors

Errors follow [host-integration.md](./host-integration.md) §Errors: a host error is thrown, a
diagnostic is returned.

Host errors, thrown, one per fault:

- **Unreadable**: the artifact cannot be read. Names what was expected and where.
- **Unknown pin**: a recorded pin names a revision the contract does not declare. One per pin,
  the same error the run's pre-flight check throws.

Diagnostics, returned beside no edition: re-derivation found that the source no longer checks
against the pinned signatures, because a declaration was edited in place. They carry spans into
the recorded source.

Nothing here is an outcome: decoding answers an edition, or diagnostics, or throws. A host that
gets a host error has an artifact that is damaged or one written against a contract that has
since dropped a revision; a host that gets diagnostics has a rule that needs its author.

## What the host owns

Klein owns the artifact's contents, the two versions, the checksum, decoding, re-derivation, and
the encodings. The host owns storage, keys, which edition a rule currently runs, the association
between runs and editions, and storing a re-derived edition in place of a stale artifact. The
library hands an edition over as an encoded artifact and takes it back the same way.

## Future work

- **Naming what changed.** A checksum mismatch says the artifact changed, not which part. If
  hosts want that, per-field hashes could say so; the single checksum is enough for integrity.
