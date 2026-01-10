# Type Inference Test Suite

Comprehensive test cases for Klein type inference, covering all expression constructs.

## Test Organization

```
klein-lib/src/commonTest/kotlin/klein/types/
├── TypeAssertions.kt        # Test helpers
├── LiteralInferTest.kt      # Primitive literals
├── IdentInferTest.kt        # Identifier lookup
├── ArithmeticInferTest.kt   # +, -, *, /, %
├── ComparisonInferTest.kt   # <, <=, >, >=, ==, !=
├── BooleanInferTest.kt      # and, or, not
├── LambdaInferTest.kt       # Lambda expressions
├── ApplicationInferTest.kt  # Function calls
├── RecordInferTest.kt       # Record literals
├── FieldAccessInferTest.kt  # Field projection
├── ImplicitParamInferTest.kt # |.| and |.field|
├── IfThenElseInferTest.kt   # Conditionals
├── BlockInferTest.kt        # Block expressions
└── CompositeInferTest.kt    # Complex combinations
```

---

## Test Helpers (TypeAssertions.kt)

```kotlin
package klein.types

import klein.*
import kotlin.reflect.KClass

/**
 * Parse and infer a single expression.
 * Returns the inferred type, throws on parse errors.
 */
fun inferExpr(source: String, env: TypeEnv = TypeEnv.empty()): Type {
    val expr = parse(source)
    return TypeGen().infer(expr, env)
}

/**
 * Parse and infer, returning result with any errors.
 * Use when testing error conditions.
 */
fun inferExprWithErrors(source: String, env: TypeEnv = TypeEnv.empty()): InferResult {
    val expr = parse(source)
    return TypeGen().inferWithErrors(expr, env)
}

/**
 * Assert type equals expected display string.
 * Uses TypePrinter for readable error messages.
 */
fun assertType(expected: String, actual: Type) {
    assertEquals(expected, TypePrinter.print(actual))
}

/**
 * Assert inference produces specific error type.
 * Fails if no error or wrong error type.
 */
fun assertTypeError(source: String, errorType: KClass<out TypeError>) {
    val result = inferExprWithErrors(source)
    assertTrue(result.hasErrors, "Expected error but inference succeeded")
    assertTrue(
        result.errors.any { errorType.isInstance(it) },
        "Expected ${errorType.simpleName} but got: ${result.errors}"
    )
}

/**
 * Build environment with type bindings.
 * Types are parsed from strings: "Int", "String -> Bool", "{ a: Int }"
 */
fun envWith(vararg bindings: Pair<String, String>): TypeEnv {
    val env = TypeEnv.empty()
    for ((name, typeStr) in bindings) {
        env.bind(name, parseType(typeStr))
    }
    return env
}
```

---

## 1. Literal Inference (LiteralInferTest.kt)

### Integer Literals

| Test | Input | Expected Type |
|------|-------|---------------|
| `intLiteral_zero` | `0` | `Int` |
| `intLiteral_positive` | `42` | `Int` |
| `intLiteral_negative` | `-17` | `Int` |
| `intLiteral_large` | `9999999999` | `Int` |

### Double Literals

| Test | Input | Expected Type |
|------|-------|---------------|
| `doubleLiteral_zero` | `0.0` | `Double` |
| `doubleLiteral_positive` | `3.14` | `Double` |
| `doubleLiteral_negative` | `-2.718` | `Double` |
| `doubleLiteral_noFraction` | `1.0` | `Double` |
| `doubleLiteral_smallFraction` | `0.001` | `Double` |

### String Literals

| Test | Input | Expected Type |
|------|-------|---------------|
| `stringLiteral_empty` | `''` | `String` |
| `stringLiteral_simple` | `'hello'` | `String` |
| `stringLiteral_withSpaces` | `'hello world'` | `String` |
| `stringLiteral_withEscapes` | `'line1\nline2'` | `String` |
| `stringLiteral_withQuotes` | `'say \'hi\''` | `String` |

### Boolean Literals

| Test | Input | Expected Type |
|------|-------|---------------|
| `boolLiteral_true` | `true` | `Bool` |
| `boolLiteral_false` | `false` | `Bool` |

### Unit Type

Unit is used for expressions that produce no meaningful value (e.g., if-without-else).

