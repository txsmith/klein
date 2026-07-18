package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GenericsTypeCheckTest {
    @Test
    fun identity_instantiatedToNum() =
        assertEquals(
            TNum,
            infer(
                """
                fun id(x: 'T): 'T = x
                id(42)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun identity_instantiatedToBool() =
        assertEquals(
            TBool,
            infer(
                """
                fun id(x: 'T): 'T = x
                id(true)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun genericFieldProjection() =
        assertEquals(
            TNum,
            infer(
                """
                fun getX(r: { x: 'T }): 'T = r.x
                getX({ x = 42 })
                """.trimIndent(),
            ).type,
        )

    // A nominal type satisfies a structural interface, so a generic function over `{ value: 'T }`
    // takes a nominal argument and solves `'T` from its field — in synth mode, with no result demand.
    @Test
    fun genericRecordParam_solvedFromNominalArg() =
        assertEquals(
            TNum,
            infer(
                """
                type Box = Box { value: Num }
                fun getValue(r: { value: 'T }): 'T = r.value
                getValue(Box(5))
                """.trimIndent(),
            ).type,
        )

    // The nominal carries extra fields beyond what the parameter demands — plain width subtyping.
    @Test
    fun genericRecordParam_nominalArgWithExtraField() =
        assertEquals(
            TNum,
            infer(
                """
                type Box = Box { value: Num, extra: String }
                fun getValue(r: { value: 'T }): 'T = r.value
                getValue(Box(5, "hi"))
                """.trimIndent(),
            ).type,
        )

    // The nominal is missing a field the parameter demands, so no instantiation of `'T` matches.
    @Test
    fun genericRecordParam_rejectsNominalMissingField() {
        val e =
            infer(
                """
                type Box = Box { value: Num }
                fun needTwo(r: { value: 'T, extra: String }): 'T = r.value
                needTwo(Box(5))
                """.trimIndent(),
            ).errors.filterIsInstance<TypeError.TypeMismatch>().single()
        assertEquals("{ value: Num }", klein.Type.print(e.subtype))
    }

    @Test
    fun genericFunctionResult() =
        assertEquals(
            TBool,
            infer(
                """
                fun useF(f: (Num) -> 'A): 'A = f(42)
                useF(|n: Num -> n > 0|)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun twoTypeParams_returnsFirst() =
        assertEquals(
            TNum,
            infer(
                """
                fun first(a: 'A, b: 'B): 'A = a
                first(1, true)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun polymorphicArgInstantiatedAtMonomorphicParam() =
        // `id : ∀A. (A) -> A` passed where `(Num) -> Num` is demanded — instantiated at the
        // argument's check (subsume), not left polymorphic.
        assertEquals(
            TNum,
            infer(
                """
                fun id(x: 'A) = x
                fun modify(f: (Num) -> Num) = f(3)
                modify(id)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun synthContravariantConstructor_keepsArgType() =
        assertEquals(
            TRef("Consumer", listOf(TNum)),
            infer(
                """
                type Consumer<'A> = Consumer { consume: 'A -> String }
                Consumer(|d: Num -> "x"|)
                """.trimIndent(),
            ).type,
        )

    // --- Local polymorphism ---
    // Polymorphism comes from a written `'T`, never inference: a local `val` annotated with a type
    // variable is generalized and can be used directly at several types (rank-1). An unannotated bare
    // lambda (`id = |x -> x|`) does not infer a polymorphic type.

    // A parameterless lambda whose block body starts with a type-annotated binding is mis-parsed:
    // `id: (...)` reads as the lambda's parameter list because `->` is both the lambda arrow and the
    // function-type constructor. Pending a decision on switching the lambda head to `=>`.
    @Ignore
    @Test
    fun localPoly_idUsedTwice() {
        val program =
            """
            |
              id: ('T) -> 'T = |x -> x|
              a = id(1)
              b = id(true)
              a
            |
            """.trimIndent()
        assertTrue(infer(program).errors.isEmpty())
    }

    @Test
    fun localPoly_classic() {
        val program =
            """
            f: ('T) -> 'T = |x -> x|
            { a = f(0), b = f(true) }
            """.trimIndent()
        assertEquals(TRecord(mapOf("a" to TNum, "b" to TBool)), infer(program).type)
    }

    // A phantom-typed binding generalizes at the annotation, so the value is used at two instantiations.
    @Test
    fun localPoly_phantomValUsedAtTwoTypes() {
        val program =
            """
            type Phantom<'A> = Phantom { tag: Num }
            fun useNum(p: Phantom<Num>): Num = p.tag
            fun useStr(p: Phantom<String>): Num = p.tag
            q: Phantom<'A> = Phantom(4)
            { a = useNum(q), b = useStr(q) }
            """.trimIndent()
        assertEquals(TRecord(mapOf("a" to TNum, "b" to TNum)), infer(program).type)
    }

    @Test
    fun localPoly_nestedLet() {
        val program =
            """
            f: ('T) -> 'T = |x -> x|
            g = f
            g(42)
            """.trimIndent()
        assertEquals(TNum, infer(program).type)
    }

    @Test
    fun localPoly_recordWithMixedLevels() {
        val program =
            """
            outer: (Num) -> Num = |x ->
              inner: ('Y) -> { first: Num, second: 'Y } = |y -> { first = x, second = y }|
              a = inner(1)
              b = inner(true)
              a.second + 1
            |
            outer(42)
            """.trimIndent()
        assertTrue(infer(program).errors.isEmpty())
    }

    @Test
    fun localPoly_withCapture() {
        val program =
            """
            |y: Num ->
              f: ('T) -> 'T = |x -> x|
              { a = f(y), b = f(true) }
            |
            """.trimIndent()
        assertTrue(infer(program).errors.isEmpty())
    }

    @Test
    fun localPoly_twiceCombinator() {
        val program =
            """
            twice: (('T) -> 'T) -> ('T) -> 'T = |f -> |x -> f(f(x))||
            twice
            """.trimIndent()
        assertTrue(infer(program).errors.isEmpty())
    }

    @Test
    fun localPoly_twiceApplied() {
        val program =
            """
            twice: (('T) -> 'T) -> ('T) -> 'T = |f -> |x -> f(f(x))||
            twice(|n: Num -> n + 1|)(0)
            """.trimIndent()
        assertEquals(TNum, infer(program).type)
    }

    // --- rigid type variables from signature annotations ---

    @Test
    fun typeVarAnnotation_paramAndReturn() =
        assertInfersType(
            TFun(listOf(tv("B")), tv("B")),
            """
            fun f(x: 'B): 'B = x
            f
            """.trimIndent(),
        )

    @Test
    fun typeVarAnnotation_mixedWithConcrete() =
        assertInfersType(
            TFun(listOf(tv("A"), TNum), tv("A")),
            """
            fun f(x: 'A, y: Num) = x
            f
            """.trimIndent(),
        )

    @Test
    fun typeVarAnnotation_sharedAcrossParams() =
        assertInfersType(
            TFun(listOf(tv("A"), tv("A")), tv("A")),
            """
            fun f(x: 'A, y: 'A) = x
            f
            """.trimIndent(),
        )

    @Test
    fun typeVarAnnotation_sharedBetweenParamAndLocalBinding() =
        assertInfersType(
            TFun(listOf(tv("A")), TRef("List", listOf(tv("A")))),
            """
            type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
            fun f(x: 'A) =
              xs: List<'A> = Cons(x, Nil)
              xs
            f
            """.trimIndent(),
        )

    @Test
    fun typeVarAnnotation_bodyMustRespectReturnType() =
        assertMismatch(
            "Num",
            "A",
            """
            fun f(x: 'A): 'A = 42
            f
            """.trimIndent(),
        )

    @Test
    fun typeVarAnnotation_bodyMustRespectReturnType_nested() {
        val e =
            infer(
                """
                type Option<'A> = None | Some { value: 'A }
                fun f(x: 'A): Option<'A> = Some(42)
                f
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.TypeMismatch>(e)
    }

    @Test
    fun typeVarAnnotation_bodyCannotConstrainTypeVar() {
        val errors =
            infer(
                """
                fun f(x: 'A, y: 'A) = x + y
                f
                """.trimIndent(),
            ).errors
        assertEquals(2, errors.size)
        assertTrue(errors.all { it is TypeError.TypeMismatch }, "errors: $errors")
    }

    @Test
    fun typeVarAnnotation_distinctSkolemsMismatch() =
        assertMismatch(
            "A",
            "B",
            """
            fun f(x: 'A): 'B = x
            f
            """.trimIndent(),
        )

    @Test
    fun typeVarAnnotation_skolemNotSubtypeOfConcrete() =
        assertMismatch(
            "A",
            "Num",
            """
            fun f(x: 'A): Num = x
            f
            """.trimIndent(),
        )

    @Test
    fun typeVarAnnotation_skolemFieldAccess() {
        assertIs<TypeError.NotARecord>(
            infer(
                """
                fun f(x: 'A) = x.name
                f
                """.trimIndent(),
            ).errors.single(),
        )
    }

    @Test
    fun typeVarAnnotation_skolemAsFunction() {
        assertIs<TypeError.NotAFunction>(
            infer(
                """
                fun f(x: 'A) = x(42)
                f
                """.trimIndent(),
            ).errors.single(),
        )
    }

    // --- Parked: rank-2 / inference-only (NOT rank-1 targets) ---
    // Each threads a polymorphic value through a binding/return and uses it at several types — that
    // is rank-2 (higher-rank), out of scope for the rank-1-first plan. Asserting they type-check
    // would silently commit us to higher-rank, so they stay parked:
    //   escapingFunction, nestedCapture, escapingCapture, capturedVar,
    //   idAppliedToItself, twiceCombinator_onIdentity
    //   withUnionInput — needs union *inference* on the captured param (unsupported)
}
