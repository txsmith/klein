---
id: TASK-49
title: Put dependency and plugin versions in a Gradle version catalog
status: Done
assignee:
  - '@claude'
created_date: '2026-09-24 09:40'
updated_date: '2026-09-24 09:44'
labels:
  - tooling
dependencies: []
ordinal: 47000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Versions are written out as strings in three build files: the four Kotlin plugins and kotlinx-benchmark in the root build.gradle.kts, kotlinx-serialization-json in klein-lib, and kotlinx-benchmark-runtime in klein-bench. The benchmark plugin and runtime must match but live in different files. A version catalog at gradle/libs.versions.toml holds them in one place, and each build file refers to it.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 gradle/libs.versions.toml declares the Kotlin, kotlinx-serialization-json and kotlinx-benchmark versions, the plugins and the libraries
- [x] #2 No build.gradle.kts in the project writes a version string for these plugins or libraries
- [x] #3 ./gradlew :klein-lib:jvmTest, :klein-example-host:test and :klein-bench:smokeBenchmark exit 0 with no new warnings
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add gradle/libs.versions.toml with versions for Kotlin, kotlinx-serialization-json and kotlinx-benchmark, the five plugins and the two libraries.
2. Refer to the catalog from the root, klein-lib and klein-bench build files.
3. Run the JVM tests, the example host tests and the smoke benchmark with all warnings shown.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Every build file applies its plugins through the catalog, including klein-example-host, so plugin ids are written once. kotlin("test") stays as it is: the Kotlin plugin sets its version.

Validation: jvmTest (2667 tests), jsNodeTest, klein-example-host test, smokeBenchmark and linkDebugExecutableLinuxX64 all exit 0 with --warning-mode all and no warnings. A search of the build files finds no version strings.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added gradle/libs.versions.toml and pointed every build file at it for plugins and libraries. Verified with the JVM, JS Node and example host tests, the smoke benchmark and the native link, with no warnings.
<!-- SECTION:FINAL_SUMMARY:END -->
