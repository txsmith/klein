# Brief: how Klein versions the host boundary
Context for an outside comparison (e.g. how BPMN engines handle the same problems).
Klein is an embedded expression language: business users write rules in a web editor;
the host application executes them. Rules can suspend on host calls for weeks, so a
running instance may outlive several host deployments. The host declares its offering
as a **contract**: a file of type definitions and capability signatures (functions the
host answers, values it supplies per run) with no bodies.

## The problems

1. **Two independently evolving sides.** The host's capabilities and the ruleset
   change on different schedules, owned by different people. Neither side can be
   forced to migrate atomically: there may be thousands of rules, and suspended
   runs that must resume against exactly the world they were started in.

2. **Long-lived suspended runs.** A run parked on a host call owns the version it
   was compiled against. The host may not remove an old capability implementation
   while any run can still resume into it. This forces coexistence of versions,
   and some notion of drain ("no runs reference this anymore, safe to delete").

3. **Semantic change is invisible to types.** Swapping the credit bureau behind an
   unchanged `creditScore(c: Customer): Num` signature changes what results mean.
   No checker can detect it, so the versioning scheme must let a human *declare* it.

4. **Rollback ambiguity.** If version labels can be reused or renumbered, an
   automated migrator cannot distinguish "v2 was rolled back" from "v1 was retired
   and v2 renamed to v1". Labels must be stable forever or automation misfires.

5. **Cascade coherence.** Types appear in many capability signatures. Changing the
   `Customer` type touches every capability mentioning it; a scheme that versions
   declarations independently must decide how a rule can hold a `Customer` value
   and pass it to several capabilities that must agree on which `Customer` that is.

## Where we landed

- **Revisions in syntax, on names.** A declaration carries a revision marker:
  `type Customer/2`, `fun creditScore/2(c: Customer/2): Num`. Absent means 1.
  Old and new revisions coexist in one contract file; the host registers an
  implementation per declared revision. Revision numbers are permanent and
  monotonic — never reused, never renumbered (problem 4).

- **Revisions are ordinary names, rule-side too.** `Customer` and `Customer/2`
  are distinct nominal types; rules may reference either, or both, and bridge
  explicitly. Coherence (problem 5) is enforced by the type system, not by a
  versioning mechanism: you cannot pass one revision where the other is expected.

- **Type definitions are invariant.** Any edit to a type definition requires a new
  revision. Capability signatures may change *compatibly* in place (over unmoved
  types) without a revision.

- **Recompilation is the compatibility verdict.** When a signature changes in
  place, each affected rule's source is rechecked against the new contract; a
  clean check both proves compatibility for that rule and *is* the new compiled
  artifact. There is no signature-level subtype shortcut — it cannot see
  compiled-in facts like constructor arity or match exhaustiveness.

- **A content hash prunes.** Compiled rules pin the signature hash of what they
  compiled against; the incremental reconciler skips everything whose hash is
  unchanged. The hash detects *that* something changed, never decides *whether*
  it is compatible. It also trips on illegal in-place incompatible edits.

- **Semantic change = bare revision bump; the safe case is marked.** A revision
  with no marker means "meaning may have changed" — affected rules queue for
  human migration (problem 3). A revision marked `mechanical` attests the
  reshaping preserves meaning, licensing the system to rewrite rule source
  (`/1` → `/2` on the affected names), recheck, and commit it as a
  machine-authored version. The default fails safe: a forgotten marker causes
  needless manual review, not a silent semantic swap.

- **Lifecycle: retire → drain → remove.** Retiring a revision stops new rules
  from targeting it (reversible); suspended runs drain at their own pace;
  removal waits for drain. Compiled artifacts are additive and never edited.

## Questions for the other space

- How do BPMN engines version the *service/connector interface* (our contract), as
  opposed to the process definition (our rule)?
- Do they support anything like the mechanical/semantic distinction, or is every
  interface change treated the same?
- How do they handle in-flight instances across incompatible interface changes —
  migration tooling, coexistence, or forced completion?
- Do any avoid permanent monotonic version labels, and if so how do they resolve
  the rollback-vs-renumber ambiguity?
