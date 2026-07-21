package klein.check

import klein.surface.BoolLiteral
import klein.surface.ConstructorPattern
import klein.surface.DoubleLiteral
import klein.surface.Expr
import klein.surface.IntLiteral
import klein.surface.LiteralPattern
import klein.surface.NullLiteral
import klein.surface.Pattern
import klein.surface.RecordPattern
import klein.surface.StringLiteral
import klein.surface.VariablePattern
import klein.surface.WildcardPattern
import klein.check.Type.*

internal class MatchCoverage private constructor(
    val scrutinee: Type,
    val core: Type,
    val isOptional: Boolean,
    private val coreCtors: List<String>,
) {
    private var nullCovered = !isOptional
    private var coreExhausted = core is TBottom
    private val coveredCtors = mutableSetOf<String>()
    private val coveredLiterals = mutableSetOf<Any>()

    fun exhausted(): Boolean = nullCovered && coreExhausted

    fun residual(): Type = if (isOptional && nullCovered) core else scrutinee

    fun reaches(pattern: Pattern): Boolean =
        when (pattern) {
            is WildcardPattern, is VariablePattern -> !exhausted()
            is RecordPattern -> !coreExhausted
            is ConstructorPattern -> !coreExhausted && pattern.name !in coveredCtors
            is LiteralPattern ->
                if (pattern.literal is NullLiteral) {
                    !nullCovered
                } else {
                    !coreExhausted && literalValue(pattern.literal) !in coveredLiterals
                }
        }

    fun cover(pattern: Pattern) {
        when (pattern) {
            is WildcardPattern, is VariablePattern -> {
                nullCovered = true
                coreExhausted = true
            }
            is RecordPattern -> coreExhausted = true
            is ConstructorPattern -> {
                coveredCtors.add(pattern.name)
                if (coreCtors.isNotEmpty() && coveredCtors.containsAll(coreCtors)) coreExhausted = true
            }
            is LiteralPattern ->
                if (pattern.literal is NullLiteral) {
                    nullCovered = true
                } else {
                    literalValue(pattern.literal)?.let { coveredLiterals.add(it) }
                    if (core is TBool && coveredLiterals.containsAll(listOf(true, false))) coreExhausted = true
                }
        }
    }

    fun missing(): List<String> =
        buildList {
            if (!nullCovered) add("null")
            if (!coreExhausted) {
                when (core) {
                    is TRef -> coreCtors.filterTo(this) { it !in coveredCtors }
                    TBool -> listOf(true, false).filterNot { it in coveredLiterals }.mapTo(this) { it.toString() }
                    else -> {}
                }
            }
        }

    private fun literalValue(literal: Expr): Any? =
        when (literal) {
            is IntLiteral -> literal.value
            is DoubleLiteral -> literal.value
            is StringLiteral -> literal.value
            is BoolLiteral -> literal.value
            else -> null
        }

    companion object {
        fun of(
            scrutinee: Type,
            env: TypeEnv,
        ): MatchCoverage? {
            val isOptional = scrutinee is TOptional || scrutinee is TNull
            val core = nonNullCore(scrutinee)
            val matchable =
                when (core) {
                    is TRef, is TRecord, TNum, TStr, TBool -> true
                    is TSkolem, TBottom -> isOptional
                    else -> false
                }
            if (!matchable) return null
            return MatchCoverage(scrutinee, core, isOptional, coreCtors(core, env))
        }

        private fun coreCtors(
            core: Type,
            env: TypeEnv,
        ): List<String> {
            if (core !is TRef) return emptyList()
            val members = env.allConstructors().filter { it.parentType == core.name }.map { it.name }
            if (members.isEmpty() && env.lookupConstructor(core.name) != null) return listOf(core.name)
            return members
        }
    }
}
