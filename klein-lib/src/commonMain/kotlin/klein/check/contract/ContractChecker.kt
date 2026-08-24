package klein.check.contract

import klein.ReleaseNumber
import klein.RevisionNumber
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
import klein.surface.CapabilityDeclaration
import klein.surface.ContractExpr
import klein.surface.FunDecl
import klein.surface.ReleaseBlock
import klein.surface.ValDecl
import klein.surface.revisionedName

/** What checking a contract produced: the artifact, and the diagnostics that decide whether it is
 *  handed out. */
internal data class ContractResult(
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
internal data class FlattenedReleaseBlock(
    val number: ReleaseNumber,
    val surface: Map<String, RevisionNumber>,
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
internal class ContractChecker {
    private val errors = mutableListOf<TypeError>()
    private val resolver = TypeResolver<RevisionNumber>(errors, { it ?: RevisionNumber(1) })
    private val subtyping = Subtyping()
    private val preprocessor = TypeDefPreprocessor(errors, resolver, subtyping)

    fun check(contract: ContractExpr): ContractResult {
        errors.clear()
        val scope: ContractEnv = TypeEnv.empty()
        preprocessor.process(contract.types, scope)
        val declarations = bindDeclarations(contract, scope)
        val releases = flattenReleaseBlocks(contract, scope)
        return ContractResult(
            EnvironmentContract(declarations, releases.map { it.number }, scope, releases.associateBy { it.number }),
            errors.toList(),
        )
    }

    /**
     * Fold every written block, in file order, into a [FlattenedReleaseBlock].
     *
     * A block states only what its release changes and inherits the rest from the block before it,
     * so reading a release means starting at the oldest and applying each in turn. Numbers must
     * increase down the file; gaps are legal, a gap being a release that has been retired.
     *
     * Entries resolve against the contract's type and declaration lists rather than against the
     * checked environment, which is what makes `Circle/2` an [TypeError.UnknownReleaseTarget]
     * instead of a resolvable pointer at a constructor.
     */
    private fun flattenReleaseBlocks(
        contract: ContractExpr,
        env: ContractEnv,
    ): List<FlattenedReleaseBlock> {
        val declared =
            contract.types.mapTo(mutableSetOf()) { it.name to (it.revision ?: RevisionNumber(1)) } +
                contract.declarations.map { it.name to (it.revision ?: RevisionNumber(1)) }

        var inherited = emptyMap<String, RevisionNumber>()
        var previous: ReleaseNumber? = null
        val releases = mutableListOf<FlattenedReleaseBlock>()
        for (block in contract.releases) {
            if (previous != null && block.number.value <= previous.value) {
                errors.add(TypeError.ReleaseOutOfOrder(block.number, previous, block.span))
            }
            previous = block.number
            inherited = applyEntries(block, inherited, declared)
            releases.add(FlattenedReleaseBlock(block.number, inherited, block.span))
        }
        releases.forEach { checkSelfContained(it, env) }
        return releases
    }

    /** [block]'s entries applied to the surface it inherits. */
    private fun applyEntries(
        block: ReleaseBlock,
        inherited: Map<String, RevisionNumber>,
        declared: Set<Pair<String, RevisionNumber>>,
    ): Map<String, RevisionNumber> {
        val surface = inherited.toMutableMap()
        val seen = mutableSetOf<String>()
        for (entry in block.entries) {
            if (!seen.add(entry.name)) {
                errors.add(TypeError.DuplicateReleaseEntry(entry.name, block.number, entry.span))
                continue
            }
            if (entry.remove) {
                // By name: inside a release a name means one revision, so naming it is unambiguous.
                if (surface.remove(entry.name) == null) {
                    errors.add(TypeError.RemoveOfUnexposedName(entry.name, block.number, entry.span))
                }
                continue
            }
            val revision = entry.revision ?: RevisionNumber(1)
            if ((entry.name to revision) !in declared) {
                errors.add(
                    TypeError.UnknownReleaseTarget(revisionedName(entry.name, revision), block.number, entry.span),
                )
                continue
            }
            surface[entry.name] = revision
        }
        return surface
    }

    /**
     * `contracts.md` §"A release must be self-contained": every type reachable from anything the
     * release exposes must itself be exposed at that same revision.
     *
     * Rooted at everything exposed rather than at capabilities alone, because an exposed type is
     * vocabulary in its own right — a rule can annotate with it whether or not a capability
     * mentions it. This is the proof [strip] stands on: dropping a revision is lossless only when
     * the set it walks is closed.
     */
    private fun checkSelfContained(
        release: FlattenedReleaseBlock,
        env: ContractEnv,
    ) {
        val exposed = mutableSetOf<Pair<String, RevisionNumber>>()
        val rootTypes = mutableListOf<ContractType>()
        for ((name, revision) in release.surface) {
            exposed.add(name to revision)
            env.constructorsOf(name, revision).forEach { exposed.add(it.name to revision) }

            if (env.lookupTypeDef(name, revision) != null) {
                // A type. Tested first: a single-constructor type also has a value binding — its
                // constructor — which reaches nothing its fields do not.
                rootTypes += env.declaredFields(name, revision)
            } else {
                // A capability. Its declared type crosses whole, `maxRetries: Num` as much as a
                // function; the arrow is only special to the carried-function rule.
                env.lookup(name, revision)?.let(rootTypes::add)
            }
        }
        // One walk per release, so vocabulary shared by several entries expands once.
        val reachableTRefs =
            rootTypes.reachableTypes(env).mapNotNullTo(mutableSetOf()) {
                if (it is Type.TRef) it.name to it.revision else null
            }
        for ((name, revision) in reachableTRefs - exposed) {
            errors.add(TypeError.ReleaseNotSelfContained("$name/${revision.value}", release.number, release.span))
        }
    }

    private fun bindDeclarations(
        contract: ContractExpr,
        env: ContractEnv,
    ): List<ContractDeclaration> {
        val declared = mutableSetOf<Pair<String, RevisionNumber>>()
        fun declare(
            name: String,
            revision: RevisionNumber,
            span: SourceSpan,
        ): Boolean {
            if (declared.add(name to revision)) return true
            errors.add(TypeError.DuplicateBinding(revisionedName(name, revision), span))
            return false
        }

        val declarations = mutableListOf<ContractDeclaration>()
        contract.declarations.forEach { declaration ->
            val revision = declaration.revision ?: RevisionNumber(1)
            if (!declare(declaration.name, revision, declaration.span)) return@forEach
            val (kind, type) =
                when (declaration) {
                    is FunDecl -> DeclarationKind.Function to bindFunDecl(declaration, revision, env)
                    is ValDecl -> DeclarationKind.Value to bindValDecl(declaration, revision, env)
                }
            declarations.add(ContractDeclaration(declaration.name, revision, kind, type))
        }
        return declarations
    }

    private fun bindFunDecl(
        declaration: FunDecl,
        revision: RevisionNumber,
        env: ContractEnv,
    ): ContractType {
        val (sigEnv, paramTypes) = resolver.openSignature(declaration.params, declaration.returnType, env)
        val returnType = resolver.resolve(declaration.returnType, sigEnv)
        // A capability is itself a function, so its own arrow is legal; what crosses the boundary
        // is its parameters and its result.
        rejectCarriedFunctions(paramTypes + returnType, declaration, revision, env)
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
        revision: RevisionNumber,
        env: ContractEnv,
    ): ContractType {
        val sigEnv = env.child()
        resolver.introduceTypeVars(listOf(declaration.type), sigEnv)
        val resolved = resolver.resolve(declaration.type, sigEnv)
        // A value capability has no arrow of its own, so the whole type crosses.
        rejectCarriedFunctions(listOf(resolved), declaration, revision, env)
        val type = quantify(sigEnv.localTypeVars(), resolved)
        env.bind(declaration.name, revision, type)
        return type
    }

    /** `contracts.md` §"No functions cross the boundary", over the types that actually cross. */
    private fun rejectCarriedFunctions(
        crossing: List<ContractType>,
        declaration: CapabilityDeclaration,
        revision: RevisionNumber,
        env: ContractEnv,
    ) {
        if (crossing.reachableTypes(env).any { it is Type.TFun }) {
            errors.add(
                TypeError.FunctionTypeInCapability(revisionedName(declaration.name, revision), declaration.span),
            )
        }
    }
}

/**
 * Every type reachable from these, the seeds themselves included: their own structure, and
 * transitively the fields of each named type they reference.
 *
 * A name is expanded once across the whole walk, so recursive and mutually recursive types
 * terminate and appear as references rather than as expansions. Seeds are always types the
 * environment already holds; nothing here constructs one.
 */
private fun List<ContractType>.reachableTypes(env: ContractEnv): List<ContractType> {
    val expanded = mutableSetOf<Pair<String, RevisionNumber>>()
    val reached = mutableListOf<ContractType>()

    fun walk(type: ContractType) {
        reached.add(type)
        when (type) {
            is Type.TFun -> {
                type.params.forEach(::walk)
                walk(type.result)
            }
            is Type.TOptional -> walk(type.type)
            is Type.TRecord -> type.fields.values.forEach(::walk)
            is Type.TForall -> walk(type.body)
            is Type.TRef -> {
                // Arguments are walked whatever the expanded set says: `Box<Order/2>` and
                // `Box<Foo/3>` are one entry under the same key but two references to follow.
                type.typeArgs.forEach(::walk)
                if (expanded.add(type.name to type.revision)) {
                    env.declaredFields(type.name, type.revision).forEach(::walk)
                }
            }
            else -> {}
        }
    }

    forEach(::walk)
    return reached
}

/**
 * The field types a named type declares, **one layer deep**: its own fields, and the fields of
 * every constructor that travels with it. Neither is a subset of the other — a sum's own fields
 * are only those every arm shares, typed as their join.
 *
 * Following what this returns is [reachableTypes]'s job, not this one's. Empty for a name that is
 * not a type.
 */
private fun ContractEnv.declaredFields(
    name: String,
    revision: RevisionNumber,
): List<ContractType> =
    lookupTypeDef(name, revision)?.iface?.fields?.values.orEmpty() +
        constructorsOf(name, revision).flatMap { it.fields.values }
