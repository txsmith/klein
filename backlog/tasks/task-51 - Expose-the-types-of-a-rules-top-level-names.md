---
id: TASK-51
title: Expose the types of a rule's top-level names
status: Done
assignee:
  - '@claude'
created_date: '2026-09-24 14:17'
updated_date: '2026-09-24 14:22'
labels:
  - checker
dependencies: []
ordinal: 49000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The CLI check command is meant to print the type of each top-level name, but it prints none, whether the program passes or fails. The checker adds the rule's names to a private copy of the environment it is given and throws the copy away, so nothing outside the checker can read them. The public check function's note still says it fills in the environment passed to it, which stopped being true when the checker began copying (the contracts work). Thomas prefers an additional function next to check over changing check's result.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A new public function next to Klein.check returns, when accepted, each top-level name of the program with its type; Klein.check is unchanged
- [x] #2 The CLI check command prints the type of each top-level name again
- [x] #3 The note on Klein.check no longer claims the passed environment is filled in
- [x] #4 Tests cover the new function for values, functions, pattern bindings and type definitions
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Return the checker's filled scope in its internal result.
2. Add Klein.checkBindings: accepted with each top-level name's type in program order, rejected on any error; Klein.check unchanged.
3. The CLI check command prints binding types from checkBindings.
4. Correct the notes on Klein.check and TypeEnv that claimed the passed environment is filled in.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Type definitions and expressions bind no name, so they add no entry. The CLI prints types only for a program that checks, matching the Checked sum. Validation: ./gradlew :klein-lib:jvmTest (2679 tests, 0 failed), Linux native compile, and a CLI run of check on a passing and a failing program.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added Klein.checkBindings, which returns each top-level name of an accepted program with its type. The checker's internal result now keeps its filled scope. The CLI check command uses it and prints binding types again. Fixed the notes that said check fills in the passed environment. Verified with the JVM suite, the new CheckBindingsTest, and a CLI smoke run.
<!-- SECTION:FINAL_SUMMARY:END -->
