---
id: TASK-53
title: Let immediate handlers wait on in-process async work
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-24 18:36'
updated_date: '2026-09-25 10:04'
labels:
  - host-boundary
dependencies: []
ordinal: 50000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
An immediate handler answers a capability call during the run. Today it is a plain function, so the host must block until it has the answer. That works on the JVM but not in JavaScript, where a fetch returns a promise: the JavaScript binding rejects an async handler as not a Klein value. Blocking was the easiest way to build it, not a deliberate decision. Deferred handlers are a different concept: waits outside the program's life that must survive a restart. A quick fetch is still an immediate answer, just one the host awaits in process. Direction agreed with Thomas while reviewing the JavaScript binding: make the immediate handler and run suspending in Kotlin, so a host can still block inside a handler but can also wait without blocking; the binding then exposes run as a promise. Transact and persist likely become suspending too, since they wrap handler work. Kept separate from the playground work on purpose.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 An immediate handler can suspend while waiting for its answer, and a blocking handler still works
- [x] #2 A JVM host that is not async can run a rule with one blocking call at its edge
- [ ] #3 In the JavaScript binding, run returns a promise and an immediate handler may return a promise
- [x] #4 The host integration spec and the example host describe the new shape
- [x] #5 Tests cover a suspending handler, a blocking handler, and a handler whose promise is rejected
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Make the host API suspending (immediate, deferred initiation, transact, persist, run); add kotlinx-coroutines-test to commonTest and wrap tests in runTest; CLI gets a private starter; example host uses runBlocking. One mechanical commit, jvmTest green.
2. Add tests: suspending handler resumed by hand, blocking handler, handler failing with an exception (the rejected promise case).
3. Update host-integration spec, effect-log spec where needed, and the example host.
4. The JavaScript binding lives on playground; its criterion is met there after this lands.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Agreed with Thomas: no coroutines dependency in the library itself; suspension uses only the standard library. Immediate handler, deferred initiation, transact, persist and run all become suspending. Tests use the coroutines test library (runTest) as a test-only dependency.

Example host uses kotlinx-coroutines runBlocking at its edge. The CLI uses a small private starter, since suspend main is unreliable on Kotlin/Native and its prompt handler never suspends.

Validation: jvmTest green (2687 tests); SuspendingHandlerTest also passes on jsNodeTest; BlockingHostTest (jvmTest) covers a thread-blocking handler and answers arriving on another thread behind runBlocking; example host test green; native CLI built and exercised interactively and with piped stdin. Criterion 3 (JavaScript binding returns a promise) belongs to the playground branch and stays open until the binding is updated after this lands.

Review: dropped the yield-based order test (order already covered by EffectLogTest) and the JVM BlockingHostTest (it tested the coroutines library, not Klein; blocking handlers are plain handlers, and the example host covers runBlocking).
<!-- SECTION:NOTES:END -->
