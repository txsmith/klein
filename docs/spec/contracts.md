# Capability Contracts

A **contract** declares what a host application provides to Klein programs: type
definitions and signatures, with no implementations. Klein checks a contract in its own
mode, and the environment it produces is what programs are then checked against.

This spec covers the language and checker side. How a contract is located, versioned, or
bound to implementations is the host's business — see
[ideas/host-integration.md](../ideas/host-integration.md).

## The file

```klein
type Customer = Customer { id: Num, name: String, score: Num }

fun creditCheck(c: Customer): Num
maxRetries: Num
```

Declarations without definitions: a `fun` header with no `= body`, a binding with no
`= value`. The syntax is ordinary Klein with the definition removed — no keyword, no new
tokens; see [grammar.md](../grammar.md) for the productions and their disambiguation.

In spirit this is an OCaml `.mli` or a TypeScript `.d.ts`, but closer in role to an IDL:
it is not the interface *of* some implementation file, it is a standalone declaration of
a boundary. Any host language can therefore produce and consume one through the parser
already in the library, without a host-language-specific API.

## Two modes, one grammar

The parser has no idea which kind of file it is reading. The checker does, through two
entry points:

| Statement | `Klein.check` (program) | `Klein.checkContract` (contract) |
|-----------|-------------------------|----------------------------------|
| `type_def` | allowed | allowed |
| `fun_decl`, `val_decl` | `DeclarationWithoutBody` | allowed — this is the point |
| `fun_def`, `binding` | allowed | `DefinitionInContract` |
| bare `expr` | allowed — it is the result | `ExpressionInContract` |

Each direction for one reason:

- A **declaration in a program** has nothing to run. Only a contract's declarations are
  answered by something outside the program.
- A **definition in a contract** computes, and a contract declares rather than computes.
  (Klein code shared between programs is a module, which is a separate mechanism.)
- A **bare expression in a program** is its result; a contract has no result.

All three are type errors, not parse errors, so checking continues and the rest of the
file is still diagnosed.

## What a contract produces

```kotlin
Klein.checkContract(program, env): StageResult<TypeEnv>
```

The environment *is* the product, and it is built by exactly the machinery a program
gets — contract mode runs the same type-definition preprocessing, so a contract's types
are as thoroughly checked as a program's:

- **Type names are registered**, so later declarations may refer to them
  (`fun creditCheck(c: Customer): Num` resolves `Customer`), and a name that isn't
  declared anywhere is `UnboundVariable`.
- **Constructors are visited**: every field's type is resolved, so
  `type Bad = Bad { x: Nope }` is an error in a contract just as in a program. Variance
  is inferred and the nominal interfaces are built the same way.
- **Constructors are bound as callables**, which is what lets the *program* checked
  against this environment write `Customer(1, "ada")`.

On top of that, each declaration binds its signature, and a name declared twice is
`DuplicateBinding`.

## Revisions

A declaration may carry a revision — `type Customer/2`, `fun creditScore/2`, `maxRetries/2` — so
two incompatible versions coexist in one file while the old one drains. The syntax is in
[grammar.md](../grammar.md); what the checker does with it is one rule:

**The key of a declaration is (name, revision), and revision 1 is the bare name.**

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer): Num
fun creditScore/2(c: Customer/2): Num
```

Everything follows from that key. `Customer` and `Customer/2` are two entries in the environment,
so they coexist; declaring `Customer/2` twice, or `creditScore` alongside `creditScore/1`, is
`DuplicateBinding`. A revised type definition revises its constructors with it — `Customer/2`'s
constructor is `Customer/2`, and `type Shape/2 = Circle { … } | Square { … }` yields `Circle/2` and
`Square/2` — which is what lets both revisions of a type keep the same constructor names. Two
revisions of a type are unrelated nominal types: nothing is inherited, and `Customer/2` is a
subtype of `Customer` only in the way any two unrelated types are (it isn't).

A revised signature is checked exactly like any other: its parameter and return types resolve
against the revisions they name (`Customer/3` with no such declaration is `UnboundVariable`), a
revised capability may not carry a function, and a revision on a builtin (`Num/2`) names nothing.

**Which revision a *rule* gets when it writes a bare `creditScore`** is not decided here. The bare
name is bound to revision 1 because revision 1 *is* the bare name; resolution across revisions
belongs to the reconciler and the edition machinery, outside the checker.

### `review`

`fun underwrite/2(a: Application): Decision review` parses and is carried on the declaration node.
It changes no typing whatsoever: it exists to tell the reconciler that the meaning changed while
the types did not, which nothing else can detect.

### One restriction: no functions cross the boundary

A capability may not carry a Klein function — not as a parameter, not as a result, and
not nested inside a type it mentions:

```klein
fun sortBy(xs: List<Num>, key: (Num) -> Num): List<Num>   # FunctionTypeInCapability
fun adder(n: Num): (Num) -> Num                           # FunctionTypeInCapability
callback: (Num) -> Num                                    # FunctionTypeInCapability

type Handler = Handler { run: (Num) -> Num }
fun register(h: Handler): Num                             # FunctionTypeInCapability
```

A Klein function's only meaning is that the machine can run it, and whatever answers a
capability is not the machine — a handler holding a closure could never call it. The
check follows type references into their constructors' fields, so hiding a function one
level down does not evade it; recursive types terminate on a visited set. Records of
functions remain perfectly legal *inside* Klein; the restriction is only on what crosses
to the host.

Polymorphic signatures, by contrast, are fine: `fun first(xs: List<'A>): 'A` is a legal
capability. A handler for it cannot inspect what it receives, which is exactly what the
type promises. Checking a program against a contract is therefore a composition of
the two stages:

```kotlin
Klein.checkContract(contract).andThen { env -> Klein.check(program, env) }
```

## Errors are recorded, bindings are kept

In program mode a declaration records `DeclarationWithoutBody` **and still binds its
signature**. Otherwise every use of the declared name cascades into a spurious
`UnboundVariable`. So:

```klein
fun creditCheck(c: Num): Num
creditCheck("nope")
```

yields exactly two honest errors — the declaration, and the argument mismatch — rather
than one real error plus noise.

## Not covered here

Capability identity, handler registration, advertisements, editions and pins, revision
*resolution*, and the CLI surface for contracts all live outside the checker. See
[ideas/host-integration.md](../ideas/host-integration.md).
