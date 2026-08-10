package klein.check

import klein.SourceSpan
import klein.surface.*
import klein.check.Type.*

internal data class TypeCheckResult(
    val program: Program,
    val type: RuleType,
    val errors: List<TypeError>,
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
}

internal data class ProgramCheck(
    val type: RuleType,
    val errors: List<TypeError>,
)

internal data class ExpectedType(
    val type: RuleType,
    val source: ExpectedTypeSource,
)

internal sealed class ExpectedTypeSource {
    data class Param(
        val fn: String?,
        val name: String?,
        val span: SourceSpan,
    ) : ExpectedTypeSource()

    data class Return(
        val fn: String?,
        val span: SourceSpan,
    ) : ExpectedTypeSource()

    data class Binding(
        val name: String,
        val span: SourceSpan,
    ) : ExpectedTypeSource()

    data class Ascription(
        val span: SourceSpan,
    ) : ExpectedTypeSource()

    data class RecordField(
        val name: String,
        val span: SourceSpan,
    ) : ExpectedTypeSource()
}

/**
 * The program checker: the bidirectional checker for the *rule* language. A capability contract is
 * a different language with a different root, and is checked by
 * [klein.check.contract.ContractChecker]; the two share [TypeResolver] and nothing else.
 */
internal class Checker {
    private val errors = mutableListOf<TypeError>()
    private val subtyping = Subtyping()
    private val constraints = ConstraintGenerator(subtyping)
    private val resolver = TypeResolver<Nothing?>(errors) { null }
    private val preprocessor =
        TypeDefPreprocessor(errors, resolver, subtyping)

    fun checkProgram(
        program: Program,
        env: RuleEnv = TypeEnv.empty(),
    ): ProgramCheck {
        errors.clear()
        val scope = env.copy()
        val type = synthBlockStmts(program.stmts, scope)
        return ProgramCheck(type, errors.toList())
    }

    fun synth(
        expr: Expr,
        env: RuleEnv,
    ): RuleType =
        when (expr) {
            is IntLiteral -> TNum
            is DoubleLiteral -> TNum
            is StringLiteral -> TStr
            is BoolLiteral -> TBool
            is NullLiteral -> TNull
            is Ident -> synthIdent(expr, env)
            is BinaryOp -> synthBinaryOp(expr, env)
            is UnaryOp -> synthUnaryOp(expr, env)
            is Lambda -> synthLambda(expr, env)
            is Apply -> synthApply(expr, env)
            is RecordLiteral -> synthRecordLiteral(expr, env)
            is FieldAccess -> synthFieldAccess(expr, env)
            is SafeFieldAccess -> synthSafeFieldAccess(expr, env)
            is SafeApply -> inferApply(safeApplyAsApply(expr), null, env)
            is IfThenElse -> synthIfThenElse(expr, env)
            is ImplicitParam -> synthImplicitParam(expr, env)
            is Ascription -> synthAscription(expr, env)
            is Block -> synthBlockStmts(expr.stmts, env.child())
            is Match -> inferMatch(expr, null, env, emptyList())
        }

    /**
     * Check [expr] against [expected] (bidirectional check mode).
     *
     * Introduction forms (lambda, record literal, if) get rules that push [expected] *inward*.
     * Apply gets the expected type pushed in to aid type-argument inference.
     * Every other (elimination) form falls back to **subsumption**: synthesize its type and verify
     * that type is a subtype of [expected].
     */
    fun check(
        expr: Expr,
        expected: RuleType,
        env: RuleEnv,
        expectedSource: List<ExpectedType> = emptyList(),
    ) {
        when (expr) {
            is Lambda -> checkLambda(expr, expected, env)
            is RecordLiteral -> checkRecordLiteral(expr, expected, env, expectedSource)
            is IfThenElse -> checkIfThenElse(expr, expected, env, expectedSource)
            is Apply -> checkApply(expr, expected, env)
            is SafeApply -> checkApply(safeApplyAsApply(expr), expected, env)
            is Match -> inferMatch(expr, expected, env, expectedSource)
            else -> synthAndCheckSubtype(expr, expected, env)
        }
    }

