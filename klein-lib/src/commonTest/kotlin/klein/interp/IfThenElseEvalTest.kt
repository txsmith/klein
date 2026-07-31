package klein.interp

import klein.interp.Value.VNull
import klein.interp.Value.VNum
import kotlin.test.Test

class IfThenElseEvalTest {
    @Test
    fun selectsByCondition() {
        assertEvaluatesTo(VNum(1.0), "if 2 > 1 then 1 else 2")
        assertEvaluatesTo(VNum(2.0), "if 2 < 1 then 1 else 2")
    }

    @Test
    fun missingElseYieldsNull() = assertEvaluatesTo(VNull, "if false then 1")

    // `if` desugars to a `match`, whose arm bodies run one scope deeper. A branch that reads an
    // enclosing binding crashes if the lowerer emits it at the match-site depth.
    @Test
    fun thenBranchReadsOuterVar() =
        assertEvaluatesTo(
            VNum(1.0),
            """
            fun f(x: Num) = if x < 2 then x else 99
            f(1)
            """,
        )

    @Test
    fun elseBranchReadsOuterVar() =
        assertEvaluatesTo(
            VNum(5.0),
            """
            fun f(x: Num) = if x < 2 then 99 else x
            f(5)
            """,
        )

    @Test
    fun untakenBranchNeverEvaluates() {
        assertEvaluatesTo(VNum(1.0), "if true then 1 else 1 / 0")
        assertEvaluatesTo(VNum(2.0), "if false then 1 / 0 else 2")
    }

    @Test
    fun missingElseTrueSide() = assertEvaluatesTo(VNum(42.0), "if 2 > 1 then 42")

    @Test
    fun missingElseSkipsPoisonedBranch() = assertEvaluatesTo(VNull, "if false then 1 / 0")

    @Test
    fun nestedIfSelection() =
        assertEvaluatesTo(VNum(2.0), "if true then if false then 1 else 2 else 3")
}
