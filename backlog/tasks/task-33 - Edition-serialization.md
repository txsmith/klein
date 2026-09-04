---
id: TASK-33
title: Edition serialization
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
updated_date: '2026-09-04 14:18'
labels:
  - host-boundary
dependencies: []
ordinal: 33000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The stored form is source + pin map, version-stamped, with the release number kept as provenance only (for the reconciler report and the migration nudge; nothing loads through it). Per the source-is-truth ADR the Core is a cache: loading re-derives it, and a stamp mismatch means discard and re-derive, never migrate.
Re-derivation goes through the pin surface, not the release: pins are exactly the names the rule source wrote, resolvePins closes them into the typing surface, and the recompile must emit the same pin map it was given (fixpoint check; divergence is the same failure class as an unserved pin at run time). Removing a release therefore stays a compile-time act: it forces migration at the next edit and touches nothing already compiled.
Invariant this rests on: everything a release contributes to compilation is captured in the stored form. Today that is only the name-to-revision surface; the result sink is the first feature that will test it.
The stored-Core cache is a pure performance option: pin-based re-derivation needs only what the run needs (the pinned revisions still declared), so the old "replay a retired release" job is gone.
Stored pins diverging from fresh re-derivation is not a storage fault; it is the signal reconciliation acts on. Replay forces this item: it needs something durable to replay against.
<!-- SECTION:DESCRIPTION:END -->
