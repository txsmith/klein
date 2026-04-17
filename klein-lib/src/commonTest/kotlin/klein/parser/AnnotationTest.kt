package klein.parser

import kotlin.test.Ignore
import kotlin.test.Test

class AnnotationTest {
    @Test
    fun topLevelLetAnnotation() {
        val stmt = parseStmt("x: Num = 42")
        assertStmtEquals(stmt, valStmt("x", int(42), typeAnnotation = typeName("Num")))
    }

    @Test
    fun functionParamAnnotation() {
        val stmt = parseTopLevel("fun f(x: Num) = x + 1")
        assertStmtEquals(
            stmt,
            funDef("f", params = listOf(param("x", typeName("Num"))), body = add(id("x"), int(1)))
        )
    }

    @Test
    fun lambdaParamAnnotation() {
        val expr = parse("|x: Num -> x + 1|")
        assertExprEquals(
            expr,
            lambda(params = listOf(param("x", typeName("Num"))), body = add(id("x"), int(1)))
        )
    }

    @Test
    fun functionReturnAnnotation() {
        val stmt = parseTopLevel("fun f(x): Num = x + 1")
        assertStmtEquals(
            stmt,
            funDef("f", params = listOf(param("x")), body = add(id("x"), int(1)), returnType = typeName("Num"))
        )
    }

    @Test
    fun functionParamAndReturnAnnotation() {
        val stmt = parseTopLevel("fun f(x: Num): Num = x * 2")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x", typeName("Num"))),
                body = mul(id("x"), int(2)),
                returnType = typeName("Num"),
            )
        )
    }

    @Test
    fun letInFunctionBody() {
        val stmt = parseTopLevel(
            """
            fun f(x) =
              y: Num = x + 1
              y
            """.trimIndent()
        )
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x")),
                body = block(
                    valStmt("y", add(id("x"), int(1)), typeAnnotation = typeName("Num")),
                    id("y"),
                ),
            )
        )
    }

    @Test
    fun letInBlock() {
        val stmts = parseProgram(
            """
            x: Num = 42
            y: String = "hello"
            """.trimIndent()
        )
        assertProgramEquals(
            stmts,
            listOf(
                valStmt("x", int(42), typeAnnotation = typeName("Num")),
                valStmt("y", string("hello"), typeAnnotation = typeName("String")),
            )
        )
    }

    @Test
    fun expressionAscription() {
        val expr = parse("(42 : Num)")
        assertExprEquals(expr, ascription(int(42), typeName("Num")))
    }

    @Test
    fun expressionAscriptionInFunctionBody() {
        val stmt = parseTopLevel(
            """
            fun f(x) = (x : Num) + 1
            """.trimIndent()
        )
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x")),
                body = add(ascription(id("x"), typeName("Num")), int(1)),
            )
        )
    }

    @Test
    fun mixedAnnotatedAndUnannotatedParams() {
        val stmt = parseTopLevel("fun f(x: Num, y, z: String) = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x", typeName("Num")), param("y"), param("z", typeName("String"))),
                body = id("x"),
            )
        )
    }

    @Test
    fun genericTypeAnnotation() {
        val stmt = parseStmt("x: List<Num> = Cons(1, Nil)")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                call(id("Cons"), int(1), id("Nil")),
                typeAnnotation = appliedType("List", typeName("Num")),
            )
        )
    }

    @Test
    fun functionTypeAnnotation() {
        val stmt = parseStmt("f: Num -> String = |x -> \"hello\"|")
        assertStmtEquals(
            stmt,
            valStmt(
                "f",
                lambda("x", body = string("hello")),
                typeAnnotation = functionType(typeName("Num"), typeName("String")),
            )
        )
    }

    @Test
    fun typeVariableAnnotation() {
        val stmt = parseTopLevel("fun f(x: 'A): 'A = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x", typeVar("A"))),
                body = id("x"),
                returnType = typeVar("A"),
            )
        )
    }

    @Test
    fun typeVariableWithConcreteParams() {
        val stmt = parseTopLevel("fun f(x: 'A, y: Num): 'A = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x", typeVar("A")), param("y", typeName("Num"))),
                body = id("x"),
                returnType = typeVar("A"),
            )
        )
    }

    @Test
    fun genericTypeAnnotationWithTypeVar() {
        val stmt = parseStmt("x: List<'A> = Nil")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                id("Nil"),
                typeAnnotation = appliedType("List", typeVar("A")),
            )
        )
    }

    @Test
    fun functionTypeAnnotationWithTypeVars() {
        val stmt = parseStmt("f: 'A -> 'B = |x -> x|")
        assertStmtEquals(
            stmt,
            valStmt(
                "f",
                lambda("x", body = id("x")),
                typeAnnotation = functionType(typeVar("A"), typeVar("B")),
            )
        )
    }

    // --- Record field annotations ---

    @Test
    fun recordFieldAnnotation() {
        val expr = parse("{ x: Num = 42 }")
        assertExprEquals(
            expr,
            annotatedRecord(recordField("x", int(42), typeAnnotation = typeName("Num"))),
        )
    }

    @Test
    fun recordFieldAnnotationWithTypeVar() {
        val expr = parse("{ id: 'A -> 'A = |x -> x| }")
        assertExprEquals(
            expr,
            annotatedRecord(recordField("id", lambda("x", body = id("x")), typeAnnotation = functionType(typeVar("A"), typeVar("A")))),
        )
    }

    @Ignore // Fun defs inside records not yet supported in parser
    @Test
    fun funDefInsideRecord() {
        val expr = parse("{ fun double(x: Num): Num = x * 2 }")
        // Should parse as a record with a function-valued field
    }

    // --- Nested function definitions ---

    @Ignore // Nested fun defs not yet supported in parser
    @Test
    fun nestedFunDef() {
        val stmt = parseTopLevel(
            """
            fun outer(x) =
              fun inner(y) = y + 1
              inner(x)
            """.trimIndent()
        )
    }

    @Ignore // Nested fun defs not yet supported in parser
    @Test
    fun nestedFunDefWithAnnotations() {
        val stmt = parseTopLevel(
            """
            fun outer(x: 'A) =
              fun inner(y: 'B): 'B = y
              inner(x)
            """.trimIndent()
        )
    }
}
