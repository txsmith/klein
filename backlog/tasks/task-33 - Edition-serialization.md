---
id: TASK-33
title: Edition serialization
status: Done
assignee: []
created_date: '2026-09-04 12:19'
updated_date: '2026-09-22 11:09'
labels:
  - host-boundary
dependencies: []
ordinal: 33000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The rules are in docs/spec/edition.md: an edition at rest is an immutable build artifact (compiled output + verbatim record of its inputs): source and language version, pins (every declaration the edition depends on, each at its revision with a hash of the declaration), the Core with its compiler version, an integrity checksum over the whole. The release is NOT in the artifact: it is author metadata the host keeps beside the rule. Decoding never compiles: it returns the edition fresh, or the recorded inputs stale with a reason (checksum mismatch, language changed, compiler changed, declaration changed); re-derivation is the host's next step and compiles the source against the recorded pins (unused pins are dropped, undeclared pins are errors); migrations produce new editions through the same compile-against-pins path and never touch an artifact. Encodings are the embedding API concern, not the spec.
Order of work: one compilation, compile(source, pins); compileRule delegates to it with the release exposed names. LanguageVersion and CompilerVersion are value classes beside RevisionNumber. The Core blob comes last; the store-and-load loop works without it first.
First encoding: JSON for inspection (source and pins readable), the Core as an opaque binary blob in base64 whose first byte is the compiler version (no magic: the checksum vouches for the bytes before the blob is opened), checksum as hex. Strict reading like the log codec.
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented on the edition-serialization branch (PR #31): spec/edition.md, compileRule(source, pins), immutable handler registry, the JSON artifact with checksum, the binary Core blob under CompilerVersion, decoding as Fresh or Stale.
<!-- SECTION:FINAL_SUMMARY:END -->
