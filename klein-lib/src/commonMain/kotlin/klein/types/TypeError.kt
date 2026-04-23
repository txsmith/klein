package klein.types

import klein.SourceSpan
import klein.Type
import klein.types.TypeSimplifier.simplifyCanonical

sealed class ConstraintContext {
    open fun render(env: TypeEnv): String = toString()

    data class FunctionCall(
        val funDef: FunDefInfo?,
        val argSpans: List<SourceSpan> = emptyList(),
    ) : ConstraintContext()

    data class Argument(
        val paramIndex: Int,
        val paramName: String?,
        val subtype: SimpleType,
        val supertype: SimpleType,
    ) : ConstraintContext() {
        override fun render(env: TypeEnv): String {
            val sub = Type.print(simplifyCanonical(subtype, env, pol = true, keepVars = true))
            val sup = Type.print(simplifyCanonical(supertype, env, pol = false, keepVars = true))
            val name = if (paramName != null) ", paramName=$paramName" else ""
            return "Argument(paramIndex=$paramIndex$name, subtype=$sub, supertype=$sup)"
        }
    }

    data class FunctionResult(
        val subtype: SimpleType,
        val supertype: SimpleType,
    ) : ConstraintContext() {
        override fun render(env: TypeEnv): String {
            val sub = Type.print(simplifyCanonical(subtype, env, pol = true, keepVars = true))
            val sup = Type.print(simplifyCanonical(supertype, env, pol = false, keepVars = true))
            return "FunctionResult(subtype=$sub, supertype=$sup)"
        }
    }

    data class ConstructorToParent(
        val constructorName: String,
        val parentName: String,
    ) : ConstraintContext()

    data class NominalToStructural(
        val typeName: String,
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

    data class UnboundVariable(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Unbound variable: $name"
    }

    data class UnboundTypeVar(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Unbound type variable: '$name"
    }

    data class TypeMismatch(
        val subtype: Type,
        val supertype: Type,
        override val span: SourceSpan,
        override val context: List<ConstraintContext> = emptyList(),
    ) : TypeError() {
        override val message = "Type mismatch"

        private val printedSubtype get() = Type.print(subtype)
        private val printedSupertype get() = Type.print(supertype)

        override fun renderMessage(env: TypeEnv) = "Type mismatch: '$printedSubtype' cannot be used as '$printedSupertype'"

        override fun render(env: TypeEnv): String {
            val msg = renderMessage(env)
            if (context.isEmpty()) return msg
            val notes = renderContext(env).toMutableList()
            val lastCtx = context.lastOrNull()
            if (lastCtx is ConstraintContext.VarianceCheck) {
                when (lastCtx.variance) {
                    Variance.Invariant, Variance.Bivariant ->
                        notes.add("└> Meaning '$printedSubtype' must equal '$printedSupertype', which it isn't")
                    Variance.Covariant, Variance.Contravariant ->
                        notes.add("└> Meaning '$printedSubtype' must be usable as '$printedSupertype', which it isn't")
                }
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
        override val message = "Type error: ${Type.print(recordType)} has no field '$field'"

        override fun equals(other: Any?) = other is MissingField && field == other.field && span == other.span

        override fun hashCode() = 31 * field.hashCode() + span.hashCode()
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

    data class RecursiveVal(
        val name: String,
        val cycle: List<String>,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Recursive value '$name' cannot be defined in terms of itself: ${cycle.joinToString(" -> ")}"
    }

    data class CallArityMismatch(
        val expected: Int,
        val actual: Int,
        override val span: SourceSpan,
        override val context: List<ConstraintContext> = emptyList(),
    ) : TypeError() {
        override val message = "Expected $expected argument(s), got $actual"
    }

    data class TypeArityMismatch(
        val typeName: String,
        val expected: Int,
        val actual: Int,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Type '$typeName' expects $expected type parameter(s), got $actual"
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

    data class ShadowsBuiltinType(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "'$name' shadows a builtin type"
    }

    data class UnsupportedAnnotation(
        val description: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Unsupported annotation: $description"
    }

    open fun render(env: TypeEnv): String {
        val msg = renderMessage(env)
        if (context.isEmpty()) return msg
        return (listOf(msg) + renderContext(env)).joinToString("\n")
    }

    protected fun renderContext(env: TypeEnv): List<String> {
        val notes = mutableListOf<String>()
        for (ctx in context) {
            when (ctx) {
                is ConstraintContext.FunctionCall -> {
                    val funName = ctx.funDef?.name
                    if (funName != null) {
                        notes.add("When calling function '$funName'")
                    }
                }
                is ConstraintContext.Argument -> {
                    val subtype = Type.print(simplifyCanonical(ctx.subtype, env, pol = true, keepVars = true))
                    val supertype = Type.print(simplifyCanonical(ctx.supertype, env, pol = false, keepVars = true))
                    notes.add("└> $subtype cannot be passed into parameter '${ctx.paramName}: $supertype'")
                }
                is ConstraintContext.FunctionResult -> {
                    val subtype = Type.print(simplifyCanonical(ctx.subtype, env, pol = true, keepVars = true))
                    val supertype = Type.print(simplifyCanonical(ctx.supertype, env, pol = false, keepVars = true))
                    notes.add("└> Function returns '$subtype', but '$supertype' is required")
                }
                is ConstraintContext.ConstructorToParent -> {
                    notes.add("└> Note: ${ctx.constructorName} is a constructor of type '${ctx.parentName}'")
                }
                is ConstraintContext.VarianceCheck -> {
                    val desc =
                        when (ctx.variance) {
                            Variance.Invariant -> "invariant"
                            Variance.Covariant -> "covariant"
                            Variance.Contravariant -> "contravariant"
                            Variance.Bivariant -> "bivariant"
                        }
                    val typeDisplay =
                        ctx.typeName +
                            if (ctx.typeParams.isNotEmpty()) {
                                "<${ctx.typeParams.joinToString(", ") { "'$it" }}>"
                            } else {
                                ""
                            }
                    notes.add("└> Note: '$typeDisplay' is $desc in '${ctx.paramName}")
                }
                is ConstraintContext.NominalToStructural -> {}
            }
        }
        return notes
    }
}
