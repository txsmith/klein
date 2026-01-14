package klein.parser

import klein.ParseError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TypeDefTest {
    @Test
    fun simpleTypeNoConstructor() {
        assertTypeDefEquals(
            parseTypeDef("type Nothing"),
            typeDef("Nothing", constructors = arrayOf()),
        )
    }

    @Test
    fun simpleEnumNoFields() {
        assertTypeDefEquals(
            parseTypeDef("type Bool = True | False"),
            typeDef(
                "Bool",
                constructors =
                    arrayOf(
                        constructor("True"),
                        constructor("False"),
                    ),
            ),
        )
    }

    @Test
    fun singleConstructorNoFields() {
        assertTypeDefEquals(
            parseTypeDef("type Unit = Unit"),
            typeDef(
                "Unit",
                constructors =
                    arrayOf(
                        constructor("Unit"),
                    ),
            ),
        )
    }

    @Test
    fun constructorWithSingleField() {
        assertTypeDefEquals(
            parseTypeDef("type Money = Money { value: Num }"),
            typeDef(
                "Money",
                constructors =
                    arrayOf(
                        constructor("Money", field("value", typeName("Num"))),
                    ),
            ),
        )
    }

    @Test
    fun constructorWithMultipleFields() {
        assertTypeDefEquals(
            parseTypeDef("type Person = Person { name: String, age: Num }"),
            typeDef(
                "Person",
                constructors =
                    arrayOf(
                        constructor(
                            "Person",
                            field("name", typeName("String")),
                            field("age", typeName("Num")),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun multipleConstructorsSomeWithFields() {
        assertTypeDefEquals(
            parseTypeDef("type Result = Ok { value: Num } | Err { message: String }"),
            typeDef(
                "Result",
                constructors =
                    arrayOf(
                        constructor("Ok", field("value", typeName("Num"))),
                        constructor("Err", field("message", typeName("String"))),
                    ),
            ),
        )
    }

    @Test
    fun mixedConstructorsWithAndWithoutFields() {
        assertTypeDefEquals(
            parseTypeDef("type Option = None | Some { value: Num }"),
            typeDef(
                "Option",
                constructors =
                    arrayOf(
                        constructor("None"),
                        constructor("Some", field("value", typeName("Num"))),
                    ),
            ),
        )
    }

    @Test
    fun singleTypeParam() {
        assertTypeDefEquals(
            parseTypeDef("type Option<'A> = None | Some { value: 'A }"),
            typeDef(
                "Option",
                listOf("A"),
                constructor("None"),
                constructor("Some", field("value", typeVar("A"))),
            ),
        )
    }

    @Test
    fun multipleTypeParams() {
        assertTypeDefEquals(
            parseTypeDef("type Either<'A, 'B> = Left { value: 'A } | Right { value: 'B }"),
            typeDef(
                "Either",
                listOf("A", "B"),
                constructor("Left", field("value", typeVar("A"))),
                constructor("Right", field("value", typeVar("B"))),
            ),
        )
    }

    @Test
    fun typeParamInMultipleFields() {
        assertTypeDefEquals(
            parseTypeDef("type Pair<'A, 'B> = Pair { first: 'A, second: 'B }"),
            typeDef(
                "Pair",
                listOf("A", "B"),
                constructor(
                    "Pair",
                    field("first", typeVar("A")),
                    field("second", typeVar("B")),
                ),
            ),
        )
    }

    @Test
    fun threeTypeParams() {
        assertTypeDefEquals(
            parseTypeDef("type Triple<'A, 'B, 'C> = Triple { a: 'A, b: 'B, c: 'C }"),
            typeDef(
                "Triple",
                listOf("A", "B", "C"),
                constructor(
                    "Triple",
                    field("a", typeVar("A")),
                    field("b", typeVar("B")),
                    field("c", typeVar("C")),
                ),
            ),
        )
    }

    @Test
    fun manyConstructors() {
        assertTypeDefEquals(
            parseTypeDef("type Color = Red | Green | Blue | Yellow"),
            typeDef(
                "Color",
                constructors =
                    arrayOf(
                        constructor("Red"),
                        constructor("Green"),
                        constructor("Blue"),
                        constructor("Yellow"),
                    ),
            ),
        )
    }

    @Test
    fun missingTypeKeyword() {
        assertFailsWith<ParseError> { parseTypeDef("Bool = True | False") }
    }

    @Test
    fun missingEquals() {
        assertFailsWith<ParseError> { parseTypeDef("type Bool True | False") }
    }

    @Test
    fun missingConstructors() {
        assertFailsWith<ParseError> { parseTypeDef("type Bool =") }
    }

    @Test
    fun lowercaseTypeName() {
        assertFailsWith<ParseError> { parseTypeDef("type bool = True | False") }
    }

    @Test
    fun lowercaseConstructorName() {
        assertFailsWith<ParseError> { parseTypeDef("type Bool = true | false") }
    }

    @Test
    fun missingFieldType() {
        assertFailsWith<ParseError> { parseTypeDef("type Box = Box { value: }") }
    }

    @Test
    fun missingFieldName() {
        assertFailsWith<ParseError> { parseTypeDef("type Box = Box { : Num }") }
    }

    @Test
    fun missingColon() {
        assertFailsWith<ParseError> { parseTypeDef("type Box = Box { value Num }") }
    }

    @Test
    fun unclosedBrace() {
        assertFailsWith<ParseError> { parseTypeDef("type Box = Box { value: Num") }
    }

    @Test
    fun unclosedTypeParams() {
        assertFailsWith<ParseError> { parseTypeDef("type Box<'A = Box { value: 'A }") }
    }

    @Test
    fun emptyTypeParams() {
        assertFailsWith<ParseError> { parseTypeDef("type Box<> = Box") }
    }

    @Test
    fun typeVarWithoutQuote() {
        assertFailsWith<ParseError> { parseTypeDef("type Box<A> = Box { value: A }") }
    }

    @Test
    fun trailingPipe() {
        assertFailsWith<ParseError> { parseTypeDef("type Bool = True | False |") }
    }

    @Test
    fun leadingPipe() {
        assertFailsWith<ParseError> { parseTypeDef("type Bool = | True | False") }
    }

    @Test
    fun emptyBraces() {
        assertFailsWith<ParseError> { parseTypeDef("type Empty = Empty { }") }
    }

    @Test
    fun trailingCommaInFields() {
        assertTypeDefEquals(
            parseTypeDef("type Person = Person { name: String, age: Num, }"),
            typeDef(
                "Person",
                constructors =
                    arrayOf(
                        constructor(
                            "Person",
                            field("name", typeName("String")),
                            field("age", typeName("Num")),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun trailingCommaInTypeParams() {
        assertTypeDefEquals(
            parseTypeDef("type Pair<'A, 'B,> = Pair { first: 'A, second: 'B }"),
            typeDef(
                "Pair",
                listOf("A", "B"),
                constructor(
                    "Pair",
                    field("first", typeVar("A")),
                    field("second", typeVar("B")),
                ),
            ),
        )
    }

    @Test
    fun singleCharacterNames() {
        assertTypeDefEquals(
            parseTypeDef("type A = B"),
            typeDef("A", constructors = arrayOf(constructor("B"))),
        )
    }

    @Test
    fun numbersInIdentifiers() {
        assertTypeDefEquals(
            parseTypeDef("type Type123 = Cons456 { field789: Num }"),
            typeDef(
                "Type123",
                constructors =
                    arrayOf(
                        constructor("Cons456", field("field789", typeName("Num"))),
                    ),
            ),
        )
    }

    @Test
    fun underscoresInNames() {
        assertTypeDefEquals(
            parseTypeDef("type My_Type = My_Cons { my_field: Num }"),
            typeDef(
                "My_Type",
                constructors =
                    arrayOf(
                        constructor("My_Cons", field("my_field", typeName("Num"))),
                    ),
            ),
        )
    }

    @Test
    fun allCapsNames() {
        assertTypeDefEquals(
            parseTypeDef("type FOO = BAR"),
            typeDef("FOO", constructors = arrayOf(constructor("BAR"))),
        )
    }

    @Test
    fun simpleRecursiveType() {
        assertTypeDefEquals(
            parseTypeDef("type Nat = Zero | Succ { pred: Nat }"),
            typeDef(
                "Nat",
                constructors =
                    arrayOf(
                        constructor("Zero"),
                        constructor("Succ", field("pred", typeName("Nat"))),
                    ),
            ),
        )
    }

    @Test
    fun recursiveTypeWithTypeParam() {
        assertTypeDefEquals(
            parseTypeDef("type List<'A> = Nil | Cons { head: 'A, tail: List }"),
            typeDef(
                "List",
                listOf("A"),
                constructor("Nil"),
                constructor(
                    "Cons",
                    field("head", typeVar("A")),
                    field("tail", typeName("List")),
                ),
            ),
        )
    }

    @Test
    fun binaryTree() {
        assertTypeDefEquals(
            parseTypeDef("type Tree<'A> = Leaf { value: 'A } | Node { left: Tree, right: Tree }"),
            typeDef(
                "Tree",
                listOf("A"),
                constructor("Leaf", field("value", typeVar("A"))),
                constructor(
                    "Node",
                    field("left", typeName("Tree")),
                    field("right", typeName("Tree")),
                ),
            ),
        )
    }

    @Test
    fun unusedTypeParam() {
        assertTypeDefEquals(
            parseTypeDef("type Phantom<'A> = Phantom { value: Num }"),
            typeDef(
                "Phantom",
                listOf("A"),
                constructor("Phantom", field("value", typeName("Num"))),
            ),
        )
    }

    @Test
    fun sameTypeParamInMultipleFields() {
        assertTypeDefEquals(
            parseTypeDef("type Same<'A> = Same { x: 'A, y: 'A, z: 'A }"),
            typeDef(
                "Same",
                listOf("A"),
                constructor(
                    "Same",
                    field("x", typeVar("A")),
                    field("y", typeVar("A")),
                    field("z", typeVar("A")),
                ),
            ),
        )
    }

    @Test
    fun onlyFirstTypeParamUsed() {
        assertTypeDefEquals(
            parseTypeDef("type Const<'A, 'B> = Const { value: 'A }"),
            typeDef(
                "Const",
                listOf("A", "B"),
                constructor("Const", field("value", typeVar("A"))),
            ),
        )
    }

    @Test
    fun manyFields() {
        assertTypeDefEquals(
            parseTypeDef("type Big = Big { a: A, b: B, c: C, d: D, e: E, f: F }"),
            typeDef(
                "Big",
                constructors =
                    arrayOf(
                        constructor(
                            "Big",
                            field("a", typeName("A")),
                            field("b", typeName("B")),
                            field("c", typeName("C")),
                            field("d", typeName("D")),
                            field("e", typeName("E")),
                            field("f", typeName("F")),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun manyTypeParams() {
        assertTypeDefEquals(
            parseTypeDef("type Tuple4<'A, 'B, 'C, 'D> = Tuple4 { a: 'A, b: 'B, c: 'C, d: 'D }"),
            typeDef(
                "Tuple4",
                listOf("A", "B", "C", "D"),
                constructor(
                    "Tuple4",
                    field("a", typeVar("A")),
                    field("b", typeVar("B")),
                    field("c", typeVar("C")),
                    field("d", typeVar("D")),
                ),
            ),
        )
    }

    @Test
    fun shadowingBuiltinInt() {
        assertTypeDefEquals(
            parseTypeDef("type Int = Int { value: Num }"),
            typeDef(
                "Int",
                constructors =
                    arrayOf(
                        constructor("Int", field("value", typeName("Num"))),
                    ),
            ),
        )
    }

    @Test
    fun shadowingBuiltinString() {
        assertTypeDefEquals(
            parseTypeDef("type String = Empty | Chars { data: Num }"),
            typeDef(
                "String",
                constructors =
                    arrayOf(
                        constructor("Empty"),
                        constructor("Chars", field("data", typeName("Num"))),
                    ),
            ),
        )
    }

    @Test
    fun minimalWhitespaceNoSpacesAroundEquals() {
        assertTypeDefEquals(
            parseTypeDef("type A=B"),
            typeDef("A", constructors = arrayOf(constructor("B"))),
        )
    }

    @Test
    fun minimalWhitespaceNoSpacesAroundPipe() {
        assertTypeDefEquals(
            parseTypeDef("type A = B|C"),
            typeDef(
                "A",
                constructors =
                    arrayOf(
                        constructor("B"),
                        constructor("C"),
                    ),
            ),
        )
    }

    @Test
    fun minimalWhitespaceThroughout() {
        assertTypeDefEquals(
            parseTypeDef("type A=B{x:C}"),
            typeDef(
                "A",
                constructors =
                    arrayOf(
                        constructor("B", field("x", typeName("C"))),
                    ),
            ),
        )
    }

    @Test
    fun nestedRecordType() {
        assertTypeDefEquals(
            parseTypeDef("type Nested = Nested { r: { inner: Num } }"),
            typeDef(
                "Nested",
                constructors =
                    arrayOf(
                        constructor(
                            "Nested",
                            field(
                                "r",
                                recordType(
                                    "inner" to typeName("Num"),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun functionTypeField() {
        assertTypeDefEquals(
            parseTypeDef("type Handler = Handler { f: Num -> String }"),
            typeDef(
                "Handler",
                constructors =
                    arrayOf(
                        constructor("Handler", field("f", functionType(typeName("Num"), typeName("String")))),
                    ),
            ),
        )
    }

    @Test
    fun functionTypeMultipleParams() {
        assertTypeDefEquals(
            parseTypeDef("type BinOp = BinOp { f: (Num, Num) -> Num }"),
            typeDef(
                "BinOp",
                constructors =
                    arrayOf(
                        constructor(
                            "BinOp",
                            field(
                                "f",
                                functionType(
                                    tupleType(typeName("Num"), typeName("Num")),
                                    typeName("Num"),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun functionTypeWithTypeVar() {
        assertTypeDefEquals(
            parseTypeDef("type Mapper<'A, 'B> = Mapper { f: 'A -> 'B }"),
            typeDef(
                "Mapper",
                listOf("A", "B"),
                constructor("Mapper", field("f", functionType(typeVar("A"), typeVar("B")))),
            ),
        )
    }

    @Test
    fun higherOrderFunctionType() {
        assertTypeDefEquals(
            parseTypeDef("type HOF = HOF { f: (Num -> Num) -> Num }"),
            typeDef(
                "HOF",
                constructors =
                    arrayOf(
                        constructor(
                            "HOF",
                            field(
                                "f",
                                functionType(
                                    functionType(typeName("Num"), typeName("Num")),
                                    typeName("Num"),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun functionReturningFunction() {
        assertTypeDefEquals(
            parseTypeDef("type Curried = Curried { f: Num -> Num -> Num }"),
            typeDef(
                "Curried",
                constructors =
                    arrayOf(
                        constructor(
                            "Curried",
                            field(
                                "f",
                                functionType(
                                    typeName("Num"),
                                    functionType(typeName("Num"), typeName("Num")),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun duplicateConstructorNames() {
        assertFailsWith<ParseError> { parseTypeDef("type Bool = True | True") }
    }

    @Test
    fun duplicateFieldNames() {
        assertFailsWith<ParseError> { parseTypeDef("type Point = Point { x: Num, x: Num }") }
    }

    @Test
    fun duplicateTypeParams() {
        assertFailsWith<ParseError> { parseTypeDef("type Pair<'A, 'A> = Pair { a: 'A, b: 'A }") }
    }

    @Test
    fun undefinedTypeVariable() {
        assertFailsWith<ParseError> { parseTypeDef("type Box<'A> = Box { value: 'B }") }
    }

    @Test
    fun missingPipeBetweenConstructors() {
        assertFailsWith<ParseError> { parseTypeDef("type Bool = True False") }
    }

    @Test
    fun commaInsteadOfPipe() {
        assertFailsWith<ParseError> { parseTypeDef("type Bool = True, False") }
    }

    @Test
    fun equalsInsteadOfColonInField() {
        assertFailsWith<ParseError> { parseTypeDef("type Foo = Foo { a = Num }") }
    }

    @Test
    fun semicolonInsteadOfCommaInFields() {
        assertFailsWith<ParseError> { parseTypeDef("type Foo = Foo { a: Num; b: Num }") }
    }

    @Test
    fun extraTokensAfterDefinition() {
        assertFailsWith<ParseError> { parseTypeDef("type Unit = Unit extra") }
    }

    @Test
    fun reservedWordAsTypeName() {
        assertFailsWith<ParseError> { parseTypeDef("type Type = Foo") }
    }

    @Test
    fun keywordAsFieldName() {
        assertFailsWith<ParseError> { parseTypeDef("type Foo = Foo { if: Num }") }
    }

    @Test
    fun keywordAsConstructorName() {
        assertFailsWith<ParseError> { parseTypeDef("type Foo = If | Then | Else") }
    }

    @Test
    fun parensInsteadOfBraces() {
        assertFailsWith<ParseError> { parseTypeDef("type Option = None | Some ( value: Num )") }
    }

    @Test
    fun bracketsInsteadOfBraces() {
        assertFailsWith<ParseError> { parseTypeDef("type Option = None | Some [ value: Num ]") }
    }

    @Test
    fun malformedFunctionType() {
        assertFailsWith<ParseError> { parseTypeDef("type Fn = Fn { f: -> Num }") }
    }

    @Test
    fun incompleteFunctionType() {
        assertFailsWith<ParseError> { parseTypeDef("type Fn = Fn { f: Num -> }") }
    }

    @Test
    fun multilineEqualsOnNewLine() {
        assertProgramEquals(
            parseProgram(
                """
                type Bool
                    = True
                    | False
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Bool",
                    constructors =
                        arrayOf(
                            constructor("True"),
                            constructor("False"),
                        ),
                ),
            ),
        )
    }

    @Test
    fun multilineConstructorsOnSeparateLines() {
        assertProgramEquals(
            parseProgram(
                """
                type Color = Red
                    | Green
                    | Blue
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Color",
                    constructors =
                        arrayOf(
                            constructor("Red"),
                            constructor("Green"),
                            constructor("Blue"),
                        ),
                ),
            ),
        )
    }

    @Test
    fun multilinePipeAtStartOfLine() {
        assertProgramEquals(
            parseProgram(
                """
                type Option<'A> = None
                               | Some { value: 'A }
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Option",
                    listOf("A"),
                    constructor("None"),
                    constructor("Some", field("value", typeVar("A"))),
                ),
            ),
        )
    }

    @Test
    fun multilineFieldsOnSeparateLines() {
        assertProgramEquals(
            parseProgram(
                """
                type Person = Person {
                    name: String,
                    age: Num,
                    email: String
                }
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Person",
                    constructors =
                        arrayOf(
                            constructor(
                                "Person",
                                field("name", typeName("String")),
                                field("age", typeName("Num")),
                                field("email", typeName("String")),
                            ),
                        ),
                ),
            ),
        )
    }

    @Test
    fun multilineTypeParamsOnSeparateLines() {
        assertProgramEquals(
            parseProgram(
                """
                type Either<
                    'A,
                    'B
                > = Left { value: 'A }
                  | Right { value: 'B }
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Either",
                    listOf("A", "B"),
                    constructor("Left", field("value", typeVar("A"))),
                    constructor("Right", field("value", typeVar("B"))),
                ),
            ),
        )
    }

    @Test
    fun multilineComplexType() {
        assertProgramEquals(
            parseProgram(
                """
                type Result<'T, 'E>
                    = Ok {
                        value: 'T
                    }
                    | Err {
                        error: 'E,
                        code: Num
                    }
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Result",
                    listOf("T", "E"),
                    constructor("Ok", field("value", typeVar("T"))),
                    constructor(
                        "Err",
                        field("error", typeVar("E")),
                        field("code", typeName("Num")),
                    ),
                ),
            ),
        )
    }

    @Test
    fun multilineTypeFollowedByExpression() {
        assertProgramEquals(
            parseProgram(
                """
                type Unit = Unit

                x = 42
                """.trimIndent(),
            ),
            listOf(
                typeDef("Unit", constructors = arrayOf(constructor("Unit"))),
                valStmt("x", int(42)),
            ),
        )
    }

    @Test
    fun multilineMultipleTypes() {
        assertProgramEquals(
            parseProgram(
                """
                type Bool = True | False

                type Option<'A>
                    = None
                    | Some { value: 'A }

                type Unit = Unit
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Bool",
                    constructors =
                        arrayOf(
                            constructor("True"),
                            constructor("False"),
                        ),
                ),
                typeDef(
                    "Option",
                    listOf("A"),
                    constructor("None"),
                    constructor("Some", field("value", typeVar("A"))),
                ),
                typeDef("Unit", constructors = arrayOf(constructor("Unit"))),
            ),
        )
    }

    @Test
    fun multilineTypeWithFunctionInProgram() {
        assertProgramEquals(
            parseProgram(
                """
                type Box<'A> = Box { value: 'A }

                unwrap(box) = box.value
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Box",
                    listOf("A"),
                    constructor("Box", field("value", typeVar("A"))),
                ),
                funDef("unwrap", "box", body = fieldAccess(id("box"), "value")),
            ),
        )
    }

    @Test
    fun multilineBadIndentation() {
        assertFailsWith<ParseError> {
            parseProgram(
                """
                type Bool = True
                | False
                """.trimIndent(),
            )
        }
    }

    @Test
    fun multilineFieldBadIndentation() {
        assertFailsWith<ParseError> {
            parseProgram(
                """
                type Person = Person {
                name: String
                }
                """.trimIndent(),
            )
        }
    }

    @Test
    fun typeInsideLambda() {
        val error = assertFailsWith<ParseError> { parse("|x -> type Inner = Inner|") }
        assertEquals("Type definitions are only allowed at the top level", error.message)
    }

    @Test
    fun typeInsideBlock() {
        val program =
            """
            result =
              type Inner = Inner
              42
            """.trimIndent()
        val error = assertFailsWith<ParseError> { parseProgram(program) }
        assertEquals("Type definitions are only allowed at the top level", error.message)
    }

    @Test
    fun typeInsideFun() {
        val program =
            """
            fun outer(x) =
              type Inner = Inner
              x
            """.trimIndent()
        val error = assertFailsWith<ParseError> { parseProgram(program) }
        assertEquals("Type definitions are only allowed at the top level", error.message)
    }

    @Test
    fun typeInsideRecord() {
        val error = assertFailsWith<ParseError> { parse("{ f = type Inner = Inner }") }
        assertEquals("Type definitions are only allowed at the top level", error.message)
    }

    @Test
    fun typeInsideIfThen() {
        val error = assertFailsWith<ParseError> { parse("if true then type T = T else 1") }
        assertEquals("Type definitions are only allowed at the top level", error.message)
    }
}
