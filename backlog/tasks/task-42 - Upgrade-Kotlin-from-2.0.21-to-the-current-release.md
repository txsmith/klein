---
id: TASK-42
title: Upgrade Kotlin from 2.0.21 to the current release
status: To Do
assignee: []
created_date: '2026-09-09 11:00'
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
- [ ] #1 build.gradle.kts pins the current stable Kotlin release for the multiplatform, jvm, serialization, and allopen plugins
- [ ] #2 kotlinx-benchmark and kotlinx-serialization-json are on releases compatible with that Kotlin, and Gradle is on a version the Kotlin plugin supports
- [ ] #3 The three `@OptIn(ExperimentalEncodingApi::class)` annotations in EditionJsonEncoding.kt and EditionJsonEncodingTest.kt are removed and the code still compiles
- [ ] #4 ./gradlew :klein-lib:allTests -x :klein-lib:jsBrowserTest and ./gradlew :klein-example-host:test exit 0
- [ ] #5 The native CLI links (linkDebugExecutableLinuxX64 or the macOS equivalent) and ./gradlew :klein-bench:smokeBenchmark runs
<!-- AC:END -->
