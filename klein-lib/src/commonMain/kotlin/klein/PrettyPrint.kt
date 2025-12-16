package klein

import klein.TokenKind.*

fun Token.prettyPrint(): String =
    when (kind) {
        INT, DOUBLE -> "Number($text)"
        IDENT -> "Ident($text)"
        STRING -> "String(\"$text\")"
        STMT_END -> "StatementEnd"
        BLOCK_START -> "BlockStart"
        BLOCK_END -> "BlockEnd"
        PIPE_OPEN -> "PipeOpen"
        PIPE_CLOSE -> "PipeClose"
        EOF -> "Eof"
        else -> if (kind.keyword != null) "Keyword($kind)" else "Symbol($kind)"
    }

fun Stmt.prettyPrint(indent: Int = 0): String {
    val pad = "  ".repeat(indent)
    return when (this) {
        is Val -> "${pad}Val($name) =\n${value.prettyPrint(indent + 1)}"
        is Expr -> prettyPrint(indent)
    }
}

fun Expr.prettyPrint(indent: Int = 0): String {
    val pad = "  ".repeat(indent)
    return when (this) {
        is IntLiteral -> "${pad}Int($value)"
        is DoubleLiteral -> "${pad}Double($value)"
        is StringLiteral -> "${pad}String(\"$value\")"
        is BoolLiteral -> "${pad}Bool($value)"
        is Ident -> "${pad}Ident($name)"
        is UnaryOp -> "${pad}$op\n${operand.prettyPrint(indent + 1)}"
        is BinaryOp -> "${pad}$op\n${left.prettyPrint(indent + 1)}\n${right.prettyPrint(indent + 1)}"
        is Lambda -> {
            val params = if (params.isEmpty()) "" else params.joinToString(", ")
            "${pad}Lambda($params)\n${body.prettyPrint(indent + 1)}"
        }
        is Apply -> {
            val argsStr = args.joinToString("\n") { it.prettyPrint(indent + 2) }
            "${pad}Apply\n${callee.prettyPrint(indent + 1)}\n$pad  args:\n$argsStr"
        }
        is Block -> {
            val stmtsStr = stmts.joinToString("\n") { it.prettyPrint(indent + 1) }
            "${pad}Block\n$stmtsStr\n${expr.prettyPrint(indent + 1)}"
        }
    }
}
