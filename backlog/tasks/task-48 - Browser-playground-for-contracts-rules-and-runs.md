---
id: TASK-48
title: 'Browser playground for contracts, rules and runs'
status: In Progress
assignee:
  - '@claude'
created_date: '2026-09-23 21:12'
updated_date: '2026-09-25 15:42'
labels:
  - tooling
dependencies: []
ordinal: 47000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Klein has a complete host side (contracts, editions, runs, effect logs) but no way to take it for a spin. There is no real host, so nobody, including its author, can try the ideas in practice or show them to others. A playground that runs entirely in the browser, on the library's JavaScript build, fills that gap: anyone can open a link and write a contract, write rules against it, and play the host by answering capability calls. Building it also tests the host API from the outside: friction in the bridge to JavaScript is a finding about the API. Later it is the natural place to show reconciliation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 The library's JavaScript build is healthy and exposed through a small bridge: check a contract, compile a rule against a release, start a run, give an answer, encode and decode editions and logs
- [ ] #2 A contract page edits the environment's contract with live diagnostics, and can register fixed answers per capability so runs do not ask for them
- [ ] #3 A rule list; each rule has an editor with live diagnostics and a release picker, with a side bar listing its editions and its runs
- [ ] #4 An edition shows its pins and whether it is stale after a contract edit
- [ ] #5 A run view shows past log entries, an answer box for the pending call checked against the declared type, and the outcome (value or rule failure) when finished
- [ ] #6 The rule source shows each logged answer inline on the line of its call
- [ ] #7 Runs can be left waiting and resumed later; contracts, rules, editions, runs and logs persist in browser storage
- [ ] #8 A whole workspace exports and imports as one JSON file
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Agreed with Thomas 2026-09-24.

Module: klein-js, a top-level Kotlin/JS module beside klein-lib. It depends on klein-lib's public API only, targets ES2015 with ES modules, and builds an ES module with TypeScript types. It is Klein's JavaScript binding, not playground code. The playground app (Vite, Svelte 5, TypeScript) lives in its own folder and imports it.

Binding shape, mirroring the Kotlin host API:
- checkContract(source) returns a Contract or throws KleinError; tokenize(source) returns tokens with kind, start, end
- Contract: environment, releases, declarations; check, compileRule, compileValue (compiles and evaluates), decodeEdition (intact or stale with reason), implement(handlers keyed by name/revision)
- Handler: immediate, deferred, or perRun
- Edition: environment, language, source, pins with revision and hex hash; encode
- Environment.run(edition, handlers, log, persist) returns an outcome: completed with a value, failed with diagnostics, or parked with the call and a reply builder; always the log
- EffectLog: entries, append, encode, decode
- Values: numbers, strings, booleans, null, undefined for unit, plain objects for records, a Tagged class for constructed values
- No parser export; tokens are enough for highlighting

Order of delivery:
1. klein-js with a Node test of every call; measure the production bundle.
2. Vite + Svelte skeleton; contract page with a plain textarea and diagnostics.
3. Our editor: token highlighting and diagnostic underlines over a transparent textarea.
4. Rules: list, editor, release picker, compile into editions.
5. Runs: start, answer box, log, outcome; fixed answers on the contract page.
6. Browser storage; parked runs resume later.
7. Editions show pins and staleness after a contract edit.
8. Logged answers inline on their call's line (needs call positions on the outcome).
9. Workspace export and import as one JSON file.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Stack chosen: Vite, TypeScript and Svelte 5, in a new module beside the library. No third-party code editor: highlighting comes from Klein's own lexer and underlines from diagnostic spans, drawn under a transparent textarea that does the editing. Inline answers sit after the end of their call's line so the two layers stay aligned.

JS build health (2026-09-24): jsNodeTest passes, 2663 tests, 6 skipped, 0 failures. Unminified dev output is about 1.1 MB for klein-lib plus 1.1 MB stdlib plus 0.3 MB serialization; a real production bundle with dead code removal still to be measured in step 1.

Host API friction found while reading, before any code:
1. Answering a call in Klein goes through compileValue, which takes a release. A parked call belongs to an edition, and an edition has no release. The playground can use the rule's recorded release, but the natural input is the edition's pins. It is also two steps: compileValue, then Klein.execute.
2. Value capabilities cannot be deferred, so a host with a person answering must collect start values before the run starts. Nothing on Edition says which pins are values; the host joins the pins with the contract's declarations itself.
3. Call and Parked carry no source span, although the machine's suspension has one. Showing an answer on its call's line needs it.
4. Klein.checkContract throws on a contract with errors. A live contract editor turns every keystroke with a typo into an exception to catch.
5. Registering an implementation for every declared capability is required even when no rule uses it. Harmless here: the playground registers a deferred handler for each.

Thomas, on finding 1: the edition will soon record its release, to support reconciliation. That removes the playground's need to look up the rule's release. Still open: an answer to a run parked on a release that was retired since.

