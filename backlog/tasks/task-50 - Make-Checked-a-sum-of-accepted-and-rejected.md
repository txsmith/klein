---
id: TASK-50
title: Make Checked a sum of accepted and rejected
status: Done
assignee:
  - '@claude'
created_date: '2026-09-24 13:31'
updated_date: '2026-09-24 14:01'
labels:
  - host-boundary
dependencies: []
ordinal: 48000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Checked today is one class holding an output and a list of diagnostics side by side. A caller has to look at the diagnostics to know whether the output means anything, and then force the output as not null; the CLI alone does this about ten times. In the JavaScript binding (klein-js, on the playground branch) the same shape is a real trap: an answer that evaluates to Klein's null gives a null output, the same as a failed compile, so a host that tests the output reads an error as a language-level null. The rule wanted is that there is no way at all to treat an error as a value. The checker does need a type next to its errors internally, but it already has its own result type for that; public Checked is only built from it at the edge, so nothing public needs a value on the failure side. Once this lands, the binding mirrors it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Checked is a sealed type with two cases: an accepted case holding a value and no diagnostics, and a rejected case holding one or more diagnostics and no value
- [x] #2 Every public function that returns Checked (tokenize, parse, check, lower, execute, the contract's check, compileRule and compileValue) returns one of the two cases
- [x] #3 The checker keeps a type beside its errors internally; only the public boundary changes
- [x] #4 Callers in the CLI, the example host, the benchmarks and the tests no longer force an output as not null after checking diagnostics
- [x] #5 The specs and docs that describe Checked or the returned-diagnostics rule match the new shape
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Make Checked sealed: Accepted(value) and Rejected(non-empty diagnostics); keep map and andThen; drop success/failure and the shared diagnostics.
2. Build the two cases at the edge in Klein and EnvironmentContract; the checker keeps its own type-plus-errors result.
3. Move the CLI, example host, benchmarks and tests onto when/is checks; tests get an assertRejected helper, diagnostics-returning test helpers return empty for Accepted.
4. CLI check prints the trailing expression type only when the program checks (agreed with Thomas).
5. Update the host-integration spec, the roadmap and CLAUDE.md.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Agreed with Thomas: no shared diagnostics property on Checked; the CLI check command prints the trailing expression's type only when the program checks; tests use orFail, a new assertRejected, and a test-only diagnosticsOrEmpty for helpers that return a diagnostics list. Validation: ./gradlew :klein-lib:jvmTest (2667 tests, 0 failed), :klein-example-host:test, :klein-bench:compileKotlinJvm, :klein-lib:compileKotlinLinuxX64, and a CLI smoke run of check and run on passing and failing input.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Checked is now a sealed class: Accepted holds only a value, Rejected holds one or more diagnostics and no value; map and andThen work on both. Klein and EnvironmentContract build the two cases from the checker's own type-plus-errors result. The CLI, example host, benchmarks and tests match on the case instead of forcing a nullable output. The host-integration spec, roadmap and CLAUDE.md describe the new shape. Verified with the JVM test suite, the example host tests, bench and native compiles, and a CLI smoke run.
<!-- SECTION:FINAL_SUMMARY:END -->
