package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(1, errors.size, "Box<Cat> should not subtype Box<Dog>")
        assertMismatch(errors[0], "Cat", "Dog")
    }

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
        assertEquals(1, errors.size, "Consumer<Dog> should not subtype Consumer<Cat>")
        assertMismatch(errors[0], "Cat", "Dog")
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
        assertEquals(1, errors.size, "Ref<Dog> should not subtype Ref<Animal>")
        assertMismatch(errors[0], "Animal", "Dog")
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
        assertEquals(2, errors.size, "Ref<Cat> should not subtype Ref<Dog>")
        assertMismatch(errors[0], "Cat", "Dog")
        assertMismatch(errors[1], "Dog", "Cat")
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
    fun phantom_unusedParamIsInvariant_catNotSubtypeDog() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Phantom<'A> = Phantom { value: Num }
                type PhantomAnimal = PhantomAnimal { p: Phantom<Animal> }
                type PhantomCat = PhantomCat { p: Phantom<Cat> }
                type PhantomDog = PhantomDog { p: Phantom<Dog> }

                PhantomAnimal(PhantomDog(Phantom(42)).p).p
                PhantomDog(PhantomCat(Phantom(42)).p).p
                """.trimIndent(),
            )
        assertEquals(3, errors.size)
        assertMismatch(errors[0], "Animal", "Dog")
        assertMismatch(errors[1], "Cat", "Dog")
        assertMismatch(errors[2], "Dog", "Cat")
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
        assertEquals(2, errors.size, "(Dog -> Dog) should not subtype (Cat -> Cat)")
        assertMismatch(errors[0], "Cat", "Dog")
        assertMismatch(errors[1], "Dog", "Cat")
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
        assertEquals(1, errors.size, "Producer<Animal> should not subtype Producer<Dog>")
        assertMismatch(errors[0], "Animal", "Dog")
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

    @Test
    fun swappedRecursion_firstParam_covariantDirectionFails() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }

                type Cast = Cast { f: Weird<Dog, Num> -> Weird<Animal, Num> }
                Cast(|w -> w|)
                """.trimIndent(),
            )
        assertEquals(1, errors.size, "Weird<Dog, Num> should not subtype Weird<Animal, Num> - 'A is invariant")
        assertMismatch(errors[0], "Animal", "Dog")
    }

    @Test
    fun swappedRecursion_secondParam_covariantDirectionFails() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }

                type Cast = Cast { f: Weird<Num, Dog> -> Weird<Num, Animal> }
                Cast(|w -> w|)
                """.trimIndent(),
            )
        assertEquals(1, errors.size, "Weird<Num, Dog> should not subtype Weird<Num, Animal> - 'B is invariant")
        assertMismatch(errors[0], "Animal", "Dog")
    }

    @Test
    fun swappedRecursion_secondParam_contravariantDirectionFails() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Weird<'A, 'B> = Weird { fn: 'A -> 'B, next: Weird<'B, 'A> }

                type Cast = Cast { f: Weird<Num, Animal> -> Weird<Num, Dog> }
                Cast(|w -> w|)
                """.trimIndent(),
            )
        assertEquals(1, errors.size, "Weird<Num, Animal> should not subtype Weird<Num, Dog> - 'B is invariant")
        assertMismatch(errors[0], "Animal", "Dog")
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
        assertEquals(1, errors.size, "Cov<Dog> should not subtype V<Animal> - V is invariant")
        assertMismatch(errors[0], "Animal", "Dog")
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
        assertEquals(1, errors.size, "Cov<Animal> should not subtype V<Dog> - V is invariant")
        assertMismatch(errors[0], "Animal", "Dog")
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
        assertEquals(1, errors.size, "Cont<Dog> should not subtype V<Animal> - V is invariant")
        assertMismatch(errors[0], "Animal", "Dog")
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
        assertEquals(1, errors.size, "Cont<Animal> should not subtype V<Dog> - V is invariant")
        assertMismatch(errors[0], "Animal", "Dog")
    }

    // ============================================================
    // Type simplification respects variance
    // Contravariant type params should simplify to their bound, not Nothing
    // ============================================================

    @Test
    fun simplification_contravariantParamShowsBound_notNothing() {
        assertType(
            "Consumer<Dog>",
            infer(
                """
                type Animal = Dog | Cat
                type Consumer<'A> = Consumer { f: 'A -> Num }
                type ForceDog = ForceDog { dog: Dog }

                Consumer(|dog ->
                    ForceDog(dog)
                    1
                |)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun contravariant_genericFunctionReceivesConsumerAnimal_constrainedToConsumerDog() {
        assertType(
            "String",
            infer(
                """
                type Animal = Dog { name: String, breed: String } | Cat { name: String }
                type Consumer<'A> = Consumer { consume: 'A -> String }
                type ConsumerDog = ConsumerDog { c: Consumer<Dog> }

                fun runConsumer(c) = ConsumerDog(c).c.consume(Dog("Rex", "Poodle"))
                runConsumer(Consumer(|a -> a.name|))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun invariant_genericFunctionInfersRefNumFromUsage() {
        assertType(
            "Num",
            infer(
                """
                type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
                type RefNum = RefNum { r: Ref<Num> }

                fun readRef(r) = RefNum(r).r.get()
                readRef(Ref(|42|, |n -> "ok"|))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mixedVariance_twoParamTypePassedThroughFunction() {
        assertType(
            "Transform<Animal, Dog>",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                type Transform<'A, 'B> = Transform { value: 'A, handler: 'B -> String }
                type TransformAnimalDog = TransformAnimalDog { t: Transform<Animal, Dog> }

                fun wrap(t) = TransformAnimalDog(t).t
                wrap(Transform(Dog("Fido"), |a -> a.name|))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun invariantTypeReturnedAndPassedToAnotherFunction() {
        assertType(
            "String",
            infer(
                """
                type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }

                fun mkRef(v) = Ref(|v|, |x -> "ok"|)
                fun useRef(r) = r.set(r.get())
                useRef(mkRef(42))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun bareConstructor_subtypesCovariantParent() {
        assertType(
            "Box<Num>",
            infer(
                """
                type Box<'A> = Full { value: 'A } | Empty
                type BoxNum = BoxNum { b: Box<Num> }
                BoxNum(Empty).b
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun bareConstructor_subtypesInvariantParent() {
        assertType(
            "Cell<Num>",
            infer(
                """
                type Cell<'A> = Live { get: () -> 'A, set: 'A -> String } | Dead
                type CellNum = CellNum { c: Cell<Num> }
                CellNum(Dead).c
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun bareConstructor_usedAtTwoDifferentInstantiations() {
        assertType(
            "{ a: Option<Num>, b: Option<String> }",
            infer(
                """
                type Option<'A> = Some { value: 'A } | None
                type OptNum = OptNum { o: Option<Num> }
                type OptStr = OptStr { o: Option<String> }
                { a = OptNum(None).o, b = OptStr(None).o }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun bareConstructor_multiParamSumWhereOtherConstructorUsesAllParams() {
        assertType(
            "Result<String, Num>",
            infer(
                """
                type Result<'A, 'B> = Ok { value: 'A } | Err { error: 'B } | Unknown
                type ResultStringNum = ResultStringNum { r: Result<String, Num> }
                ResultStringNum(Unknown).r
                """.trimIndent(),
            ),
        )
    }
}
