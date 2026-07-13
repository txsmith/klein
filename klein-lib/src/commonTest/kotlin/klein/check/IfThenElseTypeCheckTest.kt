package klein.check

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * If-then-else mechanics that don't depend on joining dissimilar branches: the condition must be
 * `Bool`, matching branches pass their type through, and the condition may come from a variable,
 * comparison, or field access. Branch-type joins live in `LubGlbTypeCheckTest`; null branches and
 * the no-else optional live in `OptionalTypeInferTest`.
 */
class IfThenElseTypeCheckTest {
    private fun assertType(
        expected: String,
        src: String,
    ) {
        val r = infer(src)
        assertTrue(r.errors.isEmpty(), "unexpected errors: ${r.errors}")
        assertEquals(expected, klein.Type.print(r.type.toLegacy()))
    }

    // --- the condition must be Bool ---

    @Test
    fun condition_mustBeBool() = assertMismatch("Num", "Bool", "if 1 then 2 else 3")

    @Test
    fun condition_mustBeBool_noElse() = assertMismatch("Num", "Bool", "if 1 then 2")

    // --- matching branches pass their type through ---

    @Test
    fun sameBranchTypes() = assertType("Num", "if true then 1 else 2")

    @Test
    fun nestedBranches() = assertType("Num", "if true then if false then 1 else 2 else 3")

    @Test
    fun stringBranches() = assertType("String", "if 1 < 2 then \"yes\" else \"no\"")

    // --- condition provenance ---

    @Test
    fun conditionFromVariable() =
        assertType(
            "Num",
            """
            x = true
            if x then 1 else 2
            """.trimIndent(),
        )

    @Test
    fun conditionFromComparison() = assertType("Num", "if 1 < 2 then 10 else 20")

    @Test
    fun conditionFromFieldAccess() =
        assertType("({ y: Bool }) -> Num", "|r: { y: Bool } -> if r.y then 1 else 2|")

    @Test
    fun inFunction() = assertType("(Bool) -> Num", "|x: Bool -> if x then 1 else 2|")
}
