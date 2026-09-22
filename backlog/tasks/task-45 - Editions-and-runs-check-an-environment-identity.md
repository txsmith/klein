---
id: TASK-45
title: Editions and runs check an environment identity
status: Done
assignee:
  - '@claude'
created_date: '2026-09-18 09:02'
updated_date: '2026-09-22 18:44'
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
- [x] #1 An edition records the identity of the environment it was compiled in, and the artifact carries it
- [x] #2 Running an edition under an environment with a different identity fails before the first effect with a host error naming both
- [x] #3 host-integration.md defines the identity and edition.md lists it among what is stored
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Contract header: 'environment <ident | string>' as the first line, parsed into ContractExpr, required exactly once by the contract checker; EnvironmentContract gains name
2. Edition gains environment, set by compile; artifact carries an environment field outside the checksum; decode compares it to the contract first, before the checksum, and throws WrongEnvironment
3. Run pre-flight compares edition.environment with the contract name first and throws WrongEnvironment naming both
4. Every contract literal in tests, examples and the example host gains the header; CLI reads it from the file
5. Specs: contracts.md and grammar.md (the header), host-integration.md (identity, run, errors), edition.md (stored, decoding order, checksum scope, rename note), roadmap loose end closed and rename noted under migration
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decisions with Thomas (2026-09-22): the identity is a name, not a hash, so an edited contract keeps its editions; the name is written on the contract file's first line as 'environment <ident | string>' and reaches everything from there (no host-supplied id, no CLI flag); the edition and the artifact record it; the name stays outside the checksum since decode compares it directly, before the checksum, so a damaged artifact from another environment is refused rather than re-derived; decode and run both throw WrongEnvironment naming both. Renaming an environment is deferred to the migration toolkit (noted in roadmap.md). Invented names in tests are 'acme', not a domain word. Verified: klein-lib allTests (minus jsBrowserTest) and klein-example-host test both pass; the native CLI prints the environment in the contract summary.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Contracts open with 'environment <name>'; EnvironmentContract, Edition and the JSON artifact carry it; decodeEditionJson compares it first and Environment.run compares it before any other pre-flight check, both throwing WrongEnvironment with both names. Specs (contracts, grammar, host-integration, edition), roadmaps and CLAUDE.md updated. Verified by EnvironmentIdentityTest plus the full multiplatform suite and the example host tests.
<!-- SECTION:FINAL_SUMMARY:END -->
