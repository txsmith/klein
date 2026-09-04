---
id: TASK-36
title: Capability derivation API
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
labels:
  - host-boundary
dependencies:
  - TASK-32
ordinal: 36000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Hosts write handlers as (List<Value>) -> Value, so Kotlin types and the Klein signature can drift apart silently. Derive Klein types from kotlinx.serialization descriptors (data class to record, sealed to sum, nullable to T?) so drift is a compile error; marshal both directions (Klein Value as a serialization format); emit the checked-in contract file, mandatory for code-first. Wraps the dynamic host boundary rather than replacing it. Boundary rules stay: no type variables, no function types. Carries the enforcement of contracts.md "the host sees exactly the declared shape": marshalling is narrowing.
<!-- SECTION:DESCRIPTION:END -->
