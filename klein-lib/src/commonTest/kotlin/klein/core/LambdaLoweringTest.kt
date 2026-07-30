package klein.core

import kotlin.test.Test

/**
 * Golden lowering spec for lambdas and the implicit `.` parameter.
 *
 * Mapping: surface Lambda(params, body) -> core Lambda(arity, body, name?). Each param becomes a
 * slot in the lambda's own scope: depth 0 inside the body, slot = param ordinal (first param slot
 * 0, second slot 1, ...). Parameter type annotations are erased. The lambda's `name` is inferred
 * from its binding — a lambda bound to `id = |x -> x|` or defined as `fun id(...)` carries "id" and
 * prints as `fun id/N -> ...`; an anonymous lambda (a bare trailing `|x -> x|`) carries null and
 * prints without a name as `fun/N -> ...`. Variables read back as `name[depth;slot]`.
 *
 * ImplicitParam (the `.` param) lowers to a slot-0 Var inside an arity-1 lambda — there is no
 * distinct implicit-param node in core: the `.` param lowers to an ordinary slot-0 ref.
 *
 * A program with no bindings is just its trailing expression, so a bare lambda lowers directly to
 * `fun/N -> ...` with no `scope` wrapper. A bound lambda fills a `bind name#N` slot inside a
 * `scope`, and the trailing reference reads it back as `name[0;N]`.
 *
 * Inputs are well-typed (the lowerer only receives checked programs).
 */
class LambdaLoweringTest {
    // --- anonymous lambdas (no binding -> null name -> printed without a name) ---

    // A bare trailing lambda is the whole program: no bindings, so no scope wrapper — it lowers
    // directly to the lambda.
    @Test
    fun anonymousSingleParamLambda() =
        assertLowersTo(
            """
            |x -> x|
            """,
            """
            fun/1 -> x[0;0]
            """,
        )

    // Two params occupy slots 0 and 1 of the lambda's scope, both at depth 0.
    @Test
    fun twoParamLambda_paramsAtSlotZeroAndOne() =
        assertLowersTo(
            """
            |x, y -> x + y|
            """,
            """
            fun/2 -> (x[0;0] + y[0;1])
            """,
        )

    // A param combined with a literal: BinaryOp Add -> PrimApp Add, the param is `x[0;0]`.
    @Test
    fun paramUsedWithOperator() =
        assertLowersTo(
            """
            |x -> x + 1|
            """,
            """
            fun/1 -> (x[0;0] + 1)
            """,
        )

    // The trailing (unnamed) lambda is the whole program; only the first param is used, but the
    // second still consumes slot 1, so the arity is 2.
    @Test
    fun twoParamLambda_secondParamUnused() =
        assertLowersTo(
            """
            |a, b -> a|
            """,
            """
            fun/2 -> a[0;0]
            """,
        )

    // --- name inference from a binding ---

    // `id = |x -> x|` infers the name "id" onto the lambda; the trailing `id` reads slot #0 back.
    @Test
    fun nameInferredFromValBinding() =
        assertLowersTo(
            """
            id = |x -> x|
            id
            """,
            """
            scope
              bind id#0 = fun id/1 -> x[0;0]
              id[0;0]
            """,
        )

    // A two-param bound lambda: name "add", params at slots 0 and 1.
    @Test
    fun nameInferredFromValBinding_twoParams() =
        assertLowersTo(
            """
            add = |x, y -> x + y|
            add
            """,
            """
            scope
              bind add#0 = fun add/2 -> (x[0;0] + y[0;1])
              add[0;0]
            """,
        )

    // A top-level `fun` lowers to a Bind of a named Lambda (name = the fun's name), exactly like the
    // `=`-bound form. Param annotation is erased; the inferred return type is dropped.
    @Test
    fun nameInferredFromFunDef() =
        assertLowersTo(
            """
            fun inc(x: Num) = x + 1
            inc
            """,
            """
            scope
              bind inc#0 = fun inc/1 -> (x[0;0] + 1)
              inc[0;0]
            """,
        )

    // --- the implicit `.` parameter ---

    // `|.|` is an arity-1 lambda whose body is the slot-0 param. Written in check position (the
    // signature supplies the type the implicit param demands), then erased at lowering. The implicit
    // param's synthesized slot name is "param", so the printer renders it `param[0;0]`.
    @Test
    fun implicitParam_identity() =
        assertLowersTo(
            """
            f: ('A) -> 'A = |.|
            f
            """,
            """
            scope
              bind f#0 = fun f/1 -> param[0;0]
              f[0;0]
            """,
        )

    // `|.x|` is field access on the implicit param: FieldGet(Var(0,0,"param"), "x") -> `param[0;0].x`.
    @Test
    fun implicitParam_fieldAccess() =
        assertLowersTo(
            """
            g: ({ x: 'A }) -> 'A = |.x|
            g
            """,
            """
            scope
              bind g#0 = fun g/1 -> param[0;0].x
              g[0;0]
            """,
        )
}
