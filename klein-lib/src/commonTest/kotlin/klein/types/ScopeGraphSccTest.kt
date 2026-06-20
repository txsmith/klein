package klein.types

import klein.parser.parseProgram
import kotlin.test.Test
import kotlin.test.assertEquals

class ScopeGraphSccTest {
    // Components as name-sets, in the dependencies-first order computeSCCs returns them.
    private fun sccs(source: String): List<Set<String>> =
        ScopeGraph
            .constructGraph(parseProgram(source).stmts)
            .graph
            .computeSCCs()
            .map { component -> component.map { it.name }.toSet() }

    @Test
    fun isolatedNodeBecomesItsOwnComponent() {
        // A function reached by no edge must still appear (the node loop, not edges, drives roots).
        assertEquals(listOf(setOf("lonely")), sccs("fun lonely() = 1"))
    }

    @Test
    fun selfRecursiveNodeIsASingleton() {
        assertEquals(listOf(setOf("a")), sccs("fun a() = a()"))
    }

    @Test
    fun twoCycleIsOneComponent() {
        val source =
            """
            fun a() = b()
            fun b() = a()
            """.trimIndent()
        assertEquals(listOf(setOf("a", "b")), sccs(source))
    }

    @Test
    fun threeCycleIsOneComponent() {
        val source =
            """
            fun a() = b()
            fun b() = c()
            fun c() = a()
            """.trimIndent()
        assertEquals(listOf(setOf("a", "b", "c")), sccs(source))
    }

    @Test
    fun helperAndTwoCallersAreThreeComponentsCalleeFirst() {
        // id has no outgoing edges, so it is emitted before its callers.
        val source =
            """
            fun id(x) = x
            fun f(x) = id(3)
            fun g(x) = id("Hello")
            """.trimIndent()
        assertEquals(listOf(setOf("id"), setOf("f"), setOf("g")), sccs(source))
    }

    @Test
    fun diamondOrdersSharedDependencyFirst() {
        // a -> b -> d, a -> c -> d. d is depended on by everything, so it comes first; a last.
        val source =
            """
            fun a() = b() + c()
            fun b() = d()
            fun c() = d()
            fun d() = 1
            """.trimIndent()
        assertEquals(listOf(setOf("d"), setOf("b"), setOf("c"), setOf("a")), sccs(source))
    }

    @Test
    fun independentComponentsAreEachSingletons() {
        val source =
            """
            fun a() = 1
            fun b() = 2
            """.trimIndent()
        assertEquals(listOf(setOf("a"), setOf("b")), sccs(source))
    }

    @Test
    fun cycleWithAnExternalDependencyOrdersDependencyFirst() {
        // {a, b} mutually recurse and both call helper; helper has no back-edge, so it is first.
        val source =
            """
            fun helper() = 0
            fun a() = b() + helper()
            fun b() = a()
            """.trimIndent()
        assertEquals(listOf(setOf("helper"), setOf("a", "b")), sccs(source))
    }
}