Thomas, on finding 2: asking every value before the run is the intended model, since the Core reads them first. Downgraded to a small convenience: a helper listing the values an edition pins would save each host the join with the declarations.

Thomas, on finding 2 again: the Core does not show where the atomic start phase ends; it just asks values one by one. Changing lowering to make the value reads one instruction was considered and not wanted. A library helper that derives the needed values from the edition's pins is the lighter route.

Thomas, on finding 3: positions are not stored in the log. They are derived data: edition plus log reproduce them on replay, and storing them risks disagreement with replay and stale positions after migration. Agreed direction: RunOutcome carries one source position per reply, in log order, plus the pending call's position when parked. Start values carry no position. The position travels beside the Call, never inside it, since replay compares calls by equality.

Agreed 2026-09-24: the bridge becomes Klein's own JavaScript binding, a top-level module beside klein-lib, not part of the playground. It mirrors the Kotlin host API in JavaScript style (contract object, environment with handlers, run with log and outcome), targets ES2015 with ES modules so exported classes are real classes. Pin hashes cross as hex strings. Kotlin upgrade (2.0.21 to 2.4.x) is being done separately first; this branch rebases onto main once it lands, then work starts on the binding.

Step 1 built: klein-js with 15 Node tests over the built ES module, all passing. Production library output after dead code removal: 1.1 MB unminified, 179 KB gzipped (klein-lib 736 KB, stdlib 325 KB, serialization 42 KB, binding 33 KB); names are not minified, the app build will do that.

Binding findings (Kotlin/JS, not the host API):
- Sealed classes cannot be export parameter types, so outcomes, log entries and decoded editions are abstract classes with internal constructors; JavaScript tells them apart with instanceof.
- Handler maps, run options, pins and values are typed any in the generated TypeScript; the d.ts also carries a metadata namespace per class.
- Host errors cross as kind and message only; fields such as a divergence's position are dropped for now.

Host API findings:
6. compileValue's output cannot tell a Klein null from no output; only the empty diagnostics do.
7. Value.print exists but nothing prints a value from outside; the binding adds printValue.

Correction on finding 7: Value.print is already public in Kotlin, so there is no host API gap; the binding exposes it as printValue. Thomas confirmed printing belongs in the public API. Finding 6 (null output ambiguity) is being fixed by TASK-50, which makes Checked a sum of accepted and rejected; the binding will mirror it.

Correction on the sealed-class binding finding: not a Kotlin limit. The warning pointed at the sealed class's constructor, which Kotlin exports for subclassing, and whose argument was the library's non-exportable LogEntry. Sealed classes and sealed interfaces work as exported argument types. Dropped from the findings.

Binding typing: handler keys stay plain strings in the Kotlin form (creditScore/2, bare means revision 1). Proposed: typed Value, Handler, RunOptions and pins in the TypeScript surface, via the plain-objects compiler plugin plus a small handwritten d.ts for the unions; awaiting Thomas's answer.

Agreed: typed TypeScript surface via the plain-objects plugin plus a handwritten d.ts that re-exports the generated one, set as the package's types entry; one import for JS and TS users alike.

Binding findings resolved 2026-09-24: registrations mirror Kotlin (immediate, deferred, perRun, implement with transact, run with registrations, log, persist), all typed; pins are a typed array; every host error has its own class with its fields. Values stay any, the most TypeScript can express from Kotlin. Package named klein.

Step 2 started 2026-09-24 while Thomas was offline: klein-playground (Vite 8, Svelte 5, TypeScript) depends on the binding through a file: dependency on its production build. The contract page has a textarea with live checking, the environment, releases and declarations, or diagnostics with line and column. On load it also runs a demo through the whole API and logs to the console (check, compile, park, reply, resume, complete, encoded log). Verified in headless Chromium: all steps log, and a broken contract shows its diagnostic. svelte-check clean; production build 491 KB, 120 KB gzipped. Uncommitted, waiting for Thomas.
Finding: a contract naming an unknown type reports 'Unbound variable: Nope'; the checker calls a type a variable.

2026-09-24 evening: step three (our editor) on hold. A review of klein-js is being worked through with Thomas one finding at a time; findings file from the reviewing session. The branch was rebased onto main, where Checked is now Accepted or Rejected, so the binding does not compile until it mirrors that.

Waiting on the sealed host error task (own branch sealed-host-errors, agent running): once it lands, rebase and drop OtherHostError and the else branch in klein-js, and update klein-js/CLAUDE.md.

2026-09-25: rebased onto main with the sealed host errors. The three new log host errors moved into the root package with the rest. klein-js dropped OtherHostError and its else branch; a new library host error now breaks the binding's compile, and the host error test table covers every kind.

Review finding B1 (host-built tagged values print in the wrong field order) is left to TASK-56, named constructor calls. A printer that reads the types was built and thrown away: Thomas did not want printing to depend on editions or contracts. Until TASK-56 lands, a printed constructor value built by a host may not be valid Klein.
<!-- SECTION:NOTES:END -->
