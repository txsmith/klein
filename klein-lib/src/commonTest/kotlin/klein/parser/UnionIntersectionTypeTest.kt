package klein.parser

import kotlin.test.Test

class UnionIntersectionTypeTest {
    @Test
    fun union_twoConcreteTypes() {
        val stmt = parseStmt("x: Num | String = 42")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                int(42),
                typeAnnotation = unionType(typeName("Num"), typeName("String")),
            ),
        )
    }

    @Test
    fun union_withTypeVar() {
        val stmt = parseTopLevel("fun f(x: 'A): 'A | Dog = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x", typeVar("A"))),
                body = id("x"),
                returnType = unionType(typeVar("A"), typeName("Dog")),
            ),
        )
    }

    @Test
    fun union_leftAssociative() {
        val stmt = parseStmt("x: A | B | C = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                nullLit(),
                typeAnnotation =
                    unionType(
                        unionType(typeName("A"), typeName("B")),
                        typeName("C"),
                    ),
            ),
        )
    }

    @Test
    fun intersection_twoConcreteTypes() {
        val stmt = parseTopLevel("fun f(x: HasName & HasAge) = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x", intersectionType(typeName("HasName"), typeName("HasAge")))),
                body = id("x"),
            ),
        )
    }

    @Test
    fun intersection_withTypeVar() {
        val stmt = parseTopLevel("fun feed(a: 'A & Animal) = a")
        assertStmtEquals(
            stmt,
            funDef(
                "feed",
                params = listOf(param("a", intersectionType(typeVar("A"), typeName("Animal")))),
                body = id("a"),
            ),
        )
    }

    @Test
    fun intersection_leftAssociative() {
        val stmt = parseTopLevel("fun f(x: A & B & C) = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params =
                    listOf(
                        param(
                            "x",
                            intersectionType(
                                intersectionType(typeName("A"), typeName("B")),
                                typeName("C"),
                            ),
                        ),
                    ),
                body = id("x"),
            ),
        )
    }

    @Test
    fun precedence_unionOfIntersections_rightSide() {
        val stmt = parseStmt("x: A | B & C = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                nullLit(),
                typeAnnotation =
                    unionType(
                        typeName("A"),
                        intersectionType(typeName("B"), typeName("C")),
                    ),
            ),
        )
    }

    @Test
    fun precedence_unionOfIntersections_leftSide() {
        val stmt = parseStmt("x: A & B | C = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                nullLit(),
                typeAnnotation =
                    unionType(
                        intersectionType(typeName("A"), typeName("B")),
                        typeName("C"),
                    ),
            ),
        )
    }

    @Test
    fun precedence_intersectionInsideUnion_bothSides() {
        val stmt = parseStmt("x: A & B | C & D = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                nullLit(),
                typeAnnotation =
                    unionType(
                        intersectionType(typeName("A"), typeName("B")),
                        intersectionType(typeName("C"), typeName("D")),
                    ),
            ),
        )
    }

    @Test
    fun precedence_arrowLowerThanUnion_inParamPosition() {
        val stmt = parseStmt("f: A | B -> C = |x -> x|")
        assertStmtEquals(
            stmt,
            valStmt(
                "f",
                lambda("x", body = id("x")),
                typeAnnotation =
                    functionType(
                        unionType(typeName("A"), typeName("B")),
                        typeName("C"),
                    ),
            ),
        )
    }

    @Test
    fun precedence_arrowLowerThanUnion_inReturnPosition() {
        val stmt = parseStmt("f: A -> B | C = |x -> x|")
        assertStmtEquals(
            stmt,
            valStmt(
                "f",
                lambda("x", body = id("x")),
                typeAnnotation =
                    functionType(
                        typeName("A"),
                        unionType(typeName("B"), typeName("C")),
                    ),
            ),
        )
    }

    @Test
    fun precedence_arrowLowerThanIntersection_inParamPosition() {
        val stmt = parseStmt("f: A & B -> C = |x -> x|")
        assertStmtEquals(
            stmt,
            valStmt(
                "f",
                lambda("x", body = id("x")),
                typeAnnotation =
                    functionType(
                        intersectionType(typeName("A"), typeName("B")),
                        typeName("C"),
                    ),
            ),
        )
    }

    @Test
    fun parens_overridePrecedence_unionInsideIntersection() {
        val stmt = parseStmt("x: (A | B) & C = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                nullLit(),
                typeAnnotation =
                    intersectionType(
                        unionType(typeName("A"), typeName("B")),
                        typeName("C"),
                    ),
            ),
        )
    }

    @Test
    fun parens_groupAroundArrowInsideUnion() {
        val stmt = parseStmt("x: (A -> B) | C = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                nullLit(),
                typeAnnotation =
                    unionType(
                        functionType(typeName("A"), typeName("B")),
                        typeName("C"),
                    ),
            ),
        )
    }

    @Test
    fun union_withRecordType() {
        val stmt = parseTopLevel("fun f(x: Animal & { name: String }) = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params =
                    listOf(
                        param(
                            "x",
                            intersectionType(
                                typeName("Animal"),
                                recordType("name" to typeName("String")),
                            ),
                        ),
                    ),
                body = id("x"),
            ),
        )
    }

    @Test
    fun union_withAppliedType() {
        val stmt = parseStmt("x: List<Num> | Null = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                nullLit(),
                typeAnnotation =
                    unionType(
                        appliedType("List", typeName("Num")),
                        typeName("Null"),
                    ),
            ),
        )
    }

    @Test
    fun union_insideAppliedType() {
        val stmt = parseStmt("x: List<Num | String> = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                nullLit(),
                typeAnnotation =
                    appliedType(
                        "List",
                        unionType(typeName("Num"), typeName("String")),
                    ),
            ),
        )
    }

    @Test
    fun intersection_insideAppliedType() {
        val stmt = parseStmt("x: List<HasName & HasAge> = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                nullLit(),
                typeAnnotation =
                    appliedType(
                        "List",
                        intersectionType(typeName("HasName"), typeName("HasAge")),
                    ),
            ),
        )
    }

    @Test
    fun intersection_twoTypeVars() {
        val stmt = parseTopLevel("fun f(x: 'A & 'B) = x")
        assertStmtEquals(
            stmt,
            funDef(
                "f",
                params = listOf(param("x", intersectionType(typeVar("A"), typeVar("B")))),
                body = id("x"),
            ),
        )
    }

    @Test
    fun union_twoTypeVars() {
        val stmt = parseStmt("x: 'A | 'B = null")
        assertStmtEquals(
            stmt,
            valStmt(
                "x",
                nullLit(),
                typeAnnotation = unionType(typeVar("A"), typeVar("B")),
            ),
        )
    }

    @Test
    fun typeDef_constructorField_withUnion() {
        val typeDef = parseTypeDef("type Box = Box { contents: Num | String }")
        assertTypeDefEquals(
            typeDef,
            typeDef(
                "Box",
                emptyList(),
                constructor(
                    "Box",
                    field("contents", unionType(typeName("Num"), typeName("String"))),
                ),
            ),
        )
    }

    @Test
    fun typeDef_constructorField_withIntersection() {
        val typeDef = parseTypeDef("type Pet = Pet { owner: Person & HasAge }")
        assertTypeDefEquals(
            typeDef,
            typeDef(
                "Pet",
                emptyList(),
                constructor(
                    "Pet",
                    field("owner", intersectionType(typeName("Person"), typeName("HasAge"))),
                ),
            ),
        )
    }

    @Test
    fun dogCatExample_fromAdr() {
        val stmt =
            parseTopLevel(
                """
                fun dogCat(x: 'A): 'A | Dog = if true then Dog("Paco") else x
                """.trimIndent(),
            )
        assertStmtEquals(
            stmt,
            funDef(
                "dogCat",
                params = listOf(param("x", typeVar("A"))),
                body =
                    ifThenElse(
                        bool(true),
                        call(id("Dog"), string("Paco")),
                        id("x"),
                    ),
                returnType = unionType(typeVar("A"), typeName("Dog")),
            ),
        )
    }
}
