---
id: TASK-34
title: Diagnostic severity
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
labels:
  - host-boundary
dependencies: []
references:
  - docs/ideas/diagnostic-severity.md
ordinal: 34000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Every TypeError gets a class saying whether it is a soundness failure or a degeneracy, decided at birth rather than mapped after the fact. Reconciliation needs it to tell an actionable recompile failure from noise. Includes splitting IncomparableEquality out of TypeMismatch at the equality emission site.
Design: docs/ideas/diagnostic-severity.md.
<!-- SECTION:DESCRIPTION:END -->
