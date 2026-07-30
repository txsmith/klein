package klein.interp

import klein.SourceSpan
import klein.core.*
import kotlin.collections.ArrayList

internal class MachineState internal constructor(
    val store: Store,
    private var control: Stack<Frame>,
    private var operands: Stack<Value>,
) {
    internal constructor(program: CoreExpr) :
        this(Store(), Stack.single(Frame(program, operandBase = 0, scope = BindingScope())), Stack.Empty)

    fun snapshot(): MachineState = MachineState(store.copy(), control, operands)

    fun controlDepth(): Int = control.size

    fun operandDepth(): Int = operands.size

    fun storeSize(): Int = store.size

    fun currentFrame(): Frame? = (control as? Stack.Cons)?.head

    fun collected(frame: Frame): Int = operands.size - frame.operandBase

    fun peekOperand(depth: Int = 0): Value = operands.peek(depth)

    fun pushControl(
        item: Control,
        scope: BindingScope,
    ): Frame {
        val frame = Frame(item, operandBase = operands.size, scope)
        control = control.push(frame)
        return frame
    }

    fun popControl() {
        control = control.pop()
    }

    fun pushOperand(value: Value) {
        operands = operands.push(value)
    }

    fun popOperand(): Value {
        val r = operands.peek()
        operands = operands.pop()
        return r
    }

    fun sweepOperands(count: Int) {
        operands = operands.pop(count)
    }

    fun popOperandsToList(count: Int): List<Value> {
        val r = operands.takeToList(count)
        operands = operands.pop(count)
        return r
    }

    fun finalValue(): Value {
        invariant(operands.size == 1) {
            "machine finished with ${operands.size} operands, expected exactly 1"
        }
        return operands.peek()
    }
}