| Test | Input | Expected Type |
|------|-------|---------------|
| `unit_ifWithoutElse` | `if true then 1` | `Unit` |

---

## 2. Identifier Inference (IdentInferTest.kt)

### Successful Lookup

| Test | Environment | Input | Expected Type |
|------|-------------|-------|---------------|
| `ident_intBinding` | `x: Int` | `x` | `Int` |
| `ident_stringBinding` | `name: String` | `name` | `String` |
| `ident_boolBinding` | `flag: Bool` | `flag` | `Bool` |
| `ident_functionBinding` | `f: Int -> String` | `f` | `Int -> String` |
| `ident_recordBinding` | `r: { a: Int }` | `r` | `{ a: Int }` |

### Unbound Variables (Errors)

| Test | Input | Expected Error |
|------|-------|----------------|
| `ident_unbound_simple` | `x` | `UnboundVariable("x")` |
| `ident_unbound_similar` | `naem` | `UnboundVariable("naem")` |
| `ident_unbound_inExpr` | `x + 1` | `UnboundVariable("x")` |

---

## 3. Arithmetic Operators (ArithmeticInferTest.kt)

### Addition

| Test | Input | Expected Type | Notes |
|------|-------|---------------|-------|
| `add_intInt` | `1 + 2` | `Int` | |
| `add_doubleDouble` | `1.0 + 2.0` | `Double` | |
| `add_intVar` | `x + 1` (x: Int) | `Int` | |
| `add_varVar` | `x + y` (x: Int, y: Int) | `Int` | |
| `add_nested` | `1 + 2 + 3` | `Int` | Left associative |
| `add_withParens` | `1 + (2 + 3)` | `Int` | |

### Addition Errors

| Test | Input | Expected Error |
|------|-------|----------------|
| `add_intString` | `1 + 'hello'` | `TypeMismatch` |
| `add_intBool` | `1 + true` | `TypeMismatch` |
| `add_intDouble` | `1 + 2.0` | `TypeMismatch` |
| `add_stringString` | `'a' + 'b'` | `TypeMismatch` (no string concat yet) |

### Subtraction

| Test | Input | Expected Type |
|------|-------|---------------|
| `sub_intInt` | `5 - 3` | `Int` |
| `sub_doubleDouble` | `5.0 - 3.0` | `Double` |
| `sub_negative` | `3 - 5` | `Int` |

### Multiplication

| Test | Input | Expected Type |
|------|-------|---------------|
| `mul_intInt` | `3 * 4` | `Int` |
| `mul_doubleDouble` | `3.0 * 4.0` | `Double` |
| `mul_byZero` | `5 * 0` | `Int` |

### Division

| Test | Input | Expected Type |
|------|-------|---------------|
| `div_intInt` | `10 / 2` | `Int` |
| `div_doubleDouble` | `10.0 / 4.0` | `Double` |
| `div_byOne` | `x / 1` (x: Int) | `Int` |

### Modulo

| Test | Input | Expected Type |
|------|-------|---------------|
| `mod_intInt` | `10 % 3` | `Int` |
| `mod_doubleDouble` | `10.0 % 3.0` | `Double` |

### Mixed Operations

| Test | Input | Expected Type |
|------|-------|---------------|
| `arith_addMul` | `1 + 2 * 3` | `Int` |
| `arith_complex` | `(1 + 2) * (3 - 4) / 5` | `Int` |
| `arith_allOps` | `1 + 2 - 3 * 4 / 5 % 6` | `Int` |

---

## 4. Comparison Operators (ComparisonInferTest.kt)

### Less Than

| Test | Input | Expected Type |
|------|-------|---------------|
| `lt_intInt` | `1 < 2` | `Bool` |
| `lt_doubleDouble` | `1.0 < 2.0` | `Bool` |
| `lt_vars` | `x < y` (x: Int, y: Int) | `Bool` |

### Less Than Errors

| Test | Input | Expected Error |
|------|-------|----------------|
| `lt_intString` | `1 < 'hello'` | `TypeMismatch` |
| `lt_boolBool` | `true < false` | `TypeMismatch` |

### Less Than or Equal

| Test | Input | Expected Type |
|------|-------|---------------|
| `lte_intInt` | `1 <= 2` | `Bool` |
| `lte_equal` | `2 <= 2` | `Bool` |

### Greater Than

