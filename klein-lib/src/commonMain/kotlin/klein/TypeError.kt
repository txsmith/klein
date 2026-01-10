package klein

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
        override val message = "Type mismatch: expected ${TypePrinter.print(expected)}, got ${TypePrinter.print(actual)}"
    }

    data class NotAFunction(
        val actual: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Not a function: ${TypePrinter.print(actual)}"
    }

    data class NotARecord(
        val actual: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Not a record: ${TypePrinter.print(actual)}"
    }

    data class MissingField(
        val field: String,
        val recordType: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Record ${TypePrinter.print(recordType)} has no field '$field'"
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
        override val message = "Implicit parameter '.' can only be used inside a lambda"
    }

    data class MixedImplicitExplicit(
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Cannot mix implicit parameter '.' with explicit parameters"
    }
}
