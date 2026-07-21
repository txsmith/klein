package klein.check

import klein.check.Type.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val SHAPES =
    "type Shape = Circle { radius: Num } | Square { side: Num } | Tri { base: Num, height: Num }"

private const val ANIMALS =
    "type Animal = Dog { name: String, legs: Num } | Cat { name: String, lives: Num } | Snake"

private const val RESULT =
    "type Result<'T, 'E> = Ok { value: 'T } | Err { error: 'E }"

class MatchTypeCheckTest {
    // ── Destructuring and narrowing ────────────────────────────────────────────

    @Test
    fun exhaustiveDestructuringMatchChecks() {
        assertInfersType(
            TFun(listOf(TRef("Shape")), TNum),
            """
            $SHAPES
            fun area(s: Shape): Num = match s
              Circle { radius } -> 3 * radius * radius
              Square { side } -> side * side
              Tri { base, height } -> base * height / 2
            area
            """.trimIndent(),
        )
    }

    @Test
    fun constructorBinderNamesTheMatchedValueAtItsConstructorType() {
        assertInfersType(
            TFun(listOf(TRef("Animal")), TStr),
            """
            $ANIMALS
            fun call(a: Animal): String = match a
              Dog d -> d.name
              Cat c -> c.name
              Snake -> "hsss"
            call
            """.trimIndent(),
        )
    }

    @Test
    fun constructorBinderIsTypedAsTheConstructorNotTheParent() {
        // legs lives only on Dog; the binder must be Dog, not the Animal parent (whose iface lacks it).
        assertInfersType(
            TFun(listOf(TRef("Animal")), TNum),
            """
            $ANIMALS
            fun f(a: Animal): Num = match a
              Dog d -> d.legs
              _ -> 0
            f
            """.trimIndent(),
        )
    }

