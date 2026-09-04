---
id: TASK-1
title: Unbox monomorphic Num locals and operands
status: To Do
assignee: []
created_date: '2026-09-04 12:16'
labels:
  - perf
dependencies: []
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: every Value is a heap object; VNum wraps a double, so a + b + c boxes the intermediate a+b and unboxes it again for +c. The defining cost of a boxed interpreter.
Why (deliberate): representation obliviousness - erased types, one slot kind, a sealed walkable Value. Value classes re-box in polymorphic slots, Array<Any> is lateral, NaN-boxing needs bit-level control Kotlin/JS cannot give.
Fix (sanctioned, suspension-compatible): typed slot layouts from checker types - per-scope DoubleArray for monomorphic Num locals/operands, boxed wherever polymorphism or width subtyping forces it. A gated variant (host-resident interiors / superinstructions) exists but is separate.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 a + b + c allocates no intermediate VNum
- [ ] #2 klein-bench before/after recorded per (program, stage) cell
<!-- AC:END -->
