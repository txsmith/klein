package klein.check

import klein.KleinError
import klein.ReleaseNumber
import klein.Revision
import klein.SourceSpan

sealed class TypeError : KleinError {
    abstract override val span: SourceSpan
    abstract override val message: String

    data class UnboundVariable(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Unbound variable: $name"
    }

    data class TypeMismatch(
        val subtype: Type<*>,
        val supertype: Type<*>,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            "Type mismatch: '${Type.print(subtype)}' cannot be used as '${Type.print(supertype)}'"
    }

    data class CannotJoinBranches(
        val thenType: Type<*>,
        val elseType: Type<*>,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            "Branches of if-else have no common type: '${Type.print(thenType)}' vs '${Type.print(elseType)}'"
    }

    data class MissingField(
        val field: String,
        val recordType: Type<*>,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Type error: ${Type.print(recordType)} has no field '$field'"

        override fun equals(other: Any?) = other is MissingField && field == other.field && span == other.span

        override fun hashCode() = 31 * field.hashCode() + span.hashCode()
    }

    data class NotARecord(
        val actual: Type<*>,
        val field: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Type error: '${Type.print(actual)}' is not a record, so it has no field '$field'"
    }

    data class NotAFunction(
        val actual: Type<*>,
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

    data class FunctionTypeInCapability(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            "'$name' has a function in its type; a host cannot call a Klein function, so capabilities cannot pass one"
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

    /**
     * A revision names one version of *declared* vocabulary, and a built-in type is never declared —
     * a contract cannot define `Num` at any revision, so `Num/1` is as meaningless as `Num/2`.
     * `/1` is rejected too: it survives elsewhere only because revision 1 is the default, and
     * letting it pass here would make the bare name and the written `/1` differ in what they permit.
     */
    data class RevisionOnPrimitive(
        val typeName: String,
        val revision: Revision,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            "'$typeName' is a built-in type and has no revisions; write '$typeName' without '/${revision.value}'"
    }

    /**
     * A release entry that names nothing the contract declares. Entries resolve against the
     * declaration lists rather than the environment, so a constructor lands here too: constructors
     * travel with their type and are never pointed at individually.
     */
    data class UnknownReleaseTarget(
        val name: String,
        val release: ReleaseNumber,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Release ${release.value} points at '$name', which this contract does not declare"
    }

    data class DuplicateReleaseEntry(
        val name: String,
        val release: ReleaseNumber,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "'$name' is named twice in release ${release.value}; a name means one revision per release"
    }

    /** Blocks are read in file order and each builds on the last, so the numbers must increase.
     *  Gaps are legal: a gap is a release that has been retired. */
    data class ReleaseOutOfOrder(
        val release: ReleaseNumber,
        val previous: ReleaseNumber,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            "Release ${release.value} follows release ${previous.value}; release numbers must increase down the file"
    }

    data class RemoveOfUnexposedName(
        val name: String,
        val release: ReleaseNumber,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            "Release ${release.value} removes '$name', which it does not expose; " +
                "either it was never there or an earlier release already removed it"
    }

    /**
     * A release exposes something reaching a type it does not expose, so a rule could meet a value
     * whose type it has no way to write down. [unreachable] always spells its revision, including
     * `/1`: which revision is reached is the whole of what went wrong.
     */
    data class ReleaseNotSelfContained(
        val unreachable: String,
        val release: ReleaseNumber,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Release ${release.value} reaches '$unreachable', which it does not expose"
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
        val expected: Type<*>,
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

    data class RefutableBinding(
        val missing: List<String>,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            if (missing.isEmpty()) {
                "Refutable pattern in a binding; use match"
            } else {
                "Refutable pattern in a binding — not covered: ${missing.joinToString(", ")}; use match"
            }
    }

    data class NonExhaustiveMatch(
        val missing: List<String>,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            if (missing.isEmpty()) {
                "Match is not exhaustive; add a '_ ->' arm"
            } else {
                "Match is not exhaustive — missing: ${missing.joinToString(", ")}; add the missing arms or a '_ ->' arm"
            }
    }

    data class UnreachableMatchArm(
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Unreachable match arm: earlier arms already cover it"
    }

    data class NotAConstructorOf(
        val constructorName: String,
        val scrutinee: Type<*>,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "'$constructorName' is not a constructor of '${Type.print(scrutinee)}'"
    }

    data class CannotMatchOn(
        val scrutinee: Type<*>,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Cannot match on a value of type '${Type.print(scrutinee)}'"
    }

    data class CannotJoinMatchArms(
        val armType: Type<*>,
        val otherType: Type<*>,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message =
            "Match arms have no common type: '${Type.print(armType)}' vs '${Type.print(otherType)}'"
    }
}
