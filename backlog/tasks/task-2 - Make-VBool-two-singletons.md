---
id: TASK-2
title: Make VBool two singletons
status: To Do
assignee: []
created_date: '2026-09-04 12:16'
updated_date: '2026-09-04 12:24'
labels:
  - perf
  - execution
dependencies: []
ordinal: 2000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: VBool is allocated per comparison; every if/guard boxes a bool only to match on it and discard it.
Why: never taken - VNull/VUnit are already singletons; this is the free adjacent win noted under boxed values.
Fix: two singleton instances for true and false.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 no VBool allocation per comparison
- [ ] #2 klein-bench before/after recorded
<!-- AC:END -->
