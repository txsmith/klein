package klein.check.contract

import klein.surface.Lexer
import klein.surface.Parser
import kotlin.test.Test
import kotlin.test.assertEquals

private val exposed = setOf("creditScore", "taxRate", "Customer", "Invoice", "Money", "Circle", "Person")

private fun used(src: String): Set<String> =
    usedCapabilities(Parser(Lexer(src.trimIndent()).tokenize().toList()).parseProgram(), exposed)

class UsedCapabilitiesTest {
    @Test
    fun aNameInCallPositionIsUsed() {
        assertEquals(setOf("creditScore"), used("creditScore(42)"))
    }

    @Test
    fun aBareValueReadIsUsed() {
        assertEquals(setOf("taxRate"), used("taxRate * 2"))
    }

    @Test
    fun aNameInAFunParamAnnotationIsUsed() {
        assertEquals(setOf("Customer"), used("fun f(c: Customer): Num = 1"))
    }

    @Test
    fun aNameInAFunReturnAnnotationIsUsed() {
        assertEquals(setOf("Invoice"), used("fun f(x: Num): Invoice = x"))
    }

    @Test
    fun aNameInAValAnnotationIsUsed() {
        assertEquals(setOf("Money"), used("m: Money = 1"))
    }

    @Test
    fun aNameInALambdaParamAnnotationIsUsed() {
        assertEquals(setOf("Customer"), used("f = |c: Customer -> 1|"))
    }

    @Test
    fun aNameInAFieldOfTheRulesOwnTypeDefinitionIsUsed() {
        assertEquals(setOf("Customer", "Money"), used("type Order = Order { who: Customer, total: Money? }"))
    }

    @Test
    fun aNameInsideANestedTypeAnnotationIsUsed() {
        assertEquals(setOf("Customer", "Money"), used("fun f(g: (Customer) -> Money, p: (Num, Customer)): Num = 1"))
    }

    @Test
    fun aConstructorInAMatchPatternIsUsed() {
        assertEquals(setOf("Circle"), used("match shape\n  Circle c -> c\n  _ -> 0"))
    }

    @Test
    fun aConstructorInADestructuringIsUsed() {
        assertEquals(setOf("Circle"), used("Circle c = x\nc"))
    }

    @Test
    fun aTagInARecordPatternIsUsed() {
        assertEquals(setOf("Person"), used("Person { name } = e\nname"))
        assertEquals(setOf("Person"), used("match e\n  Person { name } -> name"))
    }

    @Test
    fun aConstructorTheRuleDefinesItselfIsExcludedFromPatterns() {
        assertEquals(emptySet(), used("type Circle = Circle { r: Num }\nmatch x\n  Circle c -> c"))
    }

    @Test
    fun aNameTheRuleBindsItselfIsExcluded() {
        assertEquals(emptySet(), used("fun creditScore(n: Num): Num = n\ncreditScore(1)"))
        assertEquals(emptySet(), used("taxRate = 2\ntaxRate * 3"))
        assertEquals(emptySet(), used("type Customer = Customer { id: Num }\nfun f(c: Customer): Customer = c"))
        assertEquals(emptySet(), used("f = |taxRate -> taxRate + 1|"))
        assertEquals(emptySet(), used("fun f(taxRate: Num): Num = taxRate"))
    }

    @Test
    fun aNameBoundByAPatternIsExcluded() {
        assertEquals(emptySet(), used("match 1\n  taxRate -> taxRate"))
        assertEquals(emptySet(), used("{ taxRate } = { taxRate = 1 }\ntaxRate"))
    }

    @Test
    fun aBindingInsideABlockDoesNotLeakOut() {
        assertEquals(
            setOf("taxRate"),
            used(
                """
                f = |
                  taxRate = 1
                  taxRate
                |
                taxRate
                """,
            ),
        )
    }

    @Test
    fun aPrimitiveIsExcluded() {
        assertEquals(emptySet(), used("fun f(n: Num, s: String, b: Bool): Num = n"))
    }

    @Test
    fun aTypeParameterOfTheRulesOwnDefinitionIsExcluded() {
        assertEquals(emptySet(), used("type Box<'A> = Box { item: 'A }"))
    }

    @Test
    fun aNameNotInExposedIsExcluded() {
        assertEquals(emptySet(), used("fun f(c: Shape): Num = unknown(c)"))
    }

    @Test
    fun aProgramUsingNothingGivesTheEmptySet() {
        assertEquals(emptySet(), used("1 + 2"))
    }
}
