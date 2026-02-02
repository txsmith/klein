package klein.types

import klein.Type
import kotlin.test.Test

class SiblingMixTest {
    @Test
    fun siblings_with_other_type_should_simplify() {
        // If a function can return Some, None, OR a Num,
        // we'd want: Option<...> | Num, not Some<...> | None | Num
        val result = infer(
            """
            type Option<'A> = Some { value: 'A } | None
            f = |x -> if x then Some(1) else if x then None else 42|
            f
            """.trimIndent(),
        )
        println("siblings mixed with prim: ${Type.print(result)}")
    }

    @Test
    fun siblings_alone_should_simplify() {
        // Control: siblings alone should collapse to parent
        val result = infer(
            """
            type Option<'A> = Some { value: 'A } | None
            f = |x -> if x then Some(1) else None|
            f
            """.trimIndent(),
        )
        println("siblings alone: ${Type.print(result)}")
    }
}