    private fun synthBlockStmts(
        stmts: List<Stmt>,
        env: RuleEnv,
    ): RuleType {
        preprocessor.process(stmts.filterIsInstance<TypeDefStmt>().map { it.typeDef }, env)

        val scope = ScopeGraph.constructGraph(stmts)
        scope.duplicates.forEach { (name, span) -> recordError(TypeError.DuplicateBinding(name, span)) }

        val processedPatternVals = mutableSetOf<PatternVal>()
        for (component in scope.graph.computeSCCs()) {
            val bindings = component.nodes.map { it.binding }
            when {
                bindings.all { it is FunDef } -> bindFunGroup(bindings.filterIsInstance<FunDef>(), component.isRecursive, env)
                bindings.size == 1 && bindings.single() is Val -> synthAndBindVal(bindings.single() as Val, env)
                bindings.size == 1 && bindings.single() is PatternVal -> {
                    val stmt = bindings.single() as PatternVal
                    if (processedPatternVals.add(stmt)) checkPatternVal(stmt, env)
                }
                else ->
                    component.nodes.filter { it.binding is Val || it.binding is PatternVal }.forEach { node ->
                        recordError(TypeError.RecursiveVal(node.name, scope.graph.findCycle(node.name), node.binding.span))
                    }
            }
        }
        for (stmt in stmts) {
            if (stmt is PatternVal && processedPatternVals.add(stmt)) checkPatternVal(stmt, env)
        }

        var last: RuleType = TUnit
        for (stmt in stmts) {
            if (stmt is Expr) last = synth(stmt, env)
        }
        return last
    }

    private fun checkPatternVal(
        stmt: PatternVal,
        env: RuleEnv,
    ) {
        val rhsType = synth(stmt.value, env)
        val coverage = if (rhsType is TBottom) null else MatchCoverage.of(rhsType, env)
        if (coverage == null) {
            if (rhsType !is TBottom) recordError(TypeError.CannotMatchOn(rhsType, stmt.value.span))
            stmt.pattern.boundNames.forEach { env.bind(it, TBottom) }
            return
        }
        val errorsBefore = errors.size
        checkPattern(stmt.pattern, coverage, env, env)
        coverage.cover(stmt.pattern)
        if (errors.size == errorsBefore && !coverage.exhausted()) {
            recordError(TypeError.RefutableBinding(coverage.missing(), stmt.span))
        }
    }

    /**
     * Bind and check a group of mutually-defined functions.
     *
     * When [recursive], every function's signature is bound before any body is checked, so calls
     * between them resolve — which requires each to declare its return type. A non-recursive
     * function needs no such up-front binding, so it may omit its return type and have it inferred
     * from the body.
     */
    private fun bindFunGroup(
        funDefs: List<FunDef>,
        isRecursive: Boolean,
        env: RuleEnv,
    ) {
        // Pass 1: bind every function signature.
        // Yields a list of all functions that have a declared signature for checking in pass 2.
        // If no signature is declared, and the function is not recursive, synth & bind the type immediately.
        val pendingChecks: List<Triple<FunDef, RuleEnv, RuleType>> =
            funDefs.mapNotNull { funDef ->
                val (fnEnv, paramTypes) = resolver.openSignature(funDef.params, funDef.returnType, env)
                funDef.params.zip(paramTypes).forEach { (param, type) -> if (!param.isDiscard) fnEnv.bind(param.name, type) }
                val returnType =
                    when {
                        funDef.returnType != null -> resolver.resolve(funDef.returnType, fnEnv)
                        isRecursive ->
                            recordError(TypeError.RecursiveFunctionNeedsReturnType(funDef.name, funDef.span))
                        else -> synth(funDef.body, fnEnv) // non-recursive: infer straight from the body
                    }
                env.bind(funDef.name, quantify(fnEnv.localTypeVars(), TFun(paramTypes, returnType, funDef.params.map { it.name })))
                if (funDef.returnType != null) Triple(funDef, fnEnv, returnType) else null
            }

        // Pass 2: with all signatures bound, check each remaining body against its declared return.
        pendingChecks.forEach { (funDef, fnEnv, returnType) ->
            check(
                funDef.body,
                returnType,
                fnEnv,
                listOf(ExpectedType(returnType, ExpectedTypeSource.Return(funDef.name, funDef.span))),
            )
        }
    }