| Test | Input | Expected Type |
|------|-------|---------------|
| `gt_intInt` | `2 > 1` | `Bool` |
| `gt_doubleDouble` | `2.0 > 1.0` | `Bool` |

### Greater Than or Equal

| Test | Input | Expected Type |
|------|-------|---------------|
| `gte_intInt` | `2 >= 1` | `Bool` |
| `gte_equal` | `2 >= 2` | `Bool` |

### Equality

| Test | Input | Expected Type | Notes |
|------|-------|---------------|-------|
| `eq_intInt` | `1 == 1` | `Bool` | |
| `eq_stringString` | `'a' == 'b'` | `Bool` | |
| `eq_boolBool` | `true == false` | `Bool` | |
| `eq_doubleDouble` | `1.0 == 2.0` | `Bool` | |
| `eq_recordRecord` | `{a=1} == {a=2}` | `Bool` | Same structure |

### Equality Errors

| Test | Input | Expected Error |
|------|-------|----------------|
| `eq_intString` | `1 == 'hello'` | `TypeMismatch` |
| `eq_intBool` | `1 == true` | `TypeMismatch` |

### Not Equal

| Test | Input | Expected Type |
|------|-------|---------------|
| `neq_intInt` | `1 != 2` | `Bool` |
| `neq_stringString` | `'a' != 'b'` | `Bool` |

### Chained Comparisons

| Test | Input | Expected Type | Notes |
|------|-------|---------------|-------|
| `cmp_chain` | `1 < 2 == true` | `Bool` | (1 < 2) == true |

---

## 5. Boolean Operators (BooleanInferTest.kt)

### And

| Test | Input | Expected Type |
|------|-------|---------------|
| `and_trueFalse` | `true and false` | `Bool` |
| `and_vars` | `x and y` (x: Bool, y: Bool) | `Bool` |
| `and_withComparison` | `1 < 2 and 3 < 4` | `Bool` |

### And Errors

| Test | Input | Expected Error |
|------|-------|----------------|
| `and_intBool` | `1 and true` | `TypeMismatch` |
| `and_intInt` | `1 and 2` | `TypeMismatch` |

### Or

| Test | Input | Expected Type |
|------|-------|---------------|
| `or_trueFalse` | `true or false` | `Bool` |
| `or_vars` | `x or y` (x: Bool, y: Bool) | `Bool` |
| `or_withComparison` | `1 < 2 or 3 > 4` | `Bool` |

### Or Errors

| Test | Input | Expected Error |
|------|-------|----------------|
| `or_stringBool` | `'a' or true` | `TypeMismatch` |

### Not

| Test | Input | Expected Type |
|------|-------|---------------|
| `not_true` | `not true` | `Bool` |
| `not_false` | `not false` | `Bool` |
| `not_var` | `not x` (x: Bool) | `Bool` |
| `not_comparison` | `not (1 < 2)` | `Bool` |
| `not_double` | `not not true` | `Bool` |

### Not Errors

| Test | Input | Expected Error |
|------|-------|----------------|
| `not_int` | `not 1` | `TypeMismatch` |
| `not_string` | `not 'hello'` | `TypeMismatch` |

### Combined Boolean

| Test | Input | Expected Type |
|------|-------|---------------|
| `bool_complex` | `(x and y) or (not z)` | `Bool` |
| `bool_comparison` | `x > 0 and x < 10` | `Bool` |

---

## 6. Lambda Expressions (LambdaInferTest.kt)

### Single Parameter

| Test | Input | Expected Type |
|------|-------|---------------|
| `lambda_identity` | `\|x -> x\|` | `'a -> 'a` |
| `lambda_constant` | `\|x -> 42\|` | `'a -> Int` |
| `lambda_constString` | `\|x -> 'hello'\|` | `'a -> String` |
| `lambda_useParam` | `\|x -> x + 1\|` | `Int -> Int` |
| `lambda_boolParam` | `\|x -> not x\|` | `Bool -> Bool` |

### Multiple Parameters

| Test | Input | Expected Type |
|------|-------|---------------|
| `lambda_twoParams` | `\|x, y -> x + y\|` | `(Int, Int) -> Int` |
| `lambda_threeParams` | `\|x, y, z -> x\|` | `('a, 'b, 'c) -> 'a` |
| `lambda_mixedUse` | `\|x, y -> x + 1\|` | `(Int, 'a) -> Int` |
| `lambda_swap` | `\|x, y -> { a = y, b = x }\|` | `('a, 'b) -> { a: 'b, b: 'a }` |

