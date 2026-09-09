package klein.host.codec

import klein.CompilerVersion
import klein.KleinException
import klein.SourceSpan
import klein.core.Apply
import klein.core.Bind
import klein.core.Constant
import klein.core.CoreExpr
import klein.core.EnterScope
import klein.core.FieldGet
import klein.core.HostCall
import klein.core.Lambda
import klein.core.Literal
import klein.core.MakeData
import klein.core.Match
import klein.core.PrimApp
import klein.core.PrimOp
import klein.core.Run
import klein.core.ScopeStmt
import klein.core.Var
import klein.host.UnreadableEdition

private const val NODE_LITERAL = 0
private const val NODE_VAR = 1
private const val NODE_LAMBDA = 2
private const val NODE_APPLY = 3
private const val NODE_PRIM_APP = 4
private const val NODE_MAKE_DATA = 5
private const val NODE_FIELD_GET = 6
private const val NODE_HOST_CALL = 7
private const val NODE_ENTER_SCOPE = 8
private const val NODE_MATCH = 9

private const val STMT_BIND = 0
private const val STMT_RUN = 1

private const val ARM_DATA = 0
private const val ARM_LIT = 1
private const val ARM_DEFAULT = 2

private const val CONST_NUM = 0
private const val CONST_STR = 1
private const val CONST_BOOL = 2
private const val CONST_NULL = 3
private const val CONST_UNIT = 4

internal fun encodeCore(core: CoreExpr): ByteArray {
    val out = ByteWriter()
    out.writeByte(CompilerVersion.CURRENT.value)
    out.writeExpr(core)
    return out.toByteArray()
}

internal fun decodeCore(bytes: ByteArray): CoreExpr =
    try {
        readCore(bytes)
    } catch (malformed: MalformedBytes) {
        reject(malformed.message)
    }

internal fun readCoreVersion(bytes: ByteArray): CompilerVersion {
    if (bytes.isEmpty()) reject("the Core blob is empty")
    return CompilerVersion(bytes[0].toInt() and 0xFF)
}

private fun readCore(bytes: ByteArray): CoreExpr {
    val version = readCoreVersion(bytes)
    if (version != CompilerVersion.CURRENT) {
        reject("the Core was produced by lowerer version $version; this library reads lowerer version ${CompilerVersion.CURRENT}")
    }
    val input = ByteReader(bytes)
    input.readByte()
    val core = input.readExpr()
    if (!input.isExhausted) reject("${input.remaining} unexpected trailing bytes after the Core")
    return core
}

private fun reject(message: String): Nothing = throw KleinException(listOf(UnreadableEdition(message)))

private fun ByteWriter.writeExpr(expr: CoreExpr) {
    when (expr) {
        is Literal -> {
            writeByte(NODE_LITERAL)
            writeConstant(expr.value)
        }
        is Var -> {
            writeByte(NODE_VAR)
            writeInt(expr.depth)
            writeInt(expr.slot)
            writeString(expr.name)
        }
        is Lambda -> {
            writeByte(NODE_LAMBDA)
            writeInt(expr.arity)
            writeExpr(expr.body)
            writeOptionalString(expr.name)
        }
        is Apply -> {
            writeByte(NODE_APPLY)
            writeExpr(expr.callee)
            writeExprs(expr.args)
        }
        is PrimApp -> {
            writeByte(NODE_PRIM_APP)
            writeByte(expr.prim.ordinal)
            writeExprs(expr.args)
        }
        is MakeData -> {
            writeByte(NODE_MAKE_DATA)
            writeOptionalString(expr.tag)
            writeStrings(expr.fieldNames)
            writeExprs(expr.args)
        }
        is FieldGet -> {
            writeByte(NODE_FIELD_GET)
            writeExpr(expr.target)
            writeString(expr.field)
        }
        is HostCall -> {
            writeByte(NODE_HOST_CALL)
            writeString(expr.name)
            writeExprs(expr.args)
        }
        is EnterScope -> {
            writeByte(NODE_ENTER_SCOPE)
            writeInt(expr.stmts.size)
            expr.stmts.forEach { writeStmt(it) }
            writeExpr(expr.result)
        }
        is Match -> {
            writeByte(NODE_MATCH)
            writeExpr(expr.scrutinee)
            writeInt(expr.arms.size)
            expr.arms.forEach { writeArm(it) }
        }
    }
    writeSpan(expr.span)
}

