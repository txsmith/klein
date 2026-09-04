package klein.check

import klein.Klein
import klein.ReleaseNumber
import klein.check.Type.TBool
import klein.check.Type.TBottom
import klein.check.Type.TNull
import klein.check.Type.TNum
import klein.check.Type.TRecord
import klein.check.Type.TRef
import klein.check.Type.TStr
import klein.check.Type.TUnit
import klein.interp.Value
import kotlin.test.Test
import kotlin.test.assertEquals

class ValueTypesTest {
    private val env: RuleEnv =
        Klein
            .checkContract(
                """
                type Customer = Customer { id: Num, tier: String }
                type Shape = Circle { radius: Num } | Square { side: Num }
                type Box<'T> = Box { item: 'T }

                release 1
                  Customer
                  Shape
                  Box
                """.trimIndent(),
            ).resolveRelease(ReleaseNumber(1))
            .ruleTypeEnv

    private fun struct(
        tag: String?,
        vararg fields: Pair<String, Value>,
    ) = Value.VStruct(tag, fields.toMap())

    @Test
    fun scalarsMapToTheirTypes() {
        assertEquals(TNum, infer(Value.VNum(1.0), env))
        assertEquals(TStr, infer(Value.VStr("a"), env))
        assertEquals(TBool, infer(Value.VBool(true), env))
        assertEquals(TNull, infer(Value.VNull, env))
        assertEquals(TUnit, infer(Value.VUnit, env))
    }

    @Test
    fun anUntaggedStructIsARecordOfItsInferredFields() {
        val value = struct(null, "id" to Value.VNum(1.0), "name" to Value.VStr("Acme"))
        assertEquals(TRecord(mapOf("id" to TNum, "name" to TStr)), infer(value, env))
    }

    @Test
    fun aTaggedStructIsItsNominalType() {
        val customer = struct("Customer", "id" to Value.VNum(1.0), "tier" to Value.VStr("gold"))
        assertEquals(TRef("Customer", emptyList(), null), infer(customer, env))
        assertEquals(TRef("Circle", emptyList(), null), infer(struct("Circle", "radius" to Value.VNum(2.0)), env))
    }

    @Test
    fun aGenericConstructorTakesItsArgumentFromTheField() {
        assertEquals(TRef("Box", listOf(TStr), null), infer(struct("Box", "item" to Value.VStr("x")), env))
    }

    @Test
    fun aTaggedStructWhoseFieldsDoNotFitIsTheRecordItIs() {
        val customer = struct("Customer", "id" to Value.VStr("one"), "tier" to Value.VStr("gold"))
        assertEquals(TRecord(mapOf("id" to TStr, "tier" to TStr)), infer(customer, env))
    }

    @Test
    fun anUnknownTagIsBottom() {
        assertEquals(TBottom, infer(struct("Supplier", "id" to Value.VNum(1.0)), env))
    }
}
