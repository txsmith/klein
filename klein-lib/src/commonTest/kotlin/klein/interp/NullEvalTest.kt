package klein.interp

import klein.interp.Value.VBool
import klein.interp.Value.VNull
import klein.interp.Value.VNum
import klein.interp.Value.VStr
import kotlin.test.Test

class NullEvalTest {
    @Test
    fun plainValueWhereOptionalExpected() =
        assertEvaluatesTo(
            VNum(5.0),
            """
            fun orZero(x: Num?): Num = match x
              null -> 0
              y -> y
            orZero(5)
            """,
        )

    @Test
    fun bareValueThroughOptionalFunctionResult() =
        assertEvaluatesTo(
            VNum(2.0),
            """
            fun use(f: (Num) -> Num?): Num? = f(1)
            use(|x: Num -> x + 1|)
            """,
        )

    @Test
    fun nullTolerantFunctionAsNonNullCallback() =
        assertEvaluatesTo(
            VNum(42.0),
            """
            fun apply1(f: (Num) -> Num): Num = f(41)
            fun orZeroPlus(x: Num?): Num = match x
              null -> 0
              y -> y + 1
            apply1(orZeroPlus)
            """,
        )

    @Test
    fun plainFieldWhereOptionalFieldExpected() =
        assertEvaluatesTo(
            VNum(5.0),
            """
            fun getX(r: { x: Num? }): Num = match r.x
              null -> 0
              y -> y
            getX({ x = 5 })
            """,
        )

    @Test
    fun functionReturningOptional() {
        assertEvaluatesTo(VNull, "fun pick(b: Bool): Num? = if b then 42 else null\npick(false)")
        assertEvaluatesTo(VNum(42.0), "fun pick(b: Bool): Num? = if b then 42 else null\npick(true)")
    }

    @Test
    fun nullThroughGenericParameter() =
        assertEvaluatesTo(VNull, "fun identity(x: 'A) = x\nidentity(null)")

    @Test
    fun plainFieldAccessYieldsNull() =
        assertEvaluatesTo(
            VNull,
            """
            r = { x = if false then 42 else null }
            r.x
            """,
        )

    @Test
    fun safeFieldAccessOnNull() =
        assertEvaluatesTo(
            VNull,
            """
            fun pick(r: { x: Num }?): Num? = r?.x
            pick(null)
            """,
        )

    @Test
    fun safeFieldAccessOnPresent() =
        assertEvaluatesTo(
            VNum(1.0),
            """
            fun pick(r: { x: Num }?): Num? = r?.x
            pick({ x = 1 })
            """,
        )

    @Test
    fun redundantSafeAccessOnNonOptional() =
        assertEvaluatesTo(
            VStr("Alice"),
            """
            r = { name = "Alice" }
            r?.name
            """,
        )

    @Test
    fun safeChainAllPresent() =
        assertEvaluatesTo(
            VStr("NYC"),
            """
            r = if true then { address = { city = "NYC" } } else null
            r?.address?.city
            """,
        )

    @Test
    fun safeChainShortCircuitsAtHead() =
        assertEvaluatesTo(
            VNull,
            """
            r: { address: { city: String } }? = null
            r?.address?.city
            """,
        )

    @Test
    fun safeChainNullInTheMiddle() =
        assertEvaluatesTo(
            VNull,
            """
            r = if true then { b = if false then { c = 1 } else null } else null
            r?.b?.c
            """,
        )

    @Test
    fun safeAccessOnCallReceiver() {
        assertEvaluatesTo(
            VNum(1.0),
            """
            fun maybeRecord(b: Bool) = if b then { x = 1 } else null
            maybeRecord(true)?.x
            """,
        )
        assertEvaluatesTo(
            VNull,
            """
            fun maybeRecord(b: Bool) = if b then { x = 1 } else null
            maybeRecord(false)?.x
            """,
        )
    }

    @Test
    fun safeMethodCallOnPresentReceiver() =
        assertEvaluatesTo(
            VNum(42.0),
            """
            r = if true then { double = |x: Num -> x * 2| } else null
            r?.double(21)
            """,
        )

    @Test
    fun methodOwnNullPropagates() {
        assertEvaluatesTo(
            VNull,
            """
            r = if true then { find = |x: Num -> if x > 0 then x else null| } else null
            r?.find(0)
            """,
        )
        assertEvaluatesTo(
            VNum(5.0),
            """
            r = if true then { find = |x: Num -> if x > 0 then x else null| } else null
            r?.find(5)
            """,
        )
    }

    @Test
    fun nullEqualsNull() = assertEvaluatesTo(VBool(true), "null == null")

    @Test
    fun idiomaticNullTest() {
        assertEvaluatesTo(VBool(true), "maybe = if false then 42 else null\nmaybe == null")
        assertEvaluatesTo(VBool(false), "maybe = if true then 42 else null\nmaybe == null")
    }

    @Test
    fun optionalComparedToBareValue() =
        assertEvaluatesTo(VBool(true), "maybe = if true then 42 else null\nmaybe == 42")
}
