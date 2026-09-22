---
id: TASK-47
title: An edition carries its resolved surface
status: Done
assignee:
  - '@claude'
created_date: '2026-09-22 13:21'
updated_date: '2026-09-22 15:27'
labels:
  - host-boundary
dependencies:
  - TASK-44
ordinal: 46000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Resolving an edition's pins into its typing surface happens twice on the loading path: the decoder closes the pins to compare hashes and report unknown pins, and the run's pre-flight closes them again to build the surface it dispatches and checks answers against, hidden behind a memo keyed by the pin map. Decided in review of the closure-and-hash work (2026-09-22): the resolution should travel with the edition instead. Whoever makes an edition, compile against a release or pins, or decode against the contract, resolves its pins once and stores the surface on the edition; the run reads it and never resolves. The trust is the same as for the Core: an edition can only be made by the contract, so what it carries was checked. The environment identity (TASK-45) stays a separate question: a surface says what types the edition sees, not which contract it belongs to. The per-pin-map memo in EnvironmentContract then has no caller on the run path, which also dissolves the memo thread-safety loose end in docs/host-integration-roadmap.md.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 An edition holds the resolved surface of its pins, set by compile and by decode, and the run's pre-flight and log check read it instead of resolving pins
- [x] #2 Unknown pin is thrown by one code path, the pin closure, which decode and compile both call
- [x] #3 The run never touches the surface-resolution memo; the memo is removed if nothing else needs it
- [x] #4 edition.md and host-integration.md describe the carried surface and the roadmap's memo loose end is closed
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Edition gains an internal surface field; compile and decode set it through one internal resolve function that closes the pins and throws UnknownPin
2. Run, pre-flight and log check read edition.surface; the run does no pin check of its own
3. Remove the per-pin-map memo; releases keep a lazy per-release memo
4. Move the four run-time drain tests to decode; add decoded-edition-runs test and same-message test for unknown pins from decode and compile
5. Update edition.md, host-integration.md, roadmap loose end, CLAUDE.md listing
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Decision with Thomas (2026-09-22): the in-memory edition is the trust boundary. The run does no pin check of its own; an undeclared pin is caught when the edition is made (compile or decode). The four run-time removal tests moved to the decode suite. Verified: klein-lib allTests (minus jsBrowserTest) and klein-example-host test both exit 0.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Edition carries an internal ResolvedSurface set by compile (from its closed pins) and decode (from the recorded pins); run, pre-flight and log check read it. resolvePins (closePins + resolveSurface) is the one path that throws UnknownPin, called by compile and decode. The pin-set memo is gone; each release keeps a lazy surface. Specs, roadmap loose end and CLAUDE.md listing updated. Verified with the full multiplatform suite and the example host tests.
<!-- SECTION:FINAL_SUMMARY:END -->
