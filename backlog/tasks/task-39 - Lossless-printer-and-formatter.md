---
id: TASK-39
title: Lossless printer and formatter
status: To Do
assignee: []
created_date: '2026-09-05 09:28'
labels:
  - tooling
dependencies:
  - TASK-38
ordinal: 39000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A printer over the trivia-carrying tree that reproduces the source byte for byte when nothing changed, and changes only the rewritten nodes when a migration edited the tree. The formatter is the same printer with a layout policy applied. Replaces surface/PrettyPrint as the author-facing printer; PrettyPrint stays a debugging aid. Named in docs/roadmap.md under Editor + tooling and Migration toolkit.
<!-- SECTION:DESCRIPTION:END -->
