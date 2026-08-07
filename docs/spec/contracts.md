# Capability Contracts

A **contract** declares what a host application provides to Klein programs: type definitions and
signatures, with no implementations. Klein checks a contract in its own mode, and what rules can
see is governed by the contract's `expose` lines.

This spec describes the contract language as v1 must deliver it. Each section carries a status:
**implemented** means the test suites enforce it today; **target** means it is settled design the
implementation still has to reach. The system around the language — environments, editions, pins,
reconciliation — is [host-integration.md](./host-integration.md).

## The file

Status: implemented.

```klein
type Customer = Customer { id: Num, name: String, score: Num }

fun creditCheck(c: Customer): Num
maxRetries: Num
```

Declarations without definitions: a `fun` header with no `= body`, a binding with no `= value`.
The syntax is ordinary Klein with the definition removed — no keyword, no new tokens; see
[grammar.md](../grammar.md) for the productions and their disambiguation.

In spirit this is an OCaml `.mli` or a TypeScript `.d.ts`, but closer in role to an IDL: it is
not the interface *of* some implementation file, it is a standalone declaration of a boundary.
Any host language can therefore produce and consume one through the parser already in the
library, without a host-language-specific API.

## Two modes, one grammar

Status: implemented, except the `expose` row (target).

The parser has no idea which kind of file it is reading. The checker does, through two entry
points:

| Statement | `Klein.check` (program) | `Klein.checkContract` (contract) |
|-----------|-------------------------|----------------------------------|
| `type_def` | allowed | allowed |
| `fun_decl`, `val_decl` | `DeclarationWithoutBody` | allowed — this is the point |
| `fun_def`, `binding` | allowed | `DefinitionInContract` |
| bare `expr` | allowed — it is the result | `ExpressionInContract` |
| `expose` | error | allowed |

Each direction for one reason:

- A **declaration in a program** has nothing to run. Only a contract's declarations are answered
  by something outside the program.
- A **definition in a contract** computes, and a contract declares rather than computes.
- A **bare expression in a program** is its result; a contract has no result.

All of these are type errors, not parse errors, so checking continues and the rest of the file is
still diagnosed.

## Revisions

Status: implemented.

A declaration may carry a revision — `type Customer/2`, `fun creditScore/2`, `maxRetries/2` — so
two incompatible versions coexist in one file while the old one drains. The syntax is in
[grammar.md](../grammar.md); what the checker does with it is one rule:

**The key of a declaration is (name, revision), and a bare declaration is revision 1.**

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer): Num
fun creditScore/2(c: Customer/2): Num
```

Everything follows from that key. `Customer` and `Customer/2` are two entries in the environment,
so they coexist; declaring `Customer/2` twice, or `creditScore` alongside `creditScore/1`, is
`DuplicateBinding`. A revised type definition revises its constructors with it — `Customer/2`'s
constructor is `Customer/2`, and `type Shape/2 = Circle { … } | Square { … }` yields `Circle/2`
and `Square/2` — which is what lets both revisions of a type keep the same constructor names. Two
revisions of a type are unrelated nominal types: nothing is inherited, and neither is a subtype
of the other.

A revised signature is checked exactly like any other: its parameter and return types resolve
against the revisions they name (`Customer/3` with no such declaration is `UnboundVariable`), a
revised capability may not carry a function, and a revision on a builtin (`Num/2`) names nothing.

Revision syntax is contract-only. A program that writes `/N` — in a type position or anywhere
else — is rejected (`RevisionInProgram`), and a written `/1` is rejected the same as any other:
the offence is the syntax, not the number. What a rule sees is governed entirely by exposure,
below.

## Exposure and tags

Status: target.

An `expose` statement aims a rule-facing name at a declared revision. A rule-facing name is a
**tag**, spelled either bare (`Customer`) or qualified (`Customer@legacy`):

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer/1): Num
fun creditScore/2(c: Customer/2): Num

expose Customer/2 as Customer
expose Customer/1 as Customer@legacy
expose creditScore/2 as creditScore
```

Rules mention tags only. Checking a rule against a contract binds each exposed tag, under the
tag's spelling, to the declaration its `expose` line names. Nothing unexposed is reachable from a
rule, and no `/N` marker ever appears in a rule-facing type or diagnostic.

