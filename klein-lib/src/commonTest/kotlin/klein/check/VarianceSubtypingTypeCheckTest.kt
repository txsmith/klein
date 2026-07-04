package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Variance / subtyping — ported from the SimpleSub `VarianceSubtypingTest`. **All red targets:** the
 * new bidirectional checker treats `type` declarations as a no-op (no constructors, no nominal
 * subtyping, no declared/inferred variance), so every test here fails until nominal support lands.
 *
 * Ported with Path G adjustments:
 *  - functions that inferred an unannotated parameter's type from usage
 *    (`fun runConsumer(c) = ...`, `fun readRef(r) = ...`, `fun mkRef(v) = ...`, `fun wrap(t) = ...`)
 *    were given explicit annotations — Path G requires them; the concrete type is visible from usage.
 *
 * Verdict mapping:
 *  - `assertType(<nominal>, ...)` → `assertTrue(infer(src).errors.isEmpty(), ...)` (nominal names are
 *    not expressible in the new [Type] hierarchy, so we only assert a clean check).
 *  - `assertType(<structural>, ...)` → `assertChecks(<TypeExpr>, src)`.
 *  - `inferErrors` + `assertMismatch` → assert some `TypeError.TypeMismatch` is present. Exact rendered
 *    type strings (nominal) are not pinned; error *counts* are not pinned either.
 *
 * Dropped (1 test, all verdict-non-portable):
 *  - inferred-polymorphism / simplifier `where`-clause output: `invariant_inferredRefShowsWhereClause`.
 */
class VarianceSubtypingTypeCheckTest {
    /** Assert a program checks with no errors and yields [expected]. */
    private fun assertChecks(
        expected: Type,
        src: String,
    ) {
        val result = infer(src)
        assertTrue(result.errors.isEmpty(), "unexpected errors: ${result.errors}")
        kotlin.test.assertEquals(expected, result.type)
    }

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

    // ============================================================
    // Covariance: Box<Dog> <: Box<Animal>
    // ============================================================

