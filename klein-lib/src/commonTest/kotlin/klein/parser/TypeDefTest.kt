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
            parseTypeDef("type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }"),
            typeDef(
                "List",
                listOf("A"),
                constructor("Nil"),
                constructor(
                    "Cons",
                    field("head", typeVar("A")),
                    field("tail", appliedType("List", typeVar("A"))),
                ),
            ),
        )
    }

    @Test
    fun binaryTree() {
        assertTypeDefEquals(
            parseTypeDef("type Tree<'A> = Leaf { value: 'A } | Node { left: Tree<'A>, right: Tree<'A> }"),
            typeDef(
                "Tree",
                listOf("A"),
                constructor("Leaf", field("value", typeVar("A"))),
                constructor(
                    "Node",
                    field("left", appliedType("Tree", typeVar("A"))),
                    field("right", appliedType("Tree", typeVar("A"))),
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

                fun unwrap(box) = box.value
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

    @Test
    fun appliedTypeWithMultipleConcreteTypes() {
        assertTypeDefEquals(
            parseTypeDef("type Cache = Cache { data: Map<String, Num> }"),
            typeDef(
                "Cache",
                constructors =
                    arrayOf(
                        constructor(
                            "Cache",
                            field("data", appliedType("Map", typeName("String"), typeName("Num"))),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun appliedTypeWithManyConcreteTypes() {
        assertTypeDefEquals(
            parseTypeDef("type Multi = Multi { data: Tuple5<A, B, C, D, E> }"),
            typeDef(
                "Multi",
                constructors =
                    arrayOf(
                        constructor(
                            "Multi",
                            field(
                                "data",
                                appliedType(
                                    "Tuple5",
                                    typeName("A"),
                                    typeName("B"),
                                    typeName("C"),
                                    typeName("D"),
                                    typeName("E"),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun appliedTypeWithEmptyTypeArgs() {
        assertFailsWith<ParseError> { parseTypeDef("type Box = Box { value: List<> }") }
    }

    @Test
    fun appliedTypeWithMixedConcreteAndTypeVars() {
        assertTypeDefEquals(
            parseTypeDef("type Processor<'A> = Processor { result: Result<'A, String> }"),
            typeDef(
                "Processor",
                listOf("A"),
                constructor("Processor", field("result", appliedType("Result", typeVar("A"), typeName("String")))),
            ),
        )
    }

    @Test
    fun nestedAppliedTypes() {
        assertTypeDefEquals(
            parseTypeDef("type Nested<'A> = Nested { items: List<Option<'A>> }"),
            typeDef(
                "Nested",
                listOf("A"),
                constructor(
                    "Nested",
                    field("items", appliedType("List", appliedType("Option", typeVar("A")))),
                ),
            ),
        )
    }

    @Test
    fun deeplyNestedAppliedTypes() {
        assertTypeDefEquals(
            parseTypeDef("type Deep = Deep { data: Result<Option<List<Num>>, String> }"),
            typeDef(
                "Deep",
                constructors =
                    arrayOf(
                        constructor(
                            "Deep",
                            field(
                                "data",
                                appliedType(
                                    "Result",
                                    appliedType("Option", appliedType("List", typeName("Num"))),
                                    typeName("String"),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun appliedTypeWithAllTypeVars() {
        assertTypeDefEquals(
            parseTypeDef("type Either3<'A, 'B, 'C> = Wrapper { value: Triple<'A, 'B, 'C> }"),
            typeDef(
                "Either3",
                listOf("A", "B", "C"),
                constructor(
                    "Wrapper",
                    field("value", appliedType("Triple", typeVar("A"), typeVar("B"), typeVar("C"))),
                ),
            ),
        )
    }

    @Test
    fun appliedTypeAsRecursiveField() {
        assertTypeDefEquals(
            parseTypeDef("type Tree<'A> = Node { value: 'A, children: List<Tree<'A>> }"),
            typeDef(
                "Tree",
                listOf("A"),
                constructor(
                    "Node",
                    field("value", typeVar("A")),
                    field("children", appliedType("List", appliedType("Tree", typeVar("A")))),
                ),
            ),
        )
    }

    @Test
    fun multipleFieldsWithDifferentAppliedTypes() {
        assertTypeDefEquals(
            parseTypeDef("type Store<'K, 'V> = Store { keys: List<'K>, values: List<'V>, index: Map<'K, 'V> }"),
            typeDef(
                "Store",
                listOf("K", "V"),
                constructor(
                    "Store",
                    field("keys", appliedType("List", typeVar("K"))),
                    field("values", appliedType("List", typeVar("V"))),
                    field("index", appliedType("Map", typeVar("K"), typeVar("V"))),
                ),
            ),
        )
    }

    @Test
    fun appliedTypeWithSingleConcreteArg() {
        assertTypeDefEquals(
            parseTypeDef("type StringBox = StringBox { value: Box<String> }"),
            typeDef(
                "StringBox",
                constructors =
                    arrayOf(
                        constructor("StringBox", field("value", appliedType("Box", typeName("String")))),
                    ),
            ),
        )
    }

    @Test
    fun appliedTypeInMultipleConstructors() {
        assertTypeDefEquals(
            parseTypeDef("type Response<'T> = Success { data: List<'T> } | Error { messages: List<String> }"),
            typeDef(
                "Response",
                listOf("T"),
                constructor("Success", field("data", appliedType("List", typeVar("T")))),
                constructor("Error", field("messages", appliedType("List", typeName("String")))),
            ),
        )
    }

    // Tests merged from TypeDefEdgeCaseTest.kt
    @Test
    fun multilineWithEmptyLineAfterEquals() {
        assertProgramEquals(
            parseProgram(
                """
                type Color =

                    Red
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
    fun multilineWithMultipleEmptyLines() {
        assertProgramEquals(
            parseProgram(
                """
                type Option<'A>


                    = None


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
    fun multilineFieldsWithEmptyLines() {
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
    fun multilineTypeArgsSpanningLines() {
        assertProgramEquals(
            parseProgram(
                """
                type Container<'A> = Container {
                    data: Result<
                        'A,
                        String
                    >
                }
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Container",
                    listOf("A"),
                    constructor(
                        "Container",
                        field("data", appliedType("Result", typeVar("A"), typeName("String"))),
                    ),
                ),
            ),
        )
    }

    @Test
    fun multilineRecordTypeInField() {
        assertProgramEquals(
            parseProgram(
                """
                type Wrapper = Wrapper {
                    config: {
                        host: String,
                        port: Num
                    }
                }
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Wrapper",
                    constructors =
                        arrayOf(
                            constructor(
                                "Wrapper",
                                field(
                                    "config",
                                    recordType(
                                        "host" to typeName("String"),
                                        "port" to typeName("Num"),
                                    ),
                                ),
                            ),
                        ),
                ),
            ),
        )
    }

    @Test
    fun multilineFunctionTypeSpanningLines() {
        assertProgramEquals(
            parseProgram(
                """
                type Handler<'A, 'B> = Handler {
                    process: 'A
                        -> 'B
                }
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Handler",
                    listOf("A", "B"),
                    constructor(
                        "Handler",
                        field("process", functionType(typeVar("A"), typeVar("B"))),
                    ),
                ),
            ),
        )
    }

    @Test
    fun deeplyNestedFunctionTypes() {
        assertTypeDefEquals(
            parseTypeDef("type Chain = Chain { f: A -> B -> C -> D -> E }"),
            typeDef(
                "Chain",
                constructors =
                    arrayOf(
                        constructor(
                            "Chain",
                            field(
                                "f",
                                functionType(
                                    typeName("A"),
                                    functionType(
                                        typeName("B"),
                                        functionType(
                                            typeName("C"),
                                            functionType(typeName("D"), typeName("E")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun functionWithTupleParamAndTupleReturn() {
        assertTypeDefEquals(
            parseTypeDef("type Transformer = Transformer { f: (A, B) -> (C, D) }"),
            typeDef(
                "Transformer",
                constructors =
                    arrayOf(
                        constructor(
                            "Transformer",
                            field(
                                "f",
                                functionType(
                                    tupleType(typeName("A"), typeName("B")),
                                    tupleType(typeName("C"), typeName("D")),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun functionWithRecordParamAndRecordReturn() {
        assertTypeDefEquals(
            parseTypeDef("type Mapper = Mapper { f: { x: Num } -> { y: String } }"),
            typeDef(
                "Mapper",
                constructors =
                    arrayOf(
                        constructor(
                            "Mapper",
                            field(
                                "f",
                                functionType(
                                    recordType("x" to typeName("Num")),
                                    recordType("y" to typeName("String")),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun tupleContainingAppliedTypes() {
        assertTypeDefEquals(
            parseTypeDef("type Pair<'A, 'B> = Pair { value: (List<'A>, Option<'B>) }"),
            typeDef(
                "Pair",
                listOf("A", "B"),
                constructor(
                    "Pair",
                    field(
                        "value",
                        tupleType(
                            appliedType("List", typeVar("A")),
                            appliedType("Option", typeVar("B")),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun recordWithMultipleFunctionFields() {
        assertTypeDefEquals(
            parseTypeDef("type Interface = Interface { get: Num -> A, set: A -> Num, transform: A -> B }"),
            typeDef(
                "Interface",
                constructors =
                    arrayOf(
                        constructor(
                            "Interface",
                            field("get", functionType(typeName("Num"), typeName("A"))),
                            field("set", functionType(typeName("A"), typeName("Num"))),
                            field("transform", functionType(typeName("A"), typeName("B"))),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun tripleNestedRecordTypes() {
        assertTypeDefEquals(
            parseTypeDef("type Deep = Deep { outer: { middle: { inner: Num } } }"),
            typeDef(
                "Deep",
                constructors =
                    arrayOf(
                        constructor(
                            "Deep",
                            field(
                                "outer",
                                recordType(
                                    "middle" to
                                        recordType(
                                            "inner" to typeName("Num"),
                                        ),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun appliedTypeInFunctionReturn() {
        assertTypeDefEquals(
            parseTypeDef("type Factory<'A> = Factory { create: Num -> List<Option<'A>> }"),
            typeDef(
                "Factory",
                listOf("A"),
                constructor(
                    "Factory",
                    field(
                        "create",
                        functionType(
                            typeName("Num"),
                            appliedType("List", appliedType("Option", typeVar("A"))),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun multipleConstructorsWithComplexTypes() {
        assertTypeDefEquals(
            parseTypeDef(
                "type Event<'A> = Data { payload: List<'A>, meta: { timestamp: Num } } | Error { code: Num, handler: String -> Result }",
            ),
            typeDef(
                "Event",
                listOf("A"),
                constructor(
                    "Data",
                    field("payload", appliedType("List", typeVar("A"))),
                    field("meta", recordType("timestamp" to typeName("Num"))),
                ),
                constructor(
                    "Error",
                    field("code", typeName("Num")),
                    field("handler", functionType(typeName("String"), typeName("Result"))),
                ),
            ),
        )
    }

    @Test
    fun typeParamInDeeplyNestedPosition() {
        assertTypeDefEquals(
            parseTypeDef("type Complex<'A> = Complex { data: Result<Option<List<'A>>, String> }"),
            typeDef(
                "Complex",
                listOf("A"),
                constructor(
                    "Complex",
                    field(
                        "data",
                        appliedType(
                            "Result",
                            appliedType("Option", appliedType("List", typeVar("A"))),
                            typeName("String"),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun selfReferenceInMultiplePositions() {
        assertTypeDefEquals(
            parseTypeDef("type Graph<'A> = Graph { value: 'A, left: Option<Graph<'A>>, right: Option<Graph<'A>> }"),
            typeDef(
                "Graph",
                listOf("A"),
                constructor(
                    "Graph",
                    field("value", typeVar("A")),
                    field("left", appliedType("Option", appliedType("Graph", typeVar("A")))),
                    field("right", appliedType("Option", appliedType("Graph", typeVar("A")))),
                ),
            ),
        )
    }

    @Test
    fun spacesInsideTypeParams() {
        assertTypeDefEquals(
            parseTypeDef("type Box< 'A > = Box { value: 'A }"),
            typeDef(
                "Box",
                listOf("A"),
                constructor("Box", field("value", typeVar("A"))),
            ),
        )
    }

    @Test
    fun spacesInsideAppliedTypeArgs() {
        assertTypeDefEquals(
            parseTypeDef("type Container = Container { data: Map< String , Num > }"),
            typeDef(
                "Container",
                constructors =
                    arrayOf(
                        constructor(
                            "Container",
                            field("data", appliedType("Map", typeName("String"), typeName("Num"))),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun minimalWhitespaceInComplexType() {
        assertTypeDefEquals(
            parseTypeDef("type X<'A,'B> =Y{a:'A,b:List<'B>}|Z{c:('A,'B)->Result<'A,'B>}"),
            typeDef(
                "X",
                listOf("A", "B"),
                constructor(
                    "Y",
                    field("a", typeVar("A")),
                    field("b", appliedType("List", typeVar("B"))),
                ),
                constructor(
                    "Z",
                    field(
                        "c",
                        functionType(
                            tupleType(typeVar("A"), typeVar("B")),
                            appliedType("Result", typeVar("A"), typeVar("B")),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun typeParamsFollowedByEqualsNeedsSpaceToAvoidGtEqToken() {
        assertFailsWith<ParseError> { parseTypeDef("type X<'A>= X") }
    }

    @Test
    fun excessiveWhitespaceEverywhere() {
        assertTypeDefEquals(
            parseTypeDef("type   Box  <  'A  >   =   Box   {   value  :   'A   }"),
            typeDef(
                "Box",
                listOf("A"),
                constructor("Box", field("value", typeVar("A"))),
            ),
        )
    }

    @Test
    fun sameFieldNameAcrossConstructorsIsAllowed() {
        assertTypeDefEquals(
            parseTypeDef("type Result<'A, 'E> = Ok { value: 'A } | Err { value: 'E }"),
            typeDef(
                "Result",
                listOf("A", "E"),
                constructor("Ok", field("value", typeVar("A"))),
                constructor("Err", field("value", typeVar("E"))),
            ),
        )
    }

    @Test
    fun emptyTupleAsFieldType_parsesAsUnit() {
        assertTypeDefEquals(
            parseTypeDef("type Unit = Unit { value: () }"),
            typeDef(
                "Unit",
                constructors =
                    arrayOf(
                        constructor("Unit", field("value", tupleType())),
                    ),
            ),
        )
    }

    @Test
    fun singleElementTupleIsParenthesizedType() {
        assertTypeDefEquals(
            parseTypeDef("type Wrapped = Wrapped { value: (Num) }"),
            typeDef(
                "Wrapped",
                constructors =
                    arrayOf(
                        constructor("Wrapped", field("value", typeName("Num"))),
                    ),
            ),
        )
    }

    @Test
    fun parenthesizedFunctionType() {
        assertTypeDefEquals(
            parseTypeDef("type Alias = Alias { f: (A -> B) }"),
            typeDef(
                "Alias",
                constructors =
                    arrayOf(
                        constructor("Alias", field("f", functionType(typeName("A"), typeName("B")))),
                    ),
            ),
        )
    }

    @Test
    fun doublePipeIsError() {
        assertFailsWith<ParseError> { parseTypeDef("type Bool = True || False") }
    }

    @Test
    fun extraClosingAngleBracket() {
        assertFailsWith<ParseError> { parseTypeDef("type Box<'A>> = Box { value: 'A }") }
    }

    @Test
    fun missingOpeningAngleBracket() {
        assertFailsWith<ParseError> { parseTypeDef("type Box 'A> = Box { value: 'A }") }
    }

    @Test
    fun typeVarInAppliedTypePositionWithoutDefinition() {
        assertFailsWith<ParseError> { parseTypeDef("type Container = Container { data: List<'X> }") }
    }

    @Test
    fun nestedUnclosedBrace() {
        assertFailsWith<ParseError> { parseTypeDef("type Bad = Bad { r: { x: Num }") }
    }

    @Test
    fun unclosedTypeArgs() {
        assertFailsWith<ParseError> { parseTypeDef("type Bad = Bad { data: List<Num }") }
    }

    @Test
    fun multilineConstructorAtSameIndentAsTypeIsError() {
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
    fun typeWithOnlyWhitespaceAfterEquals() {
        assertFailsWith<ParseError> {
            parseTypeDef("type Empty =   ")
        }
    }

    @Test
    fun trailingCommaInAppliedTypeArgsNotSupported() {
        assertFailsWith<ParseError> { parseTypeDef("type Box = Box { value: List<Num,> }") }
    }

    @Test
    fun multilineTypeDefinitionsWithInterleavedExpressions() {
        assertProgramEquals(
            parseProgram(
                """
                type A = A

                x = 1

                type B<'T>
                    = B { value: 'T }

                y = 2

                type C = C1 | C2
                """.trimIndent(),
            ),
            listOf(
                typeDef("A", constructors = arrayOf(constructor("A"))),
                valStmt("x", int(1)),
                typeDef("B", listOf("T"), constructor("B", field("value", typeVar("T")))),
                valStmt("y", int(2)),
                typeDef("C", constructors = arrayOf(constructor("C1"), constructor("C2"))),
            ),
        )
    }

    @Test
    fun typeFollowedByFunctionDefinition() {
        assertProgramEquals(
            parseProgram(
                """
                type Option<'A> = None | Some { value: 'A }

                fun unwrap(opt, default) = opt.value
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Option",
                    listOf("A"),
                    constructor("None"),
                    constructor("Some", field("value", typeVar("A"))),
                ),
                funDef(
                    "unwrap",
                    "opt",
                    "default",
                    body = fieldAccess(id("opt"), "value"),
                ),
            ),
        )
    }

    @Test
    fun functionTypeTakingFunctionReturningFunction() {
        assertTypeDefEquals(
            parseTypeDef("type Meta = Meta { f: (A -> B) -> (C -> D) }"),
            typeDef(
                "Meta",
                constructors =
                    arrayOf(
                        constructor(
                            "Meta",
                            field(
                                "f",
                                functionType(
                                    functionType(typeName("A"), typeName("B")),
                                    functionType(typeName("C"), typeName("D")),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun recordTypeWithEmptyRecord() {
        assertTypeDefEquals(
            parseTypeDef("type Empty = Empty { config: {} }"),
            typeDef(
                "Empty",
                constructors =
                    arrayOf(
                        constructor("Empty", field("config", recordType())),
                    ),
            ),
        )
    }

    @Test
    fun veryLongTypeParamList() {
        assertTypeDefEquals(
            parseTypeDef("type Big<'A, 'B, 'C, 'D, 'E, 'F, 'G, 'H> = Big { a: 'A, b: 'B, c: 'C, d: 'D, e: 'E, f: 'F, g: 'G, h: 'H }"),
            typeDef(
                "Big",
                listOf("A", "B", "C", "D", "E", "F", "G", "H"),
                constructor(
                    "Big",
                    field("a", typeVar("A")),
                    field("b", typeVar("B")),
                    field("c", typeVar("C")),
                    field("d", typeVar("D")),
                    field("e", typeVar("E")),
                    field("f", typeVar("F")),
                    field("g", typeVar("G")),
                    field("h", typeVar("H")),
                ),
            ),
        )
    }

    @Test
    fun multilineWithDeeplyIndentedFields() {
        assertProgramEquals(
            parseProgram(
                """
                type Config = Config {
                        host: String,
                        port: Num,
                        options: {
                            timeout: Num,
                            retries: Num
                        }
                    }
                """.trimIndent(),
            ),
            listOf(
                typeDef(
                    "Config",
                    constructors =
                        arrayOf(
                            constructor(
                                "Config",
                                field("host", typeName("String")),
                                field("port", typeName("Num")),
                                field(
                                    "options",
                                    recordType(
                                        "timeout" to typeName("Num"),
                                        "retries" to typeName("Num"),
                                    ),
                                ),
                            ),
                        ),
                ),
            ),
        )
    }

    @Test
    fun appliedTypeSelfReferenceWithSwappedParams() {
        assertTypeDefEquals(
            parseTypeDef("type Flip<'A, 'B> = Flip { next: Flip<'B, 'A> }"),
            typeDef(
                "Flip",
                listOf("A", "B"),
                constructor(
                    "Flip",
                    field("next", appliedType("Flip", typeVar("B"), typeVar("A"))),
                ),
            ),
        )
    }

    @Test
    fun recordFieldWithFunctionTakingRecord() {
        assertTypeDefEquals(
            parseTypeDef("type Processor = Processor { run: { input: String } -> { output: Num } }"),
            typeDef(
                "Processor",
                constructors =
                    arrayOf(
                        constructor(
                            "Processor",
                            field(
                                "run",
                                functionType(
                                    recordType("input" to typeName("String")),
                                    recordType("output" to typeName("Num")),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun nestedTuplesInFunctionType() {
        assertTypeDefEquals(
            parseTypeDef("type Nested = Nested { f: ((A, B), C) -> (D, (E, F)) }"),
            typeDef(
                "Nested",
                constructors =
                    arrayOf(
                        constructor(
                            "Nested",
                            field(
                                "f",
                                functionType(
                                    tupleType(
                                        tupleType(typeName("A"), typeName("B")),
                                        typeName("C"),
                                    ),
                                    tupleType(
                                        typeName("D"),
                                        tupleType(typeName("E"), typeName("F")),
                                    ),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun functionTypeAssociativity() {
        assertTypeDefEquals(
            parseTypeDef("type F = F { f: A -> B -> C }"),
            typeDef(
                "F",
                constructors =
                    arrayOf(
                        constructor(
                            "F",
                            field(
                                "f",
                                functionType(
                                    typeName("A"),
                                    functionType(typeName("B"), typeName("C")),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }

    @Test
    fun functionTypeWithParensChangesAssociativity() {
        assertTypeDefEquals(
            parseTypeDef("type F = F { f: (A -> B) -> C }"),
            typeDef(
                "F",
                constructors =
                    arrayOf(
                        constructor(
                            "F",
                            field(
                                "f",
                                functionType(
                                    functionType(typeName("A"), typeName("B")),
                                    typeName("C"),
                                ),
                            ),
                        ),
                    ),
            ),
        )
    }
}
