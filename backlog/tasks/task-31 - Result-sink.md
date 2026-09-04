---
id: TASK-31
title: Result sink
status: To Do
assignee: []
created_date: '2026-09-04 12:19'
labels:
  - host-boundary
dependencies: []
references:
  - docs/ideas/result-sink.md
ordinal: 31000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A release entry may nominate one capability as where the rule answer goes (result decide, a fun returning Nothing), so the host states what a rule must produce and a rule can decide early. A rule either concludes on every path or its trailing expression is wrapped for it; mixing the two is a compile error. Spec-first: it is the one slice that changes the contract language - contracts.md and grammar.md, three new contract checks, and an ADR for the real alternatives.
Design draft: docs/ideas/result-sink.md. Builds on compileRule and the runner.
<!-- SECTION:DESCRIPTION:END -->
