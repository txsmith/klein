# Surface Spec: Pattern Matching

**Status:** Implemented (lexer, parser, checker; 2026-07-18) — evaluator pending on the
`interpreter` branch · **Branch:** `pattern-matching`

This specifies `match` — the elimination form for nominal sums. It slots into the
bidirectional checker per
[bidirectional-checking.md](./bidirectional-checking.md) §7: check mode
distributes the expected type over the arms, synth mode joins them.

---

## 1. The feature in two programs

Destructuring a sum, one arm per constructor:

```klein
type Shape = Circle { radius: Num } | Square { side: Num } | Tri { base: Num, height: Num }

fun area(s: Shape): Num =
  match s
    Circle { radius } -> 3.14159 * radius * radius
    Square { side } -> side * side
    Tri { base, height } -> base * height / 2
```

Type-checks: each pattern names a constructor of `Shape`, each destructured
field gets its declared type, all constructors are covered.

Naming the matched value without destructuring — a **constructor binder**
`Dog d` binds `d` at the constructor's type:

```klein
type Animal = Dog { name: String, legs: Num } | Cat { name: String, lives: Num } | Snake

fun call(a: Animal): String =
  match a
    Dog d -> d.name      # d : Dog
    Cat c -> c.name      # c : Cat
    Snake -> "hsss"
```

Type-checks: `d : Dog`, so `d.name` is allowed. The scrutinee `a` is **not**
narrowed — `a.name` inside the `Dog` arm is still an error, because `a : Animal`
throughout and the sum's interface has no `name` (`Snake` lacks it). Klein has
no scrutinee flow-narrowing; an arm that wants the matched value binds it (§5).

And the two headline rejections:

```klein
match s                       # s : Shape
  Circle { radius } -> radius
  Square { side } -> side
# error: match is not exhaustive — missing Tri (add an arm or `_ ->`)
```

```klein
match s
  Circle { radius } -> radius
  _ -> 0
  Square { side } -> side
# error: unreachable arm — Square is already covered by `_`
```

## 2. Grammar

```
match_expr  = 'match' expr NEWLINE INDENT arm+ DEDENT

arm         = pattern ('if' expr)? '->' block_or_expr

pattern     = '_'                                    # wildcard
            | literal                                # 42, "yes", true, null
            | IDENT                                  # variable — binds the value
            | UPPER_IDENT (IDENT | record_pattern)?  # constructor: bare, binder, or destructure
            | record_pattern                         # bare record destructure

record_pattern = '{' field_pat (',' field_pat)* '}'

field_pat   = IDENT                                  # pun: binds field to same name
            | IDENT '=' IDENT                        # rename: binds field to new name
```

`_` is valid anywhere a binder is expected and simply doesn't bind: `Dog _` is
the bare constructor pattern, and `{ name = _, age }` requires `name` on the
scrutinee (a `MissingField` error if absent) while binding only `age` — a shape
test without shadowing an outer `name`.

- `match` becomes a keyword (it was previously a legal identifier — breaking
  change for any program using it as a name).
- Arms are **bare**: no `|` or `case` marker; indentation delimits them, like
  `if`/`then` blocks. `->` is already a block starter (grammar rule 2), so an
  arm body can be an indented block.
