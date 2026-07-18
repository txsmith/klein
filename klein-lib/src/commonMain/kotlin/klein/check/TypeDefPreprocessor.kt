package klein.check

import klein.*

class TypeDefPreprocessor(
    private val errors: MutableList<TypeError>,
    private val freshSkolem: (String) -> Type.TSkolem,
    private val resolveType: (TypeExpr, TypeEnv) -> Type,
    private val subtyping: Subtyping,
) {
    fun process(
        typeDefs: List<TypeDef>,
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
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ): List<TypeDef> {
        val valid = mutableListOf<TypeDef>()
        for (typeDef in typeDefs) {
            if (typeDef.name in PRIMITIVE_TYPE_NAMES) {
                errors.add(TypeError.ShadowsBuiltinType(typeDef.name, typeDef.span))
                continue
            }
            if (env.lookupTypeDef(typeDef.name) != null) {
                errors.add(TypeError.DuplicateBinding(typeDef.name, typeDef.span))
                continue
            }

            valid.add(typeDef)
            val typeParams = typeDef.typeParams.map { TypeParamInfo(Variance.Bivariant, freshSkolem(it)) }
            env.registerTypeDef(TypeDefInfo(typeDef.name, typeParams, Type.TRecord(emptyMap()), typeDef.span))

            for (ctor in typeDef.constructors) {
                if (ctor.name in PRIMITIVE_TYPE_NAMES) {
                    errors.add(TypeError.ShadowsBuiltinType(ctor.name, ctor.span))
                    continue
                }
                if (ctor.name == typeDef.name && typeDef.constructors.size > 1) {
                    errors.add(TypeError.DuplicateBinding(ctor.name, ctor.span))
                    continue
                }
                if (env.lookupConstructor(ctor.name) != null) {
                    errors.add(TypeError.DuplicateBinding(ctor.name, ctor.span))
                    continue
                }

                val usedTypeVars = ctor.fields.flatMap { collectTypeVarNames(it.type) }.toSet()
                val declared = typeDef.typeParams.toSet()
                for (tv in usedTypeVars) {
                    if (tv !in declared) errors.add(TypeError.UndeclaredTypeParam(tv, typeDef.name, ctor.span))
                }
                val ctorTypeParams = typeParams.filter { it.skolem.name in usedTypeVars }

                env.registerConstructor(
                    ConstructorInfo(ctor.name, ctorTypeParams.map { it.skolem.name }, ctor.fields, typeDef.name, ctor.span),
                )
                if (ctor.name != typeDef.name) {
                    env.registerTypeDef(TypeDefInfo(ctor.name, ctorTypeParams, Type.TRecord(emptyMap()), ctor.span))
                }
            }
        }
        return valid
    }

    private fun computeVariance(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ) {
        if (typeDefs.isEmpty()) return

        val allTypeDefs = env.allTypeDefs()
        val allConstructors = env.allConstructors()
        val variances = mutableMapOf<Pair<String, String>, Variance>()

        for (info in allTypeDefs) {
            for (param in info.typeParams) variances[info.name to param.skolem.name] = Variance.Bivariant
        }

        fun update(
            typeExpr: TypeExpr,
            ownerName: String,
            polarity: Variance,
        ): Boolean =
            when (typeExpr) {
                is TypeVar -> {
                    val current = variances[ownerName to typeExpr.name] ?: return false
                    val merged = current.meet(polarity)
                    if (merged != current) {
                        variances[ownerName to typeExpr.name] = merged
                        true
                    } else {
                        false
                    }
                }

                is TypeName -> false

                is AppliedTypeExpr -> {
                    val refInfo = env.lookupTypeDef(typeExpr.name) ?: return false
                    var changed = false
                    for ((i, arg) in typeExpr.args.withIndex()) {
                        val paramName = refInfo.typeParams.getOrNull(i)?.skolem?.name ?: break
                        val paramVariance = variances[typeExpr.name to paramName] ?: break
                        val argPolarity =
                            when (paramVariance) {
                                Variance.Bivariant, Variance.Covariant -> polarity
                                Variance.Contravariant -> polarity.flip()
                                Variance.Invariant -> Variance.Invariant
                            }
                        changed = update(arg, ownerName, argPolarity) || changed
                    }
                    changed
                }

                is FunctionTypeExpr -> {
                    var changed = false
                    for (param in typeExpr.paramTypes) changed = update(param, ownerName, polarity.flip()) || changed
                    changed = update(typeExpr.returnType, ownerName, polarity) || changed
                    changed
                }

                is TupleTypeExpr -> {
                    var changed = false
                    for (element in typeExpr.elements) changed = update(element, ownerName, polarity) || changed
                    changed
                }

                is RecordTypeExpr -> {
                    var changed = false
                    for ((_, fieldType) in typeExpr.fields) changed = update(fieldType, ownerName, polarity) || changed
                    changed
                }

                is OptionalTypeExpr -> update(typeExpr.inner, ownerName, polarity)

                is UnionTypeExpr -> update(typeExpr.left, ownerName, polarity) or update(typeExpr.right, ownerName, polarity)
                is IntersectionTypeExpr -> update(typeExpr.left, ownerName, polarity) or update(typeExpr.right, ownerName, polarity)
            }

        var changed = true
        while (changed) {
            changed = false
            for (ctor in allConstructors) {
                for (field in ctor.fields) changed = update(field.type, ctor.name, Variance.Covariant) || changed
            }
        }

        for (ctor in allConstructors) {
            for (param in ctor.typeParams) {
                val parentKey = ctor.parentType to param
                val ctorVar = variances[ctor.name to param] ?: Variance.Bivariant
                variances[parentKey] = (variances[parentKey] ?: Variance.Bivariant).meet(ctorVar)
            }
        }

        for ((key, variance) in variances.toMap()) {
            if (variance == Variance.Bivariant) variances[key] = Variance.Invariant
        }

        for (info in allTypeDefs) {
            val updated =
                info.typeParams.map { param ->
                    param.copy(variance = variances[info.name to param.skolem.name] ?: Variance.Invariant)
                }
            env.updateTypeDef(info.copy(typeParams = updated))
        }
    }

    private fun buildIfaces(env: TypeEnv) {
        for (ctor in env.allConstructors()) {
            val ctorTypeDef = env.getTypeDef(ctor.name)
            val ctorEnv = env.child()
            ctorTypeDef.typeParams.forEach { ctorEnv.bindTypeVar(it.skolem.name, it.skolem) }
            val fields = ctor.fields.associate { it.name to resolveType(it.type, ctorEnv) }
            env.updateTypeDef(ctorTypeDef.copy(iface = Type.TRecord(fields)))
        }
    }

    private fun buildParentIfaces(env: TypeEnv) {
        for ((parentName, ctors) in env.allConstructors().groupBy { it.parentType }) {
            if (ctors.size == 1 && ctors[0].name == parentName) continue
            val parentDef = env.lookupTypeDef(parentName) ?: continue
            val ctorIfaces = ctors.map { env.getTypeDef(it.name).iface }
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
            val ctorTypeDef = env.getTypeDef(ctor.name)
            val skolems = ctorTypeDef.typeParams.map { it.skolem }
            val resultType = Type.TRef(ctor.name, skolems)
            val ctorType =
                if (ctor.fields.isEmpty()) {
                    resultType
                } else {
                    val fieldTypes = ctor.fields.map { ctorTypeDef.iface.fields.getValue(it.name) }
                    Type.TFun(fieldTypes, resultType, ctor.fields.map { it.name })
                }
            val scheme = if (skolems.isEmpty()) ctorType else Type.TForall(skolems.toSet(), ctorType)
            env.bind(ctor.name, scheme)
        }
    }

    companion object {
        val PRIMITIVE_TYPE_NAMES = setOf("Num", "String", "Bool", "Unit", "Any", "Nothing")
    }
}

internal fun collectTypeVarNames(typeExpr: TypeExpr): List<String> =
    when (typeExpr) {
        is TypeVar -> listOf(typeExpr.name)
        is FunctionTypeExpr -> typeExpr.paramTypes.flatMap { collectTypeVarNames(it) } + collectTypeVarNames(typeExpr.returnType)
        is RecordTypeExpr -> typeExpr.fields.flatMap { collectTypeVarNames(it.second) }
        is OptionalTypeExpr -> collectTypeVarNames(typeExpr.inner)
        is TupleTypeExpr -> typeExpr.elements.flatMap { collectTypeVarNames(it) }
        is AppliedTypeExpr -> typeExpr.args.flatMap { collectTypeVarNames(it) }
        is UnionTypeExpr -> collectTypeVarNames(typeExpr.left) + collectTypeVarNames(typeExpr.right)
        is IntersectionTypeExpr -> collectTypeVarNames(typeExpr.left) + collectTypeVarNames(typeExpr.right)
        is TypeName -> emptyList()
    }
