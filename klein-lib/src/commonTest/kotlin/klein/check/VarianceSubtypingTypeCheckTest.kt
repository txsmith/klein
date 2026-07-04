package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Variance / subtyping — ported from the SimpleSub `VarianceSubtypingTest`.
 *
 * Types are forced by **ascription**, not by "holder" constructors:
 *  - a demand is an ascribed binding — `consumerDog: Consumer<Dog> = Consumer(…)`;
 *  - a deliberate mismatch is a second ascription to a conflicting type — `bad: Consumer<Cat> = consumerDog`;
 *  - a lambda parameter's type comes from the demand, so inner forcing-holders (`AnimalHolder(a).a`) are dropped.
 *
 * The remaining reds are the bare-lambda cases: a lambda argument at a parameter that mentions a type
 * variable (`Consumer(|a -> a.name|)` demanded as `Consumer<Dog>`). They need result-demand-first
 * grounding (floating); until then the un-annotated parameter can't be synthesized.
 *
 * Dropped — SimpleSub inference/simplification with no Path G equivalent (Path G neither infers nor
 * simplifies; the generic-function cases need declared bounds, i.e. M6):
 *  - `invariant_inferredRefShowsWhereClause` — inferred-polymorphism `where`-clause output.
 *  - `simplification_contravariantParamShowsBound_notNothing` — the simplifier showing a *used*
 *    contravariant param as its bound (`Dog`, not `Nothing`).
 *  - `contravariant_genericFunctionReceivesConsumerAnimal…` / `invariant_genericFunctionInfersRefNumFromUsage`
 *    — a *generic* function whose param is constrained from body usage. Revisit at M6 as e.g.
 *    `fun runConsumer(c: Consumer<'A>): String where Dog <: 'A`.
 */
class VarianceSubtypingTypeCheckTest {
    private fun checksClean(
        src: String,
        why: String,
    ) {
        val errors = infer(src).errors
        assertTrue(errors.isEmpty(), "$why — but got: $errors")
    }

    private fun hasMismatch(
        src: String,
        why: String,
    ) {
        val errors = infer(src).errors
        assertTrue(errors.any { it is TypeError.TypeMismatch }, "$why — but got: $errors")
    }

    private fun assertChecks(
        expected: Type,
        src: String,
    ) {
        val result = infer(src)
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        kotlin.test.assertEquals(expected, result.type)
    }

    // ============================================================
    // Covariance: Box<Dog> <: Box<Animal>
    // ============================================================

