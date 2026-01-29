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

