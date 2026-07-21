# Surface Spec: Destructuring Bindings

**Status:** Implemented (parser + checker, 2026-07-21) — evaluator pending with the
interpreter · **Branch:** `destructuring-bindings`

A binding's left-hand side may be a **record pattern**. The pattern's forms,
bindings, and refutability are exactly those of
[pattern-matching.md](./pattern-matching.md) §3 — this spec adds one rule on
top: **a binding pattern must be irrefutable for the right-hand side's type.**
No `match`, no new keyword — bindings stay bare.

---

## 1. The feature in two programs

```klein
type Person = Person { name: String, age: Num }

{ name, age } = person           # name : String, age : Num
{ min = lo, max = hi } = bounds(xs)
hi - lo
```

Type-checks: each named field is projected from the right-hand side's type
(same rule as `expr.field`), puns and renames as in match arms.

The headline rejection — a pattern that could fail has no home in a binding:

```klein
{ name } = maybePerson           # maybePerson : Person?
# error: refutable pattern binding — a 'Person?' may be null; use match
```

## 2. Grammar

```
binding     = IDENT (':' type)? '=' block_or_expr
            | record_pattern '=' block_or_expr
```

- Disambiguation at statement start: a record pattern (`{ … }`), a constructor
  destructure (`Person { … }`), or a constructor binder (`Circle c`) followed by
  `=` (not `==`) is a destructuring binding; anything else parses as today.
  (`{ a } == b` stays a comparison; a bare `{ a = 1 }` statement stays a
  discarded literal; bare `Circle = x` is not claimed.) This stays decidable
  when structural calls land: a call statement followed by `=` is invalid, so
  `Person { name } = e` can only be the binding.
- **No type annotation on a pattern binding** — the field types come from the
  right-hand side; `{ name }: Person = e` is a parse error.
- Allowed at top level and in blocks alike.

Constructor left-hand sides follow the same irrefutability rule — legal exactly
when the type has that one constructor:

```klein
Person { name } = someone        # ok: Person is single-constructor
Circle c = c0                    # ok when c0 : Circle — binds c : Circle
Circle { radius } = s            # s : Shape — error: refutable, may be Square, Tri
```

## 3. Typing

Synthesize the right-hand side, then check the pattern against it exactly as a
match arm's pattern would be — the pattern must be **irrefutable**: as a single
unguarded arm, it would exhaust the type.

- **Record RHS:** every named field must exist at its type (`MissingField`
  otherwise); width applies — unnamed fields are ignored.
- **Nominal RHS:** fields project through the interface, so
  `{ name } = someone` works on a `Person`, and even on a *sum* whose
  constructors all carry the field:

  ```klein
  type Pet = Dog { name: String, legs: Num } | Cat { name: String, lives: Num }
  { name } = somePet               # ok: every Pet has name
  { legs } = somePet               # error: Pet has no field 'legs' (Cat lacks it)
  ```

- **Optional RHS:** refutable — the null case is uncovered; use `match`.
- **Non-record RHS** (`Num`, functions, …): `NotARecord`, per field.
- `{ name = _ }` requires the field and binds nothing, as in match arms.

## 4. Scoping

The bound names behave as sibling `val`s introduced together, after the
right-hand side:

- Sequential like any `val`: visible to later statements, not earlier ones.
- The right-hand side's references resolve **outward**, never to the names
  being bound — `{ a } = f(a)` uses an enclosing `a` or is unbound, exactly
  like `x = x + 1` today.
- Each bound name participates in duplicate detection individually:
  `{ a = x, b = x } = p` and `x = 1` followed by `{ x } = p` are both
  duplicate-binding errors.

## 5. What you can't write (and why)

- **Refutable patterns** — anything short of full coverage: `{ name }` on an
  optional, a constructor of a multi-constructor sum, or (future, with nested
  patterns) a refutable field. The error names the uncovered cases, mirroring
  the non-exhaustive-match error.
- **Bare variable or wildcard as the whole pattern** — `x = e` is already a
  binding, `_ = e` is already rejected; only record patterns add anything.
- **Lambda-parameter patterns** (`|{ name } -> name|`) — same rule, separate
  follow-up; it touches the parameter surface, not bindings.