class Machine private constructor(
    private val state: MachineState,
) {
    companion object {
        fun start(program: CoreExpr): Execution = Machine(MachineState(program)).run()
    }

    internal fun cloneSuspended(): Machine = Machine(state.snapshot())

    internal fun controlDepth(): Int = state.controlDepth()

    internal fun operandDepth(): Int = state.operandDepth()

    internal fun storeSize(): Int = state.storeSize()

    internal fun resume(value: Value): Execution {
        state.pushOperand(value)
        return run()
    }

    internal fun run(): Execution {
        while (true) {
            val frame = state.currentFrame() ?: break

            when (val expr = frame.control) {
                is Literal -> {
                    state.pushOperand(constantValue(expr.value))
                    state.popControl()
                }
                is Var -> {
                    val addr = frame.scope.lookupAddr(expr.depth, expr.slot, expr.name, expr.span)
                    state.pushOperand(state.store.get(addr, expr.name, expr.span))
                    state.popControl()
                }
                is Apply -> stepApply(expr, frame)
                is Lambda -> stepLambda(expr, frame)
                is HostCall -> stepHostCall(expr, frame)?.let { return it }
                is PrimApp -> stepPrim(expr, frame)
                is MakeData -> stepMakeData(expr, frame)
                is FieldGet -> stepFieldGet(expr, frame)
                is EnterScope -> stepScope(expr, frame)
                is Match -> stepMatch(expr, frame)
                is Match.Arm -> stepArm(expr, frame)
            }
        }
        return Execution.Done(state.finalValue())
    }

    private fun stepScope(
        expr: EnterScope,
        frame: Frame,
    ) {
        val collected = state.collected(frame)

        // Phase 1: prepare a freshly entered scope, update the environment, allocate store slots
        val current =
            if (collected == 0) {
                val slots = IntArray(expr.bindingCount) { state.store.alloc() }
                val newScope = BindingScope(slots, frame.scope)
                state.popControl()
                state.pushControl(expr, newScope)
            } else {
                frame
            }

        // Phase 2: check if the last run gave us a value for a binding, store it
        val lastBinding = expr.stmts.getOrNull(collected - 1) as? Bind
        lastBinding?.let { bind ->
            val addr = current.scope.slots[bind.slotIdx]
            state.store.set(addr, state.peekOperand())
        }

        // Phase 3: advance to the next statement
        if (collected < expr.stmts.size) {
            state.pushControl(expr.stmts[collected].body, current.scope)
            return
        }
        state.sweepOperands(expr.stmts.size)

        // The final result expression is pushed as a tail call
        state.popControl()
        state.pushControl(expr.result, current.scope)
    }

    private fun stepApply(
        expr: Apply,
        frame: Frame,
    ) {
        if (collectOperands(expr, frame)) {
            val args = state.popOperandsToList(expr.arity)
            val callee = state.popOperand()

            invariant(callee is Value.VClos, expr.span) { "Ill typed term: $callee isn't valid in application position" }
            invariant(callee.arity == expr.arity, expr.span) {
                "Ill typed term: closure has ${callee.arity} parameters, application provides ${expr.arity} arguments"
            }

            val fnBodyEnv = BindingScope(IntArray(expr.arity) { state.store.alloc(args[it]) }, parent = callee.scope)
            state.popControl()
            state.pushControl(callee.body, fnBodyEnv)
        }
    }

    private fun stepLambda(
        expr: Lambda,
        frame: Frame,
    ) {
        val closure = Value.VClos(expr.arity, expr.body, frame.scope)
        state.pushOperand(closure)
        state.popControl()
    }

    private fun stepHostCall(
        expr: HostCall,
        frame: Frame,
    ): Execution.AwaitingHost? {
        if (collectOperands(expr, frame)) {
            val args = state.popOperandsToList(expr.operands.size)
            state.popControl()
            return Execution.AwaitingHost(expr.name, args, expr.span, this)
        }
        return null
    }

    private fun stepPrim(
        expr: PrimApp,
        frame: Frame,
    ) {
        if (collectOperands(expr, frame)) {
            val args = state.popOperandsToList(expr.args.size)
            state.pushOperand(applyPrim(expr, args))
            state.popControl()
        }
    }

    private fun applyPrim(
        expr: PrimApp,
        args: List<Value>,
    ): Value =
        when (expr.prim) {
            PrimOp.Add -> Value.VNum(asNum(args[0], expr) + asNum(args[1], expr))
            PrimOp.Sub -> Value.VNum(asNum(args[0], expr) - asNum(args[1], expr))
            PrimOp.Mul -> Value.VNum(asNum(args[0], expr) * asNum(args[1], expr))
            PrimOp.Div -> Value.VNum(asNum(args[0], expr) / nonZero(asNum(args[1], expr), expr))
            PrimOp.Mod -> Value.VNum(asNum(args[0], expr) % nonZero(asNum(args[1], expr), expr))
            PrimOp.Neg -> Value.VNum(-asNum(args[0], expr))
            PrimOp.Lt -> Value.VBool(asNum(args[0], expr) < asNum(args[1], expr))
            PrimOp.LtEq -> Value.VBool(asNum(args[0], expr) <= asNum(args[1], expr))
            PrimOp.Gt -> Value.VBool(asNum(args[0], expr) > asNum(args[1], expr))
            PrimOp.GtEq -> Value.VBool(asNum(args[0], expr) >= asNum(args[1], expr))
            PrimOp.Eq -> Value.VBool(args[0] == args[1])
            PrimOp.NotEq -> Value.VBool(args[0] != args[1])
            PrimOp.Not -> Value.VBool(!asBool(args[0], expr))
        }

    private fun asNum(
        value: Value,
        expr: PrimApp,
    ): Double {
        invariant(value is Value.VNum, expr.span) { "Ill typed term: ${expr.prim} expects a Num, got $value" }
        return value.value
    }

    private fun asBool(
        value: Value,
        expr: PrimApp,
    ): Boolean {
        invariant(value is Value.VBool, expr.span) { "Ill typed term: ${expr.prim} expects a Bool, got $value" }
        return value.value
    }

    private fun nonZero(
        value: Double,
        expr: PrimApp,
    ): Double {
        if (value == 0.0) throw KleinRuntimeError("Division by zero", expr.span)
        return value
    }

    private fun stepMakeData(
        expr: MakeData,
        frame: Frame,
    ) {
        if (collectOperands(expr, frame)) {
            val args = state.popOperandsToList(expr.args.size)
            state.pushOperand(Value.VStruct(expr.tag, expr.fieldNames.zip(args).toMap()))
            state.popControl()
        }
    }

    private fun stepFieldGet(
        expr: FieldGet,
        frame: Frame,
    ) {
        if (collectOperands(expr, frame)) {
            val target = state.popOperand()
            invariant(target is Value.VStruct, expr.span) { "Ill typed term: field access on $target" }
            val value = target.fields[expr.field]
            invariant(value != null, expr.span) { "Ill typed term: $target has no field '${expr.field}'" }
            state.pushOperand(value)
            state.popControl()
        }
    }

    private fun stepMatch(
        expr: Match,
        frame: Frame,
    ) {
        val collected = state.collected(frame)
        if (collected == 0) {
            state.pushControl(expr.scrutinee, frame.scope)
            return
        }
        val scrutinee = state.peekOperand(depth = collected - 1)
        var failedGuards = collected - 1
        for (arm in expr.arms) {
            if (!matches(arm, scrutinee)) continue
            if (failedGuards > 0) {
                failedGuards--
                continue
            }
            state.pushControl(arm, armScope(arm, scrutinee, frame.scope))
            return
        }
        throw InvariantViolation("no arm matched ${Value.print(scrutinee)}", expr.span)
    }

    private fun stepArm(
        arm: Match.Arm,
        frame: Frame,
    ) {
        val guard = arm.guard
        if (guard != null) {
            if (state.collected(frame) == 0) {
                state.pushControl(guard, frame.scope)
                return
            }
            val result = state.peekOperand()
            invariant(result is Value.VBool, arm.span) { "Ill typed term: match guard produced ${Value.print(result)}" }
            if (!result.value) {
                state.popControl()
                return
            }
            state.popOperand()
        }
        state.popControl()
        val match = state.currentFrame()
        invariant(match != null && match.control is Match, arm.span) { "arm frame without a Match frame beneath it" }
        state.sweepOperands(state.collected(match))
        state.popControl()
        state.pushControl(arm.body, frame.scope)
    }

    private fun matches(
        arm: Match.Arm,
        scrutinee: Value,
    ): Boolean =
        when (arm) {
            is Match.Default -> true
            is Match.LitArm -> constantValue(arm.lit) == scrutinee
            is Match.DataArm -> scrutinee is Value.VStruct && (arm.tag == null || scrutinee.tag == arm.tag)
        }

    private fun armScope(
        arm: Match.Arm,
        scrutinee: Value,
        parent: BindingScope,
    ): BindingScope =
        when (arm) {
            is Match.DataArm -> {
                invariant(scrutinee is Value.VStruct, arm.span) { "Ill typed term: ${arm.tag ?: "record"} pattern on ${Value.print(scrutinee)}" }
                BindingScope(
                    IntArray(arm.fields.size) {
                        val value = scrutinee.fields[arm.fields[it]]
                        invariant(value != null, arm.span) { "Ill typed term: scrutinee has no field '${arm.fields[it]}'" }
                        state.store.alloc(value)
                    },
                    parent,
                )
            }
            else -> BindingScope(parent = parent)
        }

    private fun collectOperands(
        expr: CoreExpr,
        frame: Frame,
    ): Boolean {
        if (expr !is HasOperands) return true
        val collected = state.collected(frame)
        if (collected == expr.operands.size) return true
        val next = expr.operands[collected]
        state.pushControl(next, frame.scope)
        return false
    }

    private fun constantValue(c: Constant): Value =
        when (c) {
            is Constant.CNull -> Value.VNull
            is Constant.CUnit -> Value.VUnit
            is Constant.CNum -> Value.VNum(c.value)
            is Constant.CStr -> Value.VStr(c.value)
            is Constant.CBool -> Value.VBool(c.value)
        }
}

