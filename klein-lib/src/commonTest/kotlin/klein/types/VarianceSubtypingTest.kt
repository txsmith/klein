package klein.types

import kotlin.test.Test
import kotlin.test.assertTrue

class VarianceSubtypingTest {
    // ============================================================
    // Covariance: Box<Dog> <: Box<Animal>
    // If Dog <: Animal and Box is covariant, then Box<Dog> <: Box<Animal>
    // ============================================================

    @Test
    fun covariant_boxDogSubtypesBoxAnimal() {
        assertType(
            "Box<Animal>",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Box<'A> = Box { value: 'A }
                type BoxAnimal = BoxAnimal { b: Box<Animal> }

                BoxAnimal(Box(Dog("Fido", "Labrador"))).b
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun covariant_nestedBoxDogSubtypesBoxBoxAnimal() {
        assertType(
            "Box<Box<Animal>>",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Box<'A> = Box { value: 'A }
                type BoxBoxAnimal = BoxBoxAnimal { b: Box<Box<Animal>> }

                BoxBoxAnimal(Box(Box(Dog("Fido", "Labrador")))).b
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun covariant_wrongDirection_boxCatNotSubtypeBoxDog() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Box<'A> = Box { value: 'A }
                type BoxDog = BoxDog { b: Box<Dog> }

                BoxDog(Box(Cat("Whiskers"))).b
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Box<Cat> should not subtype Box<Dog>")
    }

    // ============================================================
    // Contravariance: Consumer<Animal> <: Consumer<Dog>
    // A consumer that handles any animal can be used where Dog consumer expected
    // ============================================================

    @Test
    fun contravariant_consumerAnimalSubtypesConsumerDog() {
        assertType(
            "Consumer<Dog>",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type AnimalHolder = AnimalHolder { a: Animal }
                type Consumer<'A> = Consumer { consume: 'A -> String }
                type ConsumerDog = ConsumerDog { c: Consumer<Dog> }

                ConsumerDog(Consumer(|a -> AnimalHolder(a).a.name|)).c
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun contravariant_wrongDirection_consumerDogNotSubtypeConsumerCat() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type DogHolder = DogHolder { d: Dog }
                type Consumer<'A> = Consumer { consume: 'A -> String }
                type ConsumerCat = ConsumerCat { c: Consumer<Cat> }

