---
id: TASK-46
title: Make every tree walk stack safe
status: To Do
assignee: []
created_date: '2026-09-22 12:28'
labels:
  - parser
  - checker
  - lowering
  - host-boundary
dependencies: []
references:
  - klein-lib/src/commonMain/kotlin/klein/host/codec/CoreBinaryEncoding.kt
  - klein-lib/src/commonMain/kotlin/klein/host/codec/BinaryPrimitives.kt
  - docs/decisions/2026-07-20-own-machine-not-a-rented-vm.md
ordinal: 43000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A chain of six hundred additions lowers to a Core tree six hundred levels deep, and the binary Core reader recursed once per level. A nesting cap of 512 added after a cloud review turned that valid edition into an unreadable artifact, so the cap was dropped from the Core reader in PR #36. The real problem is that every walk over the surface AST and the Core recurses on the host stack: the parser, the checker, the lowerer, both pretty printers, and both codecs. A deep enough program overflows the stack somewhere in the pipeline instead of being handled, and the depth at which that happens differs per platform (JVM, JS, native). The machine already runs on explicit stacks; the walks around it should too. Tail recursion does not help, since a tree node has several children. The effect log's value reader still carries the 512 cap on nested structs; decide whether it stays once the reader is iterative.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A rule whose Core nests at least 10000 levels deep (a long addition chain, a long else-if chain) parses, checks, lowers, prints, encodes and decodes on JVM, JS and native without a stack overflow
- [ ] #2 No walk over the surface AST or the Core in klein-lib recurses on the host stack; each uses an explicit stack or a worklist
- [ ] #3 The binary readers have no nesting cap, or the remaining cap is justified in docs/performance-debt.md
- [ ] #4 A test per walk pins the deep case
<!-- AC:END -->