    private fun synthAndBindVal(
        stmt: Val,
        env: RuleEnv,
    ) {
        if (stmt.typeAnnotation != null) {
            val sigEnv = env.child()
            resolver.introduceTypeVars(listOf(stmt.typeAnnotation), sigEnv)
            val t = resolver.resolve(stmt.typeAnnotation, sigEnv)
            check(stmt.value, t, sigEnv, listOf(ExpectedType(t, ExpectedTypeSource.Binding(stmt.name, stmt.span))))
            env.bind(stmt.name, quantify(sigEnv.localTypeVars(), t))
        } else {
            env.bind(stmt.name, synth(stmt.value, env))
        }
    }

    private fun usesImplicitParam(expr: Expr): Boolean =
        when (expr) {
            is ImplicitParam -> true
            is Lambda -> false // a nested lambda opens its own implicit-param scope
            else -> expr.children.any { usesImplicitParam(it) }
        }

    private fun synthLambda(
        expr: Lambda,
        env: RuleEnv,
    ): RuleType {
        resolver.reportDuplicateParams(expr.params)
        val bodyEnv =
            if (expr.params.isEmpty()) {
                env.child(ImplicitParamContext.NoExpectedType)
            } else {
                env.child(ImplicitParamContext.BlockedByExplicitParams(expr.params.map { it.name }))
            }
        val paramTypes =
            expr.params.map { param ->
                val type =
                    if (param.typeAnnotation != null) {
                        resolver.resolve(param.typeAnnotation, bodyEnv)
                    } else {
                        recordError(TypeError.MissingParamAnnotation(param.name, param.span))
                    }
                if (!param.isDiscard) bodyEnv.bind(param.name, type)
                type
            }
        val bodyType = synth(expr.body, bodyEnv)
        return TFun(paramTypes, bodyType, expr.params.map { it.name })
    }

    private fun checkLambda(
        expr: Lambda,
        expected: RuleType,
        env: RuleEnv,
    ) {
        if (expected !is TFun) {
            // No inward rule for a lambda against a non-function — fall back to subsumption.
            synthAndCheckSubtype(expr, expected, env)
            return
        }
        resolver.reportDuplicateParams(expr.params)
        val implicit = expr.params.isEmpty() && usesImplicitParam(expr.body)
        val arity = if (implicit) 1 else expr.params.size
        if (arity != expected.params.size) {
            recordError(TypeError.CallArityMismatch(expected.params.size, arity, expr.span))
            return
        }
        val bodyEnv =
            if (implicit) {
                env.child(ImplicitParamContext.Available(expected.params.single()))
            } else {
                env.child(ImplicitParamContext.BlockedByExplicitParams(expr.params.map { it.name }))
            }
        expr.params.zip(expected.params).forEach { (param, expectedParamType) ->
            val paramType =
                if (param.typeAnnotation != null) {
                    val annotated = resolver.resolve(param.typeAnnotation, bodyEnv)
                    if (!subtyping.isSubtype(expectedParamType, annotated, env)) {
                        recordError(
                            TypeError.TypeMismatch(expectedParamType, annotated, param.span),
                        )
                    }
                    annotated
                } else {
                    expectedParamType
                }
            if (!param.isDiscard) bodyEnv.bind(param.name, paramType)
        }
        check(expr.body, expected.result, bodyEnv)
    }

    private fun synthApply(
        expr: Apply,
        env: RuleEnv,
    ): RuleType = inferApply(expr, null, env) // null = synth mode: no result demand, minimize R

    private fun checkApply(
        expr: Apply,
        expected: RuleType,
        env: RuleEnv,
    ) {
        inferApply(expr, expected, env)
    }

