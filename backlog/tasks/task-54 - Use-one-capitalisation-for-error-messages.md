---
id: TASK-54
title: Use one capitalisation for error messages
status: To Do
assignee: []
created_date: '2026-09-24 20:27'
labels:
  - host-boundary
dependencies: []
ordinal: 51000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Host error messages are all lowercase, in the style that expects a message to be embedded mid-sentence ('run failed: the log already ends ...'). The checker's and the runtime's diagnostics start with a capital ('Type mismatch', 'Division by zero'). A host that shows both to a person gets two styles side by side. Noticed while reviewing the JavaScript binding's errors.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Host errors and diagnostics follow one documented capitalisation rule
- [ ] #2 Tests that match on message text are updated
<!-- AC:END -->
