package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleSubTest {
    @Test
    fun basic_intLiteral() {
        assertType("Num", infer("42"))
    }

    @Test
    fun basic_constFunction() {
        assertType("(Any) -> Num", infer("|x -> 42|"))
    }

    @Test
    fun basic_identity() {
        assertType("('A) -> 'A", infer("|x -> x|"))
    }

    @Test
    fun basic_applyToInt() {
        assertType("((Num) -> 'A) -> 'A", infer("|x -> x(42)|"))
    }

    @Test
    fun basic_identityAppliedToInt() {
        assertType("Num", infer("|x -> x|(42)"))
    }

    @Test
    fun basic_twice() {
        assertType("(('A | 'B) -> 'A) -> ('B) -> 'A", infer("|f -> |x -> f(f(x))||"))
    }

    @Test
    fun basic_twiceWithLet() {
        assertType(
            "(('A | 'B) -> 'A) -> ('B) -> 'A",
            infer(
                """
                twice = |f -> |x -> f(f(x))||
                twice
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun booleans_trueLiteral() {
        assertType("Bool", infer("true"))
    }

    @Test
    fun booleans_notTrue() {
        assertType("Bool", infer("not true"))
    }

    @Test
    fun booleans_notFunction() {
        assertType("(Bool) -> Bool", infer("|x -> not x|"))
    }

    @Test
    fun booleans_notApplied() {
        assertType("Bool", infer("|x -> not x|(true)"))
    }

    @Test
    fun booleans_ifThenElse() {
        assertType("(Bool) -> ('A) -> ('A) -> 'A", infer("|x -> |y -> |z -> if x then y else z|||"))
    }

    @Test
    fun booleans_ifWithSameVarInElse() {
        assertType("('A & Bool) -> ('A) -> 'A", infer("|x -> |y -> if x then y else x||"))
    }

    @Test
    fun booleans_succTrue_error() {
        val errors = inferErrors("true + 1")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun booleans_succNotX_error() {
        val errors = inferErrors("|x -> (not x) + 1|")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun booleans_notRecordField_error() {
        val errors = inferErrors("|x -> not x.f|({ f = 123 })")
        assertEquals(2, errors.size)
        assertTrue(errors.all { it is TypeError.TypeMismatch })
    }

    @Test
    fun records_fieldAccess() {
        assertType("({ f: 'A }) -> 'A", infer("|x -> x.f|"))
    }

    @Test
    fun records_emptyRecord() {
        assertType("{}", infer("{}"))
    }

    @Test
    fun records_singleField() {
        assertType("{ f: Num }", infer("{ f = 42 }"))
    }

    @Test
    fun records_accessField() {
        assertType("Num", infer("{ f = 42 }.f"))
    }

    @Test
    fun records_passToFunction() {
        assertType("Num", infer("|x -> x.f|({ f = 42 })"))
    }

    @Test
    fun records_functionInField() {
        assertType("((Num) -> 'A) -> 'A", infer("|f -> { x = f(42) }.x|"))
    }

    @Test
    fun records_unusedFieldResult() {
        assertType("((Num) -> Any) -> Num", infer("|f -> { x = f(42), y = 123 }.y|"))
    }

    @Test
    fun records_ifThenElseRecords() {
        assertType("{ b: Bool }", infer("if true then { a = 1, b = true } else { b = false, c = 42 }"))
    }

    @Test
    fun records_missingField_error() {
        val errors = inferErrors("{ a = 123, b = true }.c")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }

    @Test
    fun records_missingFieldInFunction_error() {
        val errors = inferErrors("|x -> { a = x }.b|")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.MissingField)
    }

    @Test
    fun selfApp_basic() {
        assertType("('A & (('A) -> 'B)) -> 'B", infer("|x -> x(x)|"))
    }

    @Test
    fun selfApp_triple() {
        assertType("('A & (('A) -> ('A) -> 'B)) -> 'B", infer("|x -> x(x)(x)|"))
    }

    @Test
    fun selfApp_twoParams() {
        assertType("('A & (('B) -> ('A) -> 'C)) -> ('B) -> 'C", infer("|x -> |y -> x(y)(x)||"))
    }

    @Test
    fun selfApp_twoParamsReversed() {
        assertType("('A & (('A) -> ('B) -> 'C)) -> ('B) -> 'C", infer("|x -> |y -> x(x)(y)||"))
    }

    @Test
    fun selfApp_omega() {
        assertType("Nothing", infer("|x -> x(x)|(|x -> x(x)|)"))
    }

    @Test
    fun selfApp_recordWithSelfApp() {
        assertType("('A & (('A) -> 'B)) -> { l: 'B, r: 'A }", infer("|x -> { l = x(x), r = x }|"))
    }

    @Test
    fun selfApp_yCombinator() {
        assertType("(('A) -> 'A) -> 'A", infer("|f -> |x -> f(x(x))|(|x -> f(x(x))|)|"))
    }

    @Test
    fun selfApp_zCombinator() {
        assertType(
            "((('A) -> 'B) -> 'C & (('A) -> 'B)) -> 'C",
            infer(
                "|f -> |x -> f(|v -> x(x)(v)|)|(|x -> f(|v -> x(x)(v)|)|)|",
            ),
        )
    }

    @Test
    fun selfApp_ifWithSelfApp() {
        assertType("('A & (('A) -> (Bool) -> Bool)) -> Bool", infer("|i -> if i(i)(true) then true else true|"))
    }

    @Test
    fun letPoly_applyIdentityToBothTypes() {
        assertType(
            "{ a: Num, b: Bool }",
            infer(
                """
                f = |x -> x|
                { a = f(0), b = f(true) }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_withParam() {
        assertType(
            "('A) -> { a: 'A, b: Bool }",
            infer(
                """
                |y ->
                  f = |x -> x|
                  { a = f(y), b = f(true) }
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_functionUsingParam() {
        assertType(
            "((Bool | Num) -> 'A) -> { a: 'A, b: 'A }",
            infer(
                """
                |y ->
                  f = |x -> y(x)|
                  { a = f(0), b = f(true) }
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_applyParamToIdentities() {
        assertType(
            "('A) -> { a: 'A, b: Bool }",
            infer(
                """
                |y ->
                  f = |x -> x(y)|
                  { a = f(|z -> z|), b = f(|z -> true|) }
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_applyParamToIdentityAndSucc() {
        assertType(
            "('A & Num) -> { a: 'A, b: Num }",
            infer(
                """
                |y ->
                  f = |x -> x(y)|
                  { a = f(|z -> z|), b = f(|z -> z + 1|) }
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_constrainedWithIf() {
        assertType(
            "(((Num) -> Num) -> 'A) -> 'A",
            infer(
                """
                |k ->
                  test = k(|x ->
                    tmp = x + 1
                    if true then x else 2
                  |)
                  test
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_functionWithTwoHOFParams() {
        assertType(
            "(('A) -> 'B) -> ('A) -> (('A) -> 'C) -> { a: 'B, b: 'C }",
            infer(
                """
                |f ->
                  r = |x -> |g -> { a = f(x), b = g(x) }||
                  r
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_recordWithTwoFunctions() {
        assertType(
            "(Any) -> { u: { a: Num }, v: { a: Bool } }",
            infer(
                """
                |f ->
                  r = |x -> |g -> { a = g(x) }||
                  { u = r(0)(|n -> n + 1|), v = r(true)(|b -> not b|) }
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_twoFunctionsWithSharedParam() {
        assertType(
            "((Bool | Num) -> 'A) -> { u: { a: Num, b: 'A }, v: { a: Bool, b: 'A } }",
            infer(
                """
                |f ->
                  r = |x -> |g -> { a = g(x), b = f(x) }||
                  { u = r(0)(|n -> n + 1|), v = r(true)(|b -> not b|) }
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_withRecordParam() {
        assertType(
            "((Num | { t: Bool }) -> 'A) -> { u: { a: Num, b: 'A }, v: { a: Bool, b: 'A } }",
            infer(
                """
                |f ->
                  r = |x -> |g -> { a = g(x), b = f(x) }||
                  { u = r(0)(|n -> n + 1|), v = r({ t = true })(|y -> y.t|) }
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun booleans_notFunctionAppliedToNonFunction_error() {
        val errors = inferErrors("|f -> |x -> not f(x.u)||(false)")
        assertEquals(1, errors.size)
        assertTrue(errors[0] is TypeError.TypeMismatch)
    }

    @Test
    fun letPoly_constrainedIdentity() {
        assertType(
            "((('A & Num) -> 'A) -> 'B) -> 'B",
            infer(
                """
                |k ->
                  test = k(|x ->
                    tmp = x + 1
                    x
                  |)
                  test
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_extrusionLossOfPolymorphism() {
        assertType(
            "((('A) -> 'A | Bool | Num) -> Any) -> { u: 'A | Num, v: 'A | Bool }",
            infer(
                """
                |k ->
                  test = |id -> { tmp = k(id), res = id }.res|(|x -> x|)
                  { u = test(0), v = test(true) }
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_noExtrusionWithSeparateIdentity() {
        assertType(
            "((('A) -> 'A) -> Any) -> { u: Num, v: Bool }",
            infer(
                """
                |k ->
                  test = { tmp = k(|x -> x|), res = |x -> x| }.res
                  { u = test(0), v = test(true) }
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_extrusionWithThefun() {
        assertType(
            "((('A & Num) -> 'A | Num) -> 'B) -> { l: 'B, r: Num }",
            infer(
                """
                |k ->
                  test = |thefun -> { l = k(thefun), r = thefun(1) }|(|x ->
                    tmp = x + 1
                    x
                  |)
                  test
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_constrainedIdentityWithOuterParam() {
        assertType(
            "('A & Num) -> 'A",
            infer(
                """
                |a ->
                  |k ->
                    test = k(|x ->
                      tmp = x + 1
                      x
                    |)
                    test
                  |(|f -> f(a)|)
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_nestedLetWithAdd() {
        assertType(
            "((('A & Num) -> 'A) -> 'B) -> 'B",
            infer(
                """
                |k ->
                  test = k(|x ->
                    tmp = |y -> y + 1|(x)
                    x
                  |)
                  test
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun letPoly_deeplyNestedLetWithAdd() {
        assertType(
            "((('A & Num) -> 'A) -> 'B) -> 'B",
            infer(
                """
                |k ->
                  test = k(|x ->
                    tmp =
                      f = |y -> y + 1|
                      f(x)
                    x
                  |)
                  test
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun random_selfAppWithLetResult() {
        assertType(
            "('A & (('A) -> Any)) -> Num",
            infer(
                """
                |x ->
                  y = x(x)
                  0
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun random_nestedSelfApp() {
        assertType(
            "(('A) -> 'B) -> ('C & (('C) -> 'A)) -> 'B",
            infer("|x -> |y -> x(y(y))||"),
        )
    }

    @Test
    fun random_recordFieldSelfApp() {
        assertType(
            "({ v: 'A } & (('A) -> Any)) -> Num",
            infer(
                """
                |x ->
                  y = x(x.v)
                  0
                |
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mlsub_pickAnObject() {
        // From mlsub website: "bool -> {x: int, y: bool ∨ ('a -> 'a)}"
        assertType(
            "(Bool) -> { x: Num, y: Bool | (('A) -> 'A) }",
            infer(
                """
                object1 = { x = 42, y = |x -> x| }
                object2 = { x = 17, y = false }
                |b -> if b then object1 else object2|
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun mlsub_topLevelPolymorphism() {
        // From ProgramTests: top-level-polymorphism
        assertType(
            "{ u: Num, v: Bool }",
            infer(
                """
                id = |x -> x|
                { u = id(0), v = id(true) }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun misc_join() {
        assertType(
            "('A) -> ('A) -> 'A",
            infer("|a -> |b -> if true then a else b||"),
        )
    }

    @Test
    fun misc_twoCrown() {
        assertType(
            "('A) -> ('A) -> { l: 'A, r: 'A }",
            infer("|x -> |y -> if true then { l = x, r = y } else { l = y, r = x }||"),
        )
    }

    @Test
    fun mlsub_recordWithPolymorphicField() {
        assertType(
            "{ x: Num, y: ('A) -> 'A }",
            infer("{ x = 42, y = |x -> x| }"),
        )
    }

    @Test
    fun mlsub_recordWithBoolField() {
        assertType(
            "{ x: Num, y: Bool }",
            infer("{ x = 17, y = false }"),
        )
    }

    @Test
    fun mlsub_fullProgram() {
        // From mlsub website - full program with multiple bindings
        assertType(
            "(Bool) -> { x: Num, y: Bool | (('A) -> 'A) }",
            infer(
                """
                id = |x -> x|
                twice = |f -> |x -> f(f(x))||
                object1 = { x = 42, y = id }
                object2 = { x = 17, y = false }
                |b -> if b then object1 else object2|
                """.trimIndent(),
            ),
        )
    }
}
