---
id: TASK-33
title: Edition serialization
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
updated_date: '2026-09-07 18:11'
labels:
  - host-boundary
dependencies: []
ordinal: 33000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The rules are in docs/spec/edition.md: an edition at rest is an immutable build artifact (compiled output + verbatim record of its inputs): source, language version, pins, the Core with its compiler version, an integrity checksum over the whole. The release is NOT in the artifact: it is author metadata the host keeps beside the rule. Two versions and their reactions (language: migration trigger; compiler: discard and re-derive); decoding with its two re-derivation reasons; re-derivation compiles the source against the recorded pins (unused pins are dropped, undeclared pins are errors); migrations produce new editions through the same compile-against-pins path and never touch an artifact. Encodings are the embedding API concern, not the spec.
Design notes with types, signatures, and the order of work: edition-serialization-design.md in the worktree root (untracked). One compilation, compile(source, pins); compileRule delegates to it with the release exposed names. LanguageVersion and CompilerVersion are value classes beside RevisionNumber. The Core blob comes last; the store-and-load loop works without it first.
First encoding: JSON for inspection (source and pins readable), the Core as an opaque binary blob in base64 whose first byte is the compiler version (no magic: the checksum vouches for the bytes before the blob is opened), checksum as hex. Strict reading like the log codec.
<!-- SECTION:DESCRIPTION:END -->
