---
id: TASK-52
title: 'Name unknown types, type variables and constructors in the checker''s errors'
status: To Do
assignee: []
created_date: '2026-09-24 15:10'
labels:
  - checker
dependencies: []
ordinal: 48000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The checker reports every undefined name as 'Unbound variable', whatever kind of name it is. A contract with fun f(x: Nope): Num says 'Unbound variable: Nope', though Nope is a type. The type resolver reuses the same error for an unknown type name, an unknown applied type such as Nope<Num>, and an unknown type variable, and a call to an undefined constructor such as Foo(1) goes through the ordinary name lookup and gets the same message. Rule and contract authors read these messages in the playground and the CLI, so the kind of name should be in them. Found while building the browser playground.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An undefined type name, with or without type arguments, is reported as an unknown type naming it
- [ ] #2 An undefined type variable is reported as an unknown type variable naming it
- [ ] #3 A call to an undefined capitalized name is reported as an unknown constructor naming it
- [ ] #4 An undefined lowercase name in an expression is still reported as an unbound variable
- [ ] #5 Tests cover each case in rules and in contracts
<!-- AC:END -->
