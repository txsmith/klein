---
id: TASK-32
title: Canonical form + numeric spec
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
labels:
  - host-boundary
dependencies: []
ordinal: 32000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Encoding + version stamp. A late item: its only consumers are hashing and byte-comparison, and every use of hashing here is an optimization with a correct slow path (the reconciliation prefilter falls back to recompile-everything). Lands when the fleet is big enough that the slow paths hurt. The version stamp on anything stored keeps every representation decision reversible until then, including the open Num question: encodings commit to doubles knowingly; exact rationals are a later semantics change paid for with a wipe. The value-identity rulings replay needs (-0.0 vs 0.0, NaN) belong to the pending evaluation spec.
<!-- SECTION:DESCRIPTION:END -->