### Zero Parameters (Thunks)

| Test | Input | Expected Type |
|------|-------|---------------|
| `lambda_thunk` | `\|-> 42\|` | `() -> Int` |
| `lambda_thunkString` | `\|-> 'hello'\|` | `() -> String` |

### Nested Lambdas

| Test | Input | Expected Type |
|------|-------|---------------|
| `lambda_nested` | `\|x -> \|y -> x + y\|\|` | `Int -> Int -> Int` |
| `lambda_curried` | `\|x -> \|y -> \|z -> x\|\|\|` | `'a -> 'b -> 'c -> 'a` |
| `lambda_nestedUse` | `\|f -> \|x -> f(x)\|\|` | `('a -> 'b) -> 'a -> 'b` |

### Lambda with Records

| Test | Input | Expected Type |
|------|-------|---------------|
| `lambda_returnRecord` | `\|x -> { value = x }\|` | `'a -> { value: 'a }` |
| `lambda_recordParam` | `\|r -> r.name\|` | `{ name: 'a } -> 'a` |

### Lambda with Conditionals

| Test | Input | Expected Type |
|------|-------|---------------|
| `lambda_conditional` | `\|x -> if x then 1 else 2\|` | `Bool -> Int` |
| `lambda_condCompare` | `\|x -> if x > 0 then x else -x\|` | `Int -> Int` |

---

## 7. Function Application (ApplicationInferTest.kt)

### Simple Application

| Test | Environment | Input | Expected Type |
|------|-------------|-------|---------------|
| `apply_intToString` | `f: Int -> String` | `f(42)` | `String` |
| `apply_boolToInt` | `f: Bool -> Int` | `f(true)` | `Int` |
| `apply_identity` | `id: 'a -> 'a` | `id(42)` | `Int` |

### Multi-Argument Application

| Test | Environment | Input | Expected Type |
|------|-------------|-------|---------------|
| `apply_twoArgs` | `f: (Int, Int) -> Int` | `f(1, 2)` | `Int` |
| `apply_threeArgs` | `f: (Int, String, Bool) -> Double` | `f(1, 'a', true)` | `Double` |
| `apply_mixedTypes` | `f: (Int, String) -> Bool` | `f(42, 'hello')` | `Bool` |

### Application Errors

| Test | Environment | Input | Expected Error |
|------|-------------|-------|----------------|
| `apply_wrongArgType` | `f: Int -> String` | `f('hello')` | `TypeMismatch` |
| `apply_tooFewArgs` | `f: (Int, Int) -> Int` | `f(1)` | `ArityMismatch` |
| `apply_tooManyArgs` | `f: Int -> Int` | `f(1, 2)` | `ArityMismatch` |
| `apply_notAFunction` | `x: Int` | `x(1)` | `NotAFunction` |

### Curried Application

| Test | Environment | Input | Expected Type |
|------|-------------|-------|---------------|
| `apply_curried` | `f: Int -> Int -> Int` | `f(1)` | `Int -> Int` |
| `apply_curriedFull` | `f: Int -> Int -> Int` | `f(1)(2)` | `Int` |

### Higher-Order Application

| Test | Environment | Input | Expected Type |
|------|-------------|-------|---------------|
| `apply_higherOrder` | `apply: (Int -> Int, Int) -> Int` | `apply(\|x -> x + 1\|, 5)` | `Int` |
| `apply_compose` | — | `\|f, g, x -> f(g(x))\|` | `('b -> 'c, 'a -> 'b, 'a) -> 'c` |

### Lambda Immediately Applied

| Test | Input | Expected Type |
|------|-------|---------------|
| `apply_immediateSimple` | `\|x -> x + 1\|(5)` | `Int` |
| `apply_immediateTwoArg` | `\|x, y -> x + y\|(1, 2)` | `Int` |
| `apply_immediateNested` | `\|x -> \|y -> x + y\|\|(1)(2)` | `Int` |

---

## 8. Record Literals (RecordInferTest.kt)

### Simple Records

