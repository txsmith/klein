---
id: TASK-12
title: Pre-fill capability values instead of suspending per value
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
updated_date: '2026-09-04 12:24'
labels:
  - perf
  - lowering
dependencies: []
ordinal: 12000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: a contract value binds as a bare host customer() at the top of the prelude scope, so a rule using N capability values suspends N times on entry - N round trips for data the host had in hand before it started the machine.
Fix: store pre-fill - the runner fills the value slot before the machine starts and the bind emits no HostCall at all; the slot is a pre-populated cell the rule reads.
Note: interacts with the effect log start entry (inputs are recorded by name); the recording semantics must not change.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 capability values cost no suspension
- [ ] #2 replay/log semantics unchanged (spec/effect-log.md)
<!-- AC:END -->
