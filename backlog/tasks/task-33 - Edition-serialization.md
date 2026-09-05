---
id: TASK-33
title: Edition serialization
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
updated_date: '2026-09-05 14:51'
labels:
  - host-boundary
dependencies: []
ordinal: 33000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The rules are in docs/spec/edition.md: an edition at rest is an immutable build artifact (compiled output + verbatim record of its inputs): source, language version, pins, the Core with its lowerer version, an integrity checksum over the whole. The release is NOT in the artifact: it is author metadata the host keeps beside the rule. Two versions and their reactions (language: migration trigger; lowerer: discard and re-derive); decoding with its two re-derivation reasons; re-derivation compiles the source against the recorded pins (unused pins are dropped, undeclared pins are errors); migrations produce new editions through the same compile-against-pins path and never touch an artifact. Encodings are the embedding API concern, not the spec.
Design notes with types and signatures: edition-serialization-design.md in the worktree root (untracked). One compilation, compile(source, pins); compileRule delegates to it with the release exposed names.
First encoding: JSON for inspection (source and pins readable), the Core as an opaque binary blob in base64 opening with a magic and the lowerer version, checksum as hex. Strict reading like the log codec.
<!-- SECTION:DESCRIPTION:END -->
