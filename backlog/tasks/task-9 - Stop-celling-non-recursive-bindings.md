---
id: TASK-9
title: Stop celling non-recursive bindings
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
labels:
  - perf
dependencies: []
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: stepApply cells all arguments, but store indirection is only needed for allocate-before-fill bindings (recursive and hoisted funs). Arguments, vals, and scrutinee binders are never recursive and capture by value - celling them is pure uniformity, and the single biggest source of store growth.
Fix: non-recursive bindings live as direct values in the BindingScope; reserve the store for funs.
Note: subsumed by the per-scope-array change (see the dissolve-the-store task) - if that lands first, this dissolves with it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 non-recursive bindings allocate no store cell (or task closed as subsumed)
<!-- AC:END -->
