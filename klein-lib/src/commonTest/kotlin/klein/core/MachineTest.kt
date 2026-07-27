package klein.core

import klein.SourceSpan
import klein.interp.KleinRuntimeError
import klein.interp.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val s = SourceSpan.zero

private fun num(v: Double) = Literal(Constant.CNum(v), s)

private fun str(v: String) = Literal(Constant.CStr(v), s)

private fun bool(v: Boolean) = Literal(Constant.CBool(v), s)

private fun nul() = Literal(Constant.CNull, s)

private fun unit() = Literal(Constant.CUnit, s)

private fun v(
    depth: Int,
    slot: Int,
) = Var(depth, slot, "v_${depth}_$slot", s)

private fun lam(
    arity: Int,
    body: CoreExpr,
) = Lambda(arity, body, null, s)

private fun app(
    callee: CoreExpr,
    vararg args: CoreExpr,
) = Apply(callee, args.toList(), s)

private fun scope(
    vararg stmts: EnterScope.Stmt,
    result: CoreExpr,
) = EnterScope(stmts.toList(), result, s)

private fun bind(
    slot: Int,
    body: CoreExpr,
) = EnterScope.Bind(slot, body, s)

private fun stmt(body: CoreExpr) = EnterScope.Run(body, s)

private fun host(
    name: String,
    vararg args: CoreExpr,
) = HostCall(name, args.toList(), s)

private fun prim(
    op: PrimOp,
    vararg args: CoreExpr,
) = PrimApp(op, args.toList(), s)

private fun mk(
    tag: String?,
    vararg fields: Pair<String, CoreExpr>,
) = MakeData(tag, fields.map { it.first }, fields.map { it.second }, s)

private fun get(
    target: CoreExpr,
    field: String,
) = FieldGet(target, field, s)

private fun match(
    scrutinee: CoreExpr,
    vararg arms: Match.Arm,
) = Match(scrutinee, arms.toList(), s)

