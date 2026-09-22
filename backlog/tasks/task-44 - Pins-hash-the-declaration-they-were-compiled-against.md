---
id: TASK-44
title: Pins hash the declaration they were compiled against
status: Done
assignee:
  - '@thomas'
created_date: '2026-09-17 19:37'
updated_date: '2026-09-18 09:03'
labels:
  - host-boundary
dependencies:
  - TASK-43
ordinal: 44000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A pin is (name, revision), which identifies a declaration but not what it looked like when the edition was compiled, so an in-place edit to a signature or a type definition is invisible to everything that reads pins. Ruling (2026-09-16 to 18): a pin carries a hash of the declaration as compiled against, and the hash is a change detector, not a guard. The full declaration is not stored: the actionable message on a mismatch is whatever recompiling the rule produces. Decided in chat 2026-09-18, one question at a time: the hash is a tagged byte walk of the checked type (never Type.print, a diagnostic printer must not decide hashes); function parameter names are in it, type variables are numbered by first appearance, reached types carry their revision, the declaration own revision is not in it, a type hashes every constructor name and fields; FNV-1a 64, no cryptographic hash. Pins never hold constructors: a constructor collapses to its type at every entry, because the sum shared interface (reached by a join of two constructors) depends on every constructor, so finer pins would miss edits. Hashes are compared only when an artifact is decoded, which now takes the contract; compileRule(source, pins) ignores them and recompiles, and the run pre-flight does not compare them, because the contract does not change within a host process (host misuse is for an environment identity, a task of its own). The staged-hashing draft is superseded. The reconciler (TASK-37) builds on this.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Every pin of a compiled edition carries the hash of its declaration at the pinned revision, and the JSON artifact and checksum carry it
- [x] #2 Renaming a parameter, changing a field type, or adding a constructor changes the hash; reordering record fields or constructors, or renaming a type variable, does not
- [x] #3 Decoding against a contract whose declaration was edited in place answers stale with reason declaration changed, and re-derivation compiles against the edited declaration
- [x] #4 Decoding against a contract that lacks a pinned revision throws UnknownPin per pin
- [x] #5 Pins never contain constructors; a constructor written in a rule or given as a pin resolves to its type
- [x] #6 edition.md, host-integration.md and the roadmap describe hashing, decoding with a contract, and the absence of a run-time hash check
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Four commits on top of TASK-43: (1) pins are types and capabilities only, ResolvedSurface keeps one map with isExposed and getRevision answered from the rule type env; (2) klein/Fnv.kt extracted from the checksum and DeclarationHash.kt with hashOf(name, revision) on the contract, determinism tests; (3) Pin(revision, hash), Edition.pins as name to revision and pinsWithHash, compileRule emits hashes and gains a Map<String, Pin> overload, JSON pins as objects, checksum folds the hash; (4) EnvironmentContract.decodeEditionJson(text) with Rederivation.DeclarationChanged, UnknownPin thrown at decode, contract-free reader dropped. Then the specs, roadmap and this task.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Commits 05f5d79, 8bb20cb, 3c75f9f, bef3874 on branch edition-serialization, plus the doc and backlog commit after them. Decisions taken in chat are in the description. Verified with ./gradlew :klein-lib:jvmTest (whole suite green; DeclarationHashTest 21, CompileAgainstPinsTest 18, EditionJsonEncodingTest 47) and :klein-example-host:compileKotlin. The native target was not run.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Pins are the closure of types and capabilities, each with an FNV-1a hash of its declaration from a byte walk of the checked type. The artifact carries the hash per pin and under its checksum. Decoding takes the contract and answers declaration changed as a stale reason; unknown pins throw at decode; compiling against pins ignores hashes; the run does not compare them. Verified by the JVM test suite.
<!-- SECTION:FINAL_SUMMARY:END -->
