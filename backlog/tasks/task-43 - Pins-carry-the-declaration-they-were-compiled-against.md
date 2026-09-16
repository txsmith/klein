---
id: TASK-43
title: Pins carry the declaration they were compiled against
status: To Do
assignee: []
created_date: '2026-09-16 18:54'
labels:
  - host-boundary
dependencies: []
ordinal: 43000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A pin is (name, revision) today, which identifies a declaration but not what it looked like when the edition was compiled, so an in-place edit to a signature or a type definition is invisible to everything that reads pins. Ruling (2026-09-16): a pin records the full declaration as compiled against (a capability signature as the checker saw it, a type definition), and consumers compare it structurally to the contract current declaration at that revision. No hashing.
Changes: Edition.pins from Map<String, RevisionNumber> to name -> (revision, declaration); the JSON artifact pins field and the checksum follow; run pre-flight gains a host error beside UnknownPin when the pinned declaration differs from the declared one, showing both sides ("compiled against creditScore/3(c: Customer/2): Num; the contract now declares creditScore/3(c: { id: Num, tier: String }): Num"); compileRule(source, pins) still takes the map and checks pinned declarations against the surface it builds; specs edition.md and host-integration.md sections Edition, Reconciliation, Drain, Evolution, What checking costs follow. No stored artifacts exist yet, so the format change is free. The staged-hashing draft is superseded.
The reconciler (TASK-37) builds on this: comparing pins to the contract finds exactly the editions an in-place edit touched; recompilation judges whether the edit was acceptable.
<!-- SECTION:DESCRIPTION:END -->
