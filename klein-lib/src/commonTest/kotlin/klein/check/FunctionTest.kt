package klein.check

import klein.check.Type.*
import klein.types.TypeError
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Top-level `fun` definitions: signature binding, body checking, calls, recursion. */
class FunctionTest {
    @Test
    fun inferredReturn() =
        assertEquals(
            TNum,
            infer(
                """
                fun double(x: Num) = x * 2
                double(5)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun declaredReturn() =
        assertEquals(
            TNum,
            infer(
                """
                fun add(x: Num, y: Num): Num = x + y
                add(1, 2)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun declaredReturnWiderThanBody_bindsDeclaredNotInferred() =
        // Body infers Num, but the signature declares the wider Any. The declared return must be
        // honored (bound as the function's result), not silently replaced by the inferred body type.
        assertEquals(
            TFun(listOf(TNum), TTop, listOf("x")),
            infer(
                """
                fun f(x: Num): Any = x
                f
                """.trimIndent(),
            ).type,
        )

    @Test
    fun functionAsValue() =
        assertEquals(
            TFun(listOf(TNum), TNum, listOf("x")),
            infer(
                """
                fun double(x: Num) = x * 2
                double
                """.trimIndent(),
            ).type,
        )

    @Test
    fun selfRecursionWithDeclaredReturn() =
        assertEquals(
            TNum,
            infer(
                """
                fun fib(n: Num): Num = if n < 2 then n else fib(n - 1) + fib(n - 2)
                fib(10)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun mutualRecursion_twoWay() =
        // isEven and isOdd form one recursive component: both signatures bind before either body
        // is checked, so the forward call to isOdd resolves. Each must declare its return type.
        assertEquals(
            TFun(listOf(TNum), TBool, listOf("n")),
            infer(
                """
                fun isEven(n: Num): Bool = if n == 0 then true else isOdd(n - 1)
                fun isOdd(n: Num): Bool = if n == 0 then false else isEven(n - 1)
                isEven
                """.trimIndent(),
            ).type,
        )

    @Test
    fun mutualRecursion_threeWay() =
        assertEquals(
            TFun(listOf(TNum), TNum, listOf("x")),
            infer(
                """
                fun a(x: Num): Num = if x == 0 then 0 else b(x - 1)
                fun b(x: Num): Num = if x == 0 then 1 else c(x - 1)
                fun c(x: Num): Num = if x == 0 then 2 else a(x - 1)
                a
                """.trimIndent(),
            ).type,
        )

    // --- error cases ---

    @Test
    fun bareParamErrors() = assertTrue(infer("fun f(x) = x").errors.isNotEmpty())

    @Test
    fun returnMismatchErrors() = assertTrue(infer("fun f(x: Num): Bool = x").errors.isNotEmpty())

    @Test
    fun recursionWithoutDeclaredReturnErrors() =
        assertTrue(infer("fun loop(n: Num) = loop(n)").errors.isNotEmpty())

    @Test
    fun lambdaBoundWithEqualsCannotSelfRefer() {
        // Unlike `fun f(x) = ... f ...`, a `=`-bound lambda is not in scope in its own body, so a
        // reference to its own name is unbound.
        val errors =
            infer(
                """
                f = |x: Num -> f(x)|
                f
                """.trimIndent(),
            ).errors
        assertEquals(1, errors.size)
        val e = errors[0]
        assertIs<TypeError.UnboundVariable>(e)
        assertEquals("f", e.name)
    }

    @Test
    fun duplicateParam_funDef_reported() {
        val e =
            infer(
                """
                fun f(x: Num, x: Num) = x
                f
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.DuplicateParameter>(e)
        assertEquals("x", e.name)
    }

    @Test
    fun duplicateParam_lambda_reported() {
        val e = infer("|x: Num, x: Num -> x|").errors.single()
        assertIs<TypeError.DuplicateParameter>(e)
        assertEquals("x", e.name)
    }

    @Test
    fun tripleDuplicateParam_reportsEachRepeat() {
        val errors = infer("|x: Num, x: Num, x: Num -> x|").errors
        assertEquals(2, errors.size)
        errors.forEach {
            assertIs<TypeError.DuplicateParameter>(it)
            assertEquals("x", it.name)
        }
    }

    // --- lambda in check position (checkLambda) ---

    @Test
    fun lambdaCheckedAgainstAny_passes() =
        // A lambda is a value, so it satisfies `Any` — must not error "found a function".
        assertTrue(
            infer(
                """
                f: Any = |n: Num -> n|
                f
                """.trimIndent(),
            ).errors.isEmpty(),
        )

    @Test
    fun lambdaCheckedAgainstNonFunction_reportsTypeMismatchNotMisc() {
        // Non-function expected → subsumption fallback → a real TypeMismatch, never a Misc.
        assertIs<TypeError.TypeMismatch>(
            infer(
                """
                f: Num = |n: Num -> n|
                f
                """.trimIndent(),
            ).errors.single(),
        )
    }

    @Test
    fun lambdaArityMismatch_reportsCallArityMismatchNotMisc() {
        val errors =
            infer(
                """
                f: (Num) -> Num = |a, b -> a|
                f
                """.trimIndent(),
            ).errors
        assertEquals(1, errors.size)
        val e = errors[0]
        assertIs<TypeError.CallArityMismatch>(e)
        assertEquals(1, e.expected)
        assertEquals(2, e.actual)
    }

    // --- parameter and return annotations ---

    private val animal = "type Animal = Dog { name: String, tricks: Num } | Cat { name: String }"

    @Test
    fun paramAnnotation_identityIsMonomorphic() =
        assertInfersType(
            TFun(listOf(TNum), TNum),
            """
            fun f(x: Num) = x
            f
            """.trimIndent(),
        )

    @Test
    fun lambdaParamAnnotation_identityIsMonomorphic() = assertInfersType(TFun(listOf(TNum), TNum), "|x: Num -> x|")

    @Test
    fun paramAnnotation_acceptsSubtype() =
        assertInfersType(
            TRef("Animal", emptyList()),
            """
            $animal
            fun wrap(x: Animal): Animal = x
            wrap(Dog("Rex", 3))
            """.trimIndent(),
        )

    @Test
    fun paramAnnotation_hidesSubtypeFields() {
        val e =
            infer(
                """
                $animal
                fun f(x: Animal) = x.tricks
                f
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
        assertEquals("tricks", e.field)
    }

    @Test
    fun returnAnnotation_hidesSubtypeFromCaller() {
        val e =
            infer(
                """
                $animal
                fun wrap(x: Animal): Animal = x
                wrap(Dog("Rex", 3)).tricks
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.MissingField>(e)
        assertEquals("tricks", e.field)
    }

    @Test
    fun paramAnnotation_mismatch() =
        assertMismatch(
            "String",
            "Num",
            """
            fun f(x: Num) = x
            f("hello")
            """.trimIndent(),
        )

    @Test
    fun returnAnnotation_mismatch() =
        assertMismatch(
            "Num",
            "String",
            """
            fun f(x: Num): String = x + 1
            f
            """.trimIndent(),
        )

    @Test
    fun functionTypeAnnotation_mismatch() = assertMismatch("Num", "String", "f: Num -> String = |x -> x + 1|")

    @Test
    fun anyParam_acceptsAnything() =
        assertInfersType(
            TFun(listOf(TTop), TNum),
            """
            fun f(x: Any): Num = 42
            f
            """.trimIndent(),
        )

    @Test
    fun nothingParam_isSubtypeOfAny() =
        assertInfersType(
            TFun(listOf(TBottom), TNum),
            """
            fun f(x: Nothing): Num = 42
            f
            """.trimIndent(),
        )

    @Test
    fun nestedLambda_constrainingReferencedTypeVarFails() {
        val e =
            infer(
                """
                fun outer(x: 'A) =
                  inner = |y: 'A -> y + 1|
                  inner(x)
                outer
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.TypeMismatch>(e)
    }

    @Test
    @Ignore // nested fun defs aren't implemented yet
    fun nestedFunction_innerIntroducesOwnTypeVars() =
        assertInfersType(
            TFun(listOf(TNum), TFun(listOf(tv("A")), tv("A"))),
            """
            fun outer(x: Num) =
              fun inner(y: 'A): 'A = y
              inner
            outer
            """.trimIndent(),
        )

    // --- application ---

    @Test
    fun call_nested() =
        assertEquals(
            TNum,
            infer(
                """
                fun f(x: Num): Num = x
                f(f(1))
                """.trimIndent(),
            ).type,
        )

    @Test
    fun call_zeroArg() =
        assertEquals(
            TNum,
            infer(
                """
                fun f(): Num = 42
                f()
                """.trimIndent(),
            ).type,
        )

    @Test
    fun directLambdaApplication() = assertEquals(TNum, infer("|x: Num -> x|(1)").type)

    @Test
    fun call_tooManyArgs() {
        val e =
            infer(
                """
                fun f(x: Num): Num = x
                f(1, 2)
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.CallArityMismatch>(e)
        assertEquals(1, e.expected)
        assertEquals(2, e.actual)
    }

    @Test
    fun call_tooFewArgs() {
        val e =
            infer(
                """
                fun f(x: Num, y: Num): Num = x
                f(1)
                """.trimIndent(),
            ).errors.single()
        assertIs<TypeError.CallArityMismatch>(e)
        assertEquals(2, e.expected)
        assertEquals(1, e.actual)
    }

    @Test
    fun bareLambdaArg_checkedAgainstConcreteParam() =
        // A bare lambda takes its parameter types from the concrete parameter it's checked against.
        assertEquals(
            TNum,
            infer(
                """
                fun apply(f: (Num) -> Num): Num = f(3)
                apply(|x -> x + 1|)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun bareLambdaArg_checkedAgainstGroundParamOfPolymorphicFunction() =
        // `f`'s parameter is ground even though the call is polymorphic in `'T`, so it's still checked.
        assertEquals(
            TNum,
            infer(
                """
                fun withCb(f: (Num) -> Num, x: 'T): 'T = x
                withCb(|n -> n + 1|, 5)
                """.trimIndent(),
            ).type,
        )

    @Test
    fun applyingBottomYieldsBottomWithoutError() {
        val env = TypeEnv.empty()
        env.bind("nope", TBottom)
        val result = infer("nope(1, 2)", env)
        assertEquals(TBottom, result.type)
        assertTrue(result.errors.isEmpty(), "applying Nothing should not error: ${result.errors}")
    }

    @Test
    fun unboundCallee_reportsOnlyUnbound() {
        // An unbound callee yields just the UnboundVariable — no spurious "not a function" on top.
        val e = infer("foo(3)").errors.single()
        assertIs<TypeError.UnboundVariable>(e)
        assertEquals("foo", e.name)
    }
}