| Test | Input | Expected Type |
|------|-------|---------------|
| `record_empty` | `{}` | `{}` |
| `record_oneField` | `{ a = 1 }` | `{ a: Int }` |
| `record_twoFields` | `{ a = 1, b = 'hello' }` | `{ a: Int, b: String }` |
| `record_threeFields` | `{ x = 1, y = 2, z = 3 }` | `{ x: Int, y: Int, z: Int }` |

### Records with Different Value Types

| Test | Input | Expected Type |
|------|-------|---------------|
| `record_allTypes` | `{ i = 1, d = 1.0, s = 'hi', b = true }` | `{ i: Int, d: Double, s: String, b: Bool }` |
| `record_withFunction` | `{ f = \|x -> x\| }` | `{ f: 'a -> 'a }` |

### Nested Records

| Test | Input | Expected Type |
|------|-------|---------------|
| `record_nested` | `{ inner = { x = 1 } }` | `{ inner: { x: Int } }` |
| `record_deepNested` | `{ a = { b = { c = 1 } } }` | `{ a: { b: { c: Int } } }` |
| `record_multiNested` | `{ a = { x = 1 }, b = { y = 2 } }` | `{ a: { x: Int }, b: { y: Int } }` |

### Records with Expressions

| Test | Input | Expected Type |
|------|-------|---------------|
| `record_withArithmetic` | `{ sum = 1 + 2 }` | `{ sum: Int }` |
| `record_withComparison` | `{ isPositive = x > 0 }` (x: Int) | `{ isPositive: Bool }` |
| `record_withConditional` | `{ value = if true then 1 else 2 }` | `{ value: Int }` |

### Record Shorthand

| Test | Input | Expected Type |
|------|-------|---------------|
| `record_shorthand` | `{ x }` (x: Int) | `{ x: Int }` |
| `record_mixedShorthand` | `{ x, y = 2 }` (x: Int) | `{ x: Int, y: Int }` |

---

## 9. Field Access (FieldAccessInferTest.kt)

### Simple Field Access

| Test | Input | Expected Type |
|------|-------|---------------|
| `field_simple` | `{ a = 1 }.a` | `Int` |
| `field_string` | `{ name = 'Alice' }.name` | `String` |
| `field_bool` | `{ flag = true }.flag` | `Bool` |
| `field_secondField` | `{ a = 1, b = 'hi' }.b` | `String` |

### Field Access on Variable

| Test | Environment | Input | Expected Type |
|------|-------------|-------|---------------|
| `field_onVar` | `r: { x: Int }` | `r.x` | `Int` |
| `field_onVarNested` | `r: { inner: { x: Int } }` | `r.inner` | `{ x: Int }` |

### Chained Field Access

| Test | Input | Expected Type |
|------|-------|---------------|
| `field_chained` | `{ a = { b = 1 } }.a.b` | `Int` |
| `field_deepChain` | `{ a = { b = { c = 1 } } }.a.b.c` | `Int` |

### Field Access Errors

| Test | Input | Expected Error |
|------|-------|----------------|
| `field_missing` | `{ a = 1 }.b` | `MissingField("b")` |
| `field_onInt` | `42.x` | `NotARecord` |
| `field_onString` | `'hello'.length` | `NotARecord` |
| `field_onBool` | `true.value` | `NotARecord` |

### Field Access in Expressions

| Test | Input | Expected Type |
|------|-------|---------------|
| `field_inArithmetic` | `{ x = 1 }.x + { y = 2 }.y` | `Int` |
| `field_inComparison` | `{ a = 1 }.a < { b = 2 }.b` | `Bool` |

### Width Subtyping with Field Access

Functions that access specific fields should accept records with additional fields via width subtyping.

| Test | Input | Expected Type | Notes |
|------|-------|---------------|-------|
| `field_widthSubtype` | `\|.name\|({ name = 'Alice', age = 30 })` | `String` | Extra `age` field is OK |
| `field_widthSubtypeMulti` | `\|.x + .y\|({ x = 1, y = 2, z = 3 })` | `Int` | Extra `z` field is OK |
| `field_lambdaAcceptsWider` | (see below) | `String` | Function accepting wider record |

```klein
# field_lambdaAcceptsWider
getName = |.name|
person = { name = 'Alice', age = 30, email = 'alice@example.com' }
getName(person)  # OK: person has 'name' field
```

---

## 10. Implicit Parameters (ImplicitParamInferTest.kt)

### Basic Implicit Parameter

