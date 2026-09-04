---
id: TASK-14
title: 'Hoist receivers, scrutinees, and destructure RHSs only when it pays'
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
labels:
  - perf
dependencies: []
ordinal: 14000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: scrutinee and ?. receiver hoisting wraps a fresh scope and allocates a cell even for cheap pure expressions; destructuring binds the RHS to a temp whenever it extracts 2+ fields even when the RHS is already a slot - a redundant slot-to-slot copy.
Why (deliberate): an effectful receiver (contains a HostCall) must hoist - re-evaluating fires the effect twice; a pure-but-expensive or reused receiver also wins.
Fix: rematerialization vs spilling - inline-duplicate cheap pure receivers, skip the destructure temp when the RHS is already a slot; hoist only effectful or expensive/reused values. Purity is trivial: effectful == contains a HostCall.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 cheap pure receivers are not hoisted
- [ ] #2 effectful receivers still hoist (effects fire once)
<!-- AC:END -->
