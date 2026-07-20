package klein.interp

import klein.Apply
import klein.Ascription
import klein.BinaryOp
import klein.Block
import klein.BoolLiteral
import klein.DoubleLiteral
import klein.Expr
import klein.FieldAccess
import klein.FunDef
import klein.Ident
import klein.IfThenElse
import klein.ImplicitParam
import klein.IntLiteral
import klein.KleinError
import klein.Lambda
import klein.Match
import klein.NullLiteral
import klein.Operator
import klein.Program
import klein.RecordLiteral
import klein.SafeFieldAccess
import klein.SourceSpan
import klein.Stmt
import klein.StringLiteral
import klein.TypeDef
import klein.UnaryOp
import klein.UnaryOperator
import klein.Val
import klein.check.ScopeGraph
import klein.usesImplicitParam

/** A fail-fast evaluation error carrying the source location it arose at. */
class KleinRuntimeError(
    override val message: String,
    override val span: SourceSpan,
) : Exception(message),
    KleinError

/**
 * A CESK machine over the parsed AST: Control (the expression or value at hand),
 * Environment ([Env], names to addresses), Store ([Store], addresses to values), and
 * Kontinuation ([Kont], a stack of [Frame]s). The machine takes small steps in a flat loop —
 * Klein's call structure lives in K on the heap, never on the host stack, so recursion depth
 * is bounded by memory, tail calls run in constant K, and suspension is free: at a native
 * application the machine simply stops and hands its state to the host as an
 * [Execution.Suspended].
 *
 * It assumes programs accepted by the bidirectional checker; shape errors it still detects
 * (wrong operand kind, missing field, bad arity) throw [KleinRuntimeError] rather than being
 * silently mishandled. Bindings in every scope resolve in dependency (SCC) order, mirroring
 * [klein.check.Checker]: store cells for a whole scope are allocated up front, function
 * groups fill theirs immediately (mutual recursion via the store's indirection), and `val`s
 * fill theirs as the machine reaches them in dependency order.
 */
class Interpreter {
    /** Start evaluating; returns [Execution.Done] or the first [Execution.Suspended] host call. */
    fun begin(
        program: Program,
        bindings: Map<String, Value> = emptyMap(),
    ): Execution = Machine(bindings).start(program)

    /**
     * Evaluate to completion, answering each host call with [onHostCall] — the degenerate
     * synchronous host. The default handler treats any host call as an error.
     */
    fun run(
        program: Program,
        bindings: Map<String, Value> = emptyMap(),
        onHostCall: (HostCall) -> Value = { call ->
            throw KleinRuntimeError("No host handler for native function '${call.function.name}'", call.span)
        },
    ): Value {
        var execution = begin(program, bindings)
        while (execution is Execution.Suspended) {
            execution = execution.resume(onHostCall(execution.call))
        }
        return (execution as Execution.Done).value
    }
}