| Test | Input | Expected Type |
|------|-------|---------------|
| `implicit_identity` | `\|.\|` | `'a -> 'a` |
| `implicit_arithmetic` | `\|. + 1\|` | `Int -> Int` |
| `implicit_comparison` | `\|. > 0\|` | `Int -> Bool` |
| `implicit_boolean` | `\|not .\|` | `Bool -> Bool` |

### Implicit Field Access

| Test | Input | Expected Type |
|------|-------|---------------|
| `implicit_field` | `\|.name\|` | `{ name: 'a } -> 'a` |
| `implicit_fieldArith` | `\|.x + .y\|` | `{ x: Int, y: Int } -> Int` |
| `implicit_fieldCompare` | `\|.age > 18\|` | `{ age: Int } -> Bool` |
| `implicit_nestedField` | `\|.person.name\|` | `{ person: { name: 'a } } -> 'a` |

### Multiple Implicit Uses

| Test | Input | Expected Type |
|------|-------|---------------|
| `implicit_twice` | `\|. + .\|` | `Int -> Int` |
| `implicit_multiField` | `\|.a + .b + .c\|` | `{ a: Int, b: Int, c: Int } -> Int` |

### Implicit Parameter Errors

| Test | Input | Expected Error |
|------|-------|----------------|
| `implicit_outsideLambda` | `.` | `ImplicitParamOutsideLambda` |
| `implicit_fieldOutside` | `.name` | `ImplicitParamOutsideLambda` |
| `implicit_mixedWithExplicit` | `\|x -> . + x\|` | `MixedImplicitExplicit` |
| `implicit_explicitThenImplicit` | `\|x, y -> .z\|` | `MixedImplicitExplicit` |

### Nested Lambdas with Implicit

| Test | Input | Expected Type | Notes |
|------|-------|---------------|-------|
| `implicit_inNestedLambda` | `\|x -> \|.name\|\|` | `'a -> { name: 'b } -> 'b` | Inner lambda has its own implicit |
| `implicit_outerExplicitInnerImplicit` | `\|x -> \|. + x\|\|` | `Int -> Int -> Int` | OK - different scopes |

---

## 11. If-Then-Else (IfThenElseInferTest.kt)

### Basic Conditionals

| Test | Input | Expected Type |
|------|-------|---------------|
| `if_intBranches` | `if true then 1 else 2` | `Int` |
| `if_stringBranches` | `if true then 'a' else 'b'` | `String` |
| `if_boolBranches` | `if true then true else false` | `Bool` |

### With Variables

| Test | Environment | Input | Expected Type |
|------|-------------|-------|---------------|
| `if_varCondition` | `c: Bool` | `if c then 1 else 2` | `Int` |
| `if_varBranches` | `x: Int, y: Int` | `if true then x else y` | `Int` |
| `if_comparisonCond` | `x: Int` | `if x > 0 then x else -x` | `Int` |

### Branch Type Unification

| Test | Input | Expected Type | Notes |
|------|-------|---------------|-------|
| `if_recordBranches` | `if c then {a=1} else {a=2}` | `{ a: Int }` | Same structure |
| `if_lambdaBranches` | `if c then \|x->x\| else \|x->x\|` | `'a -> 'a` | Same type |
| `if_widthSubtype` | `if c then {a=1,b=2} else {a=3}` | `{ a: Int }` | Common fields |

### Conditional Errors

| Test | Input | Expected Error |
|------|-------|----------------|
| `if_nonBoolCondition` | `if 1 then 2 else 3` | `TypeMismatch` |
| `if_stringCondition` | `if 'hello' then 1 else 2` | `TypeMismatch` |
| `if_mismatchedBranches` | `if true then 1 else 'hello'` | `TypeMismatch` |
| `if_intVsRecord` | `if true then 1 else {a=1}` | `TypeMismatch` |

### Nested Conditionals

| Test | Input | Expected Type |
|------|-------|---------------|
| `if_nestedThen` | `if a then (if b then 1 else 2) else 3` | `Int` |
| `if_nestedElse` | `if a then 1 else (if b then 2 else 3)` | `Int` |
| `if_deepNested` | `if a then (if b then (if c then 1 else 2) else 3) else 4` | `Int` |

### Conditionals in Expressions

