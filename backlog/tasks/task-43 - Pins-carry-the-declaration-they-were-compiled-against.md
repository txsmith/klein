---
id: TASK-43
title: Pins cover the closure of what an edition depends on
status: Done
assignee: []
created_date: '2026-09-16 18:54'
updated_date: '2026-09-17 20:30'
labels:
  - host-boundary
dependencies: []
ordinal: 43000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Today an edition pins only the names its source wrote; the declarations those signatures reach (a type behind a capability parameter) are implied, and resolvePins recomputes the closure every time. Ruling (2026-09-17): pins cover the closure explicitly, the names the source wrote plus every declaration their signatures reach, which is exactly the surface resolvePins builds. Nothing depended on pins being minimal; the closure is unique, so pins become canonical (annotation vs inference no longer changes them) and drain counts by revision become exact.
Changes: compileRule(source, pins) emits the closure as the edition pins; compileRule(source, pins) given a superset still drops what the closure does not reach; the run pre-flight and UnknownPin cover types and constructors the same as capabilities (they already do); tests in CompileAgainstPinsTest and the edition codec tests assert the closure. Specs already say this (edition.md, host-integration.md Edition and Reconciliation). Precedes TASK-44.
<!-- SECTION:DESCRIPTION:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. compileRule(source, pins) resolves the used names again through resolvePins and takes that surface's exposedRevisions as the edition pins, so pins are the closure: written names, reached types, their constructors.
2. Update CompileRuleTest, CompileAgainstPinsTest, EditionJsonEncodingTest, RunAgainstReleaseTest to assert the closure; add closure cases (capability reaches types and constructors, type reached through another type and through a constructor field, annotation vs inference, drain query from pins alone, superset cut down).
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
One-line production change in EnvironmentContract.compileRule; UnknownPin, the run pre-flight, the codecs and the example host needed no change. Verified with ./gradlew :klein-lib:allTests -x :klein-lib:jsBrowserTest and ./gradlew :klein-example-host:test, both green.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Edition pins are now the closure of what the source uses (names written, types reached through signatures and fields, and their constructors), emitted by compileRule(source, pins) through resolvePins; a superset of pins is cut down to that closure. Tests in CompileRuleTest, CompileAgainstPinsTest, EditionJsonEncodingTest and RunAgainstReleaseTest assert the closure and the drain and re-derivation consequences. Verified with the full library suite (minus jsBrowserTest) and the example host tests.
<!-- SECTION:FINAL_SUMMARY:END -->