    @Test
    fun bareConstructorArmDoesNotNarrowTheScrutinee() {
        // Replacing narrowing: a stays Animal, so a.name (absent from the sum's iface) errors.
        val e =
            infer(
                """
                $ANIMALS
                fun call(a: Animal): String = match a
                  Dog -> a.name
                  _ -> "x"
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
    }

    @Test
    fun genericConstructorBinderSubstitutesTheScrutineeTypeArgs() {
        assertInfersType(
            TFun(listOf(TRef("Result", listOf(TNum, TStr))), TNum),
            """
            $RESULT
            fun unwrap(r: Result<Num, String>): Num = match r
              Ok o -> o.value
              Err -> 0
            unwrap
            """.trimIndent(),
        )
    }

    @Test
    fun destructuredFieldsGetTheirDeclaredTypes() {
        assertInfersType(
            TFun(listOf(TRef("Shape")), TNum),
            """
            $SHAPES
            fun f(s: Shape) = match s
              Circle { radius } -> radius
              _ -> 0
            f
            """.trimIndent(),
        )
    }

    @Test
    fun renamedFieldBindsTheNewName() {
        assertInfersType(
            TFun(listOf(TRef("Result", listOf(TNum, TStr))), TNum),
            """
            $RESULT
            fun unwrap(r: Result<Num, String>): Num = match r
              Ok { value = v } -> v
              Err -> 0
            unwrap
            """.trimIndent(),
        )
    }

    @Test
    fun genericFieldsSubstituteTheScrutineeTypeArgs() {
        assertInfersType(
            TFun(listOf(TRef("Result", listOf(TNum, TStr))), TNum),
            """
            $RESULT
            fun unwrap(r: Result<Num, String>): Num = match r
              Ok { value } -> value
              Err -> 0
            unwrap
            """.trimIndent(),
        )
    }

    @Test
    fun contravariantNarrowingKeepsTheDeclaredArg() {
        assertMismatch(
            "String",
            "Num",
            """
            type Sink<'A> = Fn { run: ('A) -> Num } | Nop
            fun f(s: Sink<Num>): Num = match s
              Fn g -> g.run("oops")
              Nop -> 0
            """.trimIndent(),
        )
    }

    @Test
    fun contravariantUpcastThenNarrowedCallChecks() {
        assertInfersType(
            TNum,
            """
            type Sink<'A> = Fn { run: ('A) -> Num } | Nop
            anySink: Sink<Any> = Fn(|x: Any -> 1|)
            numSink: Sink<Num> = anySink
            fun use(s: Sink<Num>): Num = match s
              Fn g -> g.run(5)
              Nop -> 0
            use(numSink)
            """.trimIndent(),
        )
    }

    @Test
    fun invariantNarrowingKeepsTheExactArg() {
        assertMismatch(
            "String",
            "Num",
            """
            type Cell<'A> = Rw { get: ('A) -> 'A } | Empty
            fun f(c: Cell<Num>): Num = match c
              Rw r -> r.get("oops")
              Empty -> 0
            """.trimIndent(),
        )
    }

    @Test
    fun narrowingSurvivesAnArityErroredScrutineeType() {
        // `s: Sink` is missing its type argument; the arity error is reported and the
        // match must recover rather than crash the checker.
        val errors =
            infer(
                """
                type Sink<'A> = Fn { run: ('A) -> Num } | Nop
                fun f(s: Sink): Num = match s
                  Fn g -> 0
                  Nop -> 0
                """.trimIndent(),
            ).errors
        assertTrue(errors.any { it is TypeError.TypeArityMismatch }, "expected arity error, got: $errors")
    }

    @Test
    fun variableArmBindsTheScrutineeAtItsType() {
        assertInfersType(
            TFun(listOf(TRef("Shape")), TRef("Shape")),
            """
            $SHAPES
            fun f(s: Shape): Shape = match s
              z -> z
            f
            """.trimIndent(),
        )
    }

    @Test
    fun scrutineeCanBeAnyExpressionAndDestructuringStillWorks() {
        assertInfersType(
            TFun(listOf(TRecord(mapOf("pet" to TRef("Animal")))), TStr),
            """
            $ANIMALS
            fun f(o: { pet: Animal }): String = match o.pet
              Dog { name } -> name
              _ -> "x"
            f
            """.trimIndent(),
        )
    }

    // ── Modes: check distributes, synth joins ──────────────────────────────────

    @Test
    fun synthModeJoinsSiblingConstructorsToTheParent() {
        assertInfersType(
            TFun(listOf(TRef("Shape")), TRef("Shape")),
            """
            $SHAPES
            fun flip(s: Shape) = match s
              Circle -> Square(1)
              _ -> Circle(1)
            flip
            """.trimIndent(),
        )
    }

    @Test
    fun synthModeRejectsIncompatibleArms() {
        val errors =
            infer(
                """
                $SHAPES
                fun f(s: Shape) = match s
                  Circle -> 1
                  _ -> "x"
                """.trimIndent(),
            ).errors
        assertIs<TypeError.CannotJoinMatchArms>(errors.single())
    }

    @Test
    fun threeArmFoldJoinsToTheParent() {
        assertInfersType(
            TRef("Shape"),
            """
            $SHAPES
            match 1
              1 -> Circle(1)
              2 -> Square(2)
              _ -> Tri(1, 2)
            """.trimIndent(),
        )
    }

    @Test
    fun recordArmsJoinOnCommonFields() {
        assertInfersType(
            TRecord(mapOf("name" to TStr)),
            """
            match 1
              1 -> { name = "a", age = 1 }
              _ -> { name = "b", tall = true }
            """.trimIndent(),
        )
    }

    @Test
    fun nullArmJoinsToOptional() {
        assertInfersType(
            TOptional(TNum),
            """
            match 1
              1 -> null
              _ -> 42
            """.trimIndent(),
        )
    }

    @Test
    fun polyArmInstantiatesAgainstTheMonoArm() {
        assertInfersType(
            TFun(listOf(TNum), TNum),
            """
            fun id(x: 'T): 'T = x
            fun g(n: Num): Num = n
            match true
              true -> id
              false -> g
            """.trimIndent(),
        )
    }

    @Test
    fun bothPolyArmsStayPolymorphic() {
        val r =
            infer(
                """
                fun id(a: 'T): 'T = a
                match true
                  true -> id
                  false -> id
                """.trimIndent(),
            )
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertTrue(r.type is TForall)
    }

    @Test
    fun armFoldStopsAtTheFirstFailedJoin() {
        val errors =
            infer(
                """
                match 1
                  1 -> 1
                  2 -> "x"
                  _ -> true
                """.trimIndent(),
            ).errors
        assertIs<TypeError.CannotJoinMatchArms>(errors.single())
    }

    @Test
    fun checkModeChecksEachArmAgainstTheExpectedType() {
        // Under a declared return the arms are checked, not joined: the bad arm mismatches on its own.
        assertMismatch(
            "String",
            "Num",
            """
            $SHAPES
            fun f(s: Shape): Num = match s
              Circle -> 1
              _ -> "x"
            """.trimIndent(),
        )
    }

    // ── Exhaustiveness ─────────────────────────────────────────────────────────

    @Test
    fun missingConstructorIsAHardError() {
        val errors =
            infer(
                """
                $SHAPES
                fun f(s: Shape): Num = match s
                  Circle -> 1
                  Square -> 2
                """.trimIndent(),
            ).errors
        val e = errors.single()
        assertIs<TypeError.NonExhaustiveMatch>(e)
        assertEquals(listOf("Tri"), e.missing)
    }

    @Test
    fun wildcardCoversTheRest() {
        assertInfersType(
            TFun(listOf(TRef("Shape")), TNum),
            """
            $SHAPES
            fun f(s: Shape): Num = match s
              Circle -> 1
              _ -> 0
            f
            """.trimIndent(),
        )
    }

    @Test
    fun boolIsExhaustedByTrueAndFalse() {
        assertInfersType(
            TFun(listOf(TBool), TNum),
            """
            fun f(b: Bool): Num = match b
              true -> 1
              false -> 0
            f
            """.trimIndent(),
        )
    }

    @Test
    fun boolWithOnlyTrueIsNonExhaustive() {
        val e =
            infer(
                """
                fun f(b: Bool): Num = match b
                  true -> 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NonExhaustiveMatch>(e)
        assertEquals(listOf("false"), e.missing)
    }

    @Test
    fun numLiteralsAlwaysNeedADefaultArm() {
        val e =
            infer(
                """
                fun f(n: Num): String = match n
                  0 -> "zero"
                  1 -> "one"
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NonExhaustiveMatch>(e)
    }

    @Test
    fun numLiteralsWithDefaultCheck() {
        assertInfersType(
            TFun(listOf(TNum), TStr),
            """
            fun f(n: Num): String = match n
              0 -> "zero"
              _ -> "other"
            f
            """.trimIndent(),
        )
    }

    // ── Guards ─────────────────────────────────────────────────────────────────

    @Test
    fun guardedArmsDoNotCountTowardCoverage() {
        val e =
            infer(
                """
                $SHAPES
                fun f(s: Shape): Num = match s
                  Circle if true -> 1
                  Square -> 2
                  Tri -> 3
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NonExhaustiveMatch>(e)
        assertEquals(listOf("Circle"), e.missing)
    }

    @Test
    fun guardedThenUnguardedArmOnTheSameConstructorIsFine() {
        assertInfersType(
            TFun(listOf(TRef("Shape")), TStr),
            """
            $SHAPES
            fun f(s: Shape): String = match s
              Circle { radius } if radius > 10 -> "big circle"
              Circle -> "circle"
              _ -> "not a circle"
            f
            """.trimIndent(),
        )
    }

    @Test
    fun guardMustBeBool() {
        assertMismatch(
            "Num",
            "Bool",
            """
            $SHAPES
            fun f(s: Shape): Num = match s
              Circle if 1 -> 1
              _ -> 0
            """.trimIndent(),
        )
    }

    @Test
    fun guardSeesPatternBindings() {
        assertInfersType(
            TFun(listOf(TRef("Animal")), TNum),
            """
            $ANIMALS
            fun f(a: Animal): Num = match a
              Dog d if d.legs > 3 -> d.legs
              Dog { legs } if legs > 1 -> legs
              _ -> 0
            f
            """.trimIndent(),
        )
    }

    // ── Reachability ───────────────────────────────────────────────────────────

    @Test
    fun armAfterWildcardIsUnreachable() {
        val e =
            infer(
                """
                $SHAPES
                fun f(s: Shape): Num = match s
                  _ -> 1
                  Circle -> 2
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnreachableMatchArm>(e)
    }

    @Test
    fun armAfterVariableArmIsUnreachable() {
        val e =
            infer(
                """
                $SHAPES
                fun f(s: Shape): Num = match s
                  z -> 1
                  Circle -> 2
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnreachableMatchArm>(e)
    }

    @Test
    fun defaultArmAfterAllConstructorsIsUnreachable() {
        val e =
            infer(
                """
                $SHAPES
                fun f(s: Shape): Num = match s
                  Circle -> 1
                  Square -> 2
                  Tri -> 3
                  _ -> 4
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnreachableMatchArm>(e)
    }

    @Test
    fun armAfterIrrefutableRecordPatternIsUnreachable() {
        val e =
            infer(
                """
                fun f(p: { name: String }): String = match p
                  { name } -> name
                  _ -> "x"
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnreachableMatchArm>(e)
    }

    @Test
    fun recordPatternAfterRecordPatternIsUnreachable() {
        val e =
            infer(
                """
                fun f(p: { name: String, age: Num }): String = match p
                  { name } -> name
                  { age } -> "unreached"
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnreachableMatchArm>(e)
    }

    @Test
    fun constructorArmAfterASumCoveringRecordPatternIsUnreachable() {
        val e =
            infer(
                """
                type Pet = Dog { name: String, legs: Num } | Cat { name: String, lives: Num }
                fun f(p: Pet): String = match p
                  { name } -> name
                  Dog -> "dog"
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnreachableMatchArm>(e)
    }

    @Test
    fun recordPatternCoversTheRemainingConstructors() {
        assertInfersType(
            TFun(listOf(TRef("Pet")), TStr),
            """
            type Pet = Dog { name: String, legs: Num } | Cat { name: String, lives: Num }
            fun f(p: Pet): String = match p
              Dog d -> d.name
              { name } -> name
            f
            """.trimIndent(),
        )
    }

    @Test
    fun widerRecordPatternAfterNarrowerIsStillUnreachable() {
        val e =
            infer(
                """
                fun f(p: { name: String, age: Num }): String = match p
                  { name } -> name
                  { name, age } -> "unreached"
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnreachableMatchArm>(e)
    }

    @Test
    fun sameConstructorWithDifferentFieldsIsStillUnreachable() {
        val e =
            infer(
                """
                $ANIMALS
                fun f(a: Animal): Num = match a
                  Dog { name } -> 1
                  Dog { legs } -> legs
                  _ -> 0
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnreachableMatchArm>(e)
    }

    @Test
    fun duplicateLiteralArmIsUnreachable() {
        val e =
            infer(
                """
                fun f(n: Num): String = match n
                  42 -> "answer"
                  42 -> "again"
                  _ -> "other"
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnreachableMatchArm>(e)
    }

    @Test
    fun duplicateConstructorArmIsUnreachable() {
        val e =
            infer(
                """
                $SHAPES
                fun f(s: Shape): Num = match s
                  Circle -> 1
                  Circle -> 2
                  Square -> 3
                  Tri -> 4
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnreachableMatchArm>(e)
    }

    // ── Pattern well-formedness ────────────────────────────────────────────────

    @Test
    fun constructorMustBelongToTheScrutineeType() {
        val e =
            infer(
                """
                $SHAPES
                $ANIMALS
                fun f(s: Shape): Num = match s
                  Dog -> 1
                  _ -> 0
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NotAConstructorOf>(e)
        assertEquals("Dog", e.constructorName)
    }

    @Test
    fun matchingOnAConstructorTypedScrutineeIsTriviallyExhaustive() {
        assertInfersType(
            TFun(listOf(TRef("Dog")), TStr),
            """
            $ANIMALS
            fun f(d: Dog): String = match d
              Dog -> d.name
            f
            """.trimIndent(),
        )
    }

    @Test
    fun siblingConstructorOnAConstructorTypedScrutineeIsRejected() {
        val e =
            infer(
                """
                $ANIMALS
                fun f(d: Dog): Num = match d
                  Cat -> 1
                  _ -> 0
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NotAConstructorOf>(e)
    }

    @Test
    fun literalPatternMustMatchTheScrutineeType() {
        assertMismatch(
            "String",
            "Num",
            """
            fun f(n: Num): Num = match n
              "x" -> 1
              _ -> 0
            """.trimIndent(),
        )
    }

    @Test
    fun constructorPatternFieldMustExist() {
        val e =
            infer(
                """
                $SHAPES
                fun f(s: Shape): Num = match s
                  Circle { side } -> side
                  _ -> 0
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
    }

    @Test
    fun recordScrutineeDestructures() {
        assertInfersType(
            TFun(listOf(TRecord(mapOf("name" to TStr, "age" to TNum))), TStr),
            """
            fun f(p: { name: String, age: Num }): String = match p
              { name } -> name
            f
            """.trimIndent(),
        )
    }

    @Test
    fun recordPatternFieldMustExistOnTheScrutinee() {
        val e =
            infer(
                """
                fun f(p: { name: String }): String = match p
                  { nickname } -> nickname
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
    }

    @Test
    fun wildcardFieldRequiresTheFieldButBindsNothing() {
        assertInfersType(
            TFun(listOf(TRecord(mapOf("name" to TStr, "age" to TNum))), TNum),
            """
            fun f(p: { name: String, age: Num }): Num = match p
              { name = _, age } -> age
            f
            """.trimIndent(),
        )
    }

    @Test
    fun wildcardFieldDoesNotShadowAnOuterBinding() {
        assertInfersType(
            TFun(listOf(TRecord(mapOf("name" to TNum))), TStr),
            """
            name = "outer"
            fun f(p: { name: Num }): String = match p
              { name = _ } -> name
            f
            """.trimIndent(),
        )
    }

    @Test
    fun wildcardFieldStillChecksTheFieldExists() {
        val e =
            infer(
                """
                fun f(p: { name: String }): Num = match p
                  { nickname = _ } -> 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
    }

    @Test
    fun recordPatternNamingMoreFieldsThanTheScrutineeIsRejected() {
        val e =
            infer(
                """
                fun f(p: { name: String }): String = match p
                  { name, age } -> name
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
        assertEquals("age", e.field)
    }

    @Test
    fun recordPatternOnANonRecordScrutineeIsRejected() {
        val e =
            infer(
                """
                fun f(n: Num): Num = match n
                  { name } -> 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NotARecord>(e)
    }

    @Test
    fun recordPatternFieldMustExistOnANominalScrutineesInterface() {
        // Snake has no fields, so Animal's common interface is empty — { name } names nothing on it.
        val e =
            infer(
                """
                $ANIMALS
                fun f(a: Animal): String = match a
                  { name } -> name
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
    }

    @Test
    fun recordPatternProjectsTheCommonInterfaceOfASum() {
        assertInfersType(
            TFun(listOf(TRef("Pet")), TStr),
            """
            type Pet = Dog { name: String, legs: Num } | Cat { name: String, lives: Num }
            fun f(p: Pet): String = match p
              { name } -> name
            f
            """.trimIndent(),
        )
    }

    // ── Unmatchable scrutinees ─────────────────────────────────────────────────

    @Test
    fun cannotMatchOnAFunction() {
        val e =
            infer(
                """
                fun f(g: (Num) -> Num): Num = match g
                  _ -> 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.CannotMatchOn>(e)
    }

    @Test
    fun cannotMatchOnAny() {
        val e =
            infer(
                """
                fun f(x: Any): Num = match x
                  _ -> 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.CannotMatchOn>(e)
    }

    @Test
    fun optionalSkolemMatchesOnNullAndDefault() {
        assertInfersType(
            TFun(listOf(TOptional(tv("T"))), TNum),
            """
            fun f(x: 'T?): Num = match x
              null -> 0
              y -> 1
            f
            """.trimIndent(),
        )
    }

    @Test
    fun optionalSkolemVariableArmBindsTheResidualSkolem() {
        assertInfersType(
            TFun(listOf(tv("T"), TOptional(tv("T"))), tv("T")),
            """
            fun orElse(d: 'T, x: 'T?): 'T = match x
              null -> d
              y -> y
            orElse
            """.trimIndent(),
        )
    }

    @Test
    fun optionalSkolemRejectsConstructorPatterns() {
        val e =
            infer(
                """
                $SHAPES
                fun f(x: 'T?): Num = match x
                  Circle -> 1
                  _ -> 0
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NotAConstructorOf>(e)
    }

    @Test
    fun optionalSkolemWithOnlyANullArmIsNonExhaustive() {
        val e =
            infer(
                """
                fun f(x: 'T?): Num = match x
                  null -> 0
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NonExhaustiveMatch>(e)
    }

    @Test
    fun cannotMatchOnATypeVariable() {
        val e =
            infer(
                """
                fun f(x: 'T): Num = match x
                  _ -> 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.CannotMatchOn>(e)
    }

    // ── Optionals ──────────────────────────────────────────────────────────────

    @Test
    fun optionalSumNeedsNullAndTheCore() {
        assertInfersType(
            TFun(listOf(TOptional(TRef("Shape"))), TNum),
            """
            $SHAPES
            fun f(s: Shape?): Num = match s
              null -> 0
              Circle { radius } -> radius
              _ -> 1
            f
            """.trimIndent(),
        )
    }

    @Test
    fun optionalWithoutNullArmIsNonExhaustive() {
        val e =
            infer(
                """
                $SHAPES
                fun f(s: Shape?): Num = match s
                  Circle -> 1
                  Square -> 2
                  Tri -> 3
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NonExhaustiveMatch>(e)
        assertEquals(listOf("null"), e.missing)
    }

    @Test
    fun defaultArmCoversNullWithoutANullArm() {
        assertInfersType(
            TFun(listOf(TOptional(TNum)), TNum),
            """
            fun f(n: Num?): Num = match n
              x -> 0
            f
            """.trimIndent(),
        )
    }

    @Test
    fun recordPatternDoesNotCoverNull() {
        val e =
            infer(
                """
                fun f(p: { name: String }?): String = match p
                  { name } -> name
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NonExhaustiveMatch>(e)
        assertEquals(listOf("null"), e.missing)
    }

    @Test
    fun missingNullAndConstructorsAreBothReported() {
        val e =
            infer(
                """
                $SHAPES
                fun f(s: Shape?): Num = match s
                  Circle -> 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NonExhaustiveMatch>(e)
        assertEquals(listOf("null", "Square", "Tri"), e.missing)
    }

    @Test
    fun afterNullIsCoveredAVariableArmBindsTheCore() {
        assertInfersType(
            TFun(listOf(TOptional(TNum)), TNum),
            """
            fun f(n: Num?): Num = match n
              null -> 0
              x -> x + 1
            f
            """.trimIndent(),
        )
    }

    @Test
    fun wildcardArmDoesNotNarrowTheScrutinee() {
        // No scrutinee narrowing: after the null arm, `n` is still Num? under `_`, so `n + 1` errors.
        // Name the residual with a variable pattern instead (see afterNullIsCoveredAVariableArmBindsTheCore).
        val e =
            infer(
                """
                fun f(n: Num?): Num = match n
                  null -> 0
                  _ -> n + 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NullNotAllowed>(e)
    }

    @Test
    fun guardedNullArmDoesNotCoverNull() {
        val e =
            infer(
                """
                fun f(n: Num?): Num = match n
                  null if true -> 0
                  x -> x + 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NullNotAllowed>(e)
    }

    @Test
    fun guardAfterNullArmSeesTheResidual() {
        assertInfersType(
            TFun(listOf(TOptional(TNum)), TNum),
            """
            fun f(n: Num?): Num = match n
              null -> 0
              x if x > 1 -> x
              y -> y + 1
            f
            """.trimIndent(),
        )
    }

    @Test
    fun checkModeViaAnnotatedBinding() {
        assertMismatch(
            "String",
            "Num",
            """
            $SHAPES
            s: Shape = Circle(1)
            x: Num = match s
              Circle -> 1
              _ -> "x"
            """.trimIndent(),
        )
    }

    @Test
    fun checkModeViaArgumentPosition() {
        assertMismatch(
            "String",
            "Num",
            """
            $SHAPES
            fun g(n: Num): Num = n
            fun f(s: Shape): Num = g(match s
              Circle -> 1
              _ -> "x")
            """.trimIndent(),
        )
    }

    @Test
    fun erroredScrutineeDoesNotCascadeIntoArms() {
        val e =
            infer(
                """
                match mystery
                  _ -> 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnboundVariable>(e)
    }

    @Test
    fun cannotMatchOnUnit() {
        val e =
            infer(
                """
                fun f(u: Unit): Num = match u
                  _ -> 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.CannotMatchOn>(e)
    }

    @Test
    fun cannotMatchOnAPolymorphicValue() {
        val e =
            infer(
                """
                fun id(x: 'T): 'T = x
                match id
                  _ -> 1
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.CannotMatchOn>(e)
    }

    @Test
    fun matchOnNullLiteralIsExhaustiveWithANullArm() {
        assertInfersType(
            TNum,
            """
            match null
              null -> 1
            """.trimIndent(),
        )
    }

    @Test
    fun literalPatternsOnAnOptionalScrutineeTestTheCore() {
        assertInfersType(
            TFun(listOf(TOptional(TNum)), TStr),
            """
            fun f(n: Num?): String = match n
              null -> "none"
              0 -> "zero"
              _ -> "other"
            f
            """.trimIndent(),
        )
    }

    @Test
    fun literalPatternAloneLeavesNullUncovered() {
        val e =
            infer(
                """
                fun f(n: Num?): Num = match n
                  0 -> 0
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NonExhaustiveMatch>(e)
        assertEquals(listOf("null"), e.missing)
    }

    @Test
    fun optionalSkolemRejectsLiteralPatterns() {
        val e =
            infer(
                """
                fun f(x: 'T?): Num = match x
                  5 -> 1
                  _ -> 0
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.TypeMismatch>(e)
    }

    @Test
    fun optionalSkolemRejectsRecordPatterns() {
        val e =
            infer(
                """
                fun f(x: 'T?): Num = match x
                  { name } -> 1
                  _ -> 0
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.NotARecord>(e)
    }

    @Test
    fun stringLiteralsMatchWithADefault() {
        assertInfersType(
            TFun(listOf(TStr), TNum),
            """
            fun f(s: String): Num = match s
              "a" -> 1
              "b" -> 2
              _ -> 0
            f
            """.trimIndent(),
        )
    }

    @Test
    fun nestedMatchInArmBodyChecks() {
        assertInfersType(
            TFun(listOf(TRef("Animal"), TRef("Shape")), TNum),
            """
            $ANIMALS
            $SHAPES
            fun f(a: Animal, s: Shape): Num = match a
              Dog d -> match s
                Circle { radius } -> radius + d.legs
                _ -> d.legs
              _ -> 0
            f
            """.trimIndent(),
        )
    }

    @Test
    fun wildcardArmDoesNotBindAVariableNamedUnderscore() {
        val e =
            infer(
                """
                fun f(n: Num): Num = match n
                  _ -> _
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.UnboundVariable>(e)
    }

    @Test
    fun optionalSumConstructorBinderIsTypedAsTheConstructor() {
        assertInfersType(
            TFun(listOf(TOptional(TRef("Animal"))), TStr),
            """
            $ANIMALS
            fun f(a: Animal?): String = match a
              null -> "none"
              Dog d -> d.name
              _ -> "other"
            f
            """.trimIndent(),
        )
    }
}
