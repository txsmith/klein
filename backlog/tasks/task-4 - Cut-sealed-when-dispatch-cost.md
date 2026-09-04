---
id: TASK-4
title: Cut sealed-when dispatch cost
status: To Do
assignee: []
created_date: '2026-09-04 12:16'
labels:
  - perf
dependencies: []
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: when (expr) { is Literal ... } compiles to an instanceof chain (the JVM has no type-based jump table), so dispatch is O(arms) per step - the fundamental tree-interpreter tax.
Fix (cheap): order the arms by dynamic frequency; the ANF change shifts the hot set to Apply/PrimApp/EnterScope/Match.
Fix (structural): a flat node table addressed by Int with a dense integer opcode (enables a real tableswitch) - most of what a bytecode tier is for.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 measured dispatch improvement on klein-bench
<!-- AC:END -->