    /**
     * Type-checks a function application `f(a₁ … aₙ)` and returns the result type R.
     *
     * The callee's type is `∀T̄. (P₁ … Pₙ) → R`. Its type parameters `T̄` are what we solve for — empty
     * for a monomorphic callee. Checking the call is one subtyping obligation, between the callee's
     * function type and the shape the call demands:
     *
     *     (P₁ … Pₙ) → R   <:   (A₁ … Aₙ) → D
     *
     * where each `Aᵢ` is the type of the argument we supply and `D` is the demanded result type.
     *
     * The solver then finds concrete types for `T̄` that make `R` minimal, i.e. as informative as the
     * call allows. That instantiated `R` is the result.
     *
     * TODO(doc): write up the mainline procedure and the rest. Cover: check vs synth mode and how
     *  `D = Top` nullifies the `R <: D` constraint; the per-parameter "check ground parameters"
     *  optimization; and the bare-lambda limitation.
     */
    private fun inferApply(
        expr: Apply,
        expected: RuleType?,
        env: RuleEnv,
    ): RuleType {
        // A safe method call `r?.m(args)` on an optional receiver short-circuits: apply the unwrapped
        // method and lift the result back to optional. Its demand, likewise, is against the optional
        // result. On a non-optional receiver `?.` is redundant, so it behaves as a plain call.
        val rawCallee = synth(expr.callee, env)
        val isNullableApply = expr.callee is SafeFieldAccess && rawCallee is TOptional
        val callee = if (isNullableApply) (rawCallee as TOptional).type else rawCallee
        val demand = if (isNullableApply) expected?.let { if (it is TOptional) it.type else it } else expected
        val scheme = callee as? TForall<Nothing?> ?: TForall(emptySet(), callee)
        val body = scheme.body
        val result = when {
            body is TBottom -> { // S-App-Bot and C-App-Bot
                expr.args.forEach { synth(it, env) }
                TBottom
            }
            body !is TFun ->
                recordError(TypeError.NotAFunction(body, expr.span))
            body.params.size != expr.args.size ->
                recordError(TypeError.CallArityMismatch(body.params.size, expr.args.size, expr.span))
            else -> {
                val (demandSubst, demandFailures) =
                    if (demand != null) {
                        constraints.solveFromResult(scheme.params, body.result, demand, env)
                    } else {
                        emptyMap<TSkolem, RuleType>() to emptyList()
                    }
                demandFailures.forEach { recordError(TypeError.TypeMismatch(it.lower, it.upper, expr.span)) }
                val fn = if (demandSubst.isEmpty()) body else substitute(body, demandSubst) as TFun
                val unknowns = scheme.params - demandSubst.keys
                val calleeName =
                    when (val callSite = expr.callee) {
                        is Ident -> callSite.name
                        is FieldAccess -> callSite.field
                        is SafeFieldAccess -> callSite.field
                        else -> null
                    }
                val argTypes =
                    fn.params.mapIndexed { i, param ->
                        if (isGround(param, unknowns)) {
                            check(
                                expr.args[i],
                                param,
                                env,
                                listOf(
                                    ExpectedType(
                                        param,
                                        ExpectedTypeSource.Param(calleeName, fn.paramNames.getOrNull(i), expr.args[i].span),
                                    ),
                                ),
                            )
                            param
                        } else {
                            synth(expr.args[i], env)
                        }
                    }
                val target = if (demand == null) fn.result else TTop
                val (instantiated, errors) =
                    constraints.solveQuantified(TForall(unknowns, fn), TFun(argTypes, TTop), target, env)
                errors.forEach { recordError(TypeError.TypeMismatch(it.lower, it.upper, expr.span)) }
                if (demandFailures.isEmpty() && errors.isEmpty()) (instantiated as TFun).result else TBottom
            }
        }
        return if (isNullableApply) optionalOf(result) else result
    }

    /** A ground branch is returned as-is; a polymorphic branch is instantiated so its body fits the
     *  other (monomorphic) branch. Null when it can't be grounded — both branches polymorphic, or no
     *  instantiation of this branch fits the other. */
    private fun groundPolyBranch(
        branch: RuleType,
        other: RuleType,
        env: RuleEnv,
    ): RuleType? {
        if (branch !is TForall) return branch
        // Fit against the other branch — or, when it is itself polymorphic, a rigid skolemization of
        // it, so success means "fits every instantiation" (i.e. `branch <: other`).
        val target = if (other is TForall) skolemize(other) else other
        val solved = constraints.solveQuantified(branch, target, target, env)
        return if (solved.errors.isEmpty()) solved.type else null
    }

    private fun skolemize(forall: TForall<Nothing?>): RuleType =
        substitute(forall.body, forall.params.associateWith { resolver.freshSkolem(it.name) })

    private fun synthIfThenElse(
        expr: IfThenElse,
        env: RuleEnv,
    ): RuleType {
        check(expr.condition, TBool, env)
        val thenBranchType = synth(expr.thenBranch, env)

        if (expr.elseBranch == null) {
            return optionalOf(thenBranchType)
        }
        val elseBranchType = synth(expr.elseBranch, env)
        return joinBranches(thenBranchType, elseBranchType, env) { a, b ->
            TypeError.CannotJoinBranches(a, b, expr.span)
        } ?: TBottom
    }

