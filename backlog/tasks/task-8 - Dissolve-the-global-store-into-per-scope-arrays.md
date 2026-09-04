---
id: TASK-8
title: Dissolve the global store into per-scope arrays
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
updated_date: '2026-09-04 12:24'
labels:
  - perf
  - execution
dependencies: []
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: Store.alloc only appends; the store grows O(total allocations) and never shrinks - a straight-line leak for long-running or loop-heavy programs, the rule-engine workload.
Why (deliberate): the global store exists so closures capture integer addresses instead of object references - an acyclic serializable shape.
Unblocked by the log rethink: nothing serializes machine state. Fix: BindingScope holds Array<Value?> directly (slot i of the scope is the cell - empty until filled, same early-read error, same letrec behavior since closures share the scope object). Unreachable scopes become ordinary garbage.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 no unbounded global store
- [ ] #2 klein-bench before/after recorded
<!-- AC:END -->
