---
id: TASK-6
title: Reuse the parent scope for match arms that bind nothing
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
updated_date: '2026-09-04 12:24'
labels:
  - perf
  - execution
dependencies: []
ordinal: 6000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: armScope gives every matched arm a fresh BindingScope, including lit/_/variable arms that bind nothing - an empty-slots allocation per dispatch, plus an extra parent hop for every out-reference from the arm body or guard.
Why (deliberate): uniform lowering - treating all arms as one scope deeper lets the lowerer assign arm depths mechanically.
Fix: reuse the parent scope for arms with no field bindings and keep those arm bodies at the match own depth in the lowerer; the empty alloc and extra hop survive only on constructor arms, which genuinely bind.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 non-binding arms allocate no scope
- [ ] #2 klein-bench before/after recorded
<!-- AC:END -->
