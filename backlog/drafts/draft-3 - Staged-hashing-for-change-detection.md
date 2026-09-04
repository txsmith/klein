---
id: DRAFT-3
title: Staged hashing for change detection
status: Draft
assignee: []
created_date: '2026-09-04 12:41'
labels:
  - host-boundary
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Post-v1 optimization over the recompile-everything reconciler, staged: (1) hash entire pin sets - a crude change detector, did anything this edition sees change; also usable as the surface-resolution memo key in EnvironmentContract. (2) per-pin hashes to narrow which capability changed. Deterministic encoding suffices - the log codec for values, the printed type for signatures; canonical bytes have no consumer (nothing content-addresses). Replaces the dissolved canonical-form item (archived TASK-32).
<!-- SECTION:DESCRIPTION:END -->
