---
id: TASK-40
title: Unify the error architecture
status: Done
assignee:
  - '@claude'
created_date: '2026-09-05 15:07'
updated_date: '2026-09-05 19:09'
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Design note (error-hierarchy-design.md) agreed with Thomas: Diagnostic (document, span) vs HostError (environment, no span), one KleinException(List<HostError>), internal Abort exceptions, StageResult kept.
2. Introduce Diagnostic and HostError in klein; retype StageResult and KleinException.
3. Front end: merge LexerError/ParseError into SyntaxError value, internal Abort, Lexer/parseProgram internal.
4. Machine: KleinRuntimeError becomes RuntimeError value with internal Abort.
5. check.contract: UnknownRelease, UnknownPin, InvalidContract as HostErrors; CapabilityInAnswer gets a span; check/compileRule/compileValue return StageResult; checkContract throws KleinException(InvalidContract).
6. host: delete RunFailure, RunError, PinProblem, Diagnostic; per-fault HostErrors; run throws KleinException; log holds Diagnostics.
7. Update CLI, example host, tests, docs (effect-log.md, host-integration.md, roadmap, CLAUDE.md). Each step keeps jvmTest green; allTests minus jsBrowserTest before done.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented in six commits on error-hierarchy: Checked rename; SyntaxError value behind internal Abort (Lexer/parseProgram internal, dead --verbose flag removed); RuntimeError value behind internal Abort; KleinException carries HostErrors, check/compileRule/compileValue return Checked, checkContract throws InvalidContract; RunFailure/RunError/PinProblem replaced by per-fault HostErrors with UnknownPin in check.contract; KleinError split into Diagnostic (span) and HostError, host Diagnostic copy deleted, codecs decode RuntimeError and require a span.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
One error architecture: Diagnostic (about a document, has a span, returned in Checked / RunOutcome.Failed / LogEntry.Failure) and HostError (about the environment, no span, thrown only inside the one KleinException, one error per fault with its fields kept). RunFailure, RunError, PinProblem, UnservedPin, KleinRuntimeError, LexerError, ParseError and the host's Diagnostic copy are gone; UnknownPin lives in check.contract; checkContract throws InvalidContract; check/compileRule/compileValue return Checked; Lexer, parseProgram and the machine unwind with internal Abort exceptions. Verified with ./gradlew :klein-lib:jvmTest (2488 tests green after every commit), :klein-example-host:test, and :klein-lib:allTests -x jsBrowserTest (exit 0). Documented in spec/host-integration.md §Errors, the roadmap's Done list, and CLAUDE.md's package map.
<!-- SECTION:FINAL_SUMMARY:END -->