    private fun checkIfThenElse(
        expr: IfThenElse,
        expected: RuleType,
        env: RuleEnv,
        expectedSource: List<ExpectedType>,
    ) {
        if (expr.elseBranch != null) {
            check(expr.condition, TBool, env)
            check(expr.thenBranch, expected, env, expectedSource)
            check(expr.elseBranch, expected, env, expectedSource)
        } else {
            // No else means the result is Optional(then); synth builds it (checking the condition
            // and yielding the Optional), then subsume verifies it against expected.
            synthAndCheckSubtype(expr, expected, env)
        }
    }

    private fun inferMatch(
        expr: Match,
        expected: RuleType?,
        env: RuleEnv,
        expectedSource: List<ExpectedType>,
    ): RuleType {
        val scrutineeType = synth(expr.scrutinee, env)
        if (scrutineeType is TBottom) return TBottom
        val coverage =
            MatchCoverage.of(scrutineeType, env)
                ?: return recordError(TypeError.CannotMatchOn(scrutineeType, expr.scrutinee.span))

        val armTypes = mutableListOf<RuleType>()
        for (arm in expr.arms) {
            if (!coverage.reaches(arm.pattern)) recordError(TypeError.UnreachableMatchArm(arm.pattern.span))
            val armEnv = env.child()
            checkPattern(arm.pattern, coverage, armEnv, env)
            if (arm.guard == null) coverage.cover(arm.pattern)
            arm.guard?.let { check(it, TBool, armEnv) }
            if (expected != null) {
                check(arm.body, expected, armEnv, expectedSource)
            } else {
                armTypes.add(synth(arm.body, armEnv))
            }
        }

        if (!coverage.exhausted()) {
            recordError(TypeError.NonExhaustiveMatch(coverage.missing(), expr.span))
        }

        if (expected != null) return expected
        return joinArmTypes(armTypes, expr.span, env)
    }

    private fun checkPattern(
        pattern: Pattern,
        coverage: MatchCoverage,
        armEnv: RuleEnv,
        env: RuleEnv,
    ) {
        when (pattern) {
            is WildcardPattern -> {}
            is VariablePattern -> armEnv.bind(pattern.name, coverage.residual())
            is LiteralPattern -> checkLiteralPattern(pattern, coverage, armEnv, env)
            is DataPattern -> checkDataPattern(pattern, coverage.core, armEnv, env)
        }
    }

    private fun checkLiteralPattern(
        pattern: LiteralPattern,
        coverage: MatchCoverage,
        armEnv: RuleEnv,
        env: RuleEnv,
    ) {
        if (pattern.literal is NullLiteral) {
            if (!coverage.isOptional) recordError(TypeError.NullNotAllowed(coverage.scrutinee, pattern.span))
            return
        }
        val litType = synth(pattern.literal, armEnv)
        if (!subtyping.isSubtype(litType, coverage.core, env)) {
            recordError(TypeError.TypeMismatch(litType, coverage.core, pattern.span))
        }
    }

    private fun checkDataPattern(
        pattern: DataPattern,
        core: RuleType,
        armEnv: RuleEnv,
        env: RuleEnv,
    ) {
        val valueType: RuleType =
            if (pattern.tag != null) {
                val ctor = if (core is TRef) env.lookupConstructor(pattern.tag, core.revision) else null
                if (core !is TRef || ctor == null || (core.name != pattern.tag && ctor.parentType != core.name)) {
                    recordError(TypeError.NotAConstructorOf(pattern.tag, core, pattern.span))
                    return
                }
                constructorInstance(pattern.tag, ctor, core, env)
            } else {
                core
            }
        pattern.binder?.let { armEnv.bind(it, valueType) }
        pattern.fields.forEach { fp ->
            val fieldType = projectFieldType(valueType, fp.field, fp.span, env)
            fp.binder?.let { armEnv.bind(it, fieldType) }
        }
    }

    private fun constructorInstance(
        name: String,
        ctor: ConstructorInfo<Nothing?>,
        core: TRef<Nothing?>,
        env: RuleEnv,
    ): TRef<Nothing?> {
        if (core.name == name) return core
        val parentDef = env.getTypeDef(core.name, core.revision)
        val argByName = parentDef.typeParams.map { it.skolem.name }.zip(core.typeArgs).toMap()
        return TRef(name, ctor.typeParams.map { argByName[it] ?: TBottom }, core.revision)
    }

