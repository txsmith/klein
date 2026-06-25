# Test Porting Order (M1)

The order in which the existing type-test suites get ported as the Path G checker is
built (see [path-g-roadmap.md](./path-g-roadmap.md)). Each suite is tagged:

- **KEEP** — ports ~verbatim (verdict-stable; same answers under Path G)
- **REWRITE** — port with annotations added / reframed for bidirectional checking
- **GUARD** — keep only a few tests as a rejection guard; delete the rest
- **DELETE** — gone (tests machinery or programs Path G removes)

The **M7 green-bar** = every KEEP suite green on the new checker before any teardown.

## Already removed

- `TypeSimplifierTest`, `LevelConstraintTest` — simplifier / let-poly levels (deleted).
- `SimpleSubTest` — SimpleSub-only programs (self-application, let-polymorphism,
  inferred connectives) that Path G rejects (deleted).

## Phase A — Substrate (engine-agnostic; port first)

1. `TypeEnvTest` — KEEP/REWRITE. Redesign `TypeEnv` (no levels; `Poly.instantiate` →
   substitution). Defines the env contract the rest depends on.
2. `ScopeGraphTest` — KEEP. Name resolution, escapes, duplicates. Verbatim.
3. `ScopeGraphSccTest` — KEEP. SCC ordering + recursion detection. Verbatim.
4. `TypeAssertions` — ADAPT (helper). Repoint `infer`/`assertType` at the new checker;
   drop `expectedLub` + the LUB overload as REWRITE suites stop asserting simplified forms.

## Phase B — Synth skeleton (no `check`, no real subtyping)

5. `LiteralInferTest` — KEEP. Verbatim; the first target.
6. `IdentInferTest` — KEEP. Env lookup.
7. `RecordInferTest` — KEEP. Record synth + field access.
8. `BindingInferTest` — REWRITE (light). Annotate the `fun f(x)` case (`x: Num`).
9. `BlockInferTest` — REWRITE (light). Blocks → last-expr type.
10. `OperatorInferTest` — KEEP. The primitive-only `isSubtype` sliver (operands `<: Num`/`Bool`).
11. `TopLevelDefinitionTest` — REWRITE. Top-level binding + dependency order; recursion
    requires declared return types.

## Phase C — Subtyping (M3): the `isSubtype` spec

12. `SubtypingTest` — KEEP. The core `isSubtype` spec (width, depth, functions).
13. `NominalStructuralTest` — KEEP. Nominal → structural (records-as-interfaces).
14. `VarianceSubtypingTest` — KEEP. Variance rules for applied types.

## Phase D — Check mode + functions (M2 completion)

15. `FunctionInferTest` — REWRITE. First real `check`: annotated params, body checked
    against return, lambda-in-check vs. synth, `synth(Apply)`.
16. `AnnotationInferTest` — REWRITE. Breadth `check` driver: lift the ~55 check + generics
    cases; add ~3 rejection **GUARD**s for anonymous `&`/`|`; drop ~40 (the polarity matrix
    and the intersection-encoding bounded-poly cases). Shards later into `GenericsTest`
    (Phase F) and a small no-anonymous-connectives guard suite.
17. `ImplicitParamInferTest` — REWRITE. Implicit-param (`.field`) lambdas.

## Phase E — Nominal types & constructors

18. `ConstructorBindingTest` — REWRITE. Constructors as functions; nominal binding.
19. `TypeDefInferenceTest` — REWRITE. Type definitions, generics, variance.
20. `TypeDefErrorTest` — KEEP. Type-def error verdicts.
21. `InferredInterfaceTest` — REWRITE. Sum common-field interface survives; the
    differing-type "unifies via SimpleSub" cases change (join → supertype or error).

## Phase F — Optional & safe access

22. `OptionalSubtypingTest` — KEEP. Optional + subtyping verdicts.
23. `OptionalTypeErrorTest` — KEEP. Optional error verdicts.
24. `OptionalTypeInferTest` — REWRITE. Optional synth.
25. `SafeFieldAccessInferTest` — REWRITE. `?.` navigation.

## Phase G — Joins (M5)

26. `IfThenElseInferTest` — REWRITE (split): check-mode cases land in Phase D; the
    synth-mode join cases (supertype-or-error) land here.
27. `LubGlbSimplificationTest` — REWRITE (the join reference). Reframe the join-to-parent
    and variance-aware arg-join cases as join-or-error tests; drop the bounds/where-clause
    display cases.

## Phase H — Generics (M4)

28. `GenericsTest` (new) — lift `AnnotationInferTest`'s ~20 skolem/`typeVar` cases:
    implicit quantification, rigid-in-body, distinct-skolems, no-leak scoping,
    structural-match instantiation, return-type polymorphism.

## Deferred (M6+)

- **Bounded polymorphism** — fresh `where`-clause tests written from scratch; the old
  intersection-encoding cases are *not* resurrected.
- **First-class intersection** — only if pursued (spec §8); not a current target.
- `UnionIntersectionTypeTest` (parser) — keep iff we parse-then-reject for good errors;
  revisit if we decide not to parse `A|B`/`A&B` at all.