    @Test
    fun covariant_boxDogSubtypesBoxAnimal() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Box<'A> = Box { value: 'A }
            type BoxAnimal = BoxAnimal { b: Box<Animal> }

            BoxAnimal(Box(Dog("Fido", "Labrador"))).b
            """.trimIndent(),
            "Box<Dog> should subtype Box<Animal>",
        )

    @Test
    fun covariant_nestedBoxDogSubtypesBoxBoxAnimal() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Box<'A> = Box { value: 'A }
            type BoxBoxAnimal = BoxBoxAnimal { b: Box<Box<Animal>> }

            BoxBoxAnimal(Box(Box(Dog("Fido", "Labrador")))).b
            """.trimIndent(),
            "Box<Box<Dog>> should subtype Box<Box<Animal>>",
        )

    @Test
    fun covariant_wrongDirection_boxCatNotSubtypeBoxDog() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Box<'A> = Box { value: 'A }
            type BoxDog = BoxDog { b: Box<Dog> }

            BoxDog(Box(Cat("Whiskers"))).b
            """.trimIndent(),
            "Box<Cat> should not subtype Box<Dog>",
        )

    @Test
    fun contravariant_consumerAnimalSubtypesConsumerDog() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type AnimalHolder = AnimalHolder { a: Animal }
            type Consumer<'A> = Consumer { consume: 'A -> String }
            type ConsumerDog = ConsumerDog { c: Consumer<Dog> }

            ConsumerDog(Consumer(|a -> AnimalHolder(a).a.name|)).c
            """.trimIndent(),
            "Consumer<Animal> should subtype Consumer<Dog>",
        )

    @Test
    fun contravariant_wrongDirection_consumerDogNotSubtypeConsumerCat() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type DogHolder = DogHolder { d: Dog }
            type Consumer<'A> = Consumer { consume: 'A -> String }
            type ConsumerCat = ConsumerCat { c: Consumer<Cat> }

            ConsumerCat(Consumer(|d -> DogHolder(d).d.breed|)).c
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
            type DogHolder = DogHolder { d: Dog }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
            type RefAnimal = RefAnimal { r: Ref<Animal> }

            RefAnimal(Ref(|Dog("Fido", "Labrador")|, |d -> DogHolder(d).d.name|)).r
            """.trimIndent(),
            "Ref<Dog> should not subtype Ref<Animal>",
        )

    @Test
    fun invariant_unforcedRefCanUnifyWithRefAnimal() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
            type RefAnimal = RefAnimal { r: Ref<Animal> }

            refDog = Ref(|Dog("Fido", "Labrador")|, |d -> d.name|)
            RefAnimal(refDog)
            """.trimIndent(),
            "an unforced Ref should unify with Ref<Animal>",
        )

    @Test
    fun invariant_refCatNotSubtypeRefDog() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type CatHolder = CatHolder { c: Cat }
            type DogHolder = DogHolder { d: Dog }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
            type RefDog = RefDog { r: Ref<Dog> }

            RefDog(Ref(|Cat("Whiskers")|, |c -> CatHolder(c).c.name|)).r
            """.trimIndent(),
            "Ref<Cat> should not subtype Ref<Dog>",
        )

    @Test
    fun invariant_refSameTypeWorks() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type DogHolder = DogHolder { d: Dog }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
            type RefDog = RefDog { r: Ref<Dog> }

            RefDog(Ref(|Dog("Fido", "Labrador")|, |d -> DogHolder(d).d.breed|)).r
            """.trimIndent(),
            "Ref<Dog> should check against Ref<Dog>",
        )

    @Test
    fun invariant_equalBoundsCollapsesToConcreteType() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type ForceDog = ForceDog { d: Dog }
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }

            Ref(|Dog("Fido", "Labrador")|, |d -> ForceDog(d).d.name|)
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
            type PhantomBool = PhantomBool { p: Phantom<Bool> }

            PhantomBool(Phantom(42))
            """.trimIndent(),
            "a phantom type param should be referenceable",
        )

    @Test
    fun phantom_unusedParamIsInvariant_catNotSubtypeDog() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Phantom<'A> = Phantom { value: Num }
            type PhantomAnimal = PhantomAnimal { p: Phantom<Animal> }
            type PhantomCat = PhantomCat { p: Phantom<Cat> }
            type PhantomDog = PhantomDog { p: Phantom<Dog> }

            PhantomAnimal(PhantomDog(Phantom(42)).p).p
            PhantomDog(PhantomCat(Phantom(42)).p).p
            """.trimIndent(),
            "an unused (phantom) param should be invariant",
        )

    @Test
    fun phantom_sameTypeWorks() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Phantom<'A> = Phantom { value: Num }
            type PhantomDog = PhantomDog { p: Phantom<Dog> }
            type PhantomDog2 = PhantomDog2 { p: Phantom<Dog> }

            PhantomDog2(PhantomDog(Phantom(42)).p).p
            """.trimIndent(),
            "Phantom<Dog> should check against Phantom<Dog>",
        )

    @Test
    fun function_animalToDogSubtypesDogToAnimal() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type AnimalHolder = AnimalHolder { a: Animal }
            type FuncHolder = FuncHolder { f: Dog -> Animal }

            FuncHolder(|a -> AnimalHolder(a).a|).f
            """.trimIndent(),
            "(Animal -> Dog) should subtype (Dog -> Animal)",
        )

    @Test
    fun function_dogToDogNotSubtypesCatToCat() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type DogHolder = DogHolder { d: Dog }
            type FuncHolder = FuncHolder { f: Cat -> Cat }

            FuncHolder(|d -> DogHolder(d).d|).f
            """.trimIndent(),
            "(Dog -> Dog) should not subtype (Cat -> Cat)",
        )

    @Test
    fun producerCovariant_wrongDirectionFails() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type AnimalHolder = AnimalHolder { a: Animal }
            type Producer<'A> = Producer { produce: () -> 'A }
            type ProducerDog = ProducerDog { p: Producer<Dog> }

            ProducerDog(Producer(|AnimalHolder(Cat("Whiskers")).a|)).p
            """.trimIndent(),
            "Producer<Animal> should not subtype Producer<Dog>",
        )

    @Test
    fun handler_doubleNegationIsCovariant() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Handler<'A> = Handler { handle: ('A -> ()) -> () }
            type HandlerAnimal = HandlerAnimal { h: Handler<Animal> }

            HandlerAnimal(Handler(|f -> f(Dog("Fido", "Labrador"))|)).h
            """.trimIndent(),
            "Handler double-negation should be covariant",
        )

    @Test
    fun contravariant_nestedConsumer() =
        checksClean(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type DogHolder = DogHolder { d: Dog }
            type Consumer<'A> = Consumer { consume: 'A -> () }
            type CCAnimal = CCAnimal { c: Consumer<Consumer<Animal>> }

            CCAnimal(Consumer(|innerC -> innerC.consume(Dog("Fido", "Labrador"))|)).c
            """.trimIndent(),
            "Consumer<Consumer<Dog>> should subtype Consumer<Consumer<Animal>>",
        )

    @Test
    fun swappedRecursion_firstParam_covariantDirectionFails() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }

            type Cast = Cast { f: Weird<Dog, Num> -> Weird<Animal, Num> }
            Cast(|w -> w|)
            """.trimIndent(),
            "Weird<Dog, Num> should not subtype Weird<Animal, Num> - 'A is invariant",
        )

    @Test
    fun swappedRecursion_secondParam_covariantDirectionFails() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }

            type Cast = Cast { f: Weird<Num, Dog> -> Weird<Num, Animal> }
            Cast(|w -> w|)
            """.trimIndent(),
            "Weird<Num, Dog> should not subtype Weird<Num, Animal> - 'B is invariant",
        )

    @Test
    fun swappedRecursion_secondParam_contravariantDirectionFails() =
        hasMismatch(
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }

            type Cast = Cast { f: Weird<Num, Animal> -> Weird<Num, Dog> }
            Cast(|w -> w|)
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
            type VDog = VDog { v: V<Dog> }

            VDog(Cov(|Dog("Rex")|)).v
            """.trimIndent(),
            "Cov<Dog> should subtype V<Dog>",
        )

    @Test
    fun constructorSubtypesParent_contravariant() =
        checksClean(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
            type VDog = VDog { v: V<Dog> }
            type DogHolder = DogHolder { d: Dog }

            VDog(Cont(|d -> DogHolder(d).d.name|)).v
            """.trimIndent(),
            "Cont<Dog> should subtype V<Dog>",
        )

    @Test
    fun constructorNotSubtypesParent_covDogNotSubtypesVAnimal() =
        hasMismatch(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
            type VAnimal = VAnimal { v: V<Animal> }

            type Force = F { c: Cov<Dog> }

            cov = F(Cov(|Dog("Rex")|)).c
            VAnimal(cov)
            """.trimIndent(),
            "Cov<Dog> should not subtype V<Animal> - V is invariant",
        )

    @Test
    fun constructorNotSubtypesParent_covAnimalNotSubtypesVDog() =
        hasMismatch(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
            type AnimalHolder = AnimalHolder { a: Animal }
            type VDog = VDog { v: V<Dog> }

            VDog(Cov(|AnimalHolder(Dog("Rex")).a|)).v
            """.trimIndent(),
            "Cov<Animal> should not subtype V<Dog> - V is invariant",
        )

    @Test
    fun constructorNotSubtypesParent_contDogNotSubtypesVAnimal() =
        hasMismatch(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
            type DogHolder = DogHolder { d: Dog }
            type VAnimal = VAnimal { v: V<Animal> }

            VAnimal(Cont(|d -> DogHolder(d).d.name|)).v
            """.trimIndent(),
            "Cont<Dog> should not subtype V<Animal> - V is invariant",
        )

    @Test
    fun constructorNotSubtypesParent_contAnimalNotSubtypesVDog() =
        hasMismatch(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
            type VDog = VDog { v: V<Dog> }

            type Force = F { c: Cont<Animal> }
            contAnimal = F(Cont(|a -> a.name|)).c

            VDog(contAnimal)
            """.trimIndent(),
            "Cont<Animal> should not subtype V<Dog> - V is invariant",
        )

    // ============================================================
    // Type simplification respects variance
    // ============================================================

    @Test
    fun simplification_contravariantParamShowsBound_notNothing() =
        checksClean(
            """
            type Animal = Dog | Cat
            type Consumer<'A> = Consumer { f: 'A -> Num }
            type ForceDog = ForceDog { dog: Dog }

            Consumer(|dog ->
                ForceDog(dog)
                1
            |)
            """.trimIndent(),
            "a contravariant param should show its bound (Consumer<Dog>)",
        )

    @Test
    fun contravariant_genericFunctionReceivesConsumerAnimal_constrainedToConsumerDog() =
        assertChecks(
            TStr,
            """
            type Animal = Dog { name: String, breed: String } | Cat { name: String }
            type Consumer<'A> = Consumer { consume: 'A -> String }
            type ConsumerDog = ConsumerDog { c: Consumer<Dog> }

            fun runConsumer(c: Consumer<Dog>): String = ConsumerDog(c).c.consume(Dog("Rex", "Poodle"))
            runConsumer(Consumer(|a -> a.name|))
            """.trimIndent(),
        )

    @Test
    fun invariant_genericFunctionInfersRefNumFromUsage() =
        assertChecks(
            TNum,
            """
            type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
            type RefNum = RefNum { r: Ref<Num> }

            fun readRef(r: Ref<Num>): Num = RefNum(r).r.get()
            readRef(Ref(|42|, |n -> "ok"|))
            """.trimIndent(),
        )

    @Test
    fun mixedVariance_twoParamTypePassedThroughFunction() =
        checksClean(
            """
            type Animal = Dog { name: String } | Cat { name: String }
            type Transform<'A, 'B> = Transform { value: 'A, handler: 'B -> String }
            type TransformAnimalDog = TransformAnimalDog { t: Transform<Animal, Dog> }

            fun wrap(t: Transform<Animal, Dog>): Transform<Animal, Dog> = TransformAnimalDog(t).t
            wrap(Transform(Dog("Fido"), |a -> a.name|))
            """.trimIndent(),
            "a two-param type should pass through a function unchanged",
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

    @Test
    fun bareConstructor_subtypesCovariantParent() =
        checksClean(
            """
            type Box<'A> = Full { value: 'A } | Empty
            type BoxNum = BoxNum { b: Box<Num> }
            BoxNum(Empty).b
            """.trimIndent(),
            "a bare constructor should subtype a covariant parent (Box<Num>)",
        )

    @Test
    fun bareConstructor_subtypesInvariantParent() =
        checksClean(
            """
            type Cell<'A> = Live { get: () -> 'A, set: 'A -> String } | Dead
            type CellNum = CellNum { c: Cell<Num> }
            CellNum(Dead).c
            """.trimIndent(),
            "a bare constructor should subtype an invariant parent (Cell<Num>)",
        )

    @Test
    fun bareConstructor_usedAtTwoDifferentInstantiations() =
        checksClean(
            """
            type Option<'A> = Some { value: 'A } | None
            type OptNum = OptNum { o: Option<Num> }
            type OptStr = OptStr { o: Option<String> }
            { a = OptNum(None).o, b = OptStr(None).o }
            """.trimIndent(),
            "a bare constructor should be usable at two instantiations",
        )

    @Test
    fun bareConstructor_multiParamSumWhereOtherConstructorUsesAllParams() =
        checksClean(
            """
            type Result<'A, 'B> = Ok { value: 'A } | Err { error: 'B } | Unknown
            type ResultStringNum = ResultStringNum { r: Result<String, Num> }
            ResultStringNum(Unknown).r
            """.trimIndent(),
            "a bare constructor of a multi-param sum should check (Result<String, Num>)",
        )
}
