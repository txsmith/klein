# Type Annotations

## Syntax

Three places where type annotations can appear:

```klein
# Parameter annotations
fun double(x: Num) = x * 2

# Return type annotations
fun double(x: Num): Num = x * 2

# Val binding annotations
x: Num = 42

# Expression ascription
y = (Dog("Rex", 3) : Animal)
```

Annotations are always optional. Unannotated code works exactly as today.

Grammar additions:

```
fun_def     = 'fun' IDENT '(' annotated_params? ')' return_type? '=' block_or_expr

annotated_params = annotated_param (',' annotated_param)*
annotated_param  = IDENT (':' type)?

return_type = ':' type

binding     = IDENT (':' type)? '=' block_or_expr

ascription  = atom ':' type          # within a parenthesized expression
```

Mixed annotated and unannotated parameters are allowed: `fun f(x: Num, y) = ...`

## Decision

Annotations fix the type to exactly what was declared. The annotation type is placed directly in the type environment. The RHS/body is inferred separately and checked via `constrain(inferred, annotated)`.

This follows MLscript's approach: simple, predictable, and well-understood in the SimpleSub lineage.

### What this means in practice

Consider:

```klein
type Animal = Dog { name: String, tricks: Num } | Cat { name: String }

fun wrap(x: Animal): Animal = x
```

Without annotations, `fun wrap(x) = x` would infer `('A) -> 'A` — a polymorphic identity function. Calling `wrap(Dog("Rex", 3))` would return type `Dog` and you could access `.tricks` on the result.

With annotations, `wrap` has type `(Animal) -> Animal`. Three things happen:

1. **Parameter**: inside the body, `x` is `Animal`. The body cannot access `x.tricks` because `Animal` doesn't have that field (only `Dog` does). The annotation restricts what the body can do.
2. **Return type**: callers always see `Animal` as the result, even if they passed a `Dog`. Calling `wrap(Dog("Rex", 3)).tricks` is a type error. The annotation establishes a contract for callers.
3. **Checking**: `Dog("Rex", 3)` is accepted as an argument because `Dog <: Animal`. The annotation doesn't reject subtypes — it just forgets the extra precision.

This is the simplest and most predictable behavior: the annotation IS the type, for both the body and the callers.

### Alternative considered: preserving polymorphism

We considered an alternative where `x: Animal` means "x is some type that is at most Animal" — adding a constraint without fixing the type. Under this interpretation, `fun wrap(x: Animal) = x` would infer `('A) -> 'A where 'A <: Animal`, and calling `wrap(Dog("Rex", 3))` would return `Dog`, not `Animal`. The body still couldn't access `.tricks` (checked against Animal), but callers would retain the precise type.

This is appealing because adding an annotation never makes a function less useful. However:

- **Return annotations can't establish contracts.** If `: Animal` on the return type only checks but doesn't fix, callers would still see `Dog` instead of `Animal`. There's no way to say "this function returns Animal, don't rely on anything more specific." A function's public API should be stable even if the implementation changes.
- **Parameters and returns would need different rules.** Parameters want polymorphism (don't penalize callers), returns want contracts (hide implementation details). This asymmetry is confusing.
- **No prior art in this lineage.** MLsub, SimpleSub, and MLscript all use the simpler approach. The MLstruct paper doesn't discuss alternatives.
- **Can be added later.** If the simple approach proves too restrictive, bounded polymorphism can be introduced via explicit type variable syntax (e.g., `fun wrap(x: 'A & Animal): 'A = x`), without changing how plain annotations work.

### How MLscript does it

From the MLstruct paper (Parreaux & Chau) and MLscript codebase:

- **Parameter annotations**: the annotation type is placed directly in the type environment as the variable's type. No tvar, no constraint. The body sees exactly the annotation type.
- **Expression ascription** (`expr : T`): generates `constrain(inferred, T)` and returns `T`. One-directional check, but the result type IS `T`.
- **Subsumption checking**: for verifying inferred types against declared signatures, MLscript instantiates the inferred type's variables fresh and skolemizes (rigidifies) the signature's variables, then runs the normal constraint solver.
- **Free type variables in annotations** (e.g., `x: 'a`) are just fresh inference variables — they don't preserve polymorphism. For true polymorphism, MLscript requires `forall`.

Both parameter annotations and expression ascription produce the same result: `fun f(x: T) = x` and `fun f x = (x: T)` both give `T -> T`.

## Implementation

### Phase 1: Parser

Extend parameter parsing and binding parsing to accept optional `: Type` annotations.

- `params` becomes `annotated_params` — each param is `name` or `name: Type`
- `fun_def` gains optional return type after params: `fun f(x: Num): Num = ...`
- `binding` gains optional type: `x: Num = 42`
- Reuse the existing type expression parser (already handles `Num`, `List<Num>`, `Num -> Num`, record types, etc.)

AST changes:
- `FunDef.params: List<String>` → `List<Param>` where `Param(name: String, typeAnnotation: TypeExpr?)`
- `FunDef.returnType: TypeExpr?` (new field)
- `Val.typeAnnotation: TypeExpr?` (new field)
- Lambda params similarly extended
- New AST node: `Ascription(expr: Expr, type: TypeExpr)` for expression ascription

### Phase 2: Type checker

For each annotated parameter in `inferFunction`:

```
// Current code:
val paramTypes = params.map { env.freshVar() }
childEnv.bind(name, type)

// With annotations:
val paramType = if (param.annotation != null) {
    resolveTypeExpr(param.annotation, env)  // convert TypeExpr to SimpleType
} else {
    env.freshVar()
}
childEnv.bind(param.name, paramType)
```

The annotation type goes directly into the env. No tvar wrapping.

For return type annotations, after inferring the body:

```
val bodyType = infer(body, childEnv)
if (returnType != null) {
    val returnSimpleType = resolveTypeExpr(returnType, env)
    constrain(bodyType, returnSimpleType)  // check: inferred <: declared
}
```

For val binding annotations:

```
// With annotation:
val annotType = resolveTypeExpr(stmt.typeAnnotation, env)
val inferredType = infer(stmt.value, rhsEnv)
constrain(inferredType, annotType)  // check: inferred <: declared
env.bindPolymorphic(stmt.name, annotType)  // bind the DECLARED type, not inferred
```

For expression ascription (`expr : T`):

```
is Ascription -> {
    val exprType = infer(expr.expr, env)
    val annotType = resolveTypeExpr(expr.type, env)
    constrain(exprType, annotType)  // check: inferred <: declared
    annotType  // result IS the annotation type
}
```

All four forms use the same pattern: infer, constrain, return the annotation type.

### Phase 3: Type expression resolution

Need a function `resolveTypeExpr(typeExpr: TypeExpr, env: TypeEnv): SimpleType` that converts parsed type expressions into SimpleTypes. This already partially exists in `TypeDefPreprocessor` for field type resolution. Factor it out for reuse.

Handles:
- Primitive types: `Num` → `TNum`, `String` → `TString`, etc.
- Named types: `Animal` → `TRef("Animal", ...)` with fresh tvars for params
- Applied types: `List<Num>` → `TRef("List", [TNum])`
- Function types: `Num -> String` → `TFun([TNum], TString)`
- Record types: `{ x: Num }` → `TRecord({"x": TNum})`
- Type variables: `'A` → fresh or looked-up TVar

### Error messages

When `constrain(inferred, ascribed)` fails, produce a clear error:

```
Type mismatch: expected Num (from annotation), got String
  in: x: Num = "hello"
```

The annotation source span should be included for good error reporting.

## Test plan

- Parameter annotations: `fun f(x: Num) = x + 1` infers `(Num) -> Num`
- Return type annotations: `fun f(x): Num = x + 1` infers `(Num) -> Num`
- Val annotations: `x: Num = 42` binds x as Num
- Annotation mismatch: `x: Num = "hello"` produces type error
- Return mismatch: `fun f(x): String = x + 1` produces type error
- Mixed annotated/unannotated params: `fun f(x: Num, y) = ...`
- Nominal type annotations: `x: Animal = Dog("Fido")`
- Generic annotations: `x: List<Num> = Cons(1, Nil)`
- Annotation fixes type: `fun f(x: Animal) = x` gives `(Animal) -> Animal`, not `('A) -> 'A`
- Unannotated still polymorphic: `fun f(x) = x` still gives `('A) -> 'A`
- Expression ascription: `(Dog("Rex", 3) : Animal)` has type `Animal`
- Ascription mismatch: `(42 : String)` produces type error
- Ascription in binding: `x = (Dog("Rex", 3) : Animal)` binds x as `Animal`

## Future: bounded polymorphism

Not part of this implementation. If sealing proves too restrictive, a future extension could add bounded polymorphism via explicit type variables in annotations:

```
fun f(x: 'A & Animal): 'A = x
// f: ('A) -> 'A where 'A <: Animal
```

This would require "checked mode" (skolem-like behavior) for the annotation's type variable, where the body is verified against the bound rather than being allowed to constrain it further.