private fun ByteReader.readExpr(): CoreExpr =
    when (val tag = readByte()) {
        NODE_LITERAL -> Literal(readConstant(), readSpan())
        NODE_VAR -> Var(readInt(), readInt(), readString(), readSpan())
        NODE_LAMBDA -> Lambda(readInt(), readExpr(), readOptionalString(), readSpan())
        NODE_APPLY -> Apply(readExpr(), readExprs(), readSpan())
        NODE_PRIM_APP -> PrimApp(readPrim(), readExprs(), readSpan())
        NODE_MAKE_DATA -> {
            val dataTag = readOptionalString()
            val fieldNames = readStrings()
            val args = readExprs()
            if (fieldNames.size != args.size) reject("a data node names ${fieldNames.size} fields but carries ${args.size} arguments")
            MakeData(dataTag, fieldNames, args, readSpan())
        }
        NODE_FIELD_GET -> FieldGet(readExpr(), readString(), readSpan())
        NODE_HOST_CALL -> HostCall(readString(), readExprs(), readSpan())
        NODE_ENTER_SCOPE -> EnterScope(List(readCount()) { readStmt() }, readExpr(), readSpan())
        NODE_MATCH -> Match(readExpr(), List(readCount()) { readArm() }, readSpan())
        else -> reject("unknown Core node tag $tag")
    }

private fun ByteWriter.writeStmt(stmt: ScopeStmt) {
    when (stmt) {
        is Bind -> {
            writeByte(STMT_BIND)
            writeInt(stmt.slotIdx)
            writeString(stmt.name)
            writeExpr(stmt.body)
        }
        is Run -> {
            writeByte(STMT_RUN)
            writeExpr(stmt.body)
        }
    }
    writeSpan(stmt.span)
}

private fun ByteReader.readStmt(): ScopeStmt =
    when (val tag = readByte()) {
        STMT_BIND -> Bind(readInt(), readString(), readExpr(), readSpan())
        STMT_RUN -> Run(readExpr(), readSpan())
        else -> reject("unknown Core statement tag $tag")
    }

private fun ByteWriter.writeArm(arm: Match.Arm) {
    when (arm) {
        is Match.DataArm -> {
            writeByte(ARM_DATA)
            writeOptionalString(arm.tag)
            writeStrings(arm.fields)
        }
        is Match.LitArm -> {
            writeByte(ARM_LIT)
            writeConstant(arm.lit)
        }
        is Match.Default -> writeByte(ARM_DEFAULT)
    }
    writeOptionalExpr(arm.guard)
    writeExpr(arm.body)
    writeSpan(arm.span)
}

private fun ByteReader.readArm(): Match.Arm =
    when (val tag = readByte()) {
        ARM_DATA -> Match.DataArm(readOptionalString(), readStrings(), readOptionalExpr(), readExpr(), readSpan())
        ARM_LIT -> Match.LitArm(readConstant(), readOptionalExpr(), readExpr(), readSpan())
        ARM_DEFAULT -> Match.Default(readOptionalExpr(), readExpr(), readSpan())
        else -> reject("unknown Core arm tag $tag")
    }

private fun ByteWriter.writeConstant(constant: Constant) {
    when (constant) {
        is Constant.CNum -> {
            writeByte(CONST_NUM)
            writeLong(constant.value.toRawBits())
        }
        is Constant.CStr -> {
            writeByte(CONST_STR)
            writeString(constant.value)
        }
        is Constant.CBool -> {
            writeByte(CONST_BOOL)
            writeBoolean(constant.value)
        }
        Constant.CNull -> writeByte(CONST_NULL)
        Constant.CUnit -> writeByte(CONST_UNIT)
    }
}

private fun ByteReader.readConstant(): Constant =
    when (val tag = readByte()) {
        CONST_NUM -> Constant.CNum(Double.fromBits(readLong()))
        CONST_STR -> Constant.CStr(readString())
        CONST_BOOL -> Constant.CBool(readBoolean())
        CONST_NULL -> Constant.CNull
        CONST_UNIT -> Constant.CUnit
        else -> reject("unknown Core constant tag $tag")
    }

private fun ByteReader.readPrim(): PrimOp {
    val ordinal = readByte()
    return PrimOp.entries.getOrNull(ordinal) ?: reject("unknown primitive operation $ordinal")
}

private fun ByteWriter.writeSpan(span: SourceSpan) {
    writeInt(span.start)
    writeInt(span.end)
}

private fun ByteReader.readSpan(): SourceSpan = SourceSpan(readInt(), readInt())

private fun ByteWriter.writeExprs(exprs: List<CoreExpr>) {
    writeInt(exprs.size)
    exprs.forEach { writeExpr(it) }
}

private fun ByteReader.readExprs(): List<CoreExpr> = List(readCount()) { readExpr() }

private fun ByteWriter.writeStrings(strings: List<String>) {
    writeInt(strings.size)
    strings.forEach { writeString(it) }
}

private fun ByteReader.readStrings(): List<String> = List(readCount()) { readString() }

private fun ByteWriter.writeOptionalString(value: String?) {
    writeBoolean(value != null)
    if (value != null) writeString(value)
}

private fun ByteReader.readOptionalString(): String? = if (readBoolean()) readString() else null

private fun ByteWriter.writeOptionalExpr(expr: CoreExpr?) {
    writeBoolean(expr != null)
    if (expr != null) writeExpr(expr)
}

private fun ByteReader.readOptionalExpr(): CoreExpr? = if (readBoolean()) readExpr() else null
