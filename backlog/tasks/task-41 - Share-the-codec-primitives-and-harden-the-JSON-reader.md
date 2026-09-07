---
id: TASK-41
title: Share the codec primitives and harden the JSON reader
status: To Do
assignee: []
created_date: '2026-09-07 17:34'
labels:
  - host-boundary
dependencies: []
ordinal: 41000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Two parts, one branch.
1. Move the codec primitives out of the log codecs so the edition codecs can share them: ByteWriter and ByteReader from host/Encoding.kt into host/Bytes.kt; the Json tree, JsonReader, writeText, writeNumber, expectField, expectOnly from host/JsonEncoding.kt into host/JsonText.kt. All internal, no behavior change, every existing log test untouched and green. The primitives must not name a codec error type: ByteReader.readCount and readString currently call the log codec private reject; take the failure as a parameter (or an internal exception the codec translates) so UnreadableLog stays in Encoding.kt and a later UnreadableEdition can use the same reader.
2. Harden the JSON reader against untrusted input. Vendor JSONTestSuite (github.com/nst/JSONTestSuite, test_parsing/, MIT; include its license) under klein-lib/src/jvmTest/resources and add one JVM test that walks it: every y_ file parses and round-trips through our writer; every n_ file is rejected with UnreadableLog and nothing else (no stack overflow, no other exception); every i_ file either parses or is rejected, never crashes. Fix what it finds. Expected: a nesting depth limit that rejects instead of overflowing the stack; lone surrogates in \u escapes rejected; numbers outside double range (1e400) rejected rather than stored as infinity. Keep the reader hand-rolled; no runtime dependency on a JSON library.
Done when: ./gradlew :klein-lib:allTests -x :klein-lib:jsBrowserTest is green, the log codec files contain only log-specific code, and the suite test passes.
<!-- SECTION:DESCRIPTION:END -->