internal class Machine(
    hostBindings: Map<String, Value>,
) {
    private val store = Store()
    private val rootEnv: Env =
        Env.root(
            hostBindings.mapValues { (_, value) ->
                store.alloc().also { store.set(it, value) }
            },
        )

    fun start(program: Program): Execution {
        val (env, items) = enterScope(program.stmts, rootEnv)
        if (items.isEmpty()) return Execution.Done(Value.VUnit)
        return outcome(drive(items[0].expr, env, Kont(ScopeK(items, 0, env, Value.VUnit), null)))
    }

    fun resume(
        kont: Kont?,
        result: Value,
    ): Execution = outcome(driveValue(result, kont))

    private fun outcome(state: Stopped): Execution =
        when (state) {
            is Finished -> Execution.Done(state.value)
            is AwaitingHost -> Execution.Suspended(state.call, this, state.kont)
        }

    private sealed class Stopped

    private class Finished(
        val value: Value,
    ) : Stopped()

    private class AwaitingHost(
        val call: HostCall,
        val kont: Kont?,
    ) : Stopped()

    /** Run the machine from an expression until it finishes or reaches a host call. */
    private fun drive(
        startExpr: Expr,
        startEnv: Env,
        startKont: Kont?,
    ): Stopped = loop(startExpr, startEnv, null, startKont)

    /** Run the machine from a value being returned into [startKont]. */
    private fun driveValue(
        startValue: Value,
        startKont: Kont?,
    ): Stopped = loop(null, rootEnv, startValue, startKont)

    private fun loop(
        startExpr: Expr?,
        startEnv: Env,
        startValue: Value?,
        startKont: Kont?,
    ): Stopped {
        // The machine registers. `expr != null` means eval mode; otherwise `value` is being
        // returned into the top frame of `kont`.
        var expr: Expr? = startExpr
        var env: Env = startEnv
        var value: Value? = startValue
        var kont: Kont? = startKont

        while (true) {
            val e = expr
            if (e != null) {
                // ---- Eval: decompose an expression ----
                when (e) {
                    is Match -> throw KleinRuntimeError("match is not supported by this evaluator", e.span)
                    is IntLiteral -> {
                        value = Value.VNum(e.value.toDouble())
                        expr = null
                    }
                    is DoubleLiteral -> {
                        value = Value.VNum(e.value)
                        expr = null
                    }
                    is StringLiteral -> {
                        value = Value.VStr(e.value)
                        expr = null
                    }
                    is BoolLiteral -> {
                        value = Value.VBool(e.value)
                        expr = null
                    }
                    is NullLiteral -> {
                        value = Value.VNull
                        expr = null
                    }
                    is Ident -> {
                        value = lookup(e.name, env, e.span)
                        expr = null
                    }
                    is ImplicitParam -> {
                        value = lookup(Env.IMPLICIT_PARAM, env, e.span, "No implicit parameter in scope")
                        expr = null
                    }
                    is Lambda -> {
                        value =
                            Value.VClosure(
                                e.params.map { it.name },
                                isImplicit = e.params.isEmpty() && e.body.usesImplicitParam,
                                e.body,
                                env,
                            )
                        expr = null
                    }
                    is Ascription -> expr = e.expr
                    is BinaryOp -> {
                        kont = Kont(BinLeftK(e, env), kont)
                        expr = e.left
                    }
                    is UnaryOp -> {
                        kont = Kont(UnaryK(e), kont)
                        expr = e.operand
                    }
                    is IfThenElse -> {
                        kont = Kont(IfK(e, env), kont)
                        expr = e.condition
                    }
                    is Apply -> {
                        kont = Kont(CalleeK(e, env), kont)
                        expr = e.callee
                    }
                    is FieldAccess -> {
                        kont = Kont(FieldK(e), kont)
                        expr = e.target
                    }
                    is SafeFieldAccess -> {
                        kont = Kont(SafeFieldK(e), kont)
                        expr = e.target
                    }
                    is RecordLiteral ->
                        if (e.fields.isEmpty()) {
                            value = Value.VRecord(emptyMap())
                            expr = null
                        } else {
                            kont = Kont(RecordK(e, emptyList(), env), kont)
                            expr = e.fields[0].value
                        }
                    is Block -> {
                        val (scopeEnv, items) = enterScope(e.stmts, env)
                        if (items.isEmpty()) {
                            value = Value.VUnit
                            expr = null
                        } else {
                            kont = Kont(ScopeK(items, 0, scopeEnv, Value.VUnit), kont)
                            env = scopeEnv
                            expr = items[0].expr
                        }
                    }
                }
            } else {
                // ---- Continue: return `value` into the top frame ----
                val v = value!!
                val k = kont ?: return Finished(v)
                val parent = k.parent
                when (val f = k.frame) {
                    is BinLeftK -> {
                        val node = f.node
                        when (node.op) {
                            Operator.And ->
                                if (!asBool(v, node.left.span)) {
                                    value = Value.VBool(false)
                                    kont = parent
                                } else {
                                    kont = Kont(BoolRightK(node), parent)
                                    env = f.env
                                    expr = node.right
                                }
                            Operator.Or ->
                                if (asBool(v, node.left.span)) {
                                    value = Value.VBool(true)
                                    kont = parent
                                } else {
                                    kont = Kont(BoolRightK(node), parent)
                                    env = f.env
                                    expr = node.right
                                }
                            else -> {
                                kont = Kont(BinRightK(node, v), parent)
                                env = f.env
                                expr = node.right
                            }
                        }
                    }
                    is BinRightK -> {
                        value = binOp(f.node, f.left, v)
                        kont = parent
                    }
                    is BoolRightK -> {
                        value = Value.VBool(asBool(v, f.node.right.span))
                        kont = parent
                    }
                    is UnaryK -> {
                        value =
                            when (f.node.op) {
                                UnaryOperator.Neg -> Value.VNum(-asNum(v, f.node.operand.span))
                                UnaryOperator.Not -> Value.VBool(!asBool(v, f.node.operand.span))
                            }
                        kont = parent
                    }
                    is IfK -> {
                        // Branches are entered on the parent continuation: `if` is tail-position.
                        kont = parent
                        val node = f.node
                        if (asBool(v, node.condition.span)) {
                            env = f.env
                            expr = node.thenBranch
                        } else if (node.elseBranch != null) {
                            env = f.env
                            expr = node.elseBranch
                        } else {
                            value = Value.VNull
                        }
                    }
                    is CalleeK -> {
                        val node = f.node
                        if (node.args.isEmpty()) {
                            when (val r = applyValue(v, emptyList(), node.span)) {
                                is EnterBody -> {
                                    expr = r.body
                                    env = r.env
                                    kont = parent
                                }
                                is Produced -> {
                                    value = r.value
                                    kont = parent
                                }
                                is Suspend -> return AwaitingHost(r.call, parent)
                            }
                        } else {
                            kont = Kont(ArgsK(node, v, emptyList(), f.env), parent)
                            env = f.env
                            expr = node.args[0]
                        }
                    }
                    is ArgsK -> {
                        val node = f.node
                        val done = f.done + v
                        if (done.size < node.args.size) {
                            kont = Kont(ArgsK(node, f.callee, done, f.env), parent)
                            env = f.env
                            expr = node.args[done.size]
                        } else {
                            when (val r = applyValue(f.callee, done, node.span)) {
                                is EnterBody -> {
                                    // The callee's body runs on the caller's continuation:
                                    // proper tail calls fall out of the machine.
                                    expr = r.body
                                    env = r.env
                                    kont = parent
                                }
                                is Produced -> {
                                    value = r.value
                                    kont = parent
                                }
                                is Suspend -> return AwaitingHost(r.call, parent)
                            }
                        }
                    }
                    is FieldK -> {
                        value = accessField(v, f.node.field, f.node.span)
                        kont = parent
                    }
                    is SafeFieldK -> {
                        value = if (v == Value.VNull) Value.VNull else accessField(v, f.node.field, f.node.span)
                        kont = parent
                    }
                    is RecordK -> {
                        val node = f.node
                        val done = f.done + (node.fields[f.done.size].name to v)
                        if (done.size < node.fields.size) {
                            kont = Kont(RecordK(node, done, f.env), parent)
                            env = f.env
                            expr = node.fields[done.size].value
                        } else {
                            value = Value.VRecord(done.toMap())
                            kont = parent
                        }
                    }
                    is ScopeK -> {
                        val item = f.items[f.index]
                        var last = f.last
                        if (item.bindAddr != null) {
                            store.set(item.bindAddr, v)
                        } else {
                            last = v
                        }
                        val next = f.index + 1
                        if (next < f.items.size) {
                            kont = Kont(ScopeK(f.items, next, f.env, last), parent)
                            env = f.env
                            expr = f.items[next].expr
                        } else {
                            value = last
                            kont = parent
                        }
                    }
                }
            }
        }
    }

    // ---- Application ----

    private sealed class Applied

    private class EnterBody(
        val body: Expr,
        val env: Env,
    ) : Applied()

    private class Produced(
        val value: Value,
    ) : Applied()

    private class Suspend(
        val call: HostCall,
    ) : Applied()

    private fun applyValue(
        callee: Value,
        args: List<Value>,
        span: SourceSpan,
    ): Applied =
        when (callee) {
            is Value.VClosure -> {
                checkArity(callee.arity, args.size, span)
                val frame = HashMap<String, Int>(args.size)
                if (callee.isImplicit) {
                    frame[Env.IMPLICIT_PARAM] = store.alloc().also { store.set(it, args.single()) }
                } else {
                    callee.params.zip(args).forEach { (name, arg) ->
                        frame[name] = store.alloc().also { store.set(it, arg) }
                    }
                }
                EnterBody(callee.body, callee.env.child(frame))
            }
            is Value.VConstructor -> {
                checkArity(callee.fieldNames.size, args.size, span)
                Produced(Value.VData(callee.typeName, callee.name, callee.fieldNames.zip(args).toMap()))
            }
            is Value.VNative -> {
                checkArity(callee.arity, args.size, span)
                Suspend(HostCall(callee, args, span))
            }
            else -> throw KleinRuntimeError("Cannot call ${describe(callee)}", span)
        }

    // ---- Scope setup ----

    /**
     * Prepare a scope: allocate store cells for every binding up front (so closures and
     * mutually recursive functions can capture addresses before values exist), fill
     * constructors and function closures immediately, and return the work items — `val`
     * bindings in SCC dependency order, then expression statements in textual order.
     */
    private fun enterScope(
        stmts: List<Stmt>,
        parent: Env,
    ): Pair<Env, List<ScopeItem>> {
        val addrs = HashMap<String, Int>()
        for (stmt in stmts) {
            when (stmt) {
                is TypeDef -> stmt.constructors.forEach { addrs[it.name] = store.alloc() }
                is FunDef -> addrs[stmt.name] = store.alloc()
                is Val -> addrs[stmt.name] = store.alloc()
                else -> {}
            }
        }
        val env = parent.child(addrs)

        for (stmt in stmts) {
            when (stmt) {
                is TypeDef ->
                    for (ctor in stmt.constructors) {
                        val value =
                            if (ctor.fields.isEmpty()) {
                                Value.VData(stmt.name, ctor.name, emptyMap())
                            } else {
                                Value.VConstructor(ctor.name, stmt.name, ctor.fields.map { it.name })
                            }
                        store.set(addrs.getValue(ctor.name), value)
                    }
                is FunDef ->
                    store.set(
                        addrs.getValue(stmt.name),
                        Value.VClosure(stmt.params.map { it.name }, isImplicit = false, stmt.body, env),
                    )
                else -> {}
            }
        }

        val items = ArrayList<ScopeItem>()
        val scope = ScopeGraph.constructGraph(stmts)
        for (component in scope.graph.computeSCCs()) {
            for (node in component.nodes) {
                val binding = node.binding
                if (binding is Val) items.add(ScopeItem(binding.value, addrs.getValue(binding.name)))
            }
        }
        for (stmt in stmts) {
            if (stmt is Expr) items.add(ScopeItem(stmt, bindAddr = null))
        }
        return env to items
    }

    // ---- Operators and helpers ----

    private fun binOp(
        node: BinaryOp,
        left: Value,
        right: Value,
    ): Value =
        when (node.op) {
            Operator.Add -> Value.VNum(asNum(left, node.left.span) + asNum(right, node.right.span))
            Operator.Sub -> Value.VNum(asNum(left, node.left.span) - asNum(right, node.right.span))
            Operator.Mul -> Value.VNum(asNum(left, node.left.span) * asNum(right, node.right.span))
            Operator.Div -> Value.VNum(asNum(left, node.left.span) / nonZero(asNum(right, node.right.span), node.span))
            Operator.Mod -> Value.VNum(asNum(left, node.left.span) % nonZero(asNum(right, node.right.span), node.span))
            Operator.Lt -> Value.VBool(asNum(left, node.left.span) < asNum(right, node.right.span))
            Operator.LtEq -> Value.VBool(asNum(left, node.left.span) <= asNum(right, node.right.span))
            Operator.Gt -> Value.VBool(asNum(left, node.left.span) > asNum(right, node.right.span))
            Operator.GtEq -> Value.VBool(asNum(left, node.left.span) >= asNum(right, node.right.span))
            Operator.Eq -> Value.VBool(left == right)
            Operator.NotEq -> Value.VBool(left != right)
            Operator.And, Operator.Or -> error("unreachable: short-circuit ops handled at BinLeftK")
        }

    private fun lookup(
        name: String,
        env: Env,
        span: SourceSpan,
        missing: String = "Unbound variable '$name'",
    ): Value {
        val addr = env.lookup(name) ?: throw KleinRuntimeError(missing, span)
        return store.get(addr, name, span)
    }

    private fun checkArity(
        expected: Int,
        actual: Int,
        span: SourceSpan,
    ) {
        if (expected != actual) {
            throw KleinRuntimeError("Expected $expected argument${if (expected == 1) "" else "s"}, got $actual", span)
        }
    }

    private fun accessField(
        target: Value,
        field: String,
        span: SourceSpan,
    ): Value {
        val fields =
            when (target) {
                is Value.VRecord -> target.fields
                is Value.VData -> target.fields
                Value.VNull -> throw KleinRuntimeError("Cannot access field '$field' on null", span)
                else -> throw KleinRuntimeError("Cannot access field '$field' on ${describe(target)}", span)
            }
        return fields[field]
            ?: throw KleinRuntimeError("No field '$field' on ${describe(target)}", span)
    }

    private fun nonZero(
        value: Double,
        span: SourceSpan,
    ): Double =
        if (value == 0.0) {
            throw KleinRuntimeError("Division by zero", span)
        } else {
            value
        }

    private fun asNum(
        value: Value,
        span: SourceSpan,
    ): Double =
        when (value) {
            is Value.VNum -> value.value
            else -> throw KleinRuntimeError("Expected a Num, got ${describe(value)}", span)
        }

    private fun asBool(
        value: Value,
        span: SourceSpan,
    ): Boolean =
        when (value) {
            is Value.VBool -> value.value
            else -> throw KleinRuntimeError("Expected a Bool, got ${describe(value)}", span)
        }

    private fun describe(value: Value): String =
        when (value) {
            is Value.VNum -> "the number ${Value.print(value)}"
            is Value.VStr -> "the string ${Value.print(value)}"
            is Value.VBool -> "the boolean ${Value.print(value)}"
            Value.VNull -> "null"
            Value.VUnit -> "the unit value"
            is Value.VRecord -> "a record"
            is Value.VData -> "a ${value.constructorName} value"
            is Value.VClosure -> "a function"
            is Value.VConstructor -> "the constructor ${value.name}"
            is Value.VNative -> "the native function ${value.name}"
        }
}
