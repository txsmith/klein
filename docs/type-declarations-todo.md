# Type Declarations TODO

## Type Simplification

Type simplification produces misleading results for invariant type parameters.

Given:
```klein
type Animal = Dog { name: String, breed: String } | Cat { name: String }
type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }

type RefAnimal = RefAnimal { r: Ref<Animal> }

refDog = Ref(|Dog("Fido", "Labrador")|, |d -> d.name|)
RefAnimal(refDog)
```

The type of `refDog` is `Ref<'B>` where `Dog <: 'B <: { name: String }`, but the simplifier displays it as `Ref<Dog>`. This hides the structural upper bound and makes it look like the type is fully concrete when it isn't. For invariant type parameters, collapsing to the lower bound is misleading.

## Type Error Constraint Tracing

When a type error occurs, it's hard to understand HOW the constraint solver reached that error. We need a stack trace of the `constrain`/`constrainEqual` calls that led to each error, similar to the parser stack trace.

## Constructor Sharing Name with Parent Type

When a constructor shares its name with the parent type, things break in multi-constructor types. This crashes:

```klein
type X = X { n: Num } | A | B
```

While this works fine:

```klein
type X = X | A
```

The parent and constructor occupy the same slot in the type def registry, and the current fix (skip constructor registration when names match) only works for single-constructor types. For sum types, we need both entries because the constructor has its own type params (subset of parent's).

Simplest rule: only allow a constructor to share the parent's name when the type has exactly one constructor. Emit a parse error otherwise.

## Wrong Number of Type Args Crashes in computeVariance

Using a type with the wrong number of type arguments crashes instead of producing a type error.

```klein
type Box = Box { value: (Num) -> Num }
type NeedsDog = NeedsDog { b: Box<Dog> }
```

Crashes with: `IllegalStateException: Type 'Box' has 0 params but got arg at index 0` at `Typer.kt:540` in `computeVariance`. Should emit a type error (e.g., "Box expects 0 type arguments but got 1") instead of crashing.

## Duplicate Type Definitions Not Detected

Defining two types with the same name silently succeeds — the second definition overwrites the first. Should emit a type error (e.g., "Type 'Box' is already defined"). Same applies to constructor names — two different types can define constructors with the same name, and the second silently overwrites the first.

## Constraint Context Snapshots Have Incomplete Bounds

Type error messages show `Nothing` for type variables because constraint context snapshots types mid-inference:

```klein
type RecConsumer = RecConsumer { f: { p: Bool, r: Num } -> Num }
fun p(b) = if b.p then b.r else b.q
RecConsumer(p)
```

Error shows:
```
└> { p: Bool, r: Num } cannot be passed into parameter 'b: { p: Bool, q: Nothing, r: Nothing }'
```

The `'A` in `{ p: Bool, q: 'A, r: 'A }` becomes `Nothing` because the context clones types via `.clone()` before constraint solving finishes populating bounds. When simplified later, the type variables have no lower bounds → `Nothing` in negative position.

**Possible fixes:**
1. Don't snapshot until after constraint solving completes
2. Simplify using "live" types with final bounds instead of snapshots
3. Store type variable identities and resolve bounds lazily at render time

## Type Variables Visible in Simplified Output

Polymorphic functions show type variables in their simplified types, which may be unexpected:

```klein
fun p(b) = if b.p then b.r else 0
```

Infers to: `({ p: Bool, r: 'A }) -> 'A | Num`

Is this correct behavior for polymorphic functions, or should the simplifier handle this differently? The `'A` represents the polymorphic "whatever type `r` has" — it's genuinely polymorphic. But seeing raw type variables in output may be confusing.

**Questions:**
- Should type variables be renamed to something more canonical (`'A`, `'B`, etc.) based on occurrence order?
- Should we distinguish between "user-visible" polymorphism and "internal" type variables?
- Is the `'A | Num` union expected, or should it simplify further?

## Error Tests Only Check Presence, Not Content

Many typing tests that expect errors only verify `errors.isNotEmpty()` without asserting which specific error was thrown. This could hide bugs where the wrong error is being reported. We should:

1. Add assertions for the specific `TypeError` subclass (e.g., `TypeMismatch`, `ArityMismatch`)
2. Optionally assert on error details (expected/actual types, span location, etc.)

Example of current weak assertion:
```kotlin
assertTrue(errors.isNotEmpty(), "Box<Cat> should not subtype Box<Dog>")
```

Should be something like:
```kotlin
assertEquals(1, errors.size)
assertTrue(errors[0] is TypeError.TypeMismatch)
```