- Case does the disambiguation the grammar needs: lowercase ident is a variable
  pattern, uppercase is a constructor. After a constructor, a lowercase ident is
  its **binder** (`Dog d`) and `{ … }` is a destructure — the two are mutually
  exclusive (`d.name` already reaches the fields, so there's no need to combine).
- There is **no positional pattern** (`Some(x)`) — see §8 for why.

## 3. Pattern forms — what each matches and binds

| Pattern | Matches | Binds | Refutable? |
|---|---|---|---|
| `_` | anything | nothing | no |
| `x` | anything | `x` = scrutinee value, at its residual type (§5) | no |
| `42`, `"a"`, `true` | that value | nothing | yes |
| `null` | null (scrutinee must be `T?` or `Null`) | nothing | yes |
| `Circle` | that constructor's tag | nothing | yes* |
| `Circle c` | that tag | `c` at the constructor type (`Circle`, with the scrutinee's type args) | yes* |
| `Circle { radius }` | that tag | each named field at its declared (substituted) type | yes* |
| `{ name, age }` | any record with the fields | each named field | no |

\* irrefutable when the scrutinee's type *is* that constructor type already.

- Field puns follow the record-literal shorthand that already exists
  (`{ name }` in a literal means `{ name = name }`). Rename is `{ name = n }`;
  `{ name = _ }` tests the field's presence without binding.
- A record pattern need not list every field — `Tri { base }` is legal and
  ignores `height`. Listing a field the constructor lacks is an error.
- A **bare record pattern** (no constructor) destructures a structural-record
  scrutinee. It is irrefutable: the fields it names must already be in the
  scrutinee's type, or it's a type error — it never *tests* anything.
- Fields destructure to **variables only** in v1. Nested patterns
  (`Cons { head = Circle { radius } }`) are deferred — see §8.

## 4. Typing the match expression

Scrutinee: always synthesized (`e ⇒ S`). `S` must be one of: a nominal sum or
constructor type, `T?`, `Bool`, `Num`, `String`, or a record type. Matching on
a function, tuple, skolem (`'T` is passable, not inspectable), or `Any` is an
error.

The one skolem exception: **`'T?` is matchable**, because the null test is the
optional's own discriminator and never inspects the skolem. Only `null` and
default arms apply — a constructor, literal, or record pattern on the core
would inspect `'T` and errors as usual:

```klein
fun orElse(d: 'T, x: 'T?): 'T = match x
  null -> d
  y -> y          # y : 'T — the residual after null
```

Arms, by mode — exactly the `if` discipline from the bidi spec §7:

- **Check mode** (`match … ⇐ T`): check every arm body `⇐ T`. No join computed.
- **Synth mode**: synthesize every arm body and fold them through `lub`.
  `Subtyping.lub` already promotes sibling constructors to their parent
  (`Circle ⊔ Square = Shape`) and reports `Failure`s; a failed join is a type
  error naming the two arm types — never a silent `Any`.

```klein
x = match s              # synth mode
  Circle { radius } -> Circle(radius * 2)
  Square { side } -> Square(side * 2)
  Tri -> s
# x : Shape   (Circle ⊔ Square ⊔ Tri)

y = match s
  Circle -> 1
  _ -> "big"
# error: branches have incompatible types Num and String
```

(`synthIfThenElse` already joins via `lub` — the bidi spec's §7 status note was
stale. Match reuses the same grounding + `lub` approach in `joinMatchArms`.)

Guards: `pattern if cond -> body` — `cond ⇐ Bool`, checked in the arm's
environment (the pattern's bindings are in scope in the guard).

Constructor patterns must name a constructor **of the scrutinee's type**:

```klein
match s                  # s : Shape
  Dog -> 1               # error: Dog is not a constructor of Shape
  ...
```

## 5. Binding the matched value

**Klein has no scrutinee flow-narrowing.** The scrutinee's own type never
changes inside an arm — `match a` with `a : Animal` leaves `a : Animal` in every
arm, `Dog` arm included. An arm that wants the matched value at a more precise
type **names it**, one of two ways:

- **Constructor binder** `Dog d` — binds `d` at the constructor type.
  Generic sums substitute the scrutinee's type arguments: `match r`
  (`r : Result<Num, String>`), arm `Ok o ->` binds `o : Ok<Num>`; arm
  `Ok { value } ->` binds `value : Num` the same way.
- **Variable pattern** `x` — binds `x` at the scrutinee's **residual type**: the
  scrutinee type as-is, except the `?` is dropped once an earlier unguarded arm
  has covered `null`. This is what makes an optional non-sum matchable at all:

  ```klein
  fun f(n: Num?): Num = match n
    null -> 0
    x -> x + 1     # x : Num — null was already taken, so the residual is Num
  ```

The residual is **not** flow-narrowing of an existing variable — it's the type
the *new* binder `x` receives, computed left-to-right across the arms (the same
order-dependence exhaustiveness already has). No pre-existing variable's type is
ever mutated, so there is no soundness question and no `if x is Dog`-style
narrowing anywhere else in the language. `?.` remains the everyday optional
accessor.

Consequences worth stating:

- `Dog -> a.name` does **not** type-check (`a` is still `Animal`, whose interface
  lacks `name`); write `Dog d -> d.name`.
- `_ -> n + 1` on `n : Num?` does **not** type-check even after a `null` arm (the
  wildcard binds nothing, so `n` stays `Num?`); write `x -> x + 1`.
- `match a.pet` matches fine but there's nothing to name via a constructor binder
  unless you also destructure — the residual rule only ever removes `null`.

## 6. Exhaustiveness (hard error) and reachability

A match must cover its scrutinee. Coverage is computed from **unguarded** arms
only — a guarded arm never counts, since its guard may be false.

Per scrutinee type:

- **Nominal sum:** every constructor covered by a constructor pattern, or a
  default arm (`_` or variable) present. Scrutinee already a constructor type:
  that one constructor.
- **`T?`:** `null` covered *and* the non-null core covered (or a default arm).
- **`Bool`:** `true` and `false`, or a default.
- **`Num`, `String`:** a default arm is always required — literals never
  exhaust them.
- **Record:** a bare record pattern is irrefutable, so any unguarded
  record/variable/wildcard arm suffices.

Non-exhaustive ⇒ **type error**, listing the missing constructors. The opt-out
is explicit: add `_ -> …`. There is no runtime match-failure path; the
evaluator can treat a fall-through as an internal invariant violation.

Reachability is the dual, same strictness: an arm whose coverage is already
contained in the preceding unguarded arms' coverage is an **unreachable-arm
error** (second `Circle` arm, ctor arm after `_`, arm after an irrefutable
variable arm). A *guarded* arm on an already-covered tag is fine — guards are
the intended way to have two arms on one constructor:

```klein
match s
  Circle { radius } if radius > 10 -> "big circle"
  Circle -> "circle"
  _ -> "not a circle"
```

Because v1 patterns are flat (no nesting), coverage is literally a set of
constructor tags (plus null/true/false) — no pattern-matrix algorithm needed
yet. The usefulness-matrix approach (Maranget) becomes necessary only when
nested patterns land (§8).

## 7. Runtime semantics

First-match, top to bottom: test the scrutinee's tag (and literal equality /
null-ness, then the guard) against each arm in order; evaluate the chosen
arm's body. Record destructuring is field lookup. The scrutinee is evaluated
exactly once. (Evaluator work happens on the `interpreter` branch; this branch
delivers lexer, parser, and checker.)

## 8. What you can't write (and why)

- **Positional patterns** — `Some(x)`, `Cons(h, t)`: rejected for v1. Klein
  construction is positional, so this form is defensible on symmetry grounds,
  but two destructuring forms is more surface than the value it adds; the
  record form self-documents field names. Revisit if `Option`/`Result`
  ergonomics hurt (`Ok { value }` vs `Ok(v)`). Spread and `~` are the intended
  relief (see the resolved question below).
- **Nested patterns** — `Cons { head = Circle { radius } }`: deferred. The
  grammar slot is reserved (`field_pat` grows a `IDENT '=' pattern` form);
  exhaustiveness then needs the usefulness matrix. Until then: match the field
  in a nested `match`.
- **Tuple patterns** — deferred with the rest of tuple surface (roadmap
  Phase 3 accessors).
- **Matching a `'T` skolem** — error; rigid type variables are passable, not
  inspectable (bidi spec §6).
- **Or-patterns** (`Circle | Square ->`) — not planned; no anonymous unions is a
  standing decision. (A dedicated as-pattern keyword is unnecessary now that a
  constructor binder `Dog d` names the value directly.)

## 9. Implementation plan

1. **Lexer + AST + parser** — `match` keyword; `Match(scrutinee, arms)`,
   `Arm(pattern, guard, body)`, `Pattern` hierarchy; arm block parsing per the
   indentation model; grammar.md update.
2. **Checker** — pattern well-formedness against the scrutinee type, binding
   the pattern's names into the arm environment (no scrutinee narrowing),
   check/synth modes, exhaustiveness +
   reachability. New `TypeError` cases: not-a-constructor-of, missing-field,
   non-exhaustive (with missing set), unreachable arm, unmatchable scrutinee.
3. **Joins** — match synth joins through `lub`, mirroring `synthIfThenElse`
   (which already used it — the spec §7 status note was stale).
4. **Interpreter** — on the `interpreter` branch once 1–3 merge.

Steps 1–3 are done (TDD: red suites in `parser/MatchTest` and
`check/MatchTypeCheckTest`, then the implementation).

## Open questions

1. ~~**`Option`/`Result` ergonomics without positional patterns**~~ —
   *Resolved 2026-07-18:* the verbosity of `Ok { value } -> value` is accepted.
   Record spread (`...`) and the `~` operator (roadmap Phase 3) are the
   intended ergonomic relief, not positional patterns.
2. **Polymorphic arms** — same open sub-case as `if` (spec §7): joining two
   `∀`-typed arm bodies. Adopt whatever `if` decides (α-equal → that scheme,
   else reject).
3. **Guard-aware exhaustiveness** — `x > 0` / `x <= 0` guard pairs never count
   as coverage. Fine (standard), but worth saying in error messages ("guarded
   arms don't count toward exhaustiveness").
