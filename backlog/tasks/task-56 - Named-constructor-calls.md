---
id: TASK-56
title: Named constructor calls
status: To Do
assignee: []
created_date: '2026-09-25 15:42'
labels:
  - language
dependencies: []
ordinal: 53000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Constructors are called by position only (Customer(1, "Acme")). The value printer writes a tagged value's fields in the order its field map holds them, which is only the constructor's order when the machine built the value. A host builds values too: a JavaScript handler answering new TaggedValue("Customer", { name: "Acme", id: 1 }), or a Kotlin host building a map in another order, and the machine passes such values along unchanged, so a parked call or a log entry can print as Customer("Acme", 1), which is not valid Klein. Fixing it in the printer would need the types at every place that prints (tried on the playground branch and rejected by Thomas: printing should not depend on editions or contracts), and carrying constructor types into runtime values was rejected too. The agreed fix is a named form of the constructor call, so the printer can write fields by name and their order stops mattering. Patterns already destructure a constructor by field name (Person { name } = e), and the edition spec expects calling by name to follow from the tilde operator (TASK-25), so the syntax should fit both. Until this lands, a printed constructor value built by a host may not be valid Klein.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A rule can call a constructor with its fields by name, in any order, and the checker reports a missing, unknown or duplicated field
- [ ] #2 Positional constructor calls keep working
- [ ] #3 The value printer writes every tagged value with named fields, so a printed value is valid Klein whatever order its fields were built in
- [ ] #4 grammar.md, reference.md and the relevant specs describe the named form
- [ ] #5 Tests cover the parser, the checker, evaluation and printing
<!-- AC:END -->