    private fun joinArmTypes(
        armTypes: List<RuleType>,
        span: SourceSpan,
        env: RuleEnv,
    ): RuleType {
        var joined = armTypes.first()
        for (armType in armTypes.drop(1)) {
            joined = joinBranches(joined, armType, env) { a, b ->
                TypeError.CannotJoinMatchArms(a, b, span)
            } ?: return TBottom
        }
        return joined
    }

    private fun joinBranches(
        a: RuleType,
        b: RuleType,
        env: RuleEnv,
        error: (RuleType, RuleType) -> TypeError,
    ): RuleType? {
        if (a is TForall && b is TForall) {
            return when {
                groundPolyBranch(a, b, env) != null -> b
                groundPolyBranch(b, a, env) != null -> a
                else -> {
                    recordError(error(a, b))
                    null
                }
            }
        }
        val groundA = groundPolyBranch(a, b, env)
        val groundB = groundPolyBranch(b, a, env)
        if (groundA == null || groundB == null) {
            recordError(error(a, b))
            return null
        }
        val (joined, failures) = subtyping.lub(groundA, groundB, env)
        if (failures.isNotEmpty()) {
            recordError(error(groundA, groundB))
            return null
        }
        return joined
    }

    private fun synthIdent(
        expr: Ident,
        env: RuleEnv,
    ): RuleType = env.lookup(expr.name) ?: recordError(TypeError.UnboundVariable(expr.name, expr.span))

    private fun synthRecordLiteral(
        expr: RecordLiteral,
        env: RuleEnv,
    ): RuleType {
        val fields = mutableMapOf<String, RuleType>()
        for (field in expr.fields) {
            if (field.name in fields) {
                errors.add(TypeError.DuplicateField(field.name, expr.span))
            }
            if (field.typeAnnotation != null) {
                val fieldType = resolver.resolve(field.typeAnnotation, env)
                check(
                    field.value,
                    fieldType,
                    env,
                    listOf(ExpectedType(fieldType, ExpectedTypeSource.RecordField(field.name, field.value.span))),
                )
                fields[field.name] = fieldType
            } else {
                fields[field.name] = synth(field.value, env)
            }
        }
        return recordOf(fields)
    }

    private fun checkRecordLiteral(
        expr: RecordLiteral,
        expected: RuleType,
        env: RuleEnv,
        expectedSource: List<ExpectedType>,
    ) {
        if (expected !is TRecord) {
            synthAndCheckSubtype(expr, expected, env)
            return
        }
        val present = expr.fields.mapTo(mutableSetOf()) { it.name }
        for (field in expr.fields) {
            val fieldType = expected.fields[field.name]
            if (fieldType != null) {
                check(
                    field.value,
                    fieldType,
                    env,
                    expectedSource + ExpectedType(fieldType, ExpectedTypeSource.RecordField(field.name, field.value.span)),
                )
            } else {
                synth(field.value, env)
            }
        }
        for (name in expected.fields.keys) {
            if (name !in present) {
                recordError(TypeError.MissingField(name, expected, expr.span))
            }
        }
    }

    private fun synthFieldAccess(
        expr: FieldAccess,
        env: RuleEnv,
    ): RuleType {
        val target = synth(expr.target, env)
        return projectFieldType(target, expr.field, expr.span, env)
    }

    private fun safeApplyAsApply(expr: SafeApply): Apply =
        Apply(SafeFieldAccess(expr.target, expr.method, expr.span), expr.args, expr.span)

    private fun synthSafeFieldAccess(
        expr: SafeFieldAccess,
        env: RuleEnv,
    ): RuleType {
        val target = synth(expr.target, env)
        // A non-optional receiver can never be null, so `?.` is redundant and yields the bare field.
        if (target !is TOptional) return projectFieldType(target, expr.field, expr.span, env)
        return optionalOf(projectFieldType(target.type, expr.field, expr.span, env))
    }

