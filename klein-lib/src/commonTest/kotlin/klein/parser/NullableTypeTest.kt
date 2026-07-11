package klein.parser

import kotlin.test.Test

/**
 * Parsing of nullable type annotations (`T?`). Per the null-safety ADR, `?` is a postfix operator
 * that binds tighter than `->`, `|`, and `&`, and repeated `?` nest at parse time (collapsed to a
 * single optional only during type resolution).
 */
class NullableTypeTest {
    @Test
    fun topLevelBindingAnnotation() {
        val stmt = parseStmt("x: Num? = null")
        assertStmtEquals(
            stmt,
            valStmt("x", nullLit(), typeAnnotation = optionalType(typeName("Num"))),
        )
    }

    @Test
    fun functionParamAnnotation() {
        val stmt = parseTopLevel("fun f(x: Num?) = x")
        assertStmtEquals(
            stmt,
            funDef("f", params = listOf(param("x", optionalType(typeName("Num")))), body = id("x")),
        )
    }

    @Test
    fun functionReturnAnnotation() {
        val stmt = parseTopLevel("fun f(x): Num? = x")
        assertStmtEquals(
            stmt,
            funDef("f", params = listOf(param("x")), body = id("x"), returnType = optionalType(typeName("Num"))),
        )
    }

    @Test
    fun lambdaParamAnnotation() {
        val expr = parse("|x: Num? -> x|")
        assertExprEquals(expr, lambda(params = listOf(param("x", optionalType(typeName("Num")))), body = id("x")))
    }

    @Test
    fun optionalTypeVar() {
        val stmt = parseTopLevel("fun f(x: 'A?): 'A = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x", optionalType(typeVar("A")))),
                body = id("x"),
                returnType = typeVar("A"),
            ),
        )
    }

    @Test
    fun optionalAppliedType() {
        val stmt = parseStmt("x: List<Num>? = null")
        assertStmtEquals(
            stmt,
            valStmt("x", nullLit(), typeAnnotation = optionalType(appliedType("List", typeName("Num")))),
        )
    }

    // `?` binds tighter than `->`: this is a function *returning* an optional, not an optional function.
    @Test
    fun bindsTighterThanArrow_returnPosition() {
        val stmt = parseTopLevel("fun f(x): Num -> Num? = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x")),
                body = id("x"),
                returnType = functionType(typeName("Num"), optionalType(typeName("Num"))),
            ),
        )
    }

    // Parenthesizing the function type makes the whole function optional.
    @Test
    fun parensMakeTheFunctionOptional() {
        val stmt = parseStmt("f: (Num -> Num)? = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "f",
                nullLit(),
                typeAnnotation = optionalType(functionType(typeName("Num"), typeName("Num"))),
            ),
        )
    }

    // `?` binds tighter than `|`: `A | B?` is `A | (B?)`.
    @Test
    fun bindsTighterThanUnion() {
        val stmt = parseStmt("x: A | B? = null")
        assertStmtEquals(
            stmt,
            valStmt("x", nullLit(), typeAnnotation = unionType(typeName("A"), optionalType(typeName("B")))),
        )
    }

    // `?` binds tighter than `&`: `A & B?` is `A & (B?)`.
    @Test
    fun bindsTighterThanIntersection() {
        val stmt = parseTopLevel("fun f(x: A & B?): A = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x", intersectionType(typeName("A"), optionalType(typeName("B"))))),
                body = id("x"),
                returnType = typeName("A"),
            ),
        )
    }

    // Optional record vs record with an optional field are distinct parses.
    @Test
    fun optionalRecordVsRecordWithOptionalField() {
        assertStmtEquals(
            parseStmt("r: { name: String }? = null"),
            valStmt("r", nullLit(), typeAnnotation = optionalType(recordType("name" to typeName("String")))),
        )
        assertStmtEquals(
            parseStmt("s: { name: String? } = null"),
            valStmt("s", nullLit(), typeAnnotation = recordType("name" to optionalType(typeName("String")))),
        )
    }

    // Repeated `?` nest at parse time; resolution collapses them (`T?? = T?`).
    @Test
    fun doubleOptionalNestsAtParseTime() {
        val stmt = parseStmt("x: Num?? = null")
        assertStmtEquals(
            stmt,
            valStmt("x", nullLit(), typeAnnotation = optionalType(optionalType(typeName("Num")))),
        )
    }
}
