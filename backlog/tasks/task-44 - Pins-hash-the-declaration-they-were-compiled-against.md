---
id: TASK-44
title: Pins hash the declaration they were compiled against
status: To Do
assignee: []
created_date: '2026-09-17 19:37'
labels:
  - host-boundary
dependencies:
  - TASK-43
ordinal: 44000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A pin is (name, revision), which identifies a declaration but not what it looked like when the edition was compiled, so an in-place edit to a signature or a type definition is invisible to everything that reads pins. Ruling (2026-09-16/17): a pin carries a hash of the declaration as compiled against (a capability signature, a type definition), computed from a deterministic printing of it (Type.print); no canonical bytes. The full declaration is not stored: the actionable message on a mismatch is whatever recompiling the rule against its release produces, so a pin only has to detect a change. With TASK-43 done each pin hashes only its own declaration, so comparison is per pin with no transitive walk.
Changes: Edition.pins from Map<String, RevisionNumber> to name -> (revision, hash); the JSON artifact pins field and the checksum follow; compileRule(source, pins) checks each pin hash against the surface it builds before compiling and emits fresh hashes; run pre-flight gains a host error beside UnknownPin when a pinned hash differs ("compiled against a different creditScore/3; reconcile"); specs already say this (edition.md, host-integration.md sections Edition, Run, Reconciliation, Drain, Evolution 4, What checking costs). No stored artifacts exist yet, so the format change is free. The staged-hashing draft is superseded.
The reconciler (TASK-37) builds on this: comparing pins to the contract finds exactly the editions a change touched; recompilation judges it.
<!-- SECTION:DESCRIPTION:END -->