    @Test
    fun covariant_boxDogSubtypesBoxAnimal() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Box<'A> = Box { value: 'A }

            boxAnimal: Box<Animal> = Box(Dog("Fido", "Labrador"))
            """.trimIndent(),
            "Box<Dog> should subtype Box<Animal>",
        )

    @Test
    fun covariant_nestedBoxDogSubtypesBoxBoxAnimal() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Box<'A> = Box { value: 'A }

            bb: Box<Box<Animal>> = Box(Box(Dog("Fido", "Labrador")))
            """.trimIndent(),
            "Box<Box<Dog>> should subtype Box<Box<Animal>>",
        )

    @Test
    fun covariant_wrongDirection_boxCatNotSubtypeBoxDog() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Box<'A> = Box { value: 'A }

            boxDog: Box<Dog> = Box(Cat("Whiskers"))
            """.trimIndent(),
            "Box<Cat> should not subtype Box<Dog>",
        )

    // ============================================================
    // Contravariance: Consumer<Animal> <: Consumer<Dog>
    // ============================================================

    @Test
    fun contravariant_consumerAnimalSubtypesConsumerDog() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Consumer<'A> = Consumer { consume: 'A -> String }

            consumerAnimal: Consumer<Animal> = Consumer(|a -> a.name|)
            consumerDog: Consumer<Dog> = consumerAnimal
            """.trimIndent(),
            "Consumer<Animal> should subtype Consumer<Dog>",
        )

    @Test
    fun contravariant_wrongDirection_consumerDogNotSubtypeConsumerCat() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Consumer<'A> = Consumer { consume: 'A -> String }

            consumerDog: Consumer<Dog> = Consumer(|d -> d.name|)
            bad: Consumer<Cat> = consumerDog
            """.trimIndent(),
            "Consumer<Dog> should not subtype Consumer<Cat>",
        )

    // ============================================================
    // Invariance: Ref<Dog> NOT<: Ref<Animal> in either direction
    // ============================================================

    @Test
    fun invariant_refDogNotSubtypeRefAnimal() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }

            refDog: Ref<Dog> = Ref(|Dog("Fido", "Labrador")|, |d -> d.name|)
            refAnimal: Ref<Animal> = refDog
            """.trimIndent(),
            "Ref<Dog> should not subtype Ref<Animal>",
        )

    @Test
    fun invariant_unforcedRefCanUnifyWithRefAnimal() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }

            refAnimal: Ref<Animal> = Ref(|Dog("Fido", "Labrador")|, |d -> d.name|)
            """.trimIndent(),
            "an unforced Ref should unify with Ref<Animal>",
        )

    @Test
    fun invariant_refCatNotSubtypeRefDog() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }

            refCat: Ref<Cat> = Ref(|Cat("Whiskers")|, |c -> c.name|)
            refDog: Ref<Dog> = refCat
            """.trimIndent(),
            "Ref<Cat> should not subtype Ref<Dog>",
        )

    @Test
    fun invariant_refSameTypeWorks() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }

            refDog: Ref<Dog> = Ref(|Dog("Fido", "Labrador")|, |d -> d.breed|)
            """.trimIndent(),
            "Ref<Dog> should check against Ref<Dog>",
        )

    @Test
    fun invariant_equalBoundsCollapsesToConcreteType() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }

            ref: Ref<Dog> = Ref(|Dog("Fido", "Labrador")|, |d -> d.name|)
            """.trimIndent(),
            "equal bounds should collapse to Ref<Dog>",
        )

    // invariant_inferredRefShowsWhereClause DROPPED:
    // asserts simplifier `where`-clause output for an inferred polymorphic Ref — not verdict-portable.

    @Test
    fun phantom_canReferencePhantomTypeParam() =
        checksClean(
            """
            type Phantom<'A> = Phantom { value: Num }

            phantomBool: Phantom<Bool> = Phantom(42)
            """.trimIndent(),
            "a phantom type param should be referenceable",
        )

    @Test
    fun phantom_unusedParamIsInvariant_catNotSubtypeDog() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Phantom<'A> = Phantom { value: Num }

            phantomDog: Phantom<Dog> = Phantom(42)
            badAnimal: Phantom<Animal> = phantomDog

            phantomCat: Phantom<Cat> = Phantom(42)
            badDog: Phantom<Dog> = phantomCat
            """.trimIndent(),
            "an unused (phantom) param should be invariant",
        )

    @Test
    fun phantom_sameTypeWorks() =
        checksClean(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Phantom<'A> = Phantom { value: Num }

            phantomDog: Phantom<Dog> = Phantom(42)
            phantomDog2: Phantom<Dog> = phantomDog
            """.trimIndent(),
            "Phantom<Dog> should check against Phantom<Dog>",
        )

    // ============================================================
    // Function subtyping (contravariant param, covariant result)
    // ============================================================

    @Test
    fun function_animalToDogSubtypesDogToAnimal() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }

            animalToDog: Animal -> Dog = |a -> Dog("Rex", "Poodle")|
            f: Dog -> Animal = animalToDog
            """.trimIndent(),
            "(Animal -> Dog) should subtype (Dog -> Animal)",
        )

    @Test
    fun function_dogToDogNotSubtypesCatToCat() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }

            dogFn: Dog -> Dog = |d -> d|
            bad: Cat -> Cat = dogFn
            """.trimIndent(),
            "(Dog -> Dog) should not subtype (Cat -> Cat)",
        )

    @Test
    fun producerCovariant_wrongDirectionFails() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Producer<'A> = Producer { produce: () -> 'A }

            producerAnimal: Producer<Animal> = Producer(|Cat("Whiskers")|)
            bad: Producer<Dog> = producerAnimal
            """.trimIndent(),
            "Producer<Animal> should not subtype Producer<Dog>",
        )

    @Test
    fun handler_doubleNegationIsCovariant() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Handler<'A> = Handler { handle: ('A -> ()) -> () }

            handlerDog: Handler<Dog> = Handler(|f -> f(Dog("Fido", "Labrador"))|)
            handlerAnimal: Handler<Animal> = handlerDog
            """.trimIndent(),
            "Handler double-negation should be covariant",
        )

    @Test
    fun contravariant_nestedConsumer() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Consumer<'A> = Consumer { consume: 'A -> () }

            ccDog: Consumer<Consumer<Dog>> = Consumer(|innerC -> innerC.consume(Dog("Fido", "Labrador"))|)
            ccAnimal: Consumer<Consumer<Animal>> = ccDog
            """.trimIndent(),
            "Consumer<Consumer<Dog>> should subtype Consumer<Consumer<Animal>>",
        )

    @Test
    fun swappedRecursion_firstParam_covariantDirectionFails() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }

            f: Weird<Dog, Num> -> Weird<Animal, Num> = |w -> w|
            """.trimIndent(),
            "Weird<Dog, Num> should not subtype Weird<Animal, Num> - 'A is invariant",
        )

    @Test
    fun swappedRecursion_secondParam_covariantDirectionFails() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }

            f: Weird<Num, Dog> -> Weird<Num, Animal> = |w -> w|
            """.trimIndent(),
            "Weird<Num, Dog> should not subtype Weird<Num, Animal> - 'B is invariant",
        )

    @Test
    fun swappedRecursion_secondParam_contravariantDirectionFails() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }

            f: Weird<Num, Animal> -> Weird<Num, Dog> = |w -> w|
            """.trimIndent(),
            "Weird<Num, Animal> should not subtype Weird<Num, Dog> - 'B is invariant",
        )

    // ============================================================
    // Constructor subtypes parent
    // ============================================================

    @Test
    fun constructorSubtypesParent_covariant() =
        checksClean(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }

            v: V<Dog> = Cov(|Dog("Rex")|)
            """.trimIndent(),
            "Cov<Dog> should subtype V<Dog>",
        )

    @Test
    fun constructorSubtypesParent_contravariant() =
        checksClean(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }

            v: V<Dog> = Cont(|d -> d.name|)
            """.trimIndent(),
            "Cont<Dog> should subtype V<Dog>",
        )

    @Test
    fun constructorNotSubtypesParent_covDogNotSubtypesVAnimal() =
        hasMismatch(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }

            cov: Cov<Dog> = Cov(|Dog("Rex")|)
            bad: V<Animal> = cov
            """.trimIndent(),
            "Cov<Dog> should not subtype V<Animal> - V is invariant",
        )

    @Test
    fun constructorNotSubtypesParent_covAnimalNotSubtypesVDog() =
        hasMismatch(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }

            cov: Cov<Animal> = Cov(|Dog("Rex")|)
            bad: V<Dog> = cov
            """.trimIndent(),
            "Cov<Animal> should not subtype V<Dog> - V is invariant",
        )

    @Test
    fun constructorNotSubtypesParent_contDogNotSubtypesVAnimal() =
        hasMismatch(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }

            cont: Cont<Dog> = Cont(|d -> d.name|)
            bad: V<Animal> = cont
            """.trimIndent(),
            "Cont<Dog> should not subtype V<Animal> - V is invariant",
        )

    @Test
    fun constructorNotSubtypesParent_contAnimalNotSubtypesVDog() =
        hasMismatch(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }

            cont: Cont<Animal> = Cont(|a -> a.name|)
            bad: V<Dog> = cont
            """.trimIndent(),
            "Cont<Animal> should not subtype V<Dog> - V is invariant",
        )

    // ============================================================
    // Generic values flowing through functions
    // ============================================================

    @Test
    fun mixedVariance_twoParamTypePassedThroughFunction() =
        checksClean(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Transform<'A, 'B> = Transform { value: 'A, handler: 'B -> String }

            t: Transform<Animal, Dog> = Transform(Dog("Fido"), |a -> a.name|)
            """.trimIndent(),
            "a two-param type should pass through unchanged",
        )

    @Test
    fun invariantTypeReturnedAndPassedToAnotherFunction() =
        assertChecks(
            TStr,
            """
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }

            fun mkRef(v: Num): Ref<Num> = Ref(|v|, |x -> "ok"|)
            fun useRef(r: Ref<Num>): String = r.set(r.get())
            useRef(mkRef(42))
            """.trimIndent(),
        )

    // ============================================================
    // Bare (nullary) constructors of generic sums
    // ============================================================

    @Test
    fun bareConstructor_subtypesCovariantParent() =
        checksClean(
            """
            type Box<'A> = Full { value: 'A } | Empty
            b: Box<Num> = Empty
            """.trimIndent(),
            "a bare constructor should subtype a covariant parent (Box<Num>)",
        )

    @Test
    fun bareConstructor_subtypesInvariantParent() =
        checksClean(
            """
            type Cell<'A> = Live { get: () -> 'A, set: 'A -> String } | Dead
            c: Cell<Num> = Dead
            """.trimIndent(),
            "a bare constructor should subtype an invariant parent (Cell<Num>)",
        )

    @Test
    fun bareConstructor_usedAtTwoDifferentInstantiations() =
        checksClean(
            """
            type Option<'A> = Some { value: 'A } | None
            optNum: Option<Num> = None
            optStr: Option<String> = None
            { a = optNum, b = optStr }
            """.trimIndent(),
            "a bare constructor should be usable at two instantiations",
        )

    @Test
    fun bareConstructor_multiParamSumWhereOtherConstructorUsesAllParams() =
        checksClean(
            """
            type Result<'A, 'B> = Ok { value: 'A } | Err { error: 'B } | Unknown
            r: Result<String, Num> = Unknown
            """.trimIndent(),
            "a bare constructor of a multi-param sum should check (Result<String, Num>)",
        )
}
