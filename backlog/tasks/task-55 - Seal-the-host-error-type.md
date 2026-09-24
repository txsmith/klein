---
id: TASK-55
title: Seal the host error type
status: Done
assignee:
  - '@claude'
created_date: '2026-09-24 21:00'
updated_date: '2026-09-25 08:18'
labels:
  - host-boundary
dependencies: []
ordinal: 52000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
HostError is a plain interface today, with its cases spread over four packages (klein, klein.check.contract, klein.host, klein.host.codec). Because it is open, code that maps every host error, such as the JavaScript binding (klein-js on the playground branch), needs a catch-all branch, so a new host error in the library compiles there silently and crosses without its fields. Decided with Thomas while reviewing the binding: seal HostError in the library, which Kotlin allows only when every case lives in the same package, so all host errors move into the root package klein beside the interface. Rejected alternatives: reflection over the error classes, exporting the library types to JavaScript directly, and a test that compares two lists. The CLI's own UnanswerableCapability error (nativeMain) is not a library error and becomes a plain CLI exception. Once this lands, the binding drops its catch-all (OtherHostError), on the playground branch.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 HostError is a sealed interface and every host error class lives in the root package klein
- [x] #2 A when over HostError in the library's tests or code is exhaustive without an else branch
- [x] #3 The CLI's unanswerable-capability error is no longer a HostError, and the CLI still reports it the same way
- [x] #4 Every caller, test, the example host, the benchmarks and the docs that name a host error's package are updated
- [x] #5 jvmTest, the JS Node tests and the native CLI build pass
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. CLI: UnanswerableCapability becomes a plain CLI exception; orExit catches it and prints the same line.
2. Seal HostError; move all twelve host error classes into HostError.kt in package klein; fix imports; add a test with an exhaustive when over HostError.
3. Update the CLAUDE.md file tree.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
No jvmMain, jsMain or linux/macos main source sets exist; only nativeMain (the CLI), which no longer implements HostError. Validation: jvmTest 2681 tests green; jsNodeTest, linkDebugExecutableLinuxX64, klein-example-host build and klein-bench compile green; CLI prints the same unanswerable-capability line with piped stdin.

Dropped the exhaustive-when test on review: the sealed keyword is the guarantee, and the test only added upkeep for each new host error.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
HostError is a sealed interface; all twelve host errors live in HostError.kt in package klein. The CLI's UnanswerableCapability is a plain exception caught by orExit with the same output. Imports, tests and the CLAUDE.md tree updated; HostErrorTest holds an exhaustive when without else. Verified with jvmTest, jsNodeTest, the native link and a CLI run.
<!-- SECTION:FINAL_SUMMARY:END -->
