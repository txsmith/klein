package klein.check.contract

import klein.Revision
import klein.SourceSpan
import klein.check.Subtyping
import klein.check.Type
import klein.check.TypeDefPreprocessor
import klein.check.TypeEnv
import klein.check.TypeError
import klein.check.TypeResolver
import klein.check.quantify
import klein.surface.ContractExpr
import klein.surface.FunDecl
import klein.surface.ValDecl
import klein.surface.revisionedName

enum class DeclarationKind { Function, Value }

/** One accepted declaration: what the host must implement, and what a release may point at. */
data class ContractDeclaration(
    val name: String,
    val revision: Revision,
    val kind: DeclarationKind,
    val type: Type,
)

data class ContractResult(
    val declarations: List<ContractDeclaration>,
    val env: TypeEnv,
    val errors: List<TypeError>,
)

/**
 * Checks a capability contract. Where [klein.check.Checker] checks a rule, this checks the file
 * that says what a rule may see: type definitions are preprocessed, then declarations are bound
 * under their `(name, revision)` key.
 *
 * The two share type resolution and nothing else. There is no mode flag between them, because
 * there is no longer one function serving both.
 */
class ContractChecker {
    private val errors = mutableListOf<TypeError>()
    private val resolver = TypeResolver(errors)
    private val subtyping = Subtyping()
    private val preprocessor = TypeDefPreprocessor(errors, resolver::freshSkolem, resolver::resolve, subtyping)

    fun check(
        contract: ContractExpr,
        env: TypeEnv = TypeEnv.empty(),
    ): ContractResult {
        errors.clear()
        val scope = env.copy()
        preprocessor.process(contract.types, scope)
        val declarations = bindDeclarations(contract, scope)
        return ContractResult(declarations, scope, errors.toList())
    }

    private fun bindDeclarations(
        contract: ContractExpr,
        env: TypeEnv,
    ): List<ContractDeclaration> {
        val declared = mutableSetOf<Pair<String, Revision>>()
        fun declare(
            name: String,
            revision: Revision,
            span: SourceSpan,
        ): Boolean {
            if (declared.add(name to revision)) return true
            errors.add(TypeError.DuplicateBinding(revisionedName(name, revision), span))
            return false
        }

        val declarations = mutableListOf<ContractDeclaration>()
        contract.declarations.forEach { declaration ->
            val revision = declaration.revision ?: Revision(1)
            if (!declare(declaration.name, revision, declaration.span)) return@forEach
            val (kind, type) =
                when (declaration) {
                    is FunDecl -> DeclarationKind.Function to bindFunDecl(declaration, revision, env)
                    is ValDecl -> DeclarationKind.Value to bindValDecl(declaration, revision, env)
                }
            rejectCarriedFunctions(
                revisionedName(declaration.name, revision),
                type,
                declaration.span,
                env,
                isCallable = kind == DeclarationKind.Function,
            )
            declarations.add(ContractDeclaration(declaration.name, revision, kind, type))
        }
        return declarations
    }

    private fun bindFunDecl(
        declaration: FunDecl,
        revision: Revision,
        env: TypeEnv,
    ): Type {
        val (sigEnv, paramTypes) = resolver.openSignature(declaration.params, declaration.returnType, env)
        val returnType = resolver.resolve(declaration.returnType, sigEnv)
        val type =
            quantify(
                sigEnv.localTypeVars(),
                Type.TFun(paramTypes, returnType, declaration.params.map { it.name }),
            )
        env.bind(declaration.name, revision, type)
        return type
    }

    private fun bindValDecl(
        declaration: ValDecl,
        revision: Revision,
        env: TypeEnv,
    ): Type {
        val sigEnv = env.child()
        resolver.introduceTypeVars(listOf(declaration.type), sigEnv)
        val type = quantify(sigEnv.localTypeVars(), resolver.resolve(declaration.type, sigEnv))
        env.bind(declaration.name, revision, type)
        return type
    }

    private fun rejectCarriedFunctions(
        name: String,
        bound: Type,
        span: SourceSpan,
        env: TypeEnv,
        isCallable: Boolean,
    ) {
        if (carriesFunctionType(bound, env, isCallable)) {
            errors.add(TypeError.FunctionTypeInCapability(name, span))
        }
    }
}