| Test | Input | Expected Type |
|------|-------|---------------|
| `if_inArithmetic` | `(if true then 1 else 2) + 3` | `Int` |
| `if_inRecord` | `{ value = if true then 1 else 2 }` | `{ value: Int }` |
| `if_inLambda` | `\|x -> if x then 1 else 2\|` | `Bool -> Int` |

### If Without Else

If-without-else always returns `Unit`, regardless of the then-branch type.

| Test | Input | Expected Type | Notes |
|------|-------|---------------|-------|
| `if_noElse_int` | `if true then 1` | `Unit` | Then-branch value is discarded |
| `if_noElse_string` | `if true then 'hello'` | `Unit` | |
| `if_noElse_record` | `if true then { a = 1 }` | `Unit` | |
| `if_noElse_sideEffect` | `if flag then doSomething()` | `Unit` | Typical use case |

---

## 12. Block Expressions (BlockInferTest.kt)

### Simple Blocks

| Test | Input | Expected Type |
|------|-------|---------------|
| `block_singleExpr` | `1` | `Int` |
| `block_lastExpr` | `1; 2; 3` | `Int` (value 3) |
| `block_mixedTypes` | `1; 'hello'; true` | `Bool` |

### Blocks with Bindings (Basic)

| Test | Input | Expected Type |
|------|-------|---------------|
| `block_useBinding` | `x = 1; x + 1` | `Int` |
| `block_multipleBindings` | `x = 1; y = 2; x + y` | `Int` |
| `block_shadowBinding` | `x = 1; x = 2; x` | `Int` |

### Blocks as Expressions

| Test | Input | Expected Type |
|------|-------|---------------|
| `block_inLambda` | `\|x -> y = x + 1; y * 2\|` | `Int -> Int` |
| `block_inConditional` | `if true then (x = 1; x) else 2` | `Int` |
| `block_inRecord` | `{ a = (x = 1; x + 1) }` | `{ a: Int }` |

---

## 13. Composite/Integration Tests (CompositeInferTest.kt)

### Complex Function Types

| Test | Input | Expected Type |
|------|-------|---------------|
| `composite_higherOrder` | `\|f, x -> f(f(x))\|` | `('a -> 'a, 'a) -> 'a` |
| `composite_flip` | `\|f, x, y -> f(y, x)\|` | `(('a, 'b) -> 'c, 'b, 'a) -> 'c` |
| `composite_const` | `\|x, y -> x\|` | `('a, 'b) -> 'a` |

### Records and Functions

| Test | Input | Expected Type |
|------|-------|---------------|
| `composite_recordOfFunctions` | `{ inc = \|x -> x + 1\|, dec = \|x -> x - 1\| }` | `{ inc: Int -> Int, dec: Int -> Int }` |
| `composite_functionReturningRecord` | `\|x, y -> { first = x, second = y }\|` | `('a, 'b) -> { first: 'a, second: 'b }` |
| `composite_applyFromRecord` | `{ f = \|x -> x + 1\| }.f(5)` | `Int` |

### Nested Everything

| Test | Input | Expected Type |
|------|-------|---------------|
| `composite_nestedAll` | `\|r -> if r.flag then r.a else r.b\|` | `{ flag: Bool, a: 'a, b: 'a } -> 'a` |
| `composite_deepPipeline` | `\|x -> { value = x + 1 }\|({ value = 0 }.value)` | `{ value: Int }` |

### Real-World Patterns

| Test | Input | Expected Type |
|------|-------|---------------|
| `composite_map` | `\|f, r -> { x = f(r.x), y = f(r.y) }\|` | `('a -> 'b, { x: 'a, y: 'a }) -> { x: 'b, y: 'b }` |
| `composite_filter` | `\|pred, value -> if pred(value) then value else 0\|` | `(Int -> Bool, Int) -> Int` |
| `composite_fold` | — | (depends on list support) |

### Error Recovery

| Test | Input | Notes |
|------|-------|-------|
| `composite_continueAfterError` | `x = 1 + true; x + 1` | Should report error but infer x + 1 as Int |
| `composite_multipleErrors` | `(1 + 'a') + (true / 2)` | Should report both errors |

---

## Test Execution Patterns

### Test Helpers (TypeAssertions.kt)

#### `inferExpr` - Infer type of a single expression

