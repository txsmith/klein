package klein.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Golden lowering spec for the edition prelude: `lowerWithPrelude` driven directly with hand-built
 * [PreludeBinding] lists, no contract machinery.
 *
 * The prelude becomes an outer `scope` of hoisted binds, evaluated on entry, with the rule's own
 * scope nested inside so a rule name shadowing a contract name resolves to its own slot through the
 * ordinary chain. Every case in `lowerExpr` is untouched: to the lowerer a contract name is a name
 * already in scope. The three bind kinds differ only in what their bind evaluates to:
 *
 *  - [PreludeBinding.Ctor] lowers exactly as a `type` constructor does — a [Lambda] over [MakeData],
 *    or a bare [MakeData] when nullary;
 *  - [PreludeBinding.Function] lowers to a [Lambda] of its arity whose body is a [HostCall] on the
 *    params, so the capability stays callable (including at arity 0);
 *  - [PreludeBinding.Value] lowers to a bare nullary [HostCall], asked once at scope entry; later
 *    reads are plain `Var`s.
 *
 * Slot layout is canonical: the prelude is sorted by name before the scope is laid out, so the IR
 * is a fact about the rule and the release, not about the order the bindings were handed in.
 */
class EditionPreludeLoweringTest {
    private fun assertPreludeLowersTo(
        prelude: List<PreludeBinding>,
        source: String,
        expected: String,
    ) {
        val core = Lowering().lowerWithPrelude(parseProgram(source.trimIndent().trim()), prelude)
        assertEquals(expected.trimIndent().trim(), CorePrinter.print(core))
    }

    @Test
    fun ctor_bindsLambdaOverMakeData() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Ctor("Customer", listOf("id"))),
            "Customer",
            """
            scope
              bind Customer#0 = fun Customer/1 -> Customer{id: id[0;0]}
              Customer[0;0]
            """,
        )

    @Test
    fun function_bindsLambdaOverHostCall() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Function("creditScore", 1)),
            "creditScore",
            """
            scope
              bind creditScore#0 = fun creditScore/1 -> host creditScore(_0[0;0])
              creditScore[0;0]
            """,
        )

    @Test
    fun value_bindsBareNullaryHostCall() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Value("customer")),
            "customer",
            """
            scope
              bind customer#0 = host customer()
              customer[0;0]
            """,
        )

    @Test
    fun multiFieldConstructor_paramsFollowDeclarationOrder() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Ctor("Customer", listOf("id", "tier"))),
            """Customer(1, "gold")""",
            """
            scope
              bind Customer#0 = fun Customer/2 -> Customer{id: id[0;0], tier: tier[0;1]}
              Customer[0;0](1, "gold")
            """,
        )

    @Test
    fun nullaryConstructor_bindsBareMakeData() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Ctor("Gold", emptyList())),
            "Gold",
            """
            scope
              bind Gold#0 = Gold{}
              Gold[0;0]
            """,
        )

    @Test
    fun directCall_resolvesThroughTheChain() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Function("creditScore", 1)),
            """
            id = 7
            creditScore(id)
            """,
            """
            scope
              bind creditScore#0 = fun creditScore/1 -> host creditScore(_0[0;0])
              scope
                bind id#0 = 7
                creditScore[1;0](id[0;0])
            """,
        )

    @Test
    fun capabilityPassedAsValue() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Function("creditScore", 1)),
            """
            f = creditScore
            f(1)
            """,
            """
            scope
              bind creditScore#0 = fun creditScore/1 -> host creditScore(_0[0;0])
              scope
                bind f#0 = creditScore[1;0]
                f[0;0](1)
            """,
        )

    @Test
    fun ruleShadowingContractName_resolvesToItsOwnSlot() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Value("customer")),
            """
            customer = 1
            customer
            """,
            """
            scope
              bind customer#0 = host customer()
              scope
                bind customer#0 = 1
                customer[0;0]
            """,
        )

    @Test
    fun unmentionedPreludeName_stillBinds() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Function("creditScore", 1), PreludeBinding.Value("customer")),
            "customer",
            """
            scope
              bind creditScore#0 = fun creditScore/1 -> host creditScore(_0[0;0])
              bind customer#1 = host customer()
              customer[0;1]
            """,
        )

    @Test
    fun slotLayoutIsCanonical_regardlessOfHandedOrder() {
        val ctor = PreludeBinding.Ctor("Customer", listOf("id", "tier"))
        val fn = PreludeBinding.Function("creditScore", 1)
        val value = PreludeBinding.Value("customer")
        val expected =
            """
            scope
              bind Customer#0 = fun Customer/2 -> Customer{id: id[0;0], tier: tier[0;1]}
              bind creditScore#1 = fun creditScore/1 -> host creditScore(_0[0;0])
              bind customer#2 = host customer()
              creditScore[0;1](customer[0;2])
            """
        val source = "creditScore(customer)"
        assertPreludeLowersTo(listOf(ctor, fn, value), source, expected)
        assertPreludeLowersTo(listOf(value, ctor, fn), source, expected)
        assertPreludeLowersTo(listOf(fn, value, ctor), source, expected)
    }

    @Test
    fun nullaryFunction_staysCallable() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Function("tick", 0)),
            "tick()",
            """
            scope
              bind tick#0 = fun tick/0 -> host tick()
              tick[0;0]()
            """,
        )

    @Test
    fun value_isReadNotCalled() =
        assertPreludeLowersTo(
            listOf(PreludeBinding.Value("customer")),
            "customer.id",
            """
            scope
              bind customer#0 = host customer()
              customer[0;0].id
            """,
        )
}