    private fun projectFieldType(
        rec: RuleType,
        field: String,
        span: SourceSpan,
        env: RuleEnv,
    ): RuleType =
        when (rec) {
            // The receiver already errored (⊥); don't cascade a second error, just stay ⊥.
            TBottom -> TBottom
            is TRecord ->
                rec.fields[field] ?: recordError(TypeError.MissingField(field, rec, span))
            is TRef -> {
                val def = env.lookupTypeDef(rec.name, rec.revision)
                val fieldType = def?.iface?.fields?.get(field)
                if (def == null || fieldType == null) {
                    recordError(TypeError.MissingField(field, rec, span))
                } else {
                    substitute(
                        fieldType,
                        def.typeParams
                            .map { it.skolem }
                            .zip(rec.typeArgs)
                            .toMap(),
                    )
                }
            }
            else -> {
                recordError(TypeError.NotARecord(rec, field, span))
            }
        }

    private fun synthImplicitParam(
        expr: ImplicitParam,
        env: RuleEnv,
    ): RuleType =
        when (val ctx = env.implicitParamContext()) {
            is ImplicitParamContext.Available -> ctx.type
            is ImplicitParamContext.BlockedByNamedFunction -> {
                recordError(TypeError.ImplicitParamInNamedFunction(expr.span))
            }
            is ImplicitParamContext.BlockedByExplicitParams -> {
                recordError(TypeError.ImplicitParamWithExplicitParams(ctx.params, expr.span))
            }
            is ImplicitParamContext.NoExpectedType -> {
                recordError(TypeError.ImplicitParamWithoutExpectedType(expr.span))
            }
            is ImplicitParamContext.None -> {
                recordError(TypeError.ImplicitParamOutsideLambda(expr.span))
            }
        }

    private fun synthAscription(
        expr: Ascription,
        env: RuleEnv,
    ): RuleType {
        val type = resolver.resolve(expr.type, env)
        check(expr.expr, type, env)
        return type
    }

    private fun synthBinaryOp(
        expr: BinaryOp,
        env: RuleEnv,
    ): RuleType =
        when (expr.op) {
            Operator.Add, Operator.Sub, Operator.Mul, Operator.Div, Operator.Mod -> {
                check(expr.left, TNum, env)
                check(expr.right, TNum, env)
                TNum
            }
            Operator.Lt, Operator.LtEq, Operator.Gt, Operator.GtEq -> {
                check(expr.left, TNum, env)
                check(expr.right, TNum, env)
                TBool
            }
            Operator.And, Operator.Or -> {
                check(expr.left, TBool, env)
                check(expr.right, TBool, env)
                TBool
            }
            Operator.Eq, Operator.NotEq -> {
                check(expr.right, synth(expr.left, env), env)
                TBool
            }
        }

    private fun synthUnaryOp(
        expr: UnaryOp,
        env: RuleEnv,
    ): RuleType =
        when (expr.op) {
            UnaryOperator.Neg -> {
                check(expr.operand, TNum, env)
                TNum
            }
            UnaryOperator.Not -> {
                check(expr.operand, TBool, env)
                TBool
            }
        }

    private fun synthAndCheckSubtype(
        expr: Expr,
        expected: RuleType,
        env: RuleEnv,
    ) {
        val synthesized = synth(expr, env)
        if (synthesized is TForall) {
            // A polymorphic value meets the demand iff some instantiation fits — the solver decides.
            constraints
                .solveQuantified(synthesized, expected, expected, env)
                .errors
                .forEach { recordError(TypeError.TypeMismatch(it.lower, it.upper, expr.span)) }
        } else if (!subtyping.isSubtype(synthesized, expected, env)) {
            if ((synthesized is TNull || synthesized is TOptional) && expected !is TOptional) {
                recordError(TypeError.NullNotAllowed(expected, expr.span))
            } else {
                recordError(TypeError.TypeMismatch(synthesized, expected, expr.span))
            }
        }
    }

    /** Whether [type] mentions none of the [unknowns] — concrete enough to check an argument against. */
    private fun isGround(
        type: RuleType,
        unknowns: Set<TSkolem>,
    ): Boolean =
        when (type) {
            is TSkolem -> type !in unknowns
            is TFun -> type.params.all { isGround(it, unknowns) } && isGround(type.result, unknowns)
            is TRecord -> type.fields.values.all { isGround(it, unknowns) }
            is TOptional -> isGround(type.type, unknowns)
            is TRef -> type.typeArgs.all { isGround(it, unknowns) }
            is TForall -> isGround(type.body, unknowns - type.params)
            TNum, TStr, TBool, TUnit, TNull, TTop, TBottom -> true
        }

    private fun recordError(err: TypeError): RuleType {
        errors.add(err)
        return TBottom
    }
}