```kotlin
@Test
fun intLiteral() {
    val type = inferExpr("42")
    assertEquals(Type.TInt, type)
}

@Test
fun lambdaIdentity() {
    val type = inferExpr("|x -> x|")
    assertTrue(type is Type.TFun)
    val fn = type as Type.TFun
    assertEquals(1, fn.params.size)
    assertSame(fn.params[0], fn.result)  // Same type variable
}
```

#### `inferExprWithErrors` - Infer and capture errors

```kotlin
@Test
fun unboundVariable_reportsError() {
    val result = inferExprWithErrors("unknownVar")
    assertTrue(result.hasErrors)
    assertEquals(1, result.errors.size)
    assertTrue(result.errors[0] is TypeError.UnboundVariable)
    assertEquals("unknownVar", (result.errors[0] as TypeError.UnboundVariable).name)
}

@Test
fun typeMismatch_reportsError() {
    val result = inferExprWithErrors("1 + true")
    assertTrue(result.hasErrors)
    assertTrue(result.errors.any { it is TypeError.TypeMismatch })
}
```

#### `assertType` - Compare types using display strings

```kotlin
@Test
fun functionType_display() {
    val type = inferExpr("|x, y -> x + y|")
    assertType("(Int, Int) -> Int", type)
}

@Test
fun recordType_display() {
    val type = inferExpr("{ name = 'Alice', age = 30 }")
    assertType("{ name: String, age: Int }", type)
}
```

#### `assertTypeError` - Assert specific error type occurs

```kotlin
@Test
fun addIntString_failsWithMismatch() {
    assertTypeError("1 + 'hello'", TypeError.TypeMismatch::class)
}

@Test
fun callNonFunction_failsWithNotAFunction() {
    assertTypeError("42(1)", TypeError.NotAFunction::class)
}

@Test
fun implicitOutsideLambda_fails() {
    assertTypeError(".", TypeError.ImplicitParamOutsideLambda::class)
}
```

#### `envWith` - Build test environments with bindings

```kotlin
@Test
fun identFromEnv() {
    val env = envWith("x" to "Int", "name" to "String")
    val type = inferExpr("x", env)
    assertEquals(Type.TInt, type)
}

@Test
fun functionFromEnv() {
    val env = envWith("f" to "Int -> String")
    val type = inferExpr("f(42)", env)
    assertType("String", type)
}

@Test
fun recordFromEnv() {
    val env = envWith("person" to "{ name: String, age: Int }")
    val type = inferExpr("person.name", env)
    assertType("String", type)
}
```

### Parametric Tests

```kotlin
@Test
fun arithmeticOperators_allReturnInt() {
    val ops = listOf("+", "-", "*", "/", "%")
    for (op in ops) {
        val type = inferExpr("1 $op 2")
        assertEquals(Type.TInt, type, "Operator $op should return Int")
    }
}

@Test
fun comparisonOperators_allReturnBool() {
    val ops = listOf("<", "<=", ">", ">=", "==", "!=")
    for (op in ops) {
        val type = inferExpr("1 $op 2")
        assertEquals(Type.TBool, type, "Operator $op should return Bool")
    }
}

@Test
fun literals_haveCorrectTypes() {
    val cases = listOf(
        "42" to Type.TInt,
        "3.14" to Type.TDouble,
        "'hello'" to Type.TString,
        "true" to Type.TBool,
        "false" to Type.TBool,
    )
    for ((input, expected) in cases) {
        val type = inferExpr(input)
        assertEquals(expected, type, "Literal $input should have type ${TypePrinter.print(expected)}")
    }
}
```

---

## Design Decisions

These decisions are reflected in the tests above:

1. **Unit type**: `if-without-else` returns `Unit`, not the then-branch type
2. **Implicit/explicit mixing**: Lambdas cannot mix implicit (`.`) and explicit (`x`) parameters - this is an error
3. **Width subtyping**: Records with more fields are subtypes of records with fewer fields, so `|.name|` accepts `{ name, age }`

## Notes

1. **Type variable naming**: Tests should not depend on specific variable names ('a vs 'b). Compare structure instead.

2. **Polymorphism**: Until Phase 8 (let-polymorphism), tests involving polymorphic functions like `|x -> x|` may behave differently depending on context.

3. **Error messages**: Test that error spans point to the right location in source.

4. **Error types**: New error type `MixedImplicitExplicit` needed for implicit/explicit mixing violations.