sealed class Execution {
    data class Done(
        val value: Value,
    ) : Execution()

    class AwaitingHost internal constructor(
        val call: String,
        val args: List<Value>,
        val span: SourceSpan,
        internal val machine: Machine,
    ) : Execution() {
        private var consumed = false

        fun resume(value: Value): Execution {
            check(!consumed) { "this suspension was already resumed" }
            consumed = true
            return machine.resume(value)
        }

        fun clone(): AwaitingHost {
            check(!consumed) { "this suspension was already resumed" }
            return AwaitingHost(call, args, span, machine.cloneSuspended())
        }
    }
}

data class Frame(
    val control: Control,
    val operandBase: Int = 0,
    val scope: BindingScope = BindingScope(),
)

sealed class Stack<out T> {
    abstract val size: Int

    data object Empty : Stack<Nothing>() {
        override val size: Int = 0
    }

    class Cons<out T>(
        val head: T,
        val tail: Stack<T> = Empty,
    ) : Stack<T>() {
        override val size: Int = tail.size + 1

        operator fun component1(): T = head

        operator fun component2(): Stack<T> = tail
    }

    companion object {
        fun <T> single(value: T): Cons<T> = Cons(value, Empty)
    }

    fun isEmpty(): Boolean = this is Empty

    fun nonEmpty(): Boolean = !isEmpty()

    fun peek(depth: Int = 0): T {
        var cur = this
        repeat(depth) { cur = cur.asCons().tail }
        return cur.asCons().head
    }

    fun asCons(span: SourceSpan? = null): Cons<T> =
        this as? Cons
            ?: throw InvariantViolation("expected something on the stack", span)

    fun take(count: Int = 1): Stack<T> =
        when {
            count == 0 -> Empty
            this is Cons -> Cons(head, tail.take(count - 1))
            else -> throw InvariantViolation("take went past the bottom of the stack")
        }

    fun takeToList(count: Int): List<T> {
        var cur = this
        val list = ArrayList<T>(count)
        repeat(count) {
            val c = cur as? Cons ?: throw InvariantViolation("take went past the bottom of the stack")
            list.add(c.head)
            cur = c.tail
        }
        return list.asReversed()
    }

    fun pop(count: Int = 1): Stack<T> =
        when {
            count == 0 -> this
            this is Cons -> tail.pop(count - 1)
            else -> throw InvariantViolation("popped past the bottom of the stack")
        }

    fun <R> map(fn: (T) -> R): Stack<R> =
        when (this) {
            is Empty -> Empty
            is Cons -> Cons(fn(head), tail.map(fn))
        }
}

class BindingScope(
    val slots: IntArray = IntArray(0),
    val parent: BindingScope? = null,
) {
    fun lookupAddr(
        depth: Int,
        slot: Int,
        name: String,
        span: SourceSpan,
    ): StoreAddr =
        when {
            depth == 0 && slot !in slots.indices ->
                throw InvariantViolation("slot $slot out of range in scope of size ${slots.size} ('$name')", span)
            depth == 0 -> slots[slot]
            else ->
                parent?.lookupAddr(depth - 1, slot, name, span)
                    ?: throw InvariantViolation("no scope at depth $depth ('$name')", span)
        }
}

fun <T> Stack<T>.push(value: T): Stack.Cons<T> = Stack.Cons(value, this)
