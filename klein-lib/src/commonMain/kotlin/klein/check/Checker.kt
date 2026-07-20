package klein.check

import klein.SourceSpan
import klein.surface.*
import klein.check.Type.*

data class TypeCheckResult(
    val program: Program,
    val type: Type,
    val errors: List<TypeError>,
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
}

data class ExpectedType(
    val type: Type,
    val source: ExpectedTypeSource,
)

sealed class ExpectedTypeSource {
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

class Checker {
    private val errors = mutableListOf<TypeError>()
    private var skolemCounter = 0
    private val subtyping = Subtyping()
    private val constraints = ConstraintGenerator(subtyping)
    private val preprocessor = TypeDefPreprocessor(errors, ::freshSkolem, ::resolveType, subtyping)

    fun getErrors(): List<TypeError> = errors

    private fun freshSkolem(name: String): TSkolem = TSkolem(name, skolemCounter++)

    fun synthProgram(
        program: Program,
        env: TypeEnv = TypeEnv.empty(),
    ): Type = synthBlockStmts(program.stmts, env)

    fun synth(
        expr: Expr,
        env: TypeEnv,
    ): Type =
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
        expected: Type,
        env: TypeEnv,
        expectedSource: List<ExpectedType> = emptyList(),
    ) {
        when (expr) {
            is Lambda -> checkLambda(expr, expected, env)
            is RecordLiteral -> checkRecordLiteral(expr, expected, env, expectedSource)
            is IfThenElse -> checkIfThenElse(expr, expected, env, expectedSource)
            is Apply -> checkApply(expr, expected, env)
            is Match -> inferMatch(expr, expected, env, expectedSource)
            else -> synthAndCheckSubtype(expr, expected, env)
        }
    }

    private fun synthBlockStmts(
        stmts: List<Stmt>,
        env: TypeEnv,
    ): Type {
        preprocessor.process(stmts.filterIsInstance<TypeDef>(), env)

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

        var last: Type = TUnit
        for (stmt in stmts) {
            if (stmt is Expr) last = synth(stmt, env)
        }
        return last
    }