private fun litArm(
    lit: Constant,
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.LitArm(lit, guard, body, s)

private fun ctorArm(
    tag: String,
    fields: List<String>,
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.ConstructorArm(tag, fields, guard, body, s)

private fun default(
    body: CoreExpr,
    guard: CoreExpr? = null,
) = Match.Default(guard, body, s)

private fun countdown(
    n: Double,
    base: CoreExpr,
) = scope(
    bind(
        0,
        lam(
            1,
            match(
                v(0, 0),
                litArm(Constant.CNum(0.0), base),
                default(app(v(2, 0), prim(PrimOp.Sub, v(1, 0), num(1.0)))),
            ),
        ),
    ),
    result = app(v(0, 0), num(n)),
)

private fun run(expr: CoreExpr): Value {
    val result = Machine.start(expr)
    assertIs<Execution.Done>(result)
    return result.value
}

class MachineTest {
    @Test
    fun literalNum() = assertEquals(Value.VNum(42.0), run(num(42.0)))

    @Test
    fun literalStr() = assertEquals(Value.VStr("hi"), run(str("hi")))

    @Test
    fun literalBool() = assertEquals(Value.VBool(true), run(bool(true)))

    @Test
    fun literalNull() = assertEquals(Value.VNull, run(nul()))

    @Test
    fun literalUnit() = assertEquals(Value.VUnit, run(unit()))

    @Test
    fun varOnEmptyScopeIsAnInvariantViolation() {
        assertFailsWith<InvariantViolation> { Machine.start(v(0, 0)) }
    }

    @Test
    fun varPastRootScopeIsAnInvariantViolation() {
        assertFailsWith<InvariantViolation> { Machine.start(v(3, 0)) }
    }

    @Test
    fun lambdaEvaluatesToAClosure() {
        val closure = run(lam(1, v(0, 0)))
        assertIs<Value.VClos>(closure)
        assertEquals(1, closure.arity)
    }

    @Test
    fun identityApplication() = assertEquals(Value.VNum(42.0), run(app(lam(1, v(0, 0)), num(42.0))))

    @Test
    fun constantFunctionIgnoresItsArgument() = assertEquals(Value.VNum(7.0), run(app(lam(1, num(7.0)), num(42.0))))

    @Test
    fun twoArgumentsBindInOrder() =
        assertEquals(Value.VNum(1.0), run(app(lam(2, v(0, 0)), num(1.0), num(2.0))))

    @Test
    fun nestedApplication() =
        assertEquals(Value.VNum(42.0), run(app(lam(1, app(lam(1, v(0, 0)), v(0, 0))), num(42.0))))

    @Test
    fun innerLambdaSeesOuterScope() =
        assertEquals(Value.VNum(42.0), run(app(lam(1, app(lam(1, v(1, 0)), num(1.0))), num(42.0))))

    @Test
    fun arityMismatchIsAnInvariantViolation() {
        assertFailsWith<InvariantViolation> { Machine.start(app(lam(2, v(0, 0)), num(1.0))) }
    }

    @Test
    fun applyingANonClosureIsAnInvariantViolation() {
        assertFailsWith<InvariantViolation> { Machine.start(app(num(1.0), num(2.0))) }
    }

    @Test
    fun hostCallSuspendsWithArgsInCallOrder() {
        val result = Machine.start(host("fetch", num(1.0), num(2.0)))
        assertIs<Execution.AwaitingHost>(result)
        assertEquals("fetch", result.call)
        assertEquals(listOf<Value>(Value.VNum(1.0), Value.VNum(2.0)), result.args)
    }

    @Test
    fun resumeCompletesWithTheHostResult() {
        val suspended = Machine.start(host("fetch", num(1.0)))
        assertIs<Execution.AwaitingHost>(suspended)
        assertEquals(Execution.Done(Value.VNum(9.0)), suspended.resume(Value.VNum(9.0)))
    }

    @Test
    fun hostResultFlowsIntoTheEnclosingApplication() {
        val program = app(lam(1, v(0, 0)), host("f", num(1.0)))
        val suspended = Machine.start(program)
        assertIs<Execution.AwaitingHost>(suspended)
        assertEquals(listOf<Value>(Value.VNum(1.0)), suspended.args)
        assertEquals(Execution.Done(Value.VNum(9.0)), suspended.resume(Value.VNum(9.0)))
    }

    @Test
    fun argsLeaveTheOperandStackOnSuspension() {
        val program = app(lam(2, v(0, 1)), num(7.0), host("f", num(1.0), num(2.0)))
        val suspended = Machine.start(program)
        assertIs<Execution.AwaitingHost>(suspended)
        assertEquals(2, suspended.machine.operandDepth())
        assertEquals(Execution.Done(Value.VNum(9.0)), suspended.resume(Value.VNum(9.0)))
    }

    @Test
    fun doubleResumeIsRejected() {
        val suspended = Machine.start(host("f", num(1.0)))
        assertIs<Execution.AwaitingHost>(suspended)
        suspended.resume(Value.VNum(1.0))
        assertFailsWith<IllegalStateException> { suspended.resume(Value.VNum(2.0)) }
    }

    @Test
    fun cloneAllowsIndependentResumes() {
        val program = app(lam(1, v(0, 0)), host("f", num(1.0)))
        val suspended = Machine.start(program)
        assertIs<Execution.AwaitingHost>(suspended)
        val branch = suspended.clone()
        assertEquals(Execution.Done(Value.VNum(10.0)), suspended.resume(Value.VNum(10.0)))
        assertEquals(Execution.Done(Value.VNum(20.0)), branch.resume(Value.VNum(20.0)))
    }

    @Test
    fun cloneAfterResumeIsRejected() {
        val suspended = Machine.start(host("f", num(1.0)))
        assertIs<Execution.AwaitingHost>(suspended)
        suspended.resume(Value.VNum(1.0))
        assertFailsWith<IllegalStateException> { suspended.clone() }
    }

    @Test
    fun emptyScopeEvaluatesItsResult() = assertEquals(Value.VNum(7.0), run(scope(result = num(7.0))))

    @Test
    fun laterBindReadsEarlierBind() =
        assertEquals(
            Value.VNum(42.0),
            run(scope(bind(0, num(42.0)), bind(1, v(0, 0)), result = v(0, 1))),
        )

    @Test
    fun runStatementValueIsDiscarded() =
        assertEquals(
            Value.VNum(42.0),
            run(scope(bind(0, num(42.0)), stmt(num(99.0)), bind(1, v(0, 0)), result = v(0, 1))),
        )

    @Test
    fun scopeResultSeesEnclosingLambdaParam() =
        assertEquals(Value.VNum(5.0), run(app(lam(1, scope(result = v(1, 0))), num(5.0))))

    @Test
    fun clonedBranchesFillScopeCellsIndependently() {
        val program = scope(bind(0, host("f", num(1.0))), result = v(0, 0))
        val suspended = Machine.start(program)
        assertIs<Execution.AwaitingHost>(suspended)
        val branch = suspended.clone()
        assertEquals(Execution.Done(Value.VNum(1.0)), suspended.resume(Value.VNum(1.0)))
        assertEquals(Execution.Done(Value.VNum(2.0)), branch.resume(Value.VNum(2.0)))
    }

    @Test
    fun tailPositionScopeResultsRunInConstantControlSpace() {
        var program: CoreExpr = host("probe")
        repeat(500) { program = scope(result = program) }
        val suspended = Machine.start(program)
        assertIs<Execution.AwaitingHost>(suspended)
        assertTrue(suspended.machine.controlDepth() <= 2, "control depth was ${suspended.machine.controlDepth()}")
        assertEquals(Execution.Done(Value.VNum(9.0)), suspended.resume(Value.VNum(9.0)))
    }

    @Test
    fun tailCallsThroughLambdaBodiesRunInConstantControlSpace() {
        var program: CoreExpr = host("probe")
        repeat(500) { program = app(lam(1, program), num(0.0)) }
        val suspended = Machine.start(program)
        assertIs<Execution.AwaitingHost>(suspended)
        assertTrue(suspended.machine.controlDepth() <= 2, "control depth was ${suspended.machine.controlDepth()}")
        assertEquals(Execution.Done(Value.VNum(9.0)), suspended.resume(Value.VNum(9.0)))
    }

    @Test
    fun closuresUseLexicalNotDynamicScope() {
        val maker = lam(1, lam(1, v(1, 0)))
        val program =
            scope(
                bind(0, app(maker, num(42.0))),
                result = app(lam(1, app(v(1, 0), num(0.0))), num(99.0)),
            )
        assertEquals(Value.VNum(42.0), run(program))
    }

    @Test
    fun nestedScopesResolveDepthsIndependently() {
        val program =
            scope(
                bind(0, num(1.0)),
                result =
                    scope(
                        bind(0, num(2.0)),
                        result = app(lam(2, v(0, 0)), v(0, 0), v(1, 0)),
                    ),
            )
        assertEquals(Value.VNum(2.0), run(program))
    }

    @Test
    fun sequentialHostCallsSuspendInOrder() {
        val program = scope(bind(0, host("a")), bind(1, host("b")), result = v(0, 1))
        val first = Machine.start(program)
        assertIs<Execution.AwaitingHost>(first)
        assertEquals("a", first.call)
        val second = first.resume(Value.VNum(10.0))
        assertIs<Execution.AwaitingHost>(second)
        assertEquals("b", second.call)
        assertEquals(Execution.Done(Value.VNum(20.0)), second.resume(Value.VNum(20.0)))
    }

    @Test
    fun zeroArityClosureApplies() = assertEquals(Value.VNum(7.0), run(app(lam(0, num(7.0)))))

    @Test
    fun zeroArityClosureBodySeesEnclosingScopeAtDepthOne() =
        assertEquals(
            Value.VNum(42.0),
            run(scope(bind(0, num(42.0)), result = app(lam(0, v(1, 0))))),
        )

    @Test
    fun closurePassedAsArgumentApplies() =
        assertEquals(Value.VNum(1.0), run(app(lam(1, app(v(0, 0), num(1.0))), lam(1, v(0, 0)))))

    @Test
    fun bindReadingItsOwnUnfilledSlotIsARuntimeError() {
        val program = scope(bind(0, v(0, 0)), result = num(1.0))
        assertFailsWith<KleinRuntimeError> { Machine.start(program) }
    }

    @Test
    fun callBeforeBindThroughAFunctionIsARuntimeError() {
        val program =
            scope(
                bind(0, lam(1, v(1, 2))),
                bind(1, app(v(0, 0), num(1.0))),
                bind(2, num(2.0)),
                result = v(0, 1),
            )
        assertFailsWith<KleinRuntimeError> { Machine.start(program) }
    }

    @Test
    fun finalValueGuardRejectsImbalance() {
        val state = MachineState(num(1.0))
        state.pushOperand(Value.VNum(1.0))
        state.pushOperand(Value.VNum(2.0))
        assertFailsWith<InvariantViolation> { state.finalValue() }
    }

    @Test
    fun tailCallsThroughLambdaBodiesWithScopesRunInConstantControlSpace() {
        var program: CoreExpr = host("probe")
        repeat(300) {
            program = app(lam(1, scope(bind(0, num(1.0)), result = program)), num(0.0))
        }
        val suspended = Machine.start(program)
        assertIs<Execution.AwaitingHost>(suspended)
        assertTrue(suspended.machine.controlDepth() <= 2, "control depth was ${suspended.machine.controlDepth()}")
        assertTrue(suspended.machine.operandDepth() <= 2, "operand depth was ${suspended.machine.operandDepth()}")
        assertEquals(Execution.Done(Value.VNum(9.0)), suspended.resume(Value.VNum(9.0)))
    }

    @Test
    fun storeAllocatesExactlyTwoCellsPerTailChainLayer() {
        var program: CoreExpr = host("probe")
        repeat(300) {
            program = app(lam(1, scope(bind(0, num(1.0)), result = program)), num(0.0))
        }
        val suspended = Machine.start(program)
        assertIs<Execution.AwaitingHost>(suspended)
        assertEquals(600, suspended.machine.storeSize())
    }

    @Test
    fun argumentsEvaluateLeftToRight() {
        val program = app(lam(2, v(0, 1)), host("first"), host("second"))
        val first = Machine.start(program)
        assertIs<Execution.AwaitingHost>(first)
        assertEquals("first", first.call)
        val second = first.resume(Value.VNum(1.0))
        assertIs<Execution.AwaitingHost>(second)
        assertEquals("second", second.call)
        assertEquals(Execution.Done(Value.VNum(2.0)), second.resume(Value.VNum(2.0)))
    }

    @Test
    fun calleeEvaluatesBeforeArguments() {
        val program = app(host("getFn"), host("getArg"))
        val atCallee = Machine.start(program)
        assertIs<Execution.AwaitingHost>(atCallee)
        assertEquals("getFn", atCallee.call)
        val atArg = atCallee.resume(Value.VClos(1, v(0, 0), BindingScope()))
        assertIs<Execution.AwaitingHost>(atArg)
        assertEquals("getArg", atArg.call)
        assertEquals(Execution.Done(Value.VNum(5.0)), atArg.resume(Value.VNum(5.0)))
    }

    @Test
    fun arithmeticPrims() {
        assertEquals(Value.VNum(3.0), run(prim(PrimOp.Add, num(1.0), num(2.0))))
        assertEquals(Value.VNum(2.0), run(prim(PrimOp.Sub, num(5.0), num(3.0))))
        assertEquals(Value.VNum(6.0), run(prim(PrimOp.Mul, num(2.0), num(3.0))))
        assertEquals(Value.VNum(2.5), run(prim(PrimOp.Div, num(10.0), num(4.0))))
        assertEquals(Value.VNum(1.0), run(prim(PrimOp.Mod, num(7.0), num(3.0))))
        assertEquals(Value.VNum(-5.0), run(prim(PrimOp.Neg, num(5.0))))
    }

    @Test
    fun nestedArithmeticCollects() =
        assertEquals(Value.VNum(7.0), run(prim(PrimOp.Add, num(1.0), prim(PrimOp.Mul, num(2.0), num(3.0)))))

    @Test
    fun comparisonPrims() {
        assertEquals(Value.VBool(true), run(prim(PrimOp.Lt, num(1.0), num(2.0))))
        assertEquals(Value.VBool(false), run(prim(PrimOp.Lt, num(2.0), num(2.0))))
        assertEquals(Value.VBool(true), run(prim(PrimOp.LtEq, num(2.0), num(2.0))))
        assertEquals(Value.VBool(false), run(prim(PrimOp.Gt, num(1.0), num(2.0))))
        assertEquals(Value.VBool(true), run(prim(PrimOp.GtEq, num(2.0), num(2.0))))
    }

    @Test
    fun equalityPrimsAreStructural() {
        assertEquals(Value.VBool(true), run(prim(PrimOp.Eq, num(1.0), num(1.0))))
        assertEquals(Value.VBool(false), run(prim(PrimOp.Eq, num(1.0), num(2.0))))
        assertEquals(Value.VBool(true), run(prim(PrimOp.Eq, str("a"), str("a"))))
        assertEquals(Value.VBool(true), run(prim(PrimOp.Eq, nul(), nul())))
        assertEquals(Value.VBool(false), run(prim(PrimOp.Eq, num(1.0), str("1"))))
        assertEquals(Value.VBool(true), run(prim(PrimOp.NotEq, num(1.0), num(2.0))))
    }

    @Test
    fun notPrim() {
        assertEquals(Value.VBool(false), run(prim(PrimOp.Not, bool(true))))
        assertEquals(Value.VBool(true), run(prim(PrimOp.Not, bool(false))))
    }

    @Test
    fun divisionByZeroIsARuntimeError() {
        assertFailsWith<KleinRuntimeError> { Machine.start(prim(PrimOp.Div, num(1.0), num(0.0))) }
        assertFailsWith<KleinRuntimeError> { Machine.start(prim(PrimOp.Mod, num(1.0), num(0.0))) }
    }

    @Test
    fun illTypedPrimOperandIsAnInvariantViolation() {
        assertFailsWith<InvariantViolation> { Machine.start(prim(PrimOp.Add, str("a"), num(1.0))) }
        assertFailsWith<InvariantViolation> { Machine.start(prim(PrimOp.Not, num(1.0))) }
    }

    @Test
    fun litArmsSelectByValue() {
        fun program(n: Double) =
            match(
                num(n),
                litArm(Constant.CNum(1.0), str("one")),
                litArm(Constant.CNum(2.0), str("two")),
                default(str("many")),
            )
        assertEquals(Value.VStr("two"), run(program(2.0)))
        assertEquals(Value.VStr("many"), run(program(5.0)))
    }

    @Test
    fun constructorArmsSelectByTag() =
        assertEquals(
            Value.VNum(3.0),
            run(
                match(
                    mk("Circle", "radius" to num(3.0)),
                    ctorArm("Square", listOf("side"), num(0.0)),
                    ctorArm("Circle", listOf("radius"), v(0, 0)),
                ),
            ),
        )

    @Test
    fun constructorArmFieldsBindInDeclaredOrder() {
        val shape = mk("P", "x" to num(1.0), "y" to num(2.0))
        assertEquals(Value.VNum(2.0), run(match(shape, ctorArm("P", listOf("y", "x"), v(0, 0)))))
        assertEquals(Value.VNum(1.0), run(match(shape, ctorArm("P", listOf("y", "x"), v(0, 1)))))
    }

    @Test
    fun armBodiesRunOneScopeDeeper() =
        assertEquals(
            Value.VNum(10.0),
            run(
                scope(
                    bind(0, num(10.0)),
                    result = match(num(1.0), default(v(1, 0))),
                ),
            ),
        )

    @Test
    fun falseGuardsFallThroughInOrder() {
        fun program(n: Double) =
            match(
                mk("C", "n" to num(n)),
                ctorArm("C", listOf("n"), num(100.0), guard = prim(PrimOp.Gt, v(0, 0), num(10.0))),
                ctorArm("C", listOf("n"), v(0, 0), guard = prim(PrimOp.Gt, v(0, 0), num(3.0))),
                default(num(0.0)),
            )
        assertEquals(Value.VNum(5.0), run(program(5.0)))
        assertEquals(Value.VNum(0.0), run(program(2.0)))
    }

    @Test
    fun noMatchingArmIsAnInvariantViolation() {
        assertFailsWith<InvariantViolation> {
            Machine.start(match(num(1.0), litArm(Constant.CNum(2.0), str("x"))))
        }
        assertFailsWith<InvariantViolation> {
            Machine.start(match(num(1.0), default(str("x"), guard = bool(false))))
        }
    }

    @Test
    fun nonBoolGuardIsAnInvariantViolation() {
        assertFailsWith<InvariantViolation> {
            Machine.start(match(num(1.0), default(str("x"), guard = num(1.0))))
        }
    }

    @Test
    fun scrutineeEvaluatesOnceThroughSuspension() {
        val exec =
            Machine.start(
                match(
                    host("pick"),
                    litArm(Constant.CNum(1.0), str("one")),
                    default(str("other")),
                ),
            )
        assertIs<Execution.AwaitingHost>(exec)
        assertEquals("pick", exec.call)
        assertEquals(Execution.Done(Value.VStr("one")), exec.resume(Value.VNum(1.0)))
    }

    @Test
    fun guardSuspensionResumesIntoTheRightArm() {
        fun program() =
            match(
                num(1.0),
                default(str("yes"), guard = prim(PrimOp.Eq, host("ask"), num(1.0))),
                default(str("no")),
            )
        val yes = Machine.start(program())
        assertIs<Execution.AwaitingHost>(yes)
        assertEquals("ask", yes.call)
        assertEquals(Execution.Done(Value.VStr("yes")), yes.resume(Value.VNum(1.0)))

        val no = Machine.start(program())
        assertIs<Execution.AwaitingHost>(no)
        assertEquals(Execution.Done(Value.VStr("no")), no.resume(Value.VNum(0.0)))
    }

    @Test
    fun matchArmsAreTailPositions() {
        val suspended = Machine.start(countdown(500.0, base = host("probe")))
        assertIs<Execution.AwaitingHost>(suspended)
        assertTrue(suspended.machine.controlDepth() <= 2, "control depth was ${suspended.machine.controlDepth()}")
        assertEquals(Execution.Done(Value.VStr("done")), suspended.resume(Value.VStr("done")))
    }

    @Test
    fun millionStepCountdownTerminates() =
        assertEquals(Value.VStr("done"), run(countdown(1_000_000.0, base = str("done"))))

    @Test
    fun fib() {
        val program =
            scope(
                bind(
                    0,
                    lam(
                        1,
                        match(
                            prim(PrimOp.Lt, v(0, 0), num(2.0)),
                            litArm(Constant.CBool(true), v(1, 0)),
                            default(
                                prim(
                                    PrimOp.Add,
                                    app(v(2, 0), prim(PrimOp.Sub, v(1, 0), num(1.0))),
                                    app(v(2, 0), prim(PrimOp.Sub, v(1, 0), num(2.0))),
                                ),
                            ),
                        ),
                    ),
                ),
                result = app(v(0, 0), num(20.0)),
            )
        assertEquals(Value.VNum(6765.0), run(program))
    }

    @Test
    fun recordConstructs() =
        assertEquals(
            Value.VStruct(null, mapOf("x" to Value.VNum(1.0), "y" to Value.VNum(2.0))),
            run(mk(null, "x" to num(1.0), "y" to num(2.0))),
        )

    @Test
    fun taggedDataConstructs() =
        assertEquals(
            Value.VStruct("Circle", mapOf("radius" to Value.VNum(3.0))),
            run(mk("Circle", "radius" to num(3.0))),
        )

    @Test
    fun fieldGetReadsRecordsAndTaggedData() {
        assertEquals(Value.VNum(2.0), run(get(mk(null, "x" to num(1.0), "y" to num(2.0)), "y")))
        assertEquals(Value.VNum(3.0), run(get(mk("Circle", "radius" to num(3.0)), "radius")))
    }

    @Test
    fun structuralEqualityDistinguishesTags() {
        assertEquals(
            Value.VBool(true),
            run(prim(PrimOp.Eq, mk(null, "x" to num(1.0)), mk(null, "x" to num(1.0)))),
        )
        assertEquals(
            Value.VBool(false),
            run(prim(PrimOp.Eq, mk("Circle", "x" to num(1.0)), mk(null, "x" to num(1.0)))),
        )
    }

    @Test
    fun missingFieldIsAnInvariantViolation() {
        assertFailsWith<InvariantViolation> { Machine.start(get(mk(null, "x" to num(1.0)), "y")) }
        assertFailsWith<InvariantViolation> { Machine.start(get(num(1.0), "x")) }
    }

    @Test
    fun makeDataArgsEvaluateLeftToRight() {
        val first = Machine.start(mk(null, "a" to host("first"), "b" to host("second")))
        assertIs<Execution.AwaitingHost>(first)
        assertEquals("first", first.call)
        val second = first.resume(Value.VNum(1.0))
        assertIs<Execution.AwaitingHost>(second)
        assertEquals("second", second.call)
        assertEquals(
            Execution.Done(Value.VStruct(null, mapOf("a" to Value.VNum(1.0), "b" to Value.VNum(2.0)))),
            second.resume(Value.VNum(2.0)),
        )
    }

    @Test
    fun primOperandsEvaluateLeftToRight() {
        val first = Machine.start(prim(PrimOp.Add, host("first"), host("second")))
        assertIs<Execution.AwaitingHost>(first)
        assertEquals("first", first.call)
        val second = first.resume(Value.VNum(1.0))
        assertIs<Execution.AwaitingHost>(second)
        assertEquals("second", second.call)
        assertEquals(Execution.Done(Value.VNum(3.0)), second.resume(Value.VNum(2.0)))
    }
}
