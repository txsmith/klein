---
id: TASK-40
title: Unify the error architecture
status: To Do
assignee: []
created_date: '2026-09-05 15:07'
updated_date: '2026-09-05 15:40'
labels:
  - host-boundary
dependencies: []
ordinal: 40000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Today Klein throws two unrelated hierarchies, split by which operation noticed the problem rather than by what went wrong. KleinException carries List<KleinError> (message + nullable span) from the lexer, parser, checker, contracts, and codecs; RunFailure carries a sealed RunError (InvalidRegistration, UnservablePins, LogTypeMismatch, Diverged, HandlerTypeMismatch, CallTypeMismatch) from run. So UnreadableLog and LogTypeMismatch, both "the log the host gave is bad", land in different hierarchies, and a pin the contract does not declare is a KleinError when compiling and a PinProblem when running. Also unsettled: what KleinError means when span is null (UnreadableLog, RegistrationError, CapabilityInAnswer already have none), and PinProblem as a third small hierarchy.
Separate the two concepts clearly. KleinError is a diagnostic: a value describing one thing that is wrong, with a message and, when there is text to point at, a span; it is data, never thrown, and it is what UIs render and reports list. KleinException is the one thing Klein throws: the carrier for a list of diagnostics out of an operation that cannot continue. Nothing else should be thrown and nothing else should be a diagnostic. Decide whether KleinError splits into located (span) and plain (no span) diagnostics, and give every structured error (the RunError variants, UnknownPin, MissingHandler) a place as a diagnostic with its fields kept.
Keep the hierarchy as flat as possible, and do not keep near-duplicates: errors that are similar but subtly different (UnknownPin vs RunError.UnservablePins wrapping a list of UnknownPin and MissingHandler; RunFailure vs KleinException; PinProblem vs KleinError; RunOutcome.Failed vs RunFailure in name) must be merged or one of them removed. One diagnostic per situation, one exception type, no wrapper variants that only carry a list of other diagnostics.
Direction to evaluate: one hierarchy. Every RunError implements KleinError (structured fields kept, span null); RunFailure extends KleinException, or goes; PinProblem goes; UnknownPin lives in klein.check.contract and is the single pin error for compile and run. Hosts catch one type and inspect by class when they want structure.
Until this lands, edition decoding wraps the KleinException from resolvePins into RunFailure(UnservablePins) on the run path (see edition-serialization-design.md).
<!-- SECTION:DESCRIPTION:END -->
