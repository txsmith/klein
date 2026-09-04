---
id: TASK-7
title: Skip the scope-chain walk for top-level refs
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
updated_date: '2026-09-04 12:24'
labels:
  - perf
  - execution
dependencies: []
ordinal: 7000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: (depth, slot) resolution follows parent pointers, O(scope depth) per read.
Why (deliberate): depth is 1-2 in practice and the walk is dwarfed by boxing/dispatch; the simple version wins until a profiler disagrees.
Fix (cheap): GlobalRef(index) - direct-index the outermost scope so top-level refs (funs, constructors, host bindings) skip the walk.
Fix (structural): closure conversion / flat per-function scopes - O(1) local/captured/global addressing; Klein immutability makes capture-by-value easy, the wrinkle is local recursive bindings.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 top-level refs resolve without a chain walk
- [ ] #2 klein-bench before/after recorded
<!-- AC:END -->
