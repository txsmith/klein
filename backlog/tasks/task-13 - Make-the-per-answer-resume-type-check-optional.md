---
id: TASK-13
title: Make the per-answer resume type check optional
status: To Do
assignee: []
created_date: '2026-09-04 12:17'
updated_date: '2026-09-04 12:24'
labels:
  - perf
  - execution
dependencies: []
ordinal: 13000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Now: the runner passes each handler answer through an infer + isSubtype walk before resuming, so cost grows with the answer size and is paid on every suspension - to catch something a correct handler never does.
Why (deliberate): a wrong-typed answer otherwise fails wherever something unboxes it, far from the capability call; the check is what makes HandlerTypeMismatch name the call.
Fix: the loop calls the check in one place, so a host that trusts its handlers can switch it off - a run option that skips the check, or a per-capability opt-out.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 a run option or per-capability opt-out skips the answer check
- [ ] #2 default behavior unchanged
<!-- AC:END -->
