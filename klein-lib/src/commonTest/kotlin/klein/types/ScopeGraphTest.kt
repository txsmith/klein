package klein.types

import klein.parser.parseProgram
import kotlin.test.Test
import kotlin.test.assertEquals

class ScopeGraphTest {
    private fun graph(source: String): ScopeGraph = ScopeGraph.constructGraph(parseProgram(source).stmts).graph

    private fun edges(source: String): Set<Pair<String, String>> = graph(source).edges

    private fun nodeNames(source: String): Set<String> = graph(source).nodes.map { it.name }.toSet()

    private fun duplicateNames(source: String): List<String> =
        ScopeGraph.constructGraph(parseProgram(source).stmts).duplicates.map { it.first }

    @Test
    fun independentHelperIsNotMergedWithCallers() {
        // The SCC-leak repro: id has no outgoing edges, f and g each only point at id.
        val source =
            """
            fun id(x) = x
            fun f(x) = id(3)
            fun g(x) = id("Hello")
            """.trimIndent()
        assertEquals(setOf("id", "f", "g"), nodeNames(source))
        assertEquals(setOf("f" to "id", "g" to "id"), edges(source))
    }

    @Test
    fun mutualRecursionFormsEdgesBothWays() {
        val source =
            """
            fun a(x) = b(x)
            fun b(x) = a(x)
            """.trimIndent()
        assertEquals(setOf("a" to "b", "b" to "a"), edges(source))
    }

    @Test
    fun lambdaParamShadowsSiblingFunction() {
        // The g inside f is the lambda parameter, not the sibling function — no f -> g edge.
        val source =
            """
            fun f(x) = |g -> g(1)|
            fun g(y) = y
            """.trimIndent()
        assertEquals(emptySet(), edges(source))
    }

    @Test
    fun functionParamShadowsSiblingFunction() {
        // f's own parameter g shadows the sibling function g.
        val source =
            """
            fun f(g) = g(1)
            fun g(y) = y
            """.trimIndent()
        assertEquals(emptySet(), edges(source))
    }

    @Test
    fun blockLocalValShadowsSiblingFunction() {
        // The local val g shadows the sibling function; the trailing g refers to the local.
        val source =
            """
            fun f(x) =
              g = 1
              g
            fun g(y) = y
            """.trimIndent()
        assertEquals(emptySet(), edges(source))

        // f's block opens a child scope whose only node is the local val g.
        val f = graph(source).nodes.first { it.name == "f" }
        assertEquals(1, f.children.size)
        assertEquals(setOf("g"), f.children.single().nodes.map { it.name }.toSet())
    }

    @Test
    fun referenceFromNestedBlockLiftsToEnclosingFunction() {
        // g is referenced inside f's block; the edge is attributed to f at the top level.
        val source =
            """
            fun f(x) =
              y = g(x)
              y
            fun g(z) = z
            """.trimIndent()
        assertEquals(setOf("f" to "g"), edges(source))
        // The inner edge y -> g does not survive: g is not local to f's block.
        val f = graph(source).nodes.first { it.name == "f" }
        assertEquals(emptySet(), f.children.single().edges)
    }

    @Test
    fun localValShadowsOnlyAfterItsDefinition() {
        // r = g references the top-level function (the local g is not in scope yet); the local
        // g = 1 shadows only the statements after it. So the reference lifts to an f -> g edge.
        val source =
            """
            fun f(x) =
              r = g
              g = 1
              r
            fun g(y) = y
            """.trimIndent()
        assertEquals(setOf("f" to "g"), edges(source))
    }

    @Test
    fun valReferencingFunctionProducesEdge() {
        val source =
            """
            x = f(1)
            fun f(y) = y
            """.trimIndent()
        assertEquals(setOf("x", "f"), nodeNames(source))
        assertEquals(setOf("x" to "f"), edges(source))
    }

    @Test
    fun forwardRefToValProducesNoEdge() {
        val source =
            """
            fun f(y) = x
            x = 1
            """.trimIndent()
        assertEquals(setOf("x", "f"), nodeNames(source))
        assertEquals(emptySet(), edges(source))
    }

    @Test
    fun duplicateBindingsAreReportedAndCollapsedToOneNode() {
        // Same name declared twice in one scope: reported once, kept as a single node.
        val source =
            """
            fun f(x) = x
            fun f(y) = y
            """.trimIndent()
        assertEquals(listOf("f"), duplicateNames(source))
        assertEquals(setOf("f"), nodeNames(source))
    }

    @Test
    fun distinctNamesProduceNoDuplicates() {
        val source =
            """
            fun f(x) = x
            g = 1
            """.trimIndent()
        assertEquals(emptyList(), duplicateNames(source))
    }

    @Test
    fun unboundReferenceIsNeitherEdgeNorNode() {
        // h is not defined locally: it produces no node and no edge (the reference escapes).
        val source = "fun f(x) = h(x)"
        assertEquals(setOf("f"), nodeNames(source))
        assertEquals(emptySet(), edges(source))
    }
}
