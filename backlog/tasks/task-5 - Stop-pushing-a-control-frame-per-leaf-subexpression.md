---
id: TASK-5
title: Stop pushing a control frame per leaf subexpression
status: To Do
assignee: []
created_date: '2026-09-04 12:16'
labels:
  - perf
dependencies: []
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: every leaf (Literal, Var, Lambda) gets its own control frame and a full dispatch-loop trip, though it produces a value in one step with no continuation. Leaves are about half the nodes on fib.
Fix: evaluate atomic operands inline in collectOperands (no frame); or lower to ANF so every argument position is atomic by construction and a frame is pushed only to enter a function body or suspend.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 leaves no longer cost a dispatch-loop trip
- [ ] #2 klein-bench before/after recorded
<!-- AC:END -->
