---
id: TASK-33
title: Edition serialization
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
updated_date: '2026-09-05 09:11'
labels:
  - host-boundary
dependencies: []
ordinal: 33000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The rules are in docs/spec/edition.md: an edition at rest is an immutable build artifact (compiled output + verbatim record of its inputs): source, language version, pins, the Core with its lowerer version, an integrity checksum over the whole. The release is NOT in the artifact: it is author metadata the host keeps beside the rule. Two versions and their reactions (language: migration trigger; lowerer: discard and re-derive); decoding with its two re-derivation reasons; re-derivation through the pin surface with the pin fixpoint; migrations produce new editions through the same compile-against-pins path and never touch an artifact. Encodings are the embedding API concern, not the spec.
First encoding: JSON for inspection (source and pins readable), the Core as an opaque binary blob in base64 opening with a magic and the lowerer version, checksum as hex. Strict reading like the log codec.
API: Edition becomes core + pins + source (the release field goes; compileRule still takes a release, it just does not record it). Edition.toJson(); Environment.decodeEdition(text): EditionDecodeResult(edition, rederived: Rederivation?) with enum Rederivation { LowererChanged, ChecksumMismatch }. Unreadable text or blob throws KleinException(UnreadableEdition).
Files: Language VERSION constant (surface); check/contract/Edition (+ source, - release); EnvironmentContract.recompile (internal) + per-pin unservable errors; host/CoreEncoding (binary Core codec with magic + version, reusing ByteWriter/ByteReader from Encoding.kt made internal); host/Base64 (or kotlin.io.encoding.Base64); host/JsonText (shared JSON reader/writer extracted from JsonEncoding); host/EditionJson (toJson, decode, checksum FNV-1a 64 over the contents, UnreadableEdition); host/Environment.decodeEdition. Tests written against the spec: CoreEncodingTest (every node kind round-trips, truncated prefixes unreadable), EditionJsonTest (round trip, hand-written artifact, every rejection, checksum mismatch and lowerer change re-derive with the reason, checksum identical across reformatting, damaged + unrecompilable errors), RecompileTest (fixpoint holds for an intact artifact, each contract-drift failure per name, release removed still decodes).
<!-- SECTION:DESCRIPTION:END -->
