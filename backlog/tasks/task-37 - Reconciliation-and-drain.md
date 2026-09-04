---
id: TASK-37
title: Reconciliation and drain
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
labels:
  - host-boundary
dependencies:
  - TASK-32
  - TASK-33
  - TASK-34
references:
  - docs/ideas/suspended-run-migration.md
ordinal: 37000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Pure functions and report objects over org-supplied data: reconcile with a pin-hash prefilter and severity-classified reports, and answer drain queries - edition and parked-run counts per revision. No retire flag; removal is optimistic, stranded runs alert, a revision stays restorable. Delivering failed-recompile reports to rule authors is the org job, not the library. Prerequisite loose end: a signature change-detector hash does not exist yet (CapabilityId was deleted; identity is (name, revision)).
Migration design context: docs/ideas/suspended-run-migration.md.
<!-- SECTION:DESCRIPTION:END -->
