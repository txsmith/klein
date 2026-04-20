package klein.types

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

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

    // @Test
    // fun typeVar_namesFollowAnnotations() {
    //     // Needs: TVar nameHint so declared names survive printing
    //     assertType("('B) -> 'B", infer("fun f(x: 'B): 'B = x\nf"))
    // }


    // @Test
    // fun typeVarAnnotation_bodyMustRespectReturnType() {
    //     // Body returns Num, but declared return is 'A — Num </: 'A
    //     val errors = inferErrors("fun f(x: 'A): 'A = 42\nf")
    //     assertEquals(1, errors.size)
    // }

    // @Test
    // fun typeVarAnnotation_bodyMustRespectReturnType_nested() {
    //     // Body returns Option<Num>, but declared return is Option<'A>
    //     val errors = inferErrors(
    //         """
    //         type Option<'A> = None | Some { value: 'A }
    //         fun f(x: 'A): Option<'A> = Some(42)
    //         f
    //         """.trimIndent(),
    //     )
    //     assertEquals(1, errors.size)
    // }

    @Test
    fun typeVarAnnotation_mixedWithConcrete() {
        // x: 'A, y: Num — 'A is unconstrained, y is fixed
        assertType("('A, Num) -> 'A", infer("fun f(x: 'A, y: Num) = x\nf"))
    }

    @Test
    fun typeVarAnnotation_sharedAcrossParams() {
        // Both params share 'A, so they must unify
        assertType("('A, 'A) -> 'A", infer("fun f(x: 'A, y: 'A) = x\nf"))
    }

    // @Test
    // fun typeVarAnnotation_bodyCannotConstrainTypeVar() {
    //     // x and y are 'A (opaque) — body can't assume 'A supports +
    //     val errors = inferErrors("fun f(x: 'A, y: 'A) = x + y\nf")
    //     assertEquals(1, errors.size)
    // }

    // @Test
    // fun typeVarAnnotation_distinctSkolemsMismatch() {
    //     // 'A and 'B are independent skolems — returning 'A where 'B is expected is an error
    //     val errors = inferErrors("fun f(x: 'A): 'B = x\nf")
    //     assertEquals(1, errors.size)
    // }

    // @Test
    // fun typeVarAnnotation_skolemNotSubtypeOfConcrete() {
    //     // 'A is opaque — can't use it where Num is expected
    //     val errors = inferErrors("fun f(x: 'A): Num = x\nf")
    //     assertEquals(1, errors.size)
    // }

    // @Test
    // fun typeVarAnnotation_topLevelBindingWithSkolem() {
    //     // x: 'A = 42 — 'A is rigid, Num </: 'A
    //     val errors = inferErrors("x: 'A = 42")
    //     assertEquals(1, errors.size)
    // }

    // @Test
    // fun typeVarAnnotation_skolemFieldAccess() {
    //     // 'A is opaque — can't access any fields on it
    //     val errors = inferErrors("fun f(x: 'A) = x.name\nf")
    //     assertEquals(1, errors.size)
    // }

    // @Test
    // fun typeVarAnnotation_skolemAsFunction() {
    //     // 'A is opaque — can't call it
    //     val errors = inferErrors("fun f(x: 'A) = x(42)\nf")
    //     assertEquals(1, errors.size)
    // }

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

    // @Test
    // fun typeVar_ascriptionRejectsNewTypeVar() {
    //     // 'B is not in f's signature — error in ascription
    //     val errors = inferErrors("fun f(x) = (x : 'B)\nf")
    //     assertEquals(1, errors.size)
    // }

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

    // @Test
    // fun typeVar_localAnnotationRejectsNewTypeVar() {
    //     // 'B is not introduced in the function signature — error
    //     val errors = inferErrors(
    //         """
    //         type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }
    //         fun f(x) =
    //           xs: List<'B> = Nil
    //           xs
    //         f
    //         """.trimIndent(),
    //     )
    //     assertEquals(1, errors.size)
    // }

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

    // @Test
    // fun recordField_lambdaAnnotation_tvarNotScoped() {
    //     val errors = inferErrors("{ f = |x: 'A -> x + 1| }")
    //     assertEquals(1, errors.size)
    // }

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

    // @Test
    // fun recordField_typeVarInsideFunction_rejectsNewTypeVar() {
    //     val errors = inferErrors(
    //         """
    //         fun f(x) =
    //           r = { id: 'B -> 'B = |y -> y| }
    //           r.id(x)
    //         f
    //         """.trimIndent(),
    //     )
    //     assertEquals(1, errors.size)
    // }


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

    // @Test
    // fun nestedLambda_shadowsOuterTypeVar() {
    //     // 'A in the inner lambda is a NEW skolem that shadows outer's 'A
    //     // inner body does y + 1 which constrains inner's 'A to Num — but it's rigid
    //     val errors = inferErrors(
    //         """
    //         fun outer(x: 'A) =
    //           inner = |y: 'A -> y + 1|
    //           inner(x)
    //         outer
    //         """.trimIndent(),
    //     )
    //     assertEquals(1, errors.size)
    // }

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

    // @Test
    // fun nestedLambda_localBindingRefersToInnerScope() {
    //     // y: 'B inside the inner lambda refers to inner's 'B, not outer's 'A
    //     assertType(
    //         "('A) -> ('B) -> 'B",
    //         infer(
    //             """
    //             fun outer(x: 'A) =
    //               |z: 'B ->
    //                 y: 'B = z
    //                 y
    //               |
    //             outer
    //             """.trimIndent(),
    //         ),
    //     )
    // }

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

    // @Test
    // fun deeplyNested_eachLevelHasOwnScope() {
    //     assertType(
    //         "('A) -> ('B) -> ('C) -> 'C",
    //         infer(
    //             """
    //             fun f(x: 'A) =
    //               |y: 'B ->
    //                 |z: 'C -> z|
    //               |
    //             f
    //             """.trimIndent(),
    //         ),
    //     )
    // }
}
