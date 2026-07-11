package klein.types

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnnotationInferTest {

    @Test
    fun paramAnnotation_identityBecomesMonomorphic() {
        // Without annotation: ('A) -> 'A. With annotation: (Num) -> Num.
        assertType("(Num) -> Num", infer("fun f(x: Num) = x\nf"))
    }

    @Test
    fun lambdaParamAnnotation_identityBecomesMonomorphic() {
        assertType("(Num) -> Num", infer("|x: Num -> x|"))
    }

    @Test
    fun mixedParams_annotatedIsFixed_unannotatedIsInferred() {
        assertType("(Num, Any) -> Num", infer("fun f(x: Num, y) = x\nf"))
    }

    @Test
    fun paramAnnotation_acceptsSubtype() {
        // Dog <: Animal, so passing Dog to (Animal) -> Animal is fine
        assertType(
            "Animal",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                fun wrap(x: Animal): Animal = x
                wrap(Dog("Rex"))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun paramAnnotation_hidesSubtypeFields() {
        // Inside the body, x is Animal — accessing .tricks (Dog-only) should fail
        val errors = inferErrors(
            """
            type Animal = Dog { name: String, tricks: Num } | Cat { name: String }
            fun f(x: Animal) = x.tricks
            f
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
        assertMissingField(errors[0], "tricks")
    }

    @Test
    fun returnAnnotation_hidesSubtypeFromCaller() {
        // Return type is Animal, so caller can't access Dog-specific fields
        val errors = inferErrors(
            """
            type Animal = Dog { name: String, tricks: Num } | Cat { name: String }
            fun wrap(x): Animal = x
            wrap(Dog("Rex", 3)).tricks
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
        assertMissingField(errors[0], "tricks")
    }

    @Test
    fun valAnnotation_acceptsSubtype() {
        assertType(
            "Animal",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                x: Animal = Dog("Rex")
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun ascription_acceptsSubtype() {
        assertType(
            "Animal",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                (Dog("Rex") : Animal)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun ascription_multipleOnSameVarAccumulate() {
        assertType(
            "(Cat & Dog) -> Dog",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String }
                fun g(x) =
                  (x: Cat)
                  (x: Dog)
                g
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun typeVar_paramNameFollowsIntoReturn() {
        // 'B in the return type is part of the signature — introduces it
        // x is returned as 'B, so x must be 'B too
        assertType("('B) -> 'B", infer("fun f(x: 'B) = x\nf"))
    }

    @Test
    fun typeVar_namesFollowAnnotations() {
        // 'B in param/return resolves to the same tvar with nameHint "B" so it survives printing
        assertType("('B) -> 'B", infer("fun f(x: 'B): 'B = x\nf"))
    }


    @Test
    fun typeVarAnnotation_bodyMustRespectReturnType() {
        // Body returns Num, but declared return is 'A — Num </: 'A
        val errors = inferErrors("fun f(x: 'A): 'A = 42\nf")
        assertEquals(1, errors.size)
    }

    @Test
    fun typeVarAnnotation_bodyMustRespectReturnType_nested() {
        // Body returns Option<Num>, but declared return is Option<'A>
        val errors = inferErrors(
            """
            type Option<'A> = None | Some { value: 'A }
            fun f(x: 'A): Option<'A> = Some(42)
            f
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
    }

    @Test
    fun typeVarAnnotation_mixedWithConcrete() {
        // x: 'A, y: Num — 'A is unconstrained, y is fixed
        assertType("('A, Num) -> 'A", infer("fun f(x: 'A, y: Num) = x\nf"))
    }

    @Test
    fun union_outputAnnotation_matchesInferredType() {
        assertType(
            "(Bool) -> Num | String",
            infer("fun f(b: Bool): Num | String = if b then 1 else \"hello\"\nf"),
        )
    }

    @Test
    fun union_outputAnnotation_acceptsSingleBranch() {
        assertType(
            "(Bool) -> Num | String",
            infer("fun f(b: Bool): Num | String = 42\nf"),
        )
    }

    @Test
    fun union_outputAnnotation_dogCatExample() {
        assertType(
            "('A) -> 'A | Dog",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String } | Fish
                fun dogCat(x: 'A): 'A | Dog = if true then Dog("Paco") else x
                dogCat
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun union_outputAnnotation_dogCatCalledWithNum() {
        assertType(
            "Num | Dog",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String } | Fish
                fun dogCat(x: 'A): 'A | Dog = if true then Dog("Paco") else x
                dogCat(5)
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun union_outputAnnotation_dogCatCalledWithCat() {
        assertType(
            "Cat | Dog",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String } | Fish
                fun dogCat(x: 'A): 'A | Dog = if true then Dog("Paco") else x
                dogCat(Cat("Whiskers"))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun union_outputAnnotation_dogCatCalledWithDog() {
        assertType(
            "Dog",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String } | Fish
                fun dogCat(x: 'A): 'A | Dog = if true then Dog("Paco") else x
                dogCat(Dog("Paco"))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun union_outputAnnotation_rejectsUnrelatedReturn() {
        // Symmetric to intersection_inputAnnotation_boundedPolyRejectsMissingField:
        // a return annotated as Num | String must reject a body producing Bool.
        // Currently fails — the flexible wrapper TVar silently accumulates Bool as
        // another lower bound instead of rejecting it.
        val errors = inferErrors("fun f(x: Bool): Num | String = x\nf")
        assertTrue(errors.isNotEmpty(), "expected Bool to be rejected against Num | String")
    }

    @Test
    fun union_valAnnotation_acceptsValueOfEitherType() {
        assertType(
            "Num | String",
            infer("x: Num | String = 42\nx"),
        )
    }

    @Test
    fun union_valAnnotation_rejectsUnrelatedType() {
        val errors = inferErrors("x: Num | String = true\nx")
        assertTrue(errors.isNotEmpty(), "expected Bool to be rejected against Num | String")
    }

    @Test
    fun intersection_mixedConcreteBounds_fieldAccessFromRecordSideSucceeds() {
        // `A & B` is conjunction — a value of this type satisfies BOTH sides, so
        // accessing a field is valid if EITHER side supports it. Here the record
        // side has `name`, so x.name must succeed.
        val errors = inferErrors("fun g(x: { name: String } & Num) = x.name\ng")
        assertEquals(0, errors.size)
    }

    @Test
    fun intersection_mixedConcreteBounds_arithmeticFromNumSideSucceeds() {
        // Parallel: the Num side supports +, so x + 1 must succeed.
        val errors = inferErrors("fun g(x: { name: String } & Num) = x + 1\ng")
        assertEquals(0, errors.size)
    }

    @Test
    fun intersection_inputAnnotation_structuralRecordMerge() {
        assertType(
            "({ age: Num, name: String }) -> String",
            infer("fun greet(x: { name: String } & { age: Num }) = x.name\ngreet"),
        )
    }

    @Test
    fun intersection_inputAnnotation_boundedPolymorphism() {
        assertType(
            "('A & Animal) -> 'A",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String } | Fish
                fun feed(a: 'A & Animal): 'A = a
                feed
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun intersection_inputAnnotation_boundedPolyPreservesSpecificType() {
        assertType(
            "Dog",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String } | Fish
                fun feed(a: 'A & Animal): 'A = a
                feed(Dog("Rex"))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun intersection_inputAnnotation_boundedPolyRejectsNonSubtype() {
        val errors =
            inferErrors(
                """
                type Animal = Dog { name: String } | Cat { name: String } | Fish
                fun feed(a: 'A & Animal): 'A = a
                feed(42)
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "expected a type error when passing a non-Animal to feed")
    }

    @Test
    fun intersection_inputAnnotation_boundedPolyRejectsMissingField() {
        // Bug: with 'A & Dog, accessing x.bone (a field Dog doesn't have) currently
        // widens x's type silently instead of erroring. The annotated upper bound should
        // be authoritative — body field accesses must be checked against it.
        val errors =
            inferErrors(
                """
                type Dog = Dog { name: String }
                fun g(x: 'A & Dog) = x.bone
                g
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMissingField(errors[0], "bone")
    }

    @Test
    fun intersection_inputAnnotation_boundedPolyRejectsCallOnNonFunctionBound() {
        // Parallel to boundedPolyRejectsMissingField for the call-through path:
        // calling x(42) when the bound isn't a function must not silently widen.
        val errors =
            inferErrors(
                """
                type Dog = Dog { name: String }
                fun g(x: 'A & Dog) = x(42)
                g
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "expected a type error calling a non-callable bounded rigid var")
    }

    @Test
    fun intersection_inputAnnotation_boundedPolyRejectsArithmeticOnNonNumBound() {
        // Parallel test for arithmetic: x + 1 must be rejected when 'A's bound isn't Num.
        val errors =
            inferErrors(
                """
                type Dog = Dog { name: String }
                fun g(x: 'A & Dog) = x + 1
                g
                """.trimIndent(),
            )
        assertTrue(errors.isNotEmpty(), "expected a type error using a non-Num bounded rigid var in +")
    }

    @Test
    fun intersection_inputAnnotation_boundedPolyBoundsExtendToSharedTypeVar() {
        // y: 'A reuses the same rigid 'A bound by 'A & Dog on x, so y.bone must error too.
        // Regression guard: bounds on shared skolems apply wherever the skolem appears.
        val errors =
            inferErrors(
                """
                type Dog = Dog { name: String }
                fun g(x: 'A & Dog, y: 'A) = y.bone
                g
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertMissingField(errors[0], "bone")
    }

    @Test
    fun polarity_unionInInputPosition_rejected() {
        val errors = inferErrors("fun f(x: Num | String) = x\nf")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.InvalidAnnotationPolarity)
    }

    @Test
    fun polarity_intersectionInOutputPosition_rejected() {
        val errors = inferErrors("fun f(x): Num & String = x\nf")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.InvalidAnnotationPolarity)
    }

    @Test
    fun polarity_unionInCallbackArgument_allowed() {
        assertType(
            "((Num | String) -> Num) -> Num",
            infer("fun f(cb: (Num | String) -> Num) = cb(42)\nf"),
        )
    }

    @Test
    fun polarity_intersectionInCallbackReturn_allowed() {
        // The result type of cb flows through f, producing a bounded-polymorphism signature:
        // the cb return is 'A bounded by HasName & HasAge, and f returns that same 'A.
        assertType(
            "((Num) -> 'A & HasAge & HasName) -> 'A",
            infer(
                """
                type HasName = HasName { name: String }
                type HasAge = HasAge { age: Num }
                fun f(cb: (Num) -> HasName & HasAge) = cb(42)
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun union_outerOfAppliedTypes_inOutputPosition() {
        // Box is covariant in 'A so the simplifier collapses Box<Num> | Box<String> → Box<Num | String>.
        assertType(
            "(Bool) -> Box<Num | String>",
            infer(
                """
                type Box<'A> = Box { value: 'A }
                fun pick(b: Bool): Box<Num> | Box<String> = if b then Box(1) else Box("hi")
                pick
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun union_insideAppliedType_inOutputPosition() {
        assertType(
            "(Bool) -> Box<Num | String>",
            infer(
                """
                type Box<'A> = Box { value: 'A }
                fun pick(b: Bool): Box<Num | String> = if b then Box(1) else Box("hi")
                pick
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun intersection_insideAppliedType_inInputPosition() {
        // The parameter type is fully pinned by the annotation, so `b.value.name` is only
        // checked against it (HasName supplies `name`) and adds nothing — the intersection
        // prints back exactly as written.
        assertType(
            "(Box<HasName & HasAge>) -> String",
            infer(
                """
                type HasName = HasName { name: String }
                type HasAge = HasAge { age: Num }
                type Box<'A> = Box { value: 'A }
                fun readName(b: Box<HasName & HasAge>): String = b.value.name
                readName
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun polarity_unionInsideAppliedType_inInputPosition_rejected() {
        val errors =
            inferErrors(
                """
                type Box<'A> = Box { value: 'A }
                fun unwrap(b: Box<Num | String>) = b
                unwrap
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.InvalidAnnotationPolarity)
    }

    @Test
    fun polarity_intersectionInsideAppliedType_inOutputPosition_rejected() {
        val errors =
            inferErrors(
                """
                type Box<'A> = Box { value: 'A }
                fun make(): Box<Num & String> = Box(42)
                make
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.InvalidAnnotationPolarity)
    }

    @Test
    fun intersection_typeVarWithAppliedTypeBound() {
        assertType(
            "('A & Box<Animal>) -> 'A",
            infer(
                """
                type Animal = Dog { name: String } | Cat { name: String } | Fish
                type Box<'A> = Box { value: 'A }
                fun hold(b: 'A & Box<Animal>): 'A = b
                hold
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun union_insideContravariantType_inInputPosition_allowed() {
        assertType(
            "(Consumer<Num | String>) -> Bool",
            infer(
                """
                type Consumer<'A> = Consumer { consume: ('A) -> Bool }
                fun useConsumer(c: Consumer<Num | String>): Bool = true
                useConsumer
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun intersection_insideContravariantType_inOutputPosition_allowed() {
        assertType(
            "(Bool) -> Consumer<HasAge & HasName>",
            infer(
                """
                type HasName = HasName { name: String }
                type HasAge = HasAge { age: Num }
                type Consumer<'A> = Consumer { consume: ('A) -> Bool }
                fun makeConsumer(b: Bool): Consumer<HasName & HasAge> = Consumer(|x -> true|)
                makeConsumer
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun polarity_unionInsideContravariantType_inOutputPosition_rejected() {
        val errors =
            inferErrors(
                """
                type Consumer<'A> = Consumer { consume: ('A) -> Bool }
                fun makeConsumer(): Consumer<Num | String> = Consumer(|x -> true|)
                makeConsumer
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.InvalidAnnotationPolarity)
    }

    @Test
    fun polarity_intersectionInsideContravariantType_inInputPosition_rejected() {
        val errors =
            inferErrors(
                """
                type Consumer<'A> = Consumer { consume: ('A) -> Bool }
                fun useConsumer(c: Consumer<Num & String>): Bool = true
                useConsumer
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.InvalidAnnotationPolarity)
    }

    // A union and an intersection can never be mixed in one annotation: they require opposite
    // polarities, and a directly-nested operator sits at the same polarity as its parent, so one
    // of the two always lands at the wrong polarity.
    @Test
    fun polarity_intersectionContainingUnion_inInputPosition_rejected() {
        // `A & (B | C)` at negative: the intersection is fine, but `B | C` is also at negative.
        val errors =
            inferErrors(
                """
                type A = A { a: Num }
                type B = B { b: Num }
                type C = C { c: Num }
                fun f(x: A & (B | C)) = x
                f
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.InvalidAnnotationPolarity)
    }

    @Test
    fun polarity_unionContainingIntersection_inOutputPosition_rejected() {
        // `(A & B) | C` at positive: the union is fine, but `A & B` is also at positive.
        val errors =
            inferErrors(
                """
                type A = A { a: Num }
                type B = B { b: Num }
                type C = C { c: Num }
                fun f(x): (A & B) | C = x
                f
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.InvalidAnnotationPolarity)
    }

    // An invariant type argument is neither positive nor negative, so neither union nor
    // intersection is valid there. (Ref's 'A appears both co- and contravariantly.)
    @Test
    fun polarity_unionInInvariantPosition_rejected() {
        val errors =
            inferErrors(
                """
                type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
                fun f(r: Ref<Num | String>) = r
                f
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.InvalidAnnotationPolarity)
    }

    @Test
    fun polarity_intersectionInInvariantPosition_rejected() {
        val errors =
            inferErrors(
                """
                type Ref<'A> = Ref { get: () -> 'A, set: 'A -> String }
                fun f(r: Ref<Num & String>) = r
                f
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.InvalidAnnotationPolarity)
    }

    @Test
    fun typeVarAnnotation_sharedAcrossParams() {
        // Both params share 'A, so they must unify
        assertType("('A, 'A) -> 'A", infer("fun f(x: 'A, y: 'A) = x\nf"))
    }

    @Test
    fun typeVarAnnotation_bodyCannotConstrainTypeVar() {
        // x and y are 'A (opaque) — body can't assume 'A supports +
        val errors = inferErrors("fun f(x: 'A, y: 'A) = x + y\nf")
        assertEquals(2, errors.size)
    }

    @Test
    fun typeVarAnnotation_distinctSkolemsMismatch() {
        // 'A and 'B are independent skolems — returning 'A where 'B is expected is an error
        val errors = inferErrors("fun f(x: 'A): 'B = x\nf")
        assertEquals(1, errors.size)
    }

    @Test
    fun topLevelValTypeVarDoesNotLeakIntoLaterDefinition() {
        // A top-level val's signature type variable must be scoped to that val. A following
        // definition reusing the name 'Z must be unaffected — its inferred type must be identical
        // whether or not the val precedes it. (Regression: the val used to bind 'Z into the shared
        // top-level scope, so later definitions reused its skolem via the parent-chain lookup.)
        val defs =
            """
            type HasName = HasName { name: String }
            type Box<'A> = Box { value: 'A }
            fun readName(b: Box<'Z & HasName>): String = b.value.name
            readName
            """.trimIndent()
        val withoutVal = infer(defs)
        val withVal = infer("x: 'Z | Num | String = 24\n$defs")
        assertEquals(withoutVal.type, withVal.type)
    }

    @Test
    fun typeVarAnnotation_sameNameInTwoSignaturesAreDistinctSkolems() {
        // 'Z in f and 'Z in g are independent skolems despite the shared name — each signature
        // has its own type-variable scope. In a mutually-recursive group both functions are
        // mono-bound, so f's body passes its own 'Z to g, which demands g's distinct 'Z: a
        // skolem mismatch. If the two 'Z were shared, this would (wrongly) typecheck.
        val errors =
            inferErrors(
                """
                fun f(x: 'Z): 'Z = g(x)
                fun g(x: 'Z): 'Z = f(x)
                f
                """.trimIndent(),
            )
        assertTrue(
            errors.any { it is TypeError.TypeMismatch },
            "expected a skolem mismatch from the two distinct 'Z, got: $errors",
        )
    }

    @Test
    fun typeVarAnnotation_skolemNotSubtypeOfConcrete() {
        // 'A is opaque — can't use it where Num is expected
        val errors = inferErrors("fun f(x: 'A): Num = x\nf")
        assertEquals(1, errors.size)
    }

    @Test
    fun typeVarAnnotation_skolemFieldAccess() {
        // 'A is opaque — can't access any fields on it
        val errors = inferErrors("fun f(x: 'A) = x.name\nf")
        assertEquals(1, errors.size)
    }

    @Test
    fun typeVarAnnotation_skolemAsFunction() {
        // 'A is opaque — can't call it
        val errors = inferErrors("fun f(x: 'A) = x(42)\nf")
        assertEquals(1, errors.size)
    }

    @Test
    fun typeVarAnnotation_topLevelBindingWithSkolem() {
        // Top-level val introduces 'A as a rigid skolem — Num </: 'A → error
        val errors = inferErrors("q: 'A = 4")
        assertEquals(1, errors.size)
    }

    @Test
    fun typeVarAnnotation_topLevelBindingWithGenericSkolem() {
        // None is polymorphic, fits Option<skolem_A> without constraint — no error.
        // When `o` is referenced, the skolem is generalized to a fresh TVar at each use.
        assertType(
            "Option<'A>",
            infer(
                """
                type Option<'A> = None | Some { a: 'A }
                o: Option<'A> = None
                o
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun paramAnnotation_mismatch() {
        val errors = inferErrors("fun f(x: Num) = x\nf(\"hello\")")
        assertEquals(1, errors.size)
    }

    @Test
    fun returnAnnotation_mismatch() {
        // Body returns Num (x + 1), but declared return is String
        val errors = inferErrors("fun f(x): String = x + 1\nf")
        assertEquals(1, errors.size)
    }

    @Test
    fun valAnnotation_mismatch() {
        val errors = inferErrors("x: Num = \"hello\"")
        assertEquals(1, errors.size)
    }

    @Test
    fun ascription_mismatch() {
        val errors = inferErrors("(\"hello\" : Num)")
        assertEquals(1, errors.size)
    }

    @Test
    fun genericTypeAnnotation_valBinding() {
        assertType(
            "Option<Num>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                x: Option<Num> = Some(42)
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun genericTypeAnnotation_acceptsSubtype() {
        assertType(
            "Option<Num>",
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                x: Option<Num> = None
                x
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun functionTypeAnnotation_mismatch() {
        val errors = inferErrors("f: Num -> String = |x -> x + 1|")
        assertEquals(1, errors.size)
    }

    @Test
    fun annotation_unknownTypeName() {
        val errors = inferErrors("fun f(x: UnknownType) = x\nf")
        assertEquals(1, errors.size)
        assertUnbound(errors[0], "UnknownType")
    }

    @Test
    fun annotation_anyAcceptsAnyValue() {
        assertType("Any", infer("x: Any = 42\nx"))
    }

    @Test
    fun annotation_nothingRejectsValues() {
        // Nothing is the bottom type — no value inhabits it
        val errors = inferErrors("x: Nothing = 42")
        assertEquals(1, errors.size)
    }

    @Test
    fun annotation_anyInFunctionParam() {
        // (Any) -> Num accepts any input
        assertType("(Any) -> Num", infer("fun f(x: Any): Num = 42\nf"))
    }

    @Test
    fun annotation_anyAcceptsAnySubtype() {
        // Anything is a subtype of Any
        assertType("(Any) -> Num", infer("fun f(x: Any): Num = 42\nf(\"hello\")\nf"))
    }

    @Test
    fun annotation_nothingIsSubtypeOfAny() {
        // Nothing flows into anything (including Num via a polymorphic function)
        val errors = inferErrors("fun f(x: Nothing): Num = 42\nf")
        assertEquals(0, errors.size)
    }

    @Test
    fun annotation_typeArityMismatch() {
        val errors = inferErrors(
            """
            type Option<'A> = None | Some { value: 'A }
            fun f(x: Option) = x
            f
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
        assertTypeArityMismatch(errors[0], "Option", expected = 1, actual = 0)
    }

    @Test
    fun recordAnnotation_hidesExtraFields() {
        // Annotation has fewer fields than the value — width subtyping accepts the value,
        // but the extra fields become invisible to callers
        val errors = inferErrors(
            """
            r: { x: Num } = { x = 1, y = 2 }
            r.y
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
        assertMissingField(errors[0], "y")
    }

    // --- Type variable scoping ---

    @Test
    fun typeVar_returnTypeIntroducesTypeVar() {
        // 'B in the return type is part of the signature — introduces it
        // x is returned as 'B, so x must be 'B too
        assertType("('B) -> 'B", infer("fun f(x): 'B = x\nf"))
    }

    @Test
    fun typeVar_ascriptionRejectsNewTypeVar() {
        // 'B is not in f's signature — error in ascription
        val errors = inferErrors("fun f(x) = (x : 'B)\nf")
        assertEquals(1, errors.size)
    }

    @Test
    fun typeVar_sharedBetweenParamAndLocalBinding() {
        // 'A in the local annotation refers to the same 'A from the param
        // xs is NOT generalized because 'A is bound by the function
        assertType(
            "('A) -> List<'A>",
            infer(
                """
                type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
                fun f(x: 'A) =
                  xs: List<'A> = Cons(x, Nil)
                  xs
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun typeVar_localAnnotationRejectsNewTypeVar() {
        // 'B is not introduced in the function signature — error
        val errors = inferErrors(
            """
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            fun f(x) =
              xs: List<'B> = Nil
              xs
            f
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
    }

    // --- Record field annotations ---

    @Test
    fun recordField_concreteAnnotation_mismatch() {
        val errors = inferErrors("{ x: Num = \"hello\" }")
        assertEquals(1, errors.size)
    }

    @Test
    fun recordField_lambdaIdentityBecomesMonomorphic() {
        assertType(
            "{ id: (Num) -> Num }",
            infer("{ id = |x: Num -> x| }"),
        )
    }

    @Test
    fun recordField_lambdaAnnotation_mismatchAtCallSite() {
        val errors = inferErrors(
            """
            r = { f = |x: Num -> x| }
            r.f("hello")
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
    }

    @Test
    fun recordField_lambdaAnnotation_tvarNotScoped() {
        // Lambda introduces 'A as skolem; body constrains skolem to Num → error
        val errors = inferErrors("{ f = |x: 'A -> x + 1| }")
        assertEquals(1, errors.size)
    }

    @Test
    @Ignore // Fun defs in records aren't implemented yet
    fun recordField_funDefWithAnnotatedParams() {
        assertType(
            "{ double: (Num) -> Num }",
            infer(
                """
                {
                  fun double(x: Num): Num = x * 2
                }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun recordField_typeVarInsideFunction_rejectsNewTypeVar() {
        // 'B appears twice — one UnboundTypeVar per occurrence
        val errors = inferErrors(
            """
            fun f(x) =
              r = { id: 'B -> 'B = |y -> y| }
              r.id(x)
            f
            """.trimIndent(),
        )
        assertEquals(2, errors.size)
    }


    // --- Nesting and shadowing ---

    @Test
    @Ignore // Nested fundefs aren't implemented yet
    fun nestedFunction_innerIntroducesOwnTypeVars() {
        assertType(
            "(Num) -> ('A) -> 'A",
            infer(
                """
                fun outer(x: Num) =
                  fun inner(y: 'A): 'A = y
                  inner
                outer
                """.trimIndent(),
            ),
        )
    }

    @Test
    @Ignore // Nested fundefs aren't implemented yet
    fun nestedFunction_shadowsOuterTypeVar() {
        // 'A in inner's signature is a NEW skolem that shadows outer's 'A
        val errors = inferErrors(
            """
            fun outer(x: 'A) =
              fun inner(y: 'A) = y + 1
              inner(x)
            outer
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
    }

    @Test
    fun nestedLambda_shadowsOuterTypeVar() {
        // 'A in the inner lambda is a NEW skolem that shadows outer's 'A
        // inner body does y + 1 which constrains inner's 'A to Num — but it's rigid
        val errors = inferErrors(
            """
            fun outer(x: 'A) =
              inner = |y: 'A -> y + 1|
              inner(x)
            outer
            """.trimIndent(),
        )
        assertEquals(1, errors.size)
    }

    @Test
    fun nestedLambda_introducesOwnTypeVars() {
        assertType(
            "(Num) -> ('A) -> 'A",
            infer(
                """
                fun outer(x: Num) =
                  |y: 'A -> y|
                outer
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nestedLambda_localBindingRefersToInnerScope() {
        // y: 'B inside the inner lambda refers to inner's 'B, not outer's 'A.
        // Outer's x is unused, so 'A is single-polarity and simplifies to Any.
        assertType(
            "(Any) -> ('B) -> 'B",
            infer(
                """
                fun outer(x: 'A) =
                  |z: 'B ->
                    y: 'B = z
                    y
                  |
                outer
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun nestedLambda_localBindingCanReferToOuterTypeVar() {
        // The lambda doesn't introduce 'A, so 'A resolves from outer's scope
        assertType(
            "('A) -> 'A",
            infer(
                """
                fun outer(x: 'A) =
                  inner = |z ->
                    y: 'A = z
                    y
                  |
                  inner(x)
                outer
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun deeplyNested_eachLevelHasOwnScope() {
        // Outer 'A and middle 'B are unused — single-polarity, simplify to Any.
        // Innermost 'C is used so it's preserved. Verifies each level has its own scope.
        assertType(
            "(Any) -> (Any) -> ('C) -> 'C",
            infer(
                """
                fun f(x: 'A) =
                  |y: 'B ->
                    |z: 'C -> z|
                  |
                f
                """.trimIndent(),
            ),
        )
    }

    // --- Multi-field record annotations ---

    @Test
    fun multiFieldRecord_inputAnnotation_fieldAccess() {
        // Body touches both fields: name (returned) and age (arithmetic).
        assertType(
            "({ age: Num, name: String }) -> String",
            infer(
                """
                fun f(x: { name: String, age: Num }) =
                  ignored = x.age + 1
                  x.name
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun multiFieldRecord_inputAnnotation_accessSecondField() {
        assertType(
            "({ age: Num, name: String }) -> Num",
            infer("fun f(x: { name: String, age: Num }) = x.age\nf"),
        )
    }

    @Test
    fun multiFieldRecord_inputAnnotation_rejectsMissingField() {
        // The annotation is authoritative — accessing a field it doesn't declare is an error.
        val errors = inferErrors("fun f(x: { name: String, age: Num }) = x.bone\nf")
        assertEquals(1, errors.size)
    }

    @Test
    fun multiFieldRecord_outputAnnotation_returnsRecord() {
        assertType(
            "({ age: Num, name: String }) -> { age: Num, name: String }",
            infer("fun f(x): { name: String, age: Num } = x\nf"),
        )
    }

    // A multi-field record demanded of an intersection: each field is checked against the
    // members independently, so the demand is met as long as some member supplies each field.
    @Test
    fun intersectionReturnedAsMultiFieldRecord_fieldsSplitAcrossMembers() {
        assertType(
            "(Dog & Hero) -> { movie: String, name: String }",
            infer(
                """
                type Dog = Dog { name: String }
                type Hero = Hero { movie: String }
                fun f(x: Dog & Hero): { name: String, movie: String } = x
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun intersectionReturnedAsMultiFieldRecord_singleMemberSuffices() {
        assertType(
            "(Dog & Hero) -> { name: String }",
            infer(
                """
                type Dog = Dog { name: String }
                type Hero = Hero { movie: String }
                fun f(x: Dog & Hero): { name: String } = x
                f
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun intersectionReturnedAsMultiFieldRecord_rejectsFieldNoMemberSupplies() {
        // Neither Dog nor Hero has `fins`, so the record demand can't be satisfied.
        val errors =
            inferErrors(
                """
                type Dog = Dog { name: String }
                type Hero = Hero { movie: String }
                fun f(x: Dog & Hero): { name: String, fins: String } = x
                f
                """.trimIndent(),
            )
        assertEquals(1, errors.size)
    }
}
