---
id: TASK-38
title: Trivia-preserving syntax tree
status: To Do
assignee: []
created_date: '2026-09-05 09:28'
labels:
  - parser
dependencies: []
ordinal: 38000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The surface tree carries trivia: comments, whitespace, and the literal spelling of numbers and strings, attached to the nodes they belong to. Prerequisite for the lossless printer and for source migrations that change only what they touch (docs/roadmap.md, Migration toolkit). Also what the Num design needs for literal text preservation.
<!-- SECTION:DESCRIPTION:END -->
