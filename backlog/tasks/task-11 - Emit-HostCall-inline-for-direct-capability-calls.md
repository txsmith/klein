---
id: TASK-11
title: Emit HostCall inline for direct capability calls
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
updated_date: '2026-09-04 12:24'
labels:
  - perf
  - lowering
dependencies: []
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: lowerWithPrelude binds a contract function as an eta-lambda (fun creditScore -> host creditScore(_0)), so each capability call runs the full call protocol pushed and popped around the suspension.
Why (deliberate): lowerExpr stays syntax-driven and uniform; a contract name is just a name in scope.
Fix: resolve the callee first and match on the resolved binding - an Apply whose callee resolves to a prelude Function of matching arity lowers to HostCall(name, args) inline; only a capability passed as a value keeps the lambda.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 direct capability calls lower to inline HostCall
- [ ] #2 klein-bench before/after recorded
<!-- AC:END -->
