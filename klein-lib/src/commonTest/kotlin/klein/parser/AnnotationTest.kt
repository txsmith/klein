package klein.parser

import kotlin.test.Ignore
import kotlin.test.Test

@Ignore // Type annotations not yet implemented in parser
class AnnotationTest {
    @Test
    fun topLevelLetAnnotation() {
        val stmt = parseStmt("x: Num = 42")
        // Val("x", IntLiteral(42), typeAnnotation = TypeName("Num"))
    }

    @Test
    fun functionParamAnnotation() {
        val stmt = parseTopLevel("fun f(x: Num) = x + 1")
        // FunDef("f", params=[Param("x", TypeName("Num"))], body=...)
    }

    @Test
    fun lambdaParamAnnotation() {
        val expr = parse("|x: Num -> x + 1|")
        // Lambda(params=[Param("x", TypeName("Num"))], body=...)
    }

    @Test
    fun functionReturnAnnotation() {
        val stmt = parseTopLevel("fun f(x): Num = x + 1")
        // FunDef("f", params=["x"], returnType=TypeName("Num"), body=...)
    }

    @Test
    fun functionParamAndReturnAnnotation() {
        val stmt = parseTopLevel("fun f(x: Num): Num = x * 2")
        // FunDef("f", params=[Param("x", TypeName("Num"))], returnType=TypeName("Num"), body=...)
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
        // body contains Val("y", ..., typeAnnotation=TypeName("Num"))
    }

    @Test
    fun letInBlock() {
        val stmts = parseProgram(
            """
            x: Num = 42
            y: String = "hello"
            """.trimIndent()
        )
    }

    @Test
    fun expressionAscription() {
        val expr = parse("(42 : Num)")
        // Ascription(IntLiteral(42), TypeName("Num"))
    }

    @Test
    fun expressionAscriptionInFunctionBody() {
        val stmt = parseTopLevel(
            """
            fun f(x) = (x : Num) + 1
            """.trimIndent()
        )
    }

    @Test
    fun mixedAnnotatedAndUnannotatedParams() {
        val stmt = parseTopLevel("fun f(x: Num, y, z: String) = x")
        // params=[Param("x", TypeName("Num")), Param("y", null), Param("z", TypeName("String"))]
    }

    @Test
    fun genericTypeAnnotation() {
        val stmt = parseStmt("x: List<Num> = Cons(1, Nil)")
        // typeAnnotation = AppliedTypeExpr("List", [TypeName("Num")])
    }

    @Test
    fun functionTypeAnnotation() {
        val stmt = parseStmt("f: Num -> String = |x -> \"hello\"|")
        // typeAnnotation = FunctionTypeExpr([TypeName("Num")], TypeName("String"))
    }

    @Test
    fun typeVariableAnnotation() {
        val stmt = parseTopLevel("fun f(x: 'A): 'A = x")
        // params=[Param("x", TypeVar("A"))], returnType=TypeVar("A")
    }

    @Test
    fun typeVariableWithConcreteParams() {
        val stmt = parseTopLevel("fun f(x: 'A, y: Num): 'A = x")
        // params=[Param("x", TypeVar("A")), Param("y", TypeName("Num"))], returnType=TypeVar("A")
    }

    @Test
    fun genericTypeAnnotationWithTypeVar() {
        val stmt = parseStmt("x: List<'A> = Nil")
        // typeAnnotation = AppliedTypeExpr("List", [TypeVar("A")])
    }

    @Test
    fun functionTypeAnnotationWithTypeVars() {
        val stmt = parseStmt("f: 'A -> 'B = |x -> x|")
        // typeAnnotation = FunctionTypeExpr([TypeVar("A")], TypeVar("B"))
    }
}
