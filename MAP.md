# Klein

Orientation only: areas and open questions, not work items. Bounded work lives in the
backlog (`backlog task list --plain`); labels join the two — an area's label returns
everything hanging off it.

- **Language & types** → `docs/roadmap.md`, `docs/implementation-status.md` — labels `language`, `parser`, `checker`
  - Kleene types — research, `docs/kleene-types-experimental.md`
  - Polymorphism bugs — two known checker gaps, `docs/implementation-status.md` §Known gaps
- **Host boundary** → `docs/host-integration-roadmap.md` (v1) — labels `host-boundary`, `contracts`  ← you are here
  - `docs/spec/contracts.md`, `docs/spec/host-integration.md`, `docs/spec/effect-log.md`
  - Parked-run migration — post-v1, `docs/ideas/suspended-run-migration.md`
- **Execution & performance** → `docs/performance-debt.md` — labels `perf`, `execution`, `lowering`
- **Tooling** — `tree-sitter-klein`, `klein-bench`, formatter — label `tooling` (no owning doc yet)
