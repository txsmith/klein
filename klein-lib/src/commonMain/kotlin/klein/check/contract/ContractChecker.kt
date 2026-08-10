package klein.check.contract

import klein.ReleaseNumber
import klein.Revision
import klein.SourceSpan
import klein.check.ContractEnv
import klein.check.ContractType
import klein.check.Subtyping
import klein.check.Type
import klein.check.TypeDefPreprocessor
import klein.check.TypeEnv
import klein.check.TypeError
import klein.check.TypeResolver
import klein.check.quantify
import klein.surface.ContractExpr
import klein.surface.FunDecl
import klein.surface.ReleaseBlock
import klein.surface.ValDecl
import klein.surface.revisionedName

/** What checking a contract produced: the artifact, and the diagnostics that decide whether it is
 *  handed out. */
data class ContractResult(
    val contract: EnvironmentContract,
    val errors: List<TypeError>,
)

/**
 * One release: what each plain name means to the rules checked against it.
 *
 * [surface] is *absolute* — deltas already folded — so nothing downstream re-derives it. Inside one
 * release a name means exactly one revision, which is what makes dropping the revision on the way
 * to a rule lossless.
 */
data class Release(
    val number: ReleaseNumber,
    val surface: Map<String, Revision>,
    val span: SourceSpan = SourceSpan.zero,
)

/**
 * Checks a capability contract. Where [klein.check.Checker] checks a rule, this checks the file
 * that says what a rule may see: type definitions are preprocessed, then declarations are bound
 * under their `(name, revision)` key, then releases resolve against both.
 *
 * The two share type resolution and nothing else. There is no mode flag between them, because
 * there is no longer one function serving both.
 */
class ContractChecker {
    private val errors = mutableListOf<TypeError>()
    private val resolver = TypeResolver<Revision>(errors, { it ?: Revision(1) })
    private val subtyping = Subtyping()
    private val preprocessor = TypeDefPreprocessor(errors, resolver, subtyping)

    fun check(contract: ContractExpr): ContractResult {
        errors.clear()
        val scope: ContractEnv = TypeEnv.empty()
        preprocessor.process(contract.types, scope)
        val declarations = bindDeclarations(contract, scope)
        val releases = resolveReleases(contract)
        return ContractResult(
            EnvironmentContract(declarations, releases.map { it.number }, scope, releases.associateBy { it.number }),
            errors.toList(),
        )
    }

    /**
     * Resolve every written block into a [Release].
     *
     * Entries resolve against the contract's type and declaration lists rather than against the
     * checked environment, which is what makes `Circle/2` an [TypeError.UnknownReleaseTarget]
     * instead of a resolvable pointer at a constructor.
     *
     * Each block stands alone here; inheritance between blocks arrives with the rest of the fold.
     */
    private fun resolveReleases(contract: ContractExpr): List<Release> {
        val declared =
            contract.types.mapTo(mutableSetOf()) { it.name to (it.revision ?: Revision(1)) } +
                contract.declarations.map { it.name to (it.revision ?: Revision(1)) }
        return contract.releases.map { block -> resolveRelease(block, declared) }
    }

    private fun resolveRelease(
        block: ReleaseBlock,
        declared: Set<Pair<String, Revision>>,
    ): Release {
        val surface = mutableMapOf<String, Revision>()
        val seen = mutableSetOf<String>()
        for (entry in block.entries) {
            if (!seen.add(entry.name)) {
                errors.add(TypeError.DuplicateReleaseEntry(entry.name, block.number, entry.span))
                continue
            }
            if (entry.remove) {
                surface.remove(entry.name)
                continue
            }
            val revision = entry.revision ?: Revision(1)
            if ((entry.name to revision) !in declared) {
                errors.add(
                    TypeError.UnknownReleaseTarget(revisionedName(entry.name, revision), block.number, entry.span),
                )
                continue
            }
            surface[entry.name] = revision
        }
        return Release(block.number, surface, block.span)
    }

    private fun bindDeclarations(
        contract: ContractExpr,
        env: ContractEnv,
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
        env: ContractEnv,
    ): ContractType {
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
        env: ContractEnv,
    ): ContractType {
        val sigEnv = env.child()
        resolver.introduceTypeVars(listOf(declaration.type), sigEnv)
        val type = quantify(sigEnv.localTypeVars(), resolver.resolve(declaration.type, sigEnv))
        env.bind(declaration.name, revision, type)
        return type
    }

    private fun rejectCarriedFunctions(
        name: String,
        bound: ContractType,
        span: SourceSpan,
        env: ContractEnv,
        isCallable: Boolean,
    ) {
        if (carriesFunctionType(bound, env, isCallable)) {
            errors.add(TypeError.FunctionTypeInCapability(name, span))
        }
    }
}
