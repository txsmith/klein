---
id: TASK-17
title: Parse array literals
status: To Do
assignee: []
created_date: '2026-09-04 12:18'
labels:
  - parser
dependencies: []
ordinal: 17000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
[1, 2, 3] - LBRACKET/RBRACKET exist in the lexer, parser TODO. The cleanest-bounded syntax gap: tokens exist, grammar addition + AST node + tests. Type/eval support is its own decision (no array type exists yet); scope this task to what the roadmap row means and split if checking turns out contested.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 arrays parse per grammar.md once extended
<!-- AC:END -->
