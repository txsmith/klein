---
id: TASK-3
title: Replace persistent control/operand stacks with mutable ArrayDeque
status: To Do
assignee: []
created_date: '2026-09-04 12:16'
updated_date: '2026-09-04 12:24'
labels:
  - perf
  - execution
dependencies: []
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: each push allocates a Cons; the CESK step loop churns small short-lived nodes.
Why (deliberate): O(1) snapshots - a host-call suspension/clone shares the spine.
Unblocked by the log rethink: no snapshot consumer remains - suspension just stops the loop and forking is replay-based.
Fix: go straight to mutable ArrayDeque stacks, no copy-on-snapshot needed.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 stacks are mutable ArrayDeque
- [ ] #2 klein-bench before/after recorded
<!-- AC:END -->
