package klein.types

import klein.SourceSpan
import klein.Type
import klein.types.SimpleType.*
import klein.types.TypeSimplifier.simplifyCanonical

sealed class ConstraintContext {
    open fun render(env: TypeEnv): String = toString()

    data class FunctionCall(
        val funDef: FunDefInfo?,
    ) : ConstraintContext()

    data class Argument(
        val paramIndex: Int,
        val expected: SimpleType,
        val actual: SimpleType,
        val pos: Boolean,
    ) : ConstraintContext() {
        override fun render(env: TypeEnv): String {
            val exp = Type.print(simplifyCanonical(expected, env, positive = !pos))
            val act = Type.print(simplifyCanonical(actual, env, positive = pos))
            return "Argument(paramIndex=$paramIndex, expected=$exp, actual=$act)"
        }
    }

    data class ConstructorToParent(
        val constructorName: String,
        val parentName: String,
    ) : ConstraintContext()

    data class VarianceCheck(
        val typeName: String,
        val typeParams: List<String>,
        val paramName: String,
        val variance: Variance,
    ) : ConstraintContext()
}

sealed class TypeError {
    abstract val span: SourceSpan
    abstract val message: String
    open val context: List<ConstraintContext> = emptyList()

    open fun renderMessage(env: TypeEnv): String = message

    open fun render(env: TypeEnv): String {
        val msg = renderMessage(env)
        if (context.isEmpty()) return msg
        return (listOf(msg) + renderContext(env)).joinToString("\n")
    }

    protected fun renderContext(env: TypeEnv): List<String> {
        val notes = mutableListOf<String>()
        for ((i, ctx) in context.withIndex()) {
            when (ctx) {
                is ConstraintContext.FunctionCall -> {
                    val argument = context.getOrNull(i + 1) as? ConstraintContext.Argument
                    val argIndex = argument?.paramIndex
                    if (ctx.funDef != null && argIndex != null) {
                        notes.add("In argument '${ctx.funDef.paramNames[argument.paramIndex]}' of ${ctx.funDef.name}")
                    }
                }
                is ConstraintContext.Argument -> {
                    val expected = Type.print(simplifyCanonical(ctx.expected, env, positive = !ctx.pos))
                    val actual = Type.print(simplifyCanonical(ctx.actual, env, positive = ctx.pos))
                    notes.add("└> Requires $expected, got $actual")
                }
                is ConstraintContext.ConstructorToParent -> {
                    notes.add("└> Note: ${ctx.constructorName} is a constructor of ${ctx.parentName}")
                }
                is ConstraintContext.VarianceCheck -> {
                    val desc = when (ctx.variance) {
                        Variance.Invariant -> "invariant"
                        Variance.Covariant -> "covariant"
                        Variance.Contravariant -> "contravariant"
                        Variance.Bivariant -> "bivariant"
                    }
                    val typeDisplay = ctx.typeName + if (ctx.typeParams.isNotEmpty()) {
                        "<${ctx.typeParams.joinToString(", ") { "'$it" }}>"
                    } else ""
                    notes.add("└> Note: $typeDisplay is $desc in '${ctx.paramName}")
                }
            }
        }
        return notes
    }

    data class UnboundVariable(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Unbound variable: $name"
    }

    data class TypeMismatch(
        val expected: SimpleType,
        val actual: SimpleType,
        override val span: SourceSpan,
        override val context: List<ConstraintContext> = emptyList(),
    ) : TypeError() {
        override val message = "Type mismatch"

        private fun printExpected(env: TypeEnv) = Type.print(simplifyCanonical(expected, env, positive = false))
        private fun printActual(env: TypeEnv) = Type.print(simplifyCanonical(actual, env, positive = true))

        override fun renderMessage(env: TypeEnv) =
            "Type mismatch: expected ${printExpected(env)}, got ${printActual(env)}"

        override fun render(env: TypeEnv): String {
            val msg = renderMessage(env)
            if (context.isEmpty()) return msg
            val notes = renderContext(env).toMutableList()
            val lastCtx = context.lastOrNull()
            if (lastCtx is ConstraintContext.VarianceCheck && lastCtx.variance == Variance.Invariant) {
                notes.add("\u2514> Therefore: ${printActual(env)} must equal ${printExpected(env)}, which it doesn't")
            }
            return (listOf(msg) + notes).joinToString("\n")
        }
    }

    data class MissingField(
        val field: String,
        val recordType: Type,
        override val span: SourceSpan,
        override val context: List<ConstraintContext> = emptyList(),
    ) : TypeError() {
        override val message = "${Type.print(recordType)} has no field '$field'"
    }

    data class DuplicateField(
        val field: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Duplicate field '$field' in record"
    }

    data class DuplicateParameter(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Duplicate parameter '$name'"
    }

    data class DuplicateBinding(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "'$name' is already defined"
    }

    data class ArityMismatch(
        val expected: Int,
        val actual: Int,
        override val span: SourceSpan,
        override val context: List<ConstraintContext> = emptyList(),
    ) : TypeError() {
        override val message = "Expected $expected arguments, got $actual"
    }

    data class ImplicitParamOutsideLambda(
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Implicit dot parameter '.' can only be used inside anonymous functions"
    }

    data class ImplicitParamInNamedFunction(
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Implicit dot parameter '.' cannot be used in named functions"
    }

    data class ImplicitParamWithExplicitParams(
        val params: List<String>,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message: String
            get() {
                val paramList = params.joinToString(", ") { "'$it'" }
                return "Implicit dot parameter '.' cannot be used here, you've declared named ones: $paramList"
            }
    }

    data class NullNotAllowed(
        val expected: Type,
        override val span: SourceSpan,
        override val context: List<ConstraintContext> = emptyList(),
    ) : TypeError() {
        override val message = "Null is not allowed here: expected ${Type.print(expected)}"
    }

    data class UndeclaredTypeParam(
        val name: String,
        val typeName: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Type parameter '$name' is not declared on type '$typeName'"
    }
}
