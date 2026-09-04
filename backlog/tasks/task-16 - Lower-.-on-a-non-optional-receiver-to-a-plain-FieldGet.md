---
id: TASK-16
title: Lower ?. on a non-optional receiver to a plain FieldGet
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
updated_date: '2026-09-04 12:24'
labels:
  - perf
  - lowering
dependencies: []
ordinal: 16000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: p?.x on a non-optional p still lowers to match p { null -> null; _ -> p.x }, with a dead null arm and a needlessly optional result type.
Why (deliberate): keeps ?. a purely syntactic desugar - types are erased, so the lowerer cannot tell optional from non-optional without checker input.
Fix: type-directed lowering - a plain FieldGet when the checker says the receiver is non-optional.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 non-optional ?. lowers without a match
<!-- AC:END -->
