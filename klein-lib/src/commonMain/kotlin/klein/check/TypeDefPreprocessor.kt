package klein.check

import klein.Revision
import klein.surface.*

class TypeDefPreprocessor(
    private val errors: MutableList<TypeError>,
    private val freshSkolem: (String) -> Type.TSkolem,
    private val resolveType: (TypeExpr<*>, TypeEnv) -> Type,
    private val subtyping: Subtyping,
) {
    fun process(
        typeDefs: List<TypeDef<*>>,
        env: TypeEnv,
    ) {
        if (typeDefs.isEmpty()) return
        val valid = registerPlaceholders(typeDefs, env)
        computeVariance(valid, env)
        buildIfaces(env)
        buildParentIfaces(env)
        bindConstructors(env)
    }

    private fun registerPlaceholders(
        typeDefs: List<TypeDef<*>>,
        env: TypeEnv,
    ): List<TypeDef<*>> {
        val valid = mutableListOf<TypeDef<*>>()
        for (typeDef in typeDefs) {
            val revision = typeDef.revision ?: Revision(1)
            val typeDisplay = revisionedName(typeDef.name, revision)
            if (typeDef.name in PRIMITIVE_TYPE_NAMES) {
                errors.add(TypeError.ShadowsBuiltinType(typeDisplay, typeDef.span))
                continue
            }
            if (env.lookupTypeDef(typeDef.name, revision) != null) {
                errors.add(TypeError.DuplicateBinding(typeDisplay, typeDef.span))
                continue
            }

            valid.add(typeDef)
            val typeParams = typeDef.typeParams.map { TypeParamInfo(Variance.Bivariant, freshSkolem(it)) }
            env.registerTypeDef(TypeDefInfo(typeDef.name, revision, typeParams, Type.TRecord(emptyMap()), typeDef.span))

            for (ctor in typeDef.constructors) {
                val ctorDisplay = revisionedName(ctor.name, revision)
                if (ctor.name in PRIMITIVE_TYPE_NAMES) {
                    errors.add(TypeError.ShadowsBuiltinType(ctorDisplay, ctor.span))
                    continue
                }
                if (ctor.name == typeDef.name && typeDef.constructors.size > 1) {
                    errors.add(TypeError.DuplicateBinding(ctorDisplay, ctor.span))
                    continue
                }
                if (env.lookupConstructor(ctor.name, revision) != null) {
                    errors.add(TypeError.DuplicateBinding(ctorDisplay, ctor.span))
                    continue
                }

                val usedTypeVars = ctor.fields.flatMap { collectTypeVarNames(it.type) }.toSet()
                val declared = typeDef.typeParams.toSet()
                for (tv in usedTypeVars) {
                    if (tv !in declared) errors.add(TypeError.UndeclaredTypeParam(tv, typeDisplay, ctor.span))
                }
                val ctorTypeParams = typeParams.filter { it.skolem.name in usedTypeVars }

                env.registerConstructor(
                    ConstructorInfo(ctor.name, revision, ctorTypeParams.map { it.skolem.name }, ctor.fields, typeDef.name, ctor.span),
                )
                if (ctor.name != typeDef.name) {
                    env.registerTypeDef(TypeDefInfo(ctor.name, revision, ctorTypeParams, Type.TRecord(emptyMap()), ctor.span))
                }
            }
        }
        return valid
    }

    private fun computeVariance(
        typeDefs: List<TypeDef<*>>,
        env: TypeEnv,
    ) {
        if (typeDefs.isEmpty()) return

        val allTypeDefs = env.allTypeDefs()
        val allConstructors = env.allConstructors()
        val variances = mutableMapOf<Triple<String, Revision, String>, Variance>()

        for (info in allTypeDefs) {
            for (param in info.typeParams) variances[Triple(info.name, info.revision, param.skolem.name)] = Variance.Bivariant
        }

        fun update(
            typeExpr: TypeExpr<*>,
            owner: Pair<String, Revision>,
            polarity: Variance,
        ): Boolean =
            when (typeExpr) {
                is TypeVar -> {
                    val key = Triple(owner.first, owner.second, typeExpr.name)
                    val current = variances[key] ?: return false
                    val merged = current.meet(polarity)
                    if (merged != current) {
                        variances[key] = merged
                        true
                    } else {
                        false
                    }
                }

                is TypeName -> false

                is AppliedTypeExpr -> {
                    val refRevision = typeExpr.revision ?: Revision(1)
                    val refInfo = env.lookupTypeDef(typeExpr.name, refRevision) ?: return false
                    var changed = false
                    for ((i, arg) in typeExpr.args.withIndex()) {
                        val paramName = refInfo.typeParams.getOrNull(i)?.skolem?.name ?: break
                        val paramVariance = variances[Triple(typeExpr.name, refRevision, paramName)] ?: break
                        val argPolarity =
                            when (paramVariance) {
                                Variance.Bivariant, Variance.Covariant -> polarity
                                Variance.Contravariant -> polarity.flip()
                                Variance.Invariant -> Variance.Invariant
                            }
                        changed = update(arg, owner, argPolarity) || changed
                    }
                    changed
                }

                is FunctionTypeExpr -> {
                    var changed = false
                    for (param in typeExpr.paramTypes) changed = update(param, owner, polarity.flip()) || changed
                    changed = update(typeExpr.returnType, owner, polarity) || changed
                    changed
                }

                is TupleTypeExpr -> {
                    var changed = false
                    for (element in typeExpr.elements) changed = update(element, owner, polarity) || changed
                    changed
                }

                is RecordTypeExpr -> {
                    var changed = false
                    for ((_, fieldType) in typeExpr.fields) changed = update(fieldType, owner, polarity) || changed
                    changed
                }

                is OptionalTypeExpr -> update(typeExpr.inner, owner, polarity)
            }

        var changed = true
        while (changed) {
            changed = false
            for (ctor in allConstructors) {
                for (field in ctor.fields) changed = update(field.type, ctor.name to ctor.revision, Variance.Covariant) || changed
            }
        }

        for (ctor in allConstructors) {
            for (param in ctor.typeParams) {
                val parentKey = Triple(ctor.parentType, ctor.revision, param)
                val ctorVar = variances[Triple(ctor.name, ctor.revision, param)] ?: Variance.Bivariant
                variances[parentKey] = (variances[parentKey] ?: Variance.Bivariant).meet(ctorVar)
            }
        }

        for ((key, variance) in variances.toMap()) {
            if (variance == Variance.Bivariant) variances[key] = Variance.Invariant
        }

        for (info in allTypeDefs) {
            val updated =
                info.typeParams.map { param ->
                    param.copy(variance = variances[Triple(info.name, info.revision, param.skolem.name)] ?: Variance.Invariant)
                }
            env.updateTypeDef(info.copy(typeParams = updated))
        }
    }

    private fun buildIfaces(env: TypeEnv) {
        for (ctor in env.allConstructors()) {
            val ctorTypeDef = env.getTypeDef(ctor.name, ctor.revision)
            val ctorEnv = env.child()
            ctorTypeDef.typeParams.forEach { ctorEnv.bindTypeVar(it.skolem.name, it.skolem) }
            val fields = ctor.fields.associate { it.name to resolveType(it.type, ctorEnv) }
            env.updateTypeDef(ctorTypeDef.copy(iface = Type.TRecord(fields)))
        }
    }

    private fun buildParentIfaces(env: TypeEnv) {
        for ((parent, ctors) in env.allConstructors().groupBy { it.parentType to it.revision }) {
            val (parentName, revision) = parent
            if (ctors.size == 1 && ctors[0].name == parentName) continue
            val parentDef = env.lookupTypeDef(parentName, revision) ?: continue
            val ctorIfaces = ctors.map { env.getTypeDef(it.name, it.revision).iface }
            val commonNames = ctorIfaces.map { it.fields.keys }.reduce { a, b -> a intersect b }
            // A field shared by every constructor is readable through the sum only if its types
            // cleanly join. One whose join is degenerate (no common supertype) is erased from the
            // interface — accessing it is then a missing-field error, like a non-shared field.
            val fields =
                commonNames.mapNotNull { name ->
                    val (joined, failures) =
                        ctorIfaces
                            .map { it.fields.getValue(name) to emptyList<Failure>() }
                            .reduce { (accType, accFailures), (fieldType, _) ->
                                val (merged, mergeFailures) = subtyping.lub(accType, fieldType, env)
                                merged to (accFailures + mergeFailures)
                            }
                    if (failures.isEmpty()) name to joined else null
                }.toMap()
            env.updateTypeDef(parentDef.copy(iface = Type.TRecord(fields)))
        }
    }


    private fun bindConstructors(env: TypeEnv) {
        for (ctor in env.allConstructors()) {
            val ctorTypeDef = env.getTypeDef(ctor.name, ctor.revision)
            val skolems = ctorTypeDef.typeParams.map { it.skolem }
            val resultType = Type.TRef(ctor.name, skolems, ctor.revision)
            val ctorType =
                if (ctor.fields.isEmpty()) {
                    resultType
                } else {
                    val fieldTypes = ctor.fields.map { ctorTypeDef.iface.fields.getValue(it.name) }
                    Type.TFun(fieldTypes, resultType, ctor.fields.map { it.name })
                }
            val scheme = if (skolems.isEmpty()) ctorType else Type.TForall(skolems.toSet(), ctorType)
            env.bind(ctor.name, ctor.revision, scheme)
        }
    }

    companion object {
        val PRIMITIVE_TYPE_NAMES = setOf("Num", "String", "Bool", "Unit", "Any", "Nothing")
    }
}

internal fun collectTypeVarNames(typeExpr: TypeExpr<*>): List<String> =
    when (typeExpr) {
        is TypeVar -> listOf(typeExpr.name)
        is FunctionTypeExpr -> typeExpr.paramTypes.flatMap { collectTypeVarNames(it) } + collectTypeVarNames(typeExpr.returnType)
        is RecordTypeExpr -> typeExpr.fields.flatMap { collectTypeVarNames(it.second) }
        is OptionalTypeExpr -> collectTypeVarNames(typeExpr.inner)
        is TupleTypeExpr -> typeExpr.elements.flatMap { collectTypeVarNames(it) }
        is AppliedTypeExpr -> typeExpr.args.flatMap { collectTypeVarNames(it) }
        is TypeName -> emptyList()
    }
