---
id: TASK-42
title: Upgrade Kotlin from 2.0.21 to the current release
status: Done
assignee:
  - '@claude'
created_date: '2026-09-09 11:00'
updated_date: '2026-09-24 08:41'
labels:
  - tooling
dependencies: []
references:
  - build.gradle.kts
  - klein-lib/src/commonMain/kotlin/klein/host/codec/EditionJsonEncoding.kt
ordinal: 42000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The build pins Kotlin 2.0.21 (October 2024) for all four plugins in build.gradle.kts. As of September 2026 the current stable release on Maven Central is 2.4.20, four minor releases on. The cost shows in the edition JSON codec: it uses the common stdlib Base64, which 2.0 still marks experimental, so EditionJsonEncoding.kt and EditionJsonEncodingTest.kt carry three `@OptIn(ExperimentalEncodingApi::class)` annotations that exist only because of the pin. Base64 has been stable since Kotlin 2.2.0. The companion libraries would move with it: kotlinx-benchmark is at 0.4.13 (0.5.0 is current) and kotlinx-serialization-json at 1.7.3 (1.11 is the latest stable line). Gradle is on 8.10 and may need to move too. The multiplatform Gradle DSL changed between 2.0 and 2.4, so expect small build-script edits. Keep this as its own PR on main, separate from any feature branch.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 build.gradle.kts pins the current stable Kotlin release for the multiplatform, jvm, serialization, and allopen plugins
- [x] #2 kotlinx-benchmark and kotlinx-serialization-json are on releases compatible with that Kotlin, and Gradle is on a version the Kotlin plugin supports
- [x] #3 The three `@OptIn(ExperimentalEncodingApi::class)` annotations in EditionJsonEncoding.kt and EditionJsonEncodingTest.kt are removed and the code still compiles
- [x] #4 ./gradlew :klein-lib:allTests -x :klein-lib:jsBrowserTest and ./gradlew :klein-example-host:test exit 0
- [x] #5 The native CLI links (linkDebugExecutableLinuxX64 or the macOS equivalent) and ./gradlew :klein-bench:smokeBenchmark runs
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Move the four Kotlin plugins to 2.4.20, kotlinx-benchmark to 0.5.0 and kotlinx-serialization-json to 1.11.0.
2. Replace the deprecated js(IR) target with js, and regenerate the committed yarn lock.
3. Remove the Base64 opt-ins, stable since Kotlin 2.2.0.
4. Move the Gradle wrapper from 8.10 to 9.7.1 (Thomas chose it over 9.7.0, the newest on the plugin's tested list).
5. Run every acceptance build and compare warnings against main.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Versions checked against Maven Central on 2026-09-24: Kotlin 2.4.20, kotlinx-serialization-json 1.11.0, kotlinx-benchmark 0.5.0 are the newest stable releases. Kotlin 2.4.20 lists Gradle 7.6.3 to 9.7.0 as supported.

Main builds with no warnings. The upgrade adds: Kotlin 2.4 checker warnings (redundant casts, needless safe calls and a needless !!, deprecated readLine in the native CLI), the macosX64 target deprecation, and two Gradle 10 deprecations in klein-lib's build script (afterSuite with a closure, and the by registering delegate). Left for Thomas to decide.

Validation: allTests without jsBrowserTest (2667 JVM and 2663 JS Node tests), klein-example-host test, linkDebugExecutableLinuxX64 (the CLI evaluates 1 + 2 * 3 to 7), and smokeBenchmark (30 results) all exit 0.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Moved Kotlin to 2.4.20, kotlinx-serialization-json to 1.11.0, kotlinx-benchmark to 0.5.0 and Gradle to 9.7.1; replaced js(IR) with js; removed the Base64 opt-ins. Verified with allTests (JS browser excluded), the example host tests, the native link and the smoke benchmark.
<!-- SECTION:FINAL_SUMMARY:END -->
