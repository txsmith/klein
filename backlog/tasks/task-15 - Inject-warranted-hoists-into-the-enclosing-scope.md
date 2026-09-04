---
id: TASK-15
title: Inject warranted hoists into the enclosing scope
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
labels:
  - perf
dependencies: []
ordinal: 15000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: even when a hoist is warranted it allocates a new BindingScope around the match.
Fix: inject the bind into the enclosing scope when the match sits at a scope tail - flatter, no new scope object.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 tail-position hoists allocate no extra scope
<!-- AC:END -->
