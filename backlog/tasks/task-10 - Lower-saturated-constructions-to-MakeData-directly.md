---
id: TASK-10
title: Lower saturated constructions to MakeData directly
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
labels:
  - perf
dependencies: []
ordinal: 10000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: constructors are eta-expanded to closures and v1 lowers every saturated construction like any other call - Circle(1) becomes Apply(Circle, [1]), so building one record runs the full call protocol around a MakeData body. A 1000-element list is 1000 closure calls + 1000 param cells.
Why (deliberate): lowering stays uniform; the fold, dead-bind elimination, and linear-redex inlining are all kept out of v1.
Fix (optimizer): rewrite Apply(<known ctor>, saturated-args) to MakeData(tag, fieldNames, args) (safe: constructor bodies are linear and pure); drop constructor binds with no value-position use; more generally inline any saturated linear redex.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 saturated constructions emit MakeData with no call protocol
- [ ] #2 klein-bench before/after recorded
<!-- AC:END -->
