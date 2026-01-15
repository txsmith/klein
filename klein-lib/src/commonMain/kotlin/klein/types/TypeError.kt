package klein.types

import klein.SourceSpan
import klein.Type

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
        val expected: Type,
        val actual: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Type mismatch: expected ${Type.print(expected)}, got ${Type.print(actual)}"
    }

    data class MissingField(
        val field: String,
        val recordType: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Record ${Type.print(recordType)} has no field '$field'"
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
    ) : TypeError() {
        override val message = "Null is not allowed here: expected ${Type.print(expected)}"
    }
}