    private fun checkPatternVal(
        stmt: PatternVal,
        env: TypeEnv,
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
        env: TypeEnv,
    ) {
        // Pass 1: bind every function signature.
        // Yields a list of all functions that have a declared signature for checking in pass 2.
        // If no signature is declared, and the function is not recursive, synth & bind the type immediately.
        val pendingChecks: List<Triple<FunDef, TypeEnv, Type>> =
            funDefs.mapNotNull { funDef ->
                val fnEnv = env.child(ImplicitParamContext.BlockedByNamedFunction)
                reportDuplicateParams(funDef.params)
                introduceTypeVars(funDef.params.mapNotNull { it.typeAnnotation } + listOfNotNull(funDef.returnType), fnEnv)
                val paramTypes =
                    funDef.params.map { param ->
                        if (param.typeAnnotation != null) {
                            resolveType(param.typeAnnotation, fnEnv)
                        } else {
                            recordError(TypeError.MissingParamAnnotation(param.name, param.span))
                        }
                    }
                funDef.params.zip(paramTypes).forEach { (param, type) -> if (!param.isDiscard) fnEnv.bind(param.name, type) }
                val returnType =
                    when {
                        funDef.returnType != null -> resolveType(funDef.returnType, fnEnv)
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
        env: TypeEnv,
    ) {
        if (stmt.typeAnnotation != null) {
            val sigEnv = env.child()
            introduceTypeVars(listOf(stmt.typeAnnotation), sigEnv)
            val t = resolveType(stmt.typeAnnotation, sigEnv)
            check(stmt.value, t, sigEnv, listOf(ExpectedType(t, ExpectedTypeSource.Binding(stmt.name, stmt.span))))
            env.bind(stmt.name, quantify(sigEnv.localTypeVars(), t))
        } else {
            env.bind(stmt.name, synth(stmt.value, env))
        }
    }

    private fun reportDuplicateParams(params: List<Param>) {
        val seen = mutableSetOf<String>()
        params.forEach { param ->
            if (!param.isDiscard && !seen.add(param.name)) recordError(TypeError.DuplicateParameter(param.name, param.span))
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
        env: TypeEnv,
    ): Type {
        reportDuplicateParams(expr.params)
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
                        resolveType(param.typeAnnotation, bodyEnv)
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
        expected: Type,
        env: TypeEnv,
    ) {
        if (expected !is TFun) {
            // No inward rule for a lambda against a non-function — fall back to subsumption.
            synthAndCheckSubtype(expr, expected, env)
            return
        }
        reportDuplicateParams(expr.params)
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
                    val annotated = resolveType(param.typeAnnotation, bodyEnv)
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
        env: TypeEnv,
    ): Type = inferApply(expr, null, env) // null = synth mode: no result demand, minimize R

    private fun checkApply(
        expr: Apply,
        expected: Type,
        env: TypeEnv,
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
        expected: Type?,
        env: TypeEnv,
    ): Type {
        // A safe method call `r?.m(args)` on an optional receiver short-circuits: apply the unwrapped
        // method and lift the result back to optional. Its demand, likewise, is against the optional
        // result. On a non-optional receiver `?.` is redundant, so it behaves as a plain call.
        val rawCallee = synth(expr.callee, env)
        val isNullableApply = expr.callee is SafeFieldAccess && rawCallee is TOptional
        val callee = if (isNullableApply) (rawCallee as TOptional).type else rawCallee
        val demand = if (isNullableApply) expected?.let { if (it is TOptional) it.type else it } else expected
        val scheme = callee as? TForall ?: TForall(emptySet(), callee)
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
                        emptyMap<TSkolem, Type>() to emptyList()
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
        branch: Type,
        other: Type,
        env: TypeEnv,
    ): Type? {
        if (branch !is TForall) return branch
        // Fit against the other branch — or, when it is itself polymorphic, a rigid skolemization of
        // it, so success means "fits every instantiation" (i.e. `branch <: other`).
        val target = if (other is TForall) skolemize(other) else other
        val solved = constraints.solveQuantified(branch, target, target, env)
        return if (solved.errors.isEmpty()) solved.type else null
    }

    private fun skolemize(forall: TForall): Type =
        substitute(forall.body, forall.params.associateWith { freshSkolem(it.name) })

    private fun synthIfThenElse(
        expr: IfThenElse,
        env: TypeEnv,
    ): Type {
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
        expected: Type,
        env: TypeEnv,
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
        expected: Type?,
        env: TypeEnv,
        expectedSource: List<ExpectedType>,
    ): Type {
        val scrutineeType = synth(expr.scrutinee, env)
        if (scrutineeType is TBottom) return TBottom
        val coverage =
            MatchCoverage.of(scrutineeType, env)
                ?: return recordError(TypeError.CannotMatchOn(scrutineeType, expr.scrutinee.span))

        val armTypes = mutableListOf<Type>()
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
        armEnv: TypeEnv,
        env: TypeEnv,
    ) {
        when (pattern) {
            is WildcardPattern -> {}
            is VariablePattern -> armEnv.bind(pattern.name, coverage.residual())
            is LiteralPattern -> checkLiteralPattern(pattern, coverage, armEnv, env)
            is ConstructorPattern -> checkConstructorPattern(pattern, coverage.core, armEnv, env)
            is RecordPattern ->
                pattern.fields.forEach { fp ->
                    val fieldType = projectFieldType(coverage.core, fp.field, fp.span, env)
                    fp.binder?.let { armEnv.bind(it, fieldType) }
                }
        }
    }

    private fun checkLiteralPattern(
        pattern: LiteralPattern,
        coverage: MatchCoverage,
        armEnv: TypeEnv,
        env: TypeEnv,
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

    private fun checkConstructorPattern(
        pattern: ConstructorPattern,
        core: Type,
        armEnv: TypeEnv,
        env: TypeEnv,
    ) {
        val ctor = env.lookupConstructor(pattern.name)
        if (ctor == null || core !is TRef || (core.name != pattern.name && ctor.parentType != core.name)) {
            recordError(TypeError.NotAConstructorOf(pattern.name, core, pattern.span))
            return
        }
        val ctorRef = constructorInstance(pattern.name, ctor, core, env)
        pattern.binder?.let { armEnv.bind(it, ctorRef) }
        pattern.record?.fields?.forEach { fp ->
            val fieldType = projectFieldType(ctorRef, fp.field, fp.span, env)
            fp.binder?.let { armEnv.bind(it, fieldType) }
        }
    }

    private fun constructorInstance(
        name: String,
        ctor: ConstructorInfo,
        core: TRef,
        env: TypeEnv,
    ): TRef {
        if (core.name == name) return core
        val parentDef = env.getTypeDef(core.name)
        val argByName = parentDef.typeParams.map { it.skolem.name }.zip(core.typeArgs).toMap()
        return TRef(name, ctor.typeParams.map { argByName[it] ?: TBottom })
    }

    private fun joinArmTypes(
        armTypes: List<Type>,
        span: SourceSpan,
        env: TypeEnv,
    ): Type {
        var joined = armTypes.first()
        for (armType in armTypes.drop(1)) {
            joined = joinBranches(joined, armType, env) { a, b ->
                TypeError.CannotJoinMatchArms(a, b, span)
            } ?: return TBottom
        }
        return joined
    }

    private fun joinBranches(
        a: Type,
        b: Type,
        env: TypeEnv,
        error: (Type, Type) -> TypeError,
    ): Type? {
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
        env: TypeEnv,
    ): Type = env.lookup(expr.name) ?: recordError(TypeError.UnboundVariable(expr.name, expr.span))

    private fun synthRecordLiteral(
        expr: RecordLiteral,
        env: TypeEnv,
    ): Type {
        val fields = mutableMapOf<String, Type>()
        for (field in expr.fields) {
            if (field.name in fields) {
                errors.add(TypeError.DuplicateField(field.name, expr.span))
            }
            if (field.typeAnnotation != null) {
                val fieldType = resolveType(field.typeAnnotation, env)
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
        expected: Type,
        env: TypeEnv,
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
        env: TypeEnv,
    ): Type {
        val target = synth(expr.target, env)
        return projectFieldType(target, expr.field, expr.span, env)
    }

    private fun synthSafeFieldAccess(
        expr: SafeFieldAccess,
        env: TypeEnv,
    ): Type {
        val target = synth(expr.target, env)
        // A non-optional receiver can never be null, so `?.` is redundant and yields the bare field.
        if (target !is TOptional) return projectFieldType(target, expr.field, expr.span, env)
        return optionalOf(projectFieldType(target.type, expr.field, expr.span, env))
    }

    private fun projectFieldType(
        rec: Type,
        field: String,
        span: SourceSpan,
        env: TypeEnv,
    ): Type =
        when (rec) {
            // The receiver already errored (⊥); don't cascade a second error, just stay ⊥.
            TBottom -> TBottom
            is TRecord ->
                rec.fields[field] ?: recordError(TypeError.MissingField(field, rec, span))
            is TRef -> {
                val def = env.lookupTypeDef(rec.name)
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
        env: TypeEnv,
    ): Type =
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
        env: TypeEnv,
    ): Type {
        val type = resolveType(expr.type, env)
        check(expr.expr, type, env)
        return type
    }

    private fun synthBinaryOp(
        expr: BinaryOp,
        env: TypeEnv,
    ): Type =
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
        env: TypeEnv,
    ): Type =
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
        expected: Type,
        env: TypeEnv,
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

    private fun resolveType(
        typeExpr: TypeExpr,
        env: TypeEnv,
    ): Type =
        when (typeExpr) {
            is TypeName ->
                when (typeExpr.name) {
                    "Num" -> TNum
                    "String" -> TStr
                    "Bool" -> TBool
                    "Unit" -> TUnit
                    "Any" -> TTop
                    "Nothing" -> TBottom
                    else -> {
                        val def = env.lookupTypeDef(typeExpr.name)
                        when {
                            def == null -> recordError(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                            def.typeParams.isNotEmpty() -> {
                                recordError(TypeError.TypeArityMismatch(typeExpr.name, def.typeParams.size, 0, typeExpr.span))
                                TRef(typeExpr.name, emptyList())
                            }
                            else -> TRef(typeExpr.name, emptyList())
                        }
                    }
                }
            is FunctionTypeExpr ->
                TFun(typeExpr.paramTypes.map { resolveType(it, env) }, resolveType(typeExpr.returnType, env))
            is RecordTypeExpr ->
                recordOf(typeExpr.fields.associate { (name, t) -> name to resolveType(t, env) })
            is OptionalTypeExpr ->
                optionalOf(resolveType(typeExpr.inner, env))
            is TupleTypeExpr ->
                if (typeExpr.elements.isEmpty()) {
                    TUnit
                } else {
                    TRecord(typeExpr.elements.mapIndexed { i, t -> "_${i + 1}" to resolveType(t, env) }.toMap())
                }
            is TypeVar ->
                env.lookupTypeVar(typeExpr.name)
                    ?: recordError(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
            is AppliedTypeExpr -> {
                val info = env.lookupTypeDef(typeExpr.name)
                val args = typeExpr.args.map { resolveType(it, env) }
                when {
                    info == null -> recordError(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                    info.typeParams.size != args.size -> {
                        recordError(TypeError.TypeArityMismatch(typeExpr.name, info.typeParams.size, args.size, typeExpr.span))
                        TRef(typeExpr.name, args)
                    }
                    else -> TRef(typeExpr.name, args)
                }
            }
            // Recover with Any: returning the Bottom error-default would also fail whatever value
            // the rejected annotation governs, burying the real error under a spurious mismatch.
            is UnionTypeExpr -> {
                recordError(TypeError.AnonymousUnionType(typeExpr.span))
                TTop
            }
            is IntersectionTypeExpr -> {
                recordError(TypeError.AnonymousIntersectionType(typeExpr.span))
                TTop
            }
        }

    /** Introduce each not-yet-in-scope type variable in [annotations] as a fresh skolem at [sigEnv] —
     *  the binder owns where its `'T`s are quantified; [resolveType] only ever *references* them. */
    private fun introduceTypeVars(
        annotations: List<TypeExpr>,
        sigEnv: TypeEnv,
    ) {
        annotations.forEach { annotation ->
            collectTypeVarNames(annotation).forEach { name ->
                if (sigEnv.lookupTypeVar(name) == null) sigEnv.bindTypeVar(name, freshSkolem(name))
            }
        }
    }

    /** `∀params. body`, or just `body` when there's nothing to quantify. */
    private fun quantify(
        params: Set<TSkolem>,
        body: Type,
    ): Type = if (params.isEmpty()) body else TForall(params, body)

    /** Whether [type] mentions none of the [unknowns] — concrete enough to check an argument against. */
    private fun isGround(
        type: Type,
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

    private fun recordError(err: TypeError): Type {
        errors.add(err)
        return TBottom
    }
}
