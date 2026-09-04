---
id: TASK-33
title: Edition serialization
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
labels:
  - host-boundary
dependencies: []
ordinal: 33000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The stored form is source + release number + pin map, version-stamped: per the source-is-truth ADR the Core is a cache, so loading re-derives it, and a stamp mismatch means discard and re-derive, never migrate. Three flat fields, trivial encoding - no Core tree encoding in v1. Stored pins diverging from a fresh re-derivation is the signal reconciliation acts on, not a storage fault. Replay forces this item: it needs something durable to replay against. The optional stored-Core cache earns a second job: replaying an edition whose release has been retired needs it.
<!-- SECTION:DESCRIPTION:END -->
