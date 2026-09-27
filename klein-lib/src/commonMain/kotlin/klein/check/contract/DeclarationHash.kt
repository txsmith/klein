package klein.check.contract

import klein.Fnv
import klein.RevisionNumber
import klein.check.ConstructorInfo
import klein.check.ContractEnv
import klein.check.ContractType
import klein.check.Type
import klein.check.TypeDefInfo

private const val TAG_FUNCTION = 1
private const val TAG_VALUE = 2
private const val TAG_TYPE_DEFINITION = 3

private const val TAG_NUM = 10
private const val TAG_STR = 11
private const val TAG_BOOL = 12
private const val TAG_UNIT = 13
private const val TAG_NULL = 14
private const val TAG_TOP = 15
private const val TAG_BOTTOM = 16
private const val TAG_FUN = 17
private const val TAG_RECORD = 18
private const val TAG_OPTIONAL = 19
private const val TAG_REF = 20
private const val TAG_VARIABLE = 21
private const val TAG_FORALL = 22

internal fun hashCapability(declaration: ContractDeclaration): DeclarationHash {
    val hasher = DeclarationHasher()
    hasher.fnv.byte(if (declaration is ContractDeclaration.Function) TAG_FUNCTION else TAG_VALUE)
    hasher.type(declaration.type)
    return DeclarationHash(hasher.fnv.result())
}

internal fun hashTypeDefinition(
    definition: TypeDefInfo<RevisionNumber>,
    constructors: List<ConstructorInfo<RevisionNumber>>,
): DeclarationHash {
    val hasher = DeclarationHasher()
    hasher.fnv.byte(TAG_TYPE_DEFINITION)
    hasher.fnv.int(definition.typeParams.size)
    definition.typeParams.forEach { hasher.variable(it.skolem) }
    hasher.fnv.int(constructors.size)
    for (constructor in constructors.sortedBy { it.name }) {
        hasher.fnv.string(constructor.name)
        hasher.fields(constructor.fields)
    }
    return DeclarationHash(hasher.fnv.result())
}

internal fun ContractEnv.hashTypeDefinition(
    name: String,
    revision: RevisionNumber,
): DeclarationHash? {
    val definition = lookupTypeDef(name, revision) ?: return null
    return hashTypeDefinition(definition, constructorsOf(name, revision))
}

private class DeclarationHasher {
    val fnv = Fnv()
    private val variables = mutableListOf<Type.TSkolem>()

    fun variable(skolem: Type.TSkolem) {
        var index = variables.indexOf(skolem)
        if (index < 0) {
            variables.add(skolem)
            index = variables.size - 1
        }
        fnv.int(index)
    }

    fun fields(fields: Map<String, ContractType>) {
        fnv.int(fields.size)
        for (name in fields.keys.sorted()) {
            fnv.string(name)
            type(fields.getValue(name))
        }
    }

    fun type(type: ContractType) {
        when (type) {
            Type.TNum -> fnv.byte(TAG_NUM)
            Type.TStr -> fnv.byte(TAG_STR)
            Type.TBool -> fnv.byte(TAG_BOOL)
            Type.TUnit -> fnv.byte(TAG_UNIT)
            Type.TNull -> fnv.byte(TAG_NULL)
            Type.TTop -> fnv.byte(TAG_TOP)
            Type.TBottom -> fnv.byte(TAG_BOTTOM)
            is Type.TSkolem -> {
                fnv.byte(TAG_VARIABLE)
                variable(type)
            }
            is Type.TForall -> {
                fnv.byte(TAG_FORALL)
                type(type.body)
            }
            is Type.TFun -> {
                fnv.byte(TAG_FUN)
                fnv.int(type.params.size)
                type.params.forEachIndexed { i, param ->
                    fnv.string(type.paramNames.getOrElse(i) { "" })
                    type(param)
                }
                type(type.result)
            }
            is Type.TRecord -> {
                fnv.byte(TAG_RECORD)
                fields(type.fields)
            }
            is Type.TOptional -> {
                fnv.byte(TAG_OPTIONAL)
                type(type.type)
            }
            is Type.TRef -> {
                fnv.byte(TAG_REF)
                fnv.string(type.name)
                fnv.int(type.revision.value)
                fnv.int(type.typeArgs.size)
                type.typeArgs.forEach { type(it) }
            }
        }
    }
}
