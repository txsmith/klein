package klein

import klein.TokenKind.*

fun Token.prettyPrint(): String {
    val base =
        when (kind) {
            INT, DOUBLE -> "Number($text)"
            IDENT -> "Ident($text)"
            STRING -> "String(\"$text\")"
            PIPE -> "Pipe"
            EOF -> "Eof"
            else -> if (kind.keyword != null) "Keyword($kind)" else "Symbol($kind)"
        }
    return if (indent != null) "$base @$indent" else base
}

fun Stmt.prettyPrint(indent: Int = 0): String {
    val pad = "  ".repeat(indent)
    return when (this) {
        is Val -> "${pad}Val($name) =\n${value.prettyPrint(indent + 1)}"
        is FunDef -> {
            val paramsStr = params.joinToString(", ")
            "${pad}Fun $name($paramsStr) =\n${body.prettyPrint(indent + 1)}"
        }
        is TypeDef -> {
            val typeParamsStr = if (typeParams.isNotEmpty()) "<${typeParams.joinToString(", ") { "'$it" }}>" else ""
            val constructorsStr =
                constructors.joinToString(" | ") { c ->
                    if (c.fields.isEmpty()) {
                        c.name
                    } else {
                        "${c.name} { ${c.fields.joinToString(", ") { f -> "${f.name}: ${f.type.prettyPrint()}" }} }"
                    }
                }
            "${pad}type $name$typeParamsStr = $constructorsStr"
        }
        is Expr -> prettyPrint(indent)
    }
}

fun TypeExpr.prettyPrint(): String =
    when (this) {
        is TypeName -> name
        is TypeVar -> "'$name"
        is AppliedTypeExpr -> "$name<${args.joinToString(", ") { it.prettyPrint() }}>"
        is FunctionTypeExpr -> "(${paramType.prettyPrint()} -> ${returnType.prettyPrint()})"
        is TupleTypeExpr -> "(${elements.joinToString(", ") { it.prettyPrint() }})"
        is RecordTypeExpr -> "{ ${fields.joinToString(", ") { (n, t) -> "$n: ${t.prettyPrint()}" }} }"
    }

fun Expr.prettyPrint(indent: Int = 0): String {
    val pad = "  ".repeat(indent)
    return when (this) {
        is IntLiteral -> "${pad}Int($value)"
        is DoubleLiteral -> "${pad}Double($value)"
        is StringLiteral -> "${pad}String(\"$value\")"
        is BoolLiteral -> "${pad}Bool($value)"
        is NullLiteral -> "${pad}Null"
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
            "${pad}Block\n$stmtsStr"
        }
        is IfThenElse -> {
            val elseStr = if (elseBranch != null) "\n${pad}Else\n${elseBranch.prettyPrint(indent + 1)}" else ""
            "${pad}If\n${condition.prettyPrint(indent + 1)}\n${pad}Then\n${thenBranch.prettyPrint(indent + 1)}$elseStr"
        }
        is FieldAccess -> "${pad}FieldAccess($field)\n${target.prettyPrint(indent + 1)}"
        is SafeFieldAccess -> "${pad}SafeFieldAccess($field)\n${target.prettyPrint(indent + 1)}"
        is ImplicitParam -> "${pad}ImplicitParam"
        is RecordLiteral -> {
            val fieldsStr =
                fields.joinToString("\n") { (name, value) ->
                    "$pad  $name:\n${value.prettyPrint(indent + 2)}"
                }
            "${pad}Record\n$fieldsStr"
        }
    }
}
