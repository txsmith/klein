# Klein Type System Reading List

A curated reading list for implementing Klein's type system, focusing on algebraic subtyping with structural records and nominal types.

## Tier 1: Essential Reading

### The Simple Essence of Algebraic Subtyping
**Lionel Parreaux, ICFP 2020 (Functional Pearl)**

The starting point. Presents SimpleSub — a ~500 line algorithm that makes MLsub accessible. Covers:

- Constraint-based inference without biunification
- How to handle type variable bounds
- Let polymorphism with levels
- Type coalescing and simplification (CompactType)

| Resource | Link |
|----------|------|
| Paper | https://lptk.github.io/simple-sub-paper |
| Code | https://github.com/LPTK/simple-sub |
| Demo | https://lptk.github.io/simple-sub/ |

---

### MLstruct: Principal Type Inference in a Boolean Algebra of Structural Types
**Lionel Parreaux & Chun Yin Chau, OOPSLA 2022**

Adds nominal tags to SimpleSub. This is where structural classes (tagged records) come from. Shows how to:

- Add class tags that intersect with structural record types
- Pattern match on tags
- Keep principal type inference

Most relevant for understanding how Klein's nominal types could subsume structural ones.

| Resource | Link |
|----------|------|
| Paper | https://lptk.github.io/files/mlstruct.pdf |
| Code | https://github.com/hkust-taco/mlstruct |
| Demo | https://hkust-taco.github.io/mlstruct |

---

## Tier 2: Useful Background

### Algebraic Subtyping
**Stephen Dolan, PhD Thesis 2017**

The original. More theoretical, uses automata and biunification. Harder to read than SimpleSub but provides deeper foundations. Read to understand *why* the algebra works.

| Resource | Link |
|----------|------|
| Thesis | https://www.cl.cam.ac.uk/~sd601/thesis.pdf |
| Demo | https://www.cl.cam.ac.uk/~sd601/mlsub/ |

---

### Extensible Records with Scoped Labels
**Daan Leijen, 2005**

Not part of the MLsub family, but the classic paper on row polymorphism. Useful for understanding the alternative approach to extensible records that Klein's design references.

| Resource | Link |
|----------|------|
| Paper | https://www.microsoft.com/en-us/research/publication/extensible-records-with-scoped-labels/ |

---

## Tier 3: Advanced / Optional

### The Simple Essence of Boolean-Algebraic Subtyping
**Chun Yin Chau & Lionel Parreaux, POPL 2026**

Deep dive into *why* Boolean-algebraic subtyping is sound. Proves the theory is NP-hard. Read for rigorous foundations; skip if you just want to build something.

---

### When Subtyping Constraints Liberate: A Novel Approach to First-Class Polymorphism
**Parreaux, Boruch-Gruszecki, Fan & Chau, POPL 2024**

SuperF — extends MLsub with impredicative/first-class polymorphism via multi-bounded type variables. Not needed for Klein — target users don't need rank-2 types.

---

### The Ultimate Conditional Syntax
**Luyu Cheng & Lionel Parreaux, OOPSLA 2024 (Distinguished Paper)**

UCS — unified conditionals and pattern matching. Nice ergonomics paper. Not essential for Klein's core, but interesting for evolving the `match` syntax later.

---

## Tier 4: Alternative Implementations

### cubiml
**Robert Grosse**

A different take on MLsub — O(n³) cubic biunification algorithm in Rust. Excellent blog series walking through the implementation step by step.

| Resource | Link |
|----------|------|
| Code | https://github.com/Storyyeller/cubiml-demo |
| Blog series | https://blog.polybdenum.com/ |

---

## Suggested Reading Order

1. **SimpleSub paper** — core algorithm, let-polymorphism, simplification
2. **SimpleSub source code** — see it working (~500 lines Scala)
3. **MLstruct paper §1-2** — understand tagged records / structural classes
4. **Dolan thesis §1-3** — deeper understanding (optional)
