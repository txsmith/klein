package klein.interp

import klein.Apply
import klein.BinaryOp
import klein.Expr
import klein.FieldAccess
import klein.IfThenElse
import klein.RecordLiteral
import klein.SafeFieldAccess
import klein.UnaryOp

/**
 * The K in CESK: a defunctionalized continuation. Each frame is one "what happens to the
 * next value" shape — there is exactly one per evaluation position in the grammar, which is
 * why the set is closed and the whole machine state is plain data. Frames reference the AST
 * node they came from so error spans need no separate bookkeeping.
 */
internal sealed class Frame

/** Evaluating the left operand; next: short-circuit or evaluate the right. */
internal class BinLeftK(
    val node: BinaryOp,
    val env: Env,
) : Frame()

/** Evaluating the right operand of an arithmetic/comparison/equality operator. */
internal class BinRightK(
    val node: BinaryOp,
    val left: Value,
) : Frame()

/** Evaluating the right operand of `and`/`or` after the left didn't short-circuit. */
internal class BoolRightK(
    val node: BinaryOp,
) : Frame()

internal class UnaryK(
    val node: UnaryOp,
) : Frame()

/** Evaluating the condition; branches are entered with no frame — `if` is tail-position. */
internal class IfK(
    val node: IfThenElse,
    val env: Env,
) : Frame()

/** Evaluating the callee. */
internal class CalleeK(
    val node: Apply,
    val env: Env,
) : Frame()

/** Evaluating argument `done.size`; when all are done, apply. */
internal class ArgsK(
    val node: Apply,
    val callee: Value,
    val done: List<Value>,
    val env: Env,
) : Frame()

internal class FieldK(
    val node: FieldAccess,
) : Frame()

internal class SafeFieldK(
    val node: SafeFieldAccess,
) : Frame()

/** Evaluating field `done.size`'s value. */
internal class RecordK(
    val node: RecordLiteral,
    val done: List<Pair<String, Value>>,
    val env: Env,
) : Frame()

/**
 * Executing a scope's items in order: `val` bindings (in SCC dependency order) fill their
 * pre-allocated store cell; expression statements update the scope's result value. Currently
 * executing `items[index]`.
 */
internal class ScopeK(
    val items: List<ScopeItem>,
    val index: Int,
    val env: Env,
    val last: Value,
) : Frame()

/** One unit of work in a scope: evaluate [expr], then bind to [bindAddr] (or keep as result if null). */
internal class ScopeItem(
    val expr: Expr,
    val bindAddr: Int?,
)

/** The continuation stack: an immutable cons list, so suspension snapshots are zero-copy. */
internal class Kont(
    val frame: Frame,
    val parent: Kont?,
)
