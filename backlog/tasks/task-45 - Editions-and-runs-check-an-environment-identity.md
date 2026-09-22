---
id: TASK-45
title: Editions and runs check an environment identity
status: To Do
assignee: []
created_date: '2026-09-18 09:02'
updated_date: '2026-09-22 15:53'
labels:
  - host-boundary
dependencies: []
ordinal: 45000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Two environments in one host process may declare the same names, and an edition compiled under one environment can be handed to the other. Since TASK-47 the run trusts the edition it is handed: pins are resolved once, when the edition is compiled or decoded, and the run checks nothing against the contract. So a run under the wrong environment is not refused at all; it reaches a capability whose handler is missing, or runs with a surface the environment never declared. The pin hashes are not compared at run time by decision (TASK-44, 2026-09-18: within one process the contract does not change, so a hash check there only ever caught host misuse). An environment identity closes that gap: the edition records the environment it was compiled in, and a run under another environment is refused before its first effect. What the identity is (a name in the contract file, a hash of the contract, a host-supplied id) is the open question to settle first; see docs/host-integration-roadmap.md loose ends.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 An edition records the identity of the environment it was compiled in, and the artifact carries it
- [ ] #2 Running an edition under an environment with a different identity fails before the first effect with a host error naming both
- [ ] #3 host-integration.md defines the identity and edition.md lists it among what is stored
<!-- AC:END -->
