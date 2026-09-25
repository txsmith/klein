---
id: TASK-57
title: Pin the toolchain with mise
status: To Do
assignee: []
created_date: '2026-09-27 13:43'
labels:
  - tooling
dependencies: []
ordinal: 54000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Thomas wants one central, pinned way to build the codebase. Today the pins are spread out and partly implicit. The Gradle wrapper pins Gradle, and the daemon pins its Java version. The Kotlin multiplatform plugin downloads its own Node for the JavaScript tests, at a version that is its default for the Kotlin release (24.16 for 2.4.20) and that nothing in the repo names. The binding's TypeScript type check needs @types/node, pinned by hand in klein-js/build.gradle.kts to a version that has to match that Node, so a Kotlin upgrade can silently leave the types behind. The playground (klein-playground) runs npm and Vite on whatever Node the developer has, outside Gradle. Considered: pinning Node in the version catalog (still leaves the playground and the types as separate pins), npm engines and devEngines checks (warnings or per-command checks only), a dev container, and Nix (heavier, and Kotlin Native downloads its own toolchain, which is awkward under Nix). The choice was mise: one mise.toml at the repo root names the exact Java and Node, developers and CI install from it, and every build tool uses that Node.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A mise.toml at the repository root pins the exact Java and Node versions
- [ ] #2 The Kotlin plugin no longer downloads its own Node; the library's and the binding's JavaScript tests and the npm installs run on the Node mise provides
- [ ] #3 The @types/node version for the binding's type check follows the pinned Node version instead of being pinned separately
- [ ] #4 The playground's npm scripts run on the same Node
- [ ] #5 A build check fails with a clear message when the Node or Java in use differs from mise.toml
- [ ] #6 CI installs its tools from mise.toml
- [ ] #7 The project guide (CLAUDE.md) explains how to set up mise
<!-- AC:END -->