The rules of exposure:

- A tag may only point at a revision the same file declares. Anything else is a check error.
- A name with a single declared revision is implicitly exposed bare, so a contract that never
  versions anything never mentions exposure.
- Declaring a second revision cancels the implicit exposure. From then on the name's exposure
  must be written out; `Customer/2` declared with no `expose` line for the `Customer` lineage is
  a check error.
- Leaving an old revision unexposed is legal. That is what removes it from rule vocabulary.
- An exposed capability's signature may only mention revisions that some tag reaches, in
  parameter and return positions alike. Anything else is a check error. This closes the exposed
  surface: every type a rule can encounter has a tag, so diagnostics and tooling always have a
  rule-legal spelling and never print `/N`. When several tags reach one revision, the bare tag
  wins the printing.
- Constructors are exposed through their type's tag and carry its qualifier. Exposing `Shape/2
  as Shape` makes its constructors `Circle` and `Square`; exposing `Shape/1 as Shape@legacy`
  makes revision 1's constructors `Circle@legacy` and `Square@legacy`. There is no
  per-constructor exposure: constructors version with their type and reach rules only through
  its tags.

Tags and declarations are two namespaces that never share a context. A name in a capability
signature is a declaration reference; a name in a rule is a tag; `expose` is the one statement
where both appear, declaration on the left of `as`, tag on the right. A bare declaration
reference in a signature means revision 1 and is permitted only while the name has a single
declared revision; declaring a second forces the older references to be spelled `/1`. That edit
changes spelling, not meaning.

## One restriction: no functions cross the boundary

Status: implemented.

A capability may not carry a Klein function — not as a parameter, not as a result, and not
nested inside a type it mentions:

```klein
fun sortBy(xs: List<Num>, key: (Num) -> Num): List<Num>   # FunctionTypeInCapability
fun adder(n: Num): (Num) -> Num                           # FunctionTypeInCapability
callback: (Num) -> Num                                    # FunctionTypeInCapability

type Handler = Handler { run: (Num) -> Num }
fun register(h: Handler): Num                             # FunctionTypeInCapability
```

A Klein function's only meaning is that the interpreter can run it, and whatever answers a
capability is not the interpreter — a handler holding a closure could never call it. The check
follows type references into their constructors' fields, so hiding a function one level down does
not evade it; recursive types terminate on a visited set. Records of functions remain perfectly
legal *inside* Klein; the restriction is only on what crosses to the host.

Polymorphic signatures, by contrast, are fine: `fun first(xs: List<'A>): 'A` is a legal
capability. A handler for it cannot inspect what it receives, which is exactly what the type
promises.

## The host sees exactly the declared shape

Status: ruling — binds the runtime boundary when capabilities become callable.

Klein lets a rule pass a *wider* record than a signature asks for; inside Klein the extra fields
physically travel along. Crossing to the host, only the declared fields cross — echo the value
back and the extras are gone:

```klein
type Wrap = Wrap { tags: { a: Num } }
w = Wrap({ a = 1, b = 99 })      # legal: { a, b } is usable as { a }
echo(w).tags == w.tags           # false: the round trip dropped b
```

This is not a broken promise, because the type never made one — a plain Klein function with the
same signature may do exactly the same thing (`fun echo(w: Wrap): Wrap = Wrap({ a = w.tags.a })`),
and no caller can tell the two apart: undeclared fields are unreachable through the narrowed type,
so the only instrument that notices is `==`, and both versions give the same `==` results. The
boundary promises what the types promise. Authors who need a field to survive a host round trip
put it in the declared type — where it had to be anyway for the host to be allowed to look at it.

## Errors are recorded, bindings are kept

Status: implemented.

In program mode a declaration records `DeclarationWithoutBody` **and still binds its signature**.
Otherwise every use of the declared name cascades into a spurious `UnboundVariable`. So:

```klein
fun creditCheck(c: Num): Num
creditCheck("nope")
```

yields exactly two honest errors — the declaration, and the argument mismatch — rather than one
real error plus noise.

## Not covered here

Capability identity, implementation registration, serving, editions and pins,
reconciliation, and the CLI surface for contracts all live outside the checker. See
[host-integration.md](./host-integration.md).
