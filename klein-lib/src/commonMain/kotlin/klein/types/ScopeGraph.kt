package klein.types

import klein.*

data class ScopeGraph(
    val nodes: List<Node>,
    val edges: Set<Pair<String, String>>,
    val children: List<ScopeGraph>,
) {
    data class Node(
        val name: String,
        val binding: Stmt,
        val children: List<ScopeGraph>,
    )

    /**
     * The result of building a scope's graph: the [graph] itself, the names that [escaped] the
     * scope (references to bindings in an enclosing scope), and any [duplicates] — bindings whose
     * name was already declared in the same scope, each paired with its span for error reporting.
     */
    data class GraphResult(
        val graph: ScopeGraph,
        val escaped: Set<String>,
        val duplicates: List<Pair<String, SourceSpan>>,
    )

    /**
     * Partition this scope's [nodes] into strongly connected components (Tarjan's algorithm).
     *
     * The result is ordered **dependencies-first**: a component appears before any component
     * that references it (callees before callers). That is the order in which components should
     * be typed and generalized — a referenced binding is fully resolved before the binding that
     * uses it. Each component is the set of mutually-recursive nodes; a node not part of any
     * cycle is its own singleton (a self-recursive node is also a singleton). Edges to names
     * outside this scope are ignored.
     */
    fun computeSCCs(): List<Set<Node>> {
        val byName = nodes.associateBy { it.name }
        val adjacency = edges.groupBy({ it.first }, { it.second })

        val index = HashMap<String, Int>()
        val lowlink = HashMap<String, Int>()
        val onStack = HashSet<String>()
        val stack = ArrayDeque<String>()
        val sccs = mutableListOf<Set<Node>>()
        var counter = 0

        fun strongConnect(v: String) {
            index[v] = counter
            lowlink[v] = counter
            counter++
            stack.addLast(v)
            onStack.add(v)

            for (w in adjacency[v].orEmpty()) {
                when {
                    w !in index -> {
                        strongConnect(w)
                        lowlink[v] = minOf(lowlink.getValue(v), lowlink.getValue(w))
                    }
                    w in onStack -> lowlink[v] = minOf(lowlink.getValue(v), index.getValue(w))
                }
            }

            // v is the root of a component: pop the stack down to it.
            if (lowlink.getValue(v) == index.getValue(v)) {
                val component = mutableSetOf<Node>()
                while (true) {
                    val u = stack.removeLast()
                    onStack.remove(u)
                    component.add(byName.getValue(u))
                    if (u == v) break
                }
                sccs.add(component)
            }
        }

        // Seed a DFS from every not-yet-visited node, in declared order for determinism.
        for (node in nodes) {
            if (node.name !in index) strongConnect(node.name)
        }
        return sccs
    }

    /**
     * Find a simple cycle through [start] by following edges, returned as a closed path such as
     * `[start, a, b, start]`. Any cycle through a node lies entirely within its strongly connected
     * component, so this only needs the full edge set. Intended for nodes already known to be part
     * of a cycle (a component of more than one node); a node with no cycle yields just `[start]`.
     */
    fun findCycle(start: String): List<String> {
        val adjacency = edges.groupBy({ it.first }, { it.second })
        val path = mutableListOf<String>()

        fun walk(v: String): Boolean {
            path.add(v)
            for (w in adjacency[v].orEmpty()) {
                if (w == start) return true // closed the cycle back to the start
                if (w !in path && walk(w)) return true
            }
            path.removeAt(path.lastIndex)
            return false
        }

        walk(start)
        return path + start
    }

    companion object {
        /**
         * Build the [GraphResult] for a top-level program (or any scope's statements). Escaped
         * names are references the typer resolves against an enclosing scope or reports as unbound
         * during inference; [GraphResult.duplicates] are reported as duplicate-binding errors.
         */
        fun constructGraph(block: List<Stmt>): GraphResult = fromBlock(block)

        /**
         * Collect the references and nested scopes of an expression.
         *
         * Returns the inner [ScopeGraph]s the expression opens (via blocks) along with the set
         * of names it references. Binder-introducing nodes are special-cased: a [Block] opens a
         * nested scope (handled by [fromBlock]), and a [Lambda] removes its parameters from the
         * references of its body (so a parameter that shadows an outer name is not mistaken for a
         * reference to it). Everything else is scope-transparent and recurses into [children].
         */
        private fun findReferences(expr: Expr): Pair<List<ScopeGraph>, MutableSet<String>> =
            when (expr) {
                // Nested-scope duplicates are reported during that block's own inference, not
                // surfaced here, so we take only its graph and escaped names.
                is Block -> fromBlock(expr.stmts).let { listOf(it.graph) to it.escaped.toMutableSet() }
                is Ident -> emptyList<ScopeGraph>() to mutableSetOf(expr.name)
                is Lambda ->
                    findReferences(expr.body).let { (graphs, refs) ->
                        refs.removeAll(expr.params.toSet())
                        graphs to refs
                    }
                else ->
                    expr.children.map { findReferences(it) }.unzip().let { (graphs, refs) ->
                        graphs.flatten() to refs.flatten().toMutableSet()
                    }
            }

        /**
         * Build the [ScopeGraph] for a block's statements, returning it along with the names that
         * escape the block (references not bound locally, to be resolved by an enclosing scope).
         *
         * Each named binding ([Val]/[FunDef]) becomes a node; edges point from a binding to the
         * local siblings it references. Bare expressions contribute their nested scopes as
         * anonymous children and their references to the escape set, but never edges (no source
         * node).
         *
         * Scoping is split by binding kind: functions are mutually recursive, so they are in scope
         * across the whole block regardless of order; vals are sequential, coming into scope only
         * after their own statement. So a reference resolves locally only against the functions
         * plus the vals defined before it — a reference to a val that is defined later (or never)
         * escapes to an enclosing scope rather than binding to the local val.
         */
        private fun fromBlock(block: List<Stmt>): GraphResult {
            val localFns = block.filterIsInstance<FunDef>().map { it.name }.toSet()
            val valsInScope = mutableSetOf<String>()

            val seen = mutableSetOf<String>() // binding names already declared in this scope
            val duplicates = mutableListOf<Pair<String, SourceSpan>>()
            val escapes = mutableSetOf<String>()
            val nodes = mutableListOf<Node>()
            val anonChildren = mutableListOf<ScopeGraph>()
            val edges = mutableSetOf<Pair<String, String>>()

            // Determine if a reference forms an edge to a local binding, or whether it escapes (i.e. refers to a binding in an outer scope)
            fun classify(
                from: String?,
                refs: Set<String>,
            ) {
                for (ref in refs) {
                    if (ref in localFns || ref in valsInScope) {
                        if (from != null) edges.add(from to ref)
                    } else {
                        escapes.add(ref)
                    }
                }
            }

            block.forEach { stmt ->
                when (stmt) {
                    is Val -> {
                        if (!seen.add(stmt.name)) {
                            duplicates.add(stmt.name to stmt.span)
                            return@forEach
                        }
                        val (graphs, refs) = findReferences(stmt.value)
                        classify(stmt.name, refs) // resolved against scope *before* this val
                        nodes.add(Node(stmt.name, stmt, graphs))
                        valsInScope.add(stmt.name) // now visible to later statements
                    }
                    is FunDef -> {
                        if (!seen.add(stmt.name)) {
                            duplicates.add(stmt.name to stmt.span)
                            return@forEach
                        }
                        val (graphs, refs) = findReferences(stmt.body)
                        refs.removeAll(stmt.params.toSet())
                        classify(stmt.name, refs)
                        nodes.add(Node(stmt.name, stmt, graphs))
                    }
                    is Expr -> {
                        val (graphs, refs) = findReferences(stmt)
                        classify(null, refs) // bare expression: no source node, so no edges
                        anonChildren.addAll(graphs)
                    }
                    else -> {} // TypeDef etc. introduce no value-level scope
                }
            }
            return GraphResult(ScopeGraph(nodes, edges, anonChildren), escapes, duplicates)
        }
    }
}
