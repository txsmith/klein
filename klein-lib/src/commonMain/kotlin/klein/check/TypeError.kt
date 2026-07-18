package klein.check

import klein.SourceSpan

sealed class TypeError {
    abstract val span: SourceSpan
    abstract val message: String

    data class UnboundVariable(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Unbound variable: $name"
    }

    data class TypeMismatch(
        val subtype: Type,
        val supertype: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            "Type mismatch: '${Type.print(subtype)}' cannot be used as '${Type.print(supertype)}'"
    }

    data class CannotJoinBranches(
        val thenType: Type,
        val elseType: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            "Branches of if-else have no common type: '${Type.print(thenType)}' vs '${Type.print(elseType)}'"
    }

    data class MissingField(
        val field: String,
        val recordType: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Type error: ${Type.print(recordType)} has no field '$field'"

        override fun equals(other: Any?) = other is MissingField && field == other.field && span == other.span

        override fun hashCode() = 31 * field.hashCode() + span.hashCode()
    }

    data class NotARecord(
        val actual: Type,
        val field: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Type error: '${Type.print(actual)}' is not a record, so it has no field '$field'"
    }

    data class NotAFunction(
        val actual: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Type error: '${Type.print(actual)}' is not a function"
    }

    data class MissingParamAnnotation(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Cannot infer the type of parameter '$name'; add a type annotation, " +
            "or use the lambda where a function type is expected"
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

    data class RecursiveFunctionNeedsReturnType(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Recursive function '$name' needs a declared return type"
    }

    data class CallArityMismatch(
        val expected: Int,
        val actual: Int,
        override val span: SourceSpan,
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

    data class ImplicitParamWithoutExpectedType(
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Implicit dot parameter '.' can only be used where the lambda's type is expected"
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

    data class AnonymousUnionType(
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Anonymous union types ('A | B') aren't supported — define a nominal type"
    }

    data class AnonymousIntersectionType(
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Anonymous intersection types ('A & B') aren't supported yet"
    }
}
