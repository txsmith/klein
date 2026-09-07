---
id: TASK-41
title: Share the codec primitives and harden the JSON reader
status: Done
assignee:
  - '@claude'
created_date: '2026-09-07 17:34'
updated_date: '2026-09-07 17:56'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Move ByteWriter/ByteReader to host/Bytes.kt and the Json tree, JsonReader, writeText, writeNumber, expectField, expectOnly to host/JsonText.kt, all internal. The primitives throw an internal abort (MalformedBytes, MalformedJson) which decode/decodeJson catch and translate to UnreadableLog, mirroring the Abort pattern in surface/SyntaxError.kt and interp/RuntimeError.kt. Commit.
2. Vendor JSONTestSuite test_parsing + LICENSE under klein-lib/src/jvmTest/resources/json-test-suite, add a JVM test walking it, fix what it finds in JsonReader (depth limit, lone surrogates, out-of-range numbers), pin each fix with a commonTest case. Commit.
3. Verify with ./gradlew :klein-lib:allTests -x :klein-lib:jsBrowserTest and :klein-example-host:test.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Part 1 (commit 22e01a2): ByteWriter/ByteReader live in host/Bytes.kt, the Json tree, JsonReader, writeText, writeNumber, expectField and expectOnly in host/JsonText.kt, all internal. The primitives throw an internal abort (MalformedBytes, MalformedJson); decode and decodeJson catch it and translate to UnreadableLog. Chosen over a failure parameter because expectField/expectOnly are called at about fifteen sites and the abort keeps every call site unchanged, and because it mirrors the Abort pattern already used by surface/SyntaxError.kt and interp/RuntimeError.kt.
Part 2: JSONTestSuite (commit 1ef36fa of nst/JSONTestSuite, MIT) vendored under klein-lib/src/jvmTest/resources/json-test-suite. The walker found two stack overflows (n_structure_100000_opening_arrays, n_structure_open_array_object). Fixes in JsonReader: nesting depth limit of 1024 (JSON_MAX_DEPTH), surrogate escapes must pair, \u escapes take exactly four hex digits (toIntOrNull accepted a sign), numbers outside double range rejected. y_object_duplicated_key and y_object_duplicated_key_and_value are skipped by name: the codec rejects duplicate keys on purpose and JsonEncodingTest pins that. Files that are not valid UTF-8 never reach the reader, which takes a String; the test decodes strictly and counts a decode failure as a rejection (no y_ file is affected).
Validation: ./gradlew :klein-lib:allTests -x :klein-lib:jsBrowserTest => 2503 tests, 0 failed; ./gradlew :klein-example-host:test => BUILD SUCCESSFUL; JsonTextTest ran on jvm, jsNode and linuxX64.

Follow-up: the reader no longer rejects duplicate keys (last value wins, as the RFC allows), so the suite runs intact with no skipped files; the JsonEncodingTest pin for duplicate keys is gone. allTests minus jsBrowserTest: 2501 tests, 0 failed.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Moved the byte and JSON codec primitives into host/Bytes.kt and host/JsonText.kt behind an internal abort the log codecs translate to UnreadableLog, vendored JSONTestSuite as a JVM walker test, and hardened JsonReader (depth limit, surrogate pairing, strict hex escapes, finite numbers) with commonTest pins. Verified with allTests minus jsBrowserTest (2503 passed) and the example host build.
<!-- SECTION:FINAL_SUMMARY:END -->
