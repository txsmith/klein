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
