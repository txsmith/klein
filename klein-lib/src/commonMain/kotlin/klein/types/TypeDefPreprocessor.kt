package klein.types

import klein.*
import klein.types.SimpleType.*

class TypeDefPreprocessor(
    private val subtyping: Subtyping,
    private val errors: MutableList<TypeError>,
) {
    fun process(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ) {
        if (typeDefs.isEmpty()) return

        val validTypeDefs = registerPlaceholders(typeDefs, env)

        resolveNames(env)
        if (errors.isNotEmpty()) return

        computeVariance(validTypeDefs, env)

        buildIfaces(env)

        bindConstructors(env)
    }

    private fun registerPlaceholders(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ): List<TypeDef> {
        val validTypeDefs = mutableListOf<TypeDef>()
        val polyEnv = env.enterBindingScope()
        for (typeDef in typeDefs) {
            if (typeDef.name in SimpleType.primitiveNames) {
                errors.add(TypeError.ShadowsBuiltinType(typeDef.name, typeDef.span))
                continue
            }
            if (env.lookupTypeDef(typeDef.name) != null) {
                errors.add(TypeError.DuplicateBinding(typeDef.name, typeDef.span))
                continue
            }

            validTypeDefs.add(typeDef)
            val typeParams = typeDef.typeParams.map { TypeParamInfo(it, Variance.Bivariant, polyEnv.freshVar()) }
            val isSingleConstructorType = typeDef.constructors.size == 1 && typeDef.constructors[0].name == typeDef.name
            val parentIface: SimpleType = if (isSingleConstructorType) TRecord(emptyMap()) else polyEnv.freshVar()

            env.registerTypeDef(
                TypeDefInfo(
                    name = typeDef.name,
                    typeParams = typeParams,
                    iface = TypeBinding.Poly(0, parentIface),
                    span = typeDef.span,
                ),
            )

            for (constructor in typeDef.constructors) {
                if (constructor.name in SimpleType.primitiveNames) {
                    errors.add(TypeError.ShadowsBuiltinType(constructor.name, constructor.span))
                    continue
                }

                if (constructor.name == typeDef.name && typeDef.constructors.size > 1) {
                    errors.add(TypeError.DuplicateBinding(constructor.name, constructor.span))
                    continue
                }

                if (env.lookupConstructor(constructor.name) != null) {
                    errors.add(TypeError.DuplicateBinding(constructor.name, constructor.span))
                    continue
                }

                val usedTypeVars = constructor.fields.flatMap { collectTypeVars(it.type) }.toSet()
                val declaredTypeParams = typeDef.typeParams.toSet()

                for (typeVar in usedTypeVars) {
                    if (typeVar !in declaredTypeParams) {
                        errors.add(TypeError.UndeclaredTypeParam(typeVar, typeDef.name, constructor.span))
                    }
                }

                val ctorTypeParams = typeParams.filter { it.name in usedTypeVars }

                env.registerConstructor(
                    ConstructorInfo(
                        name = constructor.name,
                        typeParams = ctorTypeParams.map { it.name },
                        fields = constructor.fields,
                        parentType = typeDef.name,
                        span = constructor.span,
                    ),
                )
                env.registerFunDef(FunDefInfo(constructor.name, constructor.fields.map { it.name }))

                if (constructor.name != typeDef.name) {
                    env.registerTypeDef(
                        TypeDefInfo(
                            name = constructor.name,
                            typeParams = ctorTypeParams,
                            iface = TypeBinding.Poly(0, TRecord(emptyMap())),
                            span = constructor.span,
                        ),
                    )
                }
            }
        }
        return validTypeDefs
    }

    private fun resolveNames(env: TypeEnv) {
        for (ctor in env.allConstructors()) {
            for (field in ctor.fields) {
                resolveTypeExpr(field.type, env)
            }
        }
    }

    private fun resolveTypeExpr(
        typeExpr: TypeExpr,
        env: TypeEnv,
    ) {
        when (typeExpr) {
            is TypeVar -> {}
            is TypeName -> {
                if (typeExpr.name in SimpleType.primitiveNames) return
                val typeDef = env.lookupTypeDef(typeExpr.name)
                if (typeDef == null) {
                    errors.add(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                } else if (typeDef.typeParams.isNotEmpty()) {
                    errors.add(TypeError.ArityMismatch(typeDef.typeParams.size, 0, typeExpr.span))
                }
            }
            is AppliedTypeExpr -> {
                val typeDef = env.lookupTypeDef(typeExpr.name)
                if (typeDef == null) {
                    errors.add(TypeError.UnboundVariable(typeExpr.name, typeExpr.span))
                } else if (typeExpr.args.size != typeDef.typeParams.size) {
                    errors.add(TypeError.ArityMismatch(typeDef.typeParams.size, typeExpr.args.size, typeExpr.span))
                }
                for (arg in typeExpr.args) {
                    resolveTypeExpr(arg, env)
                }
            }
            is FunctionTypeExpr -> {
                for (param in typeExpr.paramTypes) resolveTypeExpr(param, env)
                resolveTypeExpr(typeExpr.returnType, env)
            }
            is TupleTypeExpr -> {
                for (element in typeExpr.elements) {
                    resolveTypeExpr(element, env)
                }
            }
            is RecordTypeExpr -> {
                for ((_, fieldType) in typeExpr.fields) {
                    resolveTypeExpr(fieldType, env)
                }
            }
        }
    }

    private fun collectTypeVars(typeExpr: TypeExpr): Set<String> =
        when (typeExpr) {
            is TypeVar -> setOf(typeExpr.name)
            is TypeName -> emptySet()
            is AppliedTypeExpr -> typeExpr.args.flatMap { collectTypeVars(it) }.toSet()
            is FunctionTypeExpr -> typeExpr.paramTypes.flatMap { collectTypeVars(it) }.toSet() + collectTypeVars(typeExpr.returnType)
            is TupleTypeExpr -> typeExpr.elements.flatMap { collectTypeVars(it) }.toSet()
            is RecordTypeExpr -> typeExpr.fields.flatMap { collectTypeVars(it.second) }.toSet()
        }

    private fun computeVariance(
        typeDefs: List<TypeDef>,
        env: TypeEnv,
    ) {
        if (typeDefs.isEmpty()) return

        val allTypeDefs = env.allTypeDefs()
        val allConstructors = env.allConstructors()

        val variances = mutableMapOf<Pair<String, String>, Variance>()

        for (typeDefInfo in allTypeDefs) {
            for (param in typeDefInfo.typeParams) {
                variances[typeDefInfo.name to param.name] = Variance.Bivariant
            }
        }

        fun update(
            typeExpr: TypeExpr,
            ownerName: String,
            polarity: Variance,
        ): Boolean =
            when (typeExpr) {
                is TypeVar -> {
                    val current =
                        variances[ownerName to typeExpr.name]
                            ?: error("TypeVar '${typeExpr.name}' not found in variances for $ownerName")
                    val newVariance = current.meet(polarity)
                    if (newVariance != current) {
                        variances[ownerName to typeExpr.name] = newVariance
                        true
                    } else {
                        false
                    }
                }

                is TypeName -> false

                is AppliedTypeExpr -> {
                    val refInfo = env.getTypeDef(typeExpr.name)
                    var changed = false
                    for ((i, arg) in typeExpr.args.withIndex()) {
                        val paramName =
                            refInfo.typeParams.getOrNull(i)?.name
                                ?: error("Type '${typeExpr.name}' has ${refInfo.typeParams.size} params but got arg at index $i")
                        val paramVariance =
                            variances[typeExpr.name to paramName]
                                ?: error("Variance not found for ${typeExpr.name}.$paramName")
                        val argPolarity =
                            when (paramVariance) {
                                Variance.Bivariant -> polarity
                                Variance.Covariant -> polarity
                                Variance.Contravariant -> polarity.flip()
                                Variance.Invariant -> Variance.Invariant
                            }
                        changed = update(arg, ownerName, argPolarity) || changed
                    }
                    changed
                }

                is FunctionTypeExpr -> {
                    var changed = false
                    for (param in typeExpr.paramTypes) {
                        changed = update(param, ownerName, polarity.flip()) || changed
                    }
                    changed = update(typeExpr.returnType, ownerName, polarity) || changed
                    changed
                }

                is TupleTypeExpr -> {
                    var changed = false
                    for (element in typeExpr.elements) {
                        changed = update(element, ownerName, polarity) || changed
                    }
                    changed
                }

                is RecordTypeExpr -> {
                    var changed = false
                    for ((_, fieldType) in typeExpr.fields) {
                        changed = update(fieldType, ownerName, polarity) || changed
                    }
                    changed
                }
            }

        var changed = true
        while (changed) {
            changed = false
            for (ctor in allConstructors) {
                for (field in ctor.fields) {
                    changed = update(field.type, ctor.name, Variance.Covariant) || changed
                }
            }
        }

        for (ctor in allConstructors) {
            for (param in ctor.typeParams) {
                val parentKey = ctor.parentType to param
                val ctorKey = ctor.name to param
                val parentVar = variances[parentKey] ?: Variance.Bivariant
                val ctorVar = variances[ctorKey] ?: Variance.Bivariant
                variances[parentKey] = parentVar.meet(ctorVar)
            }
        }

        for ((key, variance) in variances.toMap()) {
            if (variance == Variance.Bivariant) {
                variances[key] = Variance.Invariant
            }
        }

        for (typeDefInfo in allTypeDefs) {
            val updatedParams =
                typeDefInfo.typeParams.map { param ->
                    val v = variances[typeDefInfo.name to param.name] ?: Variance.Invariant
                    param.copy(variance = v)
                }
            env.updateTypeDef(typeDefInfo.copy(typeParams = updatedParams))
        }
    }

    private fun buildIfaces(env: TypeEnv) {
        for (ctor in env.allConstructors()) {
            val ctorTypeDef = env.getTypeDef(ctor.name)
            val iface = inferCtorIface(ctor, env)
            env.updateTypeDef(ctorTypeDef.copy(iface = TypeBinding.Poly(0, iface)))

            val parentTypeDef = env.getTypeDef(ctor.parentType)
            val parentIface = parentTypeDef.iface.body
            if (parentIface is TVar) {
                subtyping.constrain(iface, parentIface, ctor.span)
            }
        }
    }

    private fun bindConstructors(env: TypeEnv) {
        for (ctor in env.allConstructors()) {
            val ctorTypeDef = env.getTypeDef(ctor.name)
            val ctorIface = ctorTypeDef.iface.body as TRecord
            val tvars = ctorTypeDef.typeParams.map { it.tvar }
            val resultType = TRef(ctor.name, tvars, ctor.span)

            val ctorType =
                if (ctor.fields.isEmpty()) {
                    resultType
                } else {
                    val fieldTypes = ctor.fields.map { ctorIface.fields[it.name]!! }
                    TFun(fieldTypes, resultType, ctor.fields.map { it.name })
                }

            env.bindPolymorphic(ctor.name, ctorType)
        }
    }

    private fun resolveTypeExprToSimpleType(
        typeExpr: TypeExpr,
        typeVarMap: Map<String, TVar>,
    ): SimpleType =
        when (typeExpr) {
            is TypeVar ->
                typeVarMap[typeExpr.name]
                    ?: error("Type variable '${typeExpr.name}' not in scope")

            is TypeName ->
                SimpleType.fromName(typeExpr.name)
                    ?: TRef(typeExpr.name, emptyList(), typeExpr.span)

            is AppliedTypeExpr -> {
                val args = typeExpr.args.map { resolveTypeExprToSimpleType(it, typeVarMap) }
                TRef(typeExpr.name, args, typeExpr.span)
            }

            is FunctionTypeExpr ->
                TFun(
                    typeExpr.paramTypes.map { resolveTypeExprToSimpleType(it, typeVarMap) },
                    resolveTypeExprToSimpleType(typeExpr.returnType, typeVarMap),
                )

            is TupleTypeExpr -> {
                if (typeExpr.elements.isEmpty()) {
                    TUnit
                } else {
                    val fields =
                        typeExpr.elements
                            .mapIndexed { i, elem ->
                                "_$i" to resolveTypeExprToSimpleType(elem, typeVarMap)
                            }.toMap()
                    TRecord(fields)
                }
            }

            is RecordTypeExpr ->
                TRecord(
                    typeExpr.fields.associate { (name, type) ->
                        name to resolveTypeExprToSimpleType(type, typeVarMap)
                    },
                )
        }

    private fun inferCtorIface(
        ctor: ConstructorInfo,
        env: TypeEnv,
    ): TRecord {
        val ctorTypeDef = env.getTypeDef(ctor.name)
        val typeVarMap: Map<String, TVar> = ctorTypeDef.typeParams.associate { it.name to it.tvar }
        val fields = ctor.fields.associate { it.name to resolveTypeExprToSimpleType(it.type, typeVarMap) }

        return TRecord(fields)
    }
}
