---
id: TASK-37
title: Reconciliation and drain
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
updated_date: '2026-09-04 12:39'
labels:
  - host-boundary
dependencies:
  - TASK-33
  - TASK-34
references:
  - docs/ideas/suspended-run-migration.md
ordinal: 37000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Pure functions and report objects over org-supplied data: the v1 reconciler recompiles every edition against the edited contract and reports with severity-classified diagnostics; it also answers drain queries - edition and parked-run counts per revision. No retire flag; removal is optimistic, stranded runs alert, a revision stays restorable. Delivering failed-recompile reports to rule authors is the org job, not the library.
Hashing is a later optimization, staged: first hash entire pin sets (a crude change detector - did anything this edition sees change?), then per-pin hashing to narrow which capability changed. Deterministic encoding suffices (the log codec / printed types); canonical bytes have no consumer. The same pin-set hash would also serve the resolvePins memo key in EnvironmentContract.
Migration design context: docs/ideas/suspended-run-migration.md.
<!-- SECTION:DESCRIPTION:END -->