                ConsumerCat(Consumer(|d -> DogHolder(d).d.breed|)).c
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Consumer<Dog> should not subtype Consumer<Cat>")
    }

    // ============================================================
    // Invariance: Ref<Dog> NOT<: Ref<Animal> in either direction
    // Because 'A appears in both covariant and contravariant positions
    // ============================================================

    @Test
    fun invariant_refDogNotSubtypeRefAnimal() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type DogHolder = DogHolder { d: Dog }
                type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
                type RefAnimal = RefAnimal { r: Ref<Animal> }

                RefAnimal(Ref(|Dog("Fido", "Labrador")|, |d -> DogHolder(d).d.name|)).r
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Ref<Dog> should not subtype Ref<Animal>")
    }

    @Test
    fun invariant_unforcedRefCanUnifyWithRefAnimal() {
        assertType(
            "RefAnimal",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
                type RefAnimal = RefAnimal { r: Ref<Animal> }

                refDog = Ref(|Dog("Fido", "Labrador")|, |d -> d.name|)
                RefAnimal(refDog)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun invariant_refCatNotSubtypeRefDog() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type CatHolder = CatHolder { c: Cat }
                type DogHolder = DogHolder { d: Dog }
                type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
                type RefDog = RefDog { r: Ref<Dog> }

                RefDog(Ref(|Cat("Whiskers")|, |c -> CatHolder(c).c.name|)).r
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Ref<Cat> should not subtype Ref<Dog>")
    }

    @Test
    fun invariant_refSameTypeWorks() {
        assertType(
            "Ref<Dog>",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type DogHolder = DogHolder { d: Dog }
                type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
                type RefDog = RefDog { r: Ref<Dog> }

                RefDog(Ref(|Dog("Fido", "Labrador")|, |d -> DogHolder(d).d.breed|)).r
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Phantom types: Unused params default to invariance
    // This enables phantom types for type-level distinctions
    // ============================================================

    @Test
    fun phantom_canReferencePhantomTypeParam() {
        assertType(
            "PhantomBool",
            infer(
                """
                type Phantom<'A> = Phantom { value: Num }
                type PhantomBool = PhantomBool { p: Phantom<Bool> }

                PhantomBool(Phantom(42))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun phantom_unusedParamIsInvariant_dogNotSubtypeAnimal() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Phantom<'A> = Phantom { value: Num }
                type PhantomDog = PhantomDog { p: Phantom<Dog> }
                type PhantomAnimal = PhantomAnimal { p: Phantom<Animal> }

                PhantomAnimal(PhantomDog(Phantom(42)).p).p
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Phantom<Dog> should not subtype Phantom<Animal> - unused params are invariant")
    }

    @Test
    fun phantom_unusedParamIsInvariant_catNotSubtypeDog() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Phantom<'A> = Phantom { value: Num }
                type PhantomCat = PhantomCat { p: Phantom<Cat> }
                type PhantomDog = PhantomDog { p: Phantom<Dog> }

                PhantomDog(PhantomCat(Phantom(42)).p).p
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Phantom<Cat> should not subtype Phantom<Dog> - unused params are invariant")
    }

    @Test
    fun phantom_sameTypeWorks() {
        assertType(
            "Phantom<Dog>",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Phantom<'A> = Phantom { value: Num }
                type PhantomDog = PhantomDog { p: Phantom<Dog> }
                type PhantomDog2 = PhantomDog2 { p: Phantom<Dog> }

                PhantomDog2(PhantomDog(Phantom(42)).p).p
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Function variance: contravariant in input, covariant in output
    // ============================================================

    @Test
    fun function_animalToDogSubtypesDogToAnimal() {
        assertType(
            "(Dog) -> Animal",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type AnimalHolder = AnimalHolder { a: Animal }
                type FuncHolder = FuncHolder { f: Dog -> Animal }

                FuncHolder(|a -> AnimalHolder(a).a|).f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun function_dogToDogNotSubtypesCatToCat() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type DogHolder = DogHolder { d: Dog }
                type FuncHolder = FuncHolder { f: Cat -> Cat }

                FuncHolder(|d -> DogHolder(d).d|).f
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "(Dog -> Dog) should not subtype (Cat -> Cat)")
    }

    // ============================================================
    // Producer covariance: Producer<Dog> <: Producer<Animal>
    // ============================================================

    @Test
    fun producerCovariant_producerDogSubtypesProducerAnimal() {
        assertType(
            "Producer<Animal>",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Producer<'A> = Producer { produce: () -> 'A }
                type ProducerAnimal = ProducerAnimal { p: Producer<Animal> }

                ProducerAnimal(Producer(|Dog("Fido", "Labrador")|)).p
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun producerCovariant_wrongDirectionFails() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type AnimalHolder = AnimalHolder { a: Animal }
                type Producer<'A> = Producer { produce: () -> 'A }
                type ProducerDog = ProducerDog { p: Producer<Dog> }

                ProducerDog(Producer(|AnimalHolder(Cat("Whiskers")).a|)).p
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Producer<Animal> should not subtype Producer<Dog>")
    }

    // ============================================================
    // Handler has double negation: ('A -> ()) -> ()
    // Double contravariance = covariance
    // ============================================================

    @Test
    fun handler_doubleNegationIsCovariant() {
        assertType(
            "Handler<Animal>",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Handler<'A> = Handler { handle: ('A -> ()) -> () }
                type HandlerAnimal = HandlerAnimal { h: Handler<Animal> }

                HandlerAnimal(Handler(|f -> f(Dog("Fido", "Labrador"))|)).h
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Nested contravariance: Consumer<Consumer<Dog>> <: Consumer<Consumer<Animal>>
    // Two contravariants = covariant (flip twice)
    // ============================================================

    @Test
    fun contravariant_nestedConsumer() {
        assertType(
            "Consumer<Consumer<Animal>>",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type DogHolder = DogHolder { d: Dog }
                type Consumer<'A> = Consumer { consume: 'A -> () }
                type CCAnimal = CCAnimal { c: Consumer<Consumer<Animal>> }

                CCAnimal(Consumer(|innerC -> innerC.consume(Dog("Fido", "Labrador"))|)).c
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Transform: covariant in first param, contravariant in second
    // ============================================================

    @Test
    fun multipleParams_independentVariances() {
        assertType(
            "Transform<Animal, Dog>",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type AnimalHolder = AnimalHolder { a: Animal }
                type Transform<'A, 'B> = Transform { value: 'A, handler: 'B -> String }
                type TransformAnimalDog = TransformAnimalDog { t: Transform<Animal, Dog> }

                TransformAnimalDog(Transform(Dog("Fido", "Labrador"), |a -> AnimalHolder(a).a.name|)).t
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Swapped recursion forces invariance
    // type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }
    // 'A: contravariant (fn input) + covariant (swapped in next) = invariant
    // 'B: covariant (fn output) + contravariant (swapped in next) = invariant
    // Test each param in both directions (4 tests)
    // ============================================================

    @Test
    fun swappedRecursion_firstParam_covariantDirectionFails() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type DogHolder = DogHolder { d: Dog }
                type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }
                type WeirdDogNum = WeirdDogNum { w: Weird<Dog, Num> }
                type WeirdAnimalNum = WeirdAnimalNum { w: Weird<Animal, Num> }

                fun mkWeird() = Weird(|d -> DogHolder(d).d.name.size|, mkWeird())
                WeirdAnimalNum(WeirdDogNum(mkWeird()).w).w
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Weird<Dog, Num> should not subtype Weird<Animal, Num> - 'A is invariant")
    }

    @Test
    fun swappedRecursion_firstParam_contravariantDirectionFails() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type AnimalHolder = AnimalHolder { a: Animal }
                type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }
                type WeirdAnimalNum = WeirdAnimalNum { w: Weird<Animal, Num> }
                type WeirdDogNum = WeirdDogNum { w: Weird<Dog, Num> }

                fun mkWeird() = Weird(|a -> AnimalHolder(a).a.name.size|, mkWeird())
                WeirdDogNum(WeirdAnimalNum(mkWeird()).w).w
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Weird<Animal, Num> should not subtype Weird<Dog, Num> - 'A is invariant")
    }

    @Test
    fun swappedRecursion_secondParam_covariantDirectionFails() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type DogHolder = DogHolder { d: Dog }
                type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }
                type WeirdNumDog = WeirdNumDog { w: Weird<Num, Dog> }
                type WeirdNumAnimal = WeirdNumAnimal { w: Weird<Num, Animal> }

                fun mkWeird() = Weird(|n -> Dog("name", "breed")|, mkWeird())
                WeirdNumAnimal(WeirdNumDog(mkWeird()).w).w
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Weird<Num, Dog> should not subtype Weird<Num, Animal> - 'B is invariant")
    }

    @Test
    fun swappedRecursion_secondParam_contravariantDirectionFails() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type AnimalHolder = AnimalHolder { a: Animal }
                type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }
                type WeirdNumAnimal = WeirdNumAnimal { w: Weird<Num, Animal> }
                type WeirdNumDog = WeirdNumDog { w: Weird<Num, Dog> }

                fun mkWeird() = Weird(|n -> AnimalHolder(Dog("name", "breed")).a|, mkWeird())
                WeirdNumDog(WeirdNumAnimal(mkWeird()).w).w
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Weird<Num, Animal> should not subtype Weird<Num, Dog> - 'B is invariant")
    }

    // ============================================================
    // Constructor subtypes parent: Cov<X> <: V<X>, Cont<X> <: V<X>
    // ============================================================

    @Test
    fun constructorSubtypesParent_covariant() {
        assertType(
            "V<Dog>",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
                type VDog = VDog { v: V<Dog> }

                VDog(Cov(|Dog("Rex")|)).v
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun constructorSubtypesParent_contravariant() {
        assertType(
            "V<Dog>",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
                type VDog = VDog { v: V<Dog> }
                type DogHolder = DogHolder { d: Dog }

                VDog(Cont(|d -> DogHolder(d).d.name|)).v
                """.trimIndent(),
            ),
        )
    }

    // ============================================================
    // Constructor does NOT subtype parent with different type arg
    // V is invariant (has both covariant and contravariant constructors)
    // So Cov<Dog> <: V<Animal> fails, Cont<Animal> <: V<Dog> fails, etc.
    // ============================================================

    @Test
    fun constructorNotSubtypesParent_covDogNotSubtypesVAnimal() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
                type VAnimal = VAnimal { v: V<Animal> }

                type Force = F { c: Cov<Dog> }

                cov = F(Cov(|Dog("Rex")|)).c
                VAnimal(cov)
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Cov<Dog> should not subtype V<Animal> - V is invariant")
    }

    @Test
    fun constructorNotSubtypesParent_covAnimalNotSubtypesVDog() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
                type AnimalHolder = AnimalHolder { a: Animal }
                type VDog = VDog { v: V<Dog> }

                VDog(Cov(|AnimalHolder(Dog("Rex")).a|)).v
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Cov<Animal> should not subtype V<Dog> - V is invariant")
    }

    @Test
    fun constructorNotSubtypesParent_contDogNotSubtypesVAnimal() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
                type DogHolder = DogHolder { d: Dog }
                type VAnimal = VAnimal { v: V<Animal> }

                VAnimal(Cont(|d -> DogHolder(d).d.name|)).v
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Cont<Dog> should not subtype V<Animal> - V is invariant")
    }

    @Test
    fun constructorNotSubtypesParent_contAnimalNotSubtypesVDog() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type V<'A> = Cov { f: () -> 'A } | Cont { f: 'A -> String }
                type VDog = VDog { v: V<Dog> }

                type Force = F { c: Cont<Animal> }
                contAnimal = F(Cont(|a -> a.name|)).c

                VDog(contAnimal)
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "Cont<Animal> should not subtype V<Dog> - V is invariant")
    }

    // ============================================================
    // Constructors have their own variance
    // Cov<Dog> <: Cov<Animal> (covariant)
    // Cont<Animal> <: Cont<Dog> (contravariant)
    // ============================================================

    @Test
    fun constructorVariance_covariant() {
        assertType(
            "Cov<Animal>",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type Cov<'A> = Cov { f: () -> 'A }
                type CovAnimal = CovAnimal { c: Cov<Animal> }

                CovAnimal(Cov(|Dog("Rex")|)).c
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun constructorVariance_contravariant() {
        assertType(
            "Cont<Dog>",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type Cont<'A> = Cont { f: 'A -> String }
                type AnimalHolder = AnimalHolder { a: Animal }
                type ContAnimal = ContAnimal { c: Cont<Animal> }
                type ContDog = ContDog { c: Cont<Dog> }

                ContDog(ContAnimal(Cont(|a -> AnimalHolder(a).a.name|)).c).c
                """.trimIndent(),
            ),
        )
    }
}
