# Klein Type Inference Implementation Plan

A phased implementation plan for SimpleSub-style type inference on Klein's currently implemented features.

## Overview

### What We're Building

Type inference for Klein's expression language **without surface syntax changes**. We need:

1. **Internal type representation** - Data structures for types
2. **Type display syntax** - For error messages and inference results (output only)
3. **Inference algorithm** - SimpleSub-style with subtyping and simplification
4. **CLI integration** - `./klein infer` command to show inferred types

### What SimpleSub Provides

SimpleSub combines Hindley-Milner with subtyping:
- **Polar types**: Types are split into positive (output) and negative (input) positions
- **Type bounds**: Type variables carry upper and lower bounds instead of equality constraints
- **Simplification**: Types are simplified during inference to stay compact
- **Subtyping**: Records and functions have natural subtyping relationships

Key paper: "The Simple Essence of Algebraic Subtyping" (Parreaux 2020)

### Currently Implemented AST Nodes

| Node | Type Rule Needed |
|------|------------------|
| `IntLiteral` | Constant `Int` |
| `DoubleLiteral` | Constant `Double` |
| `StringLiteral` | Constant `String` |
| `BoolLiteral` | Constant `Bool` |
| `Ident` | Lookup in environment |
| `BinaryOp` | Operator-specific constraints |
| `UnaryOp` | Operator-specific constraints |
| `Lambda` | Function type introduction |
| `Apply` | Function type elimination |
| `Block` | Sequential binding with scoping |
| `IfThenElse` | Conditional with branch unification |
| `FieldAccess` | Record field projection |
| `ImplicitParam` | Implicit lambda parameter |
| `RecordLiteral` | Record type introduction |
| `Val` | Let-binding (with let-polymorphism) |
| `FunDef` | Function definition (recursive) |

---

## Phase 1: Type Representation

**Goal**: Define internal type data structures.

### Files to Create

```
klein-lib/src/commonMain/kotlin/klein/
├── Type.kt              # Type AST
└── TypePrinter.kt       # Pretty-printing types
```

### Type.kt - Type Data Structures

```kotlin
package klein

sealed class Type {
    // Primitive types
    object TInt : Type()
    object TDouble : Type()
    object TString : Type()
    object TBool : Type()

    // Type variable with bounds (SimpleSub style)
    data class TVar(
        val id: Int,
        val lowerBounds: MutableSet<Type> = mutableSetOf(),  // T :> lower
        val upperBounds: MutableSet<Type> = mutableSetOf(),  // T <: upper
    ) : Type()

    // Function type: (params) -> result
    data class TFun(
        val params: List<Type>,
        val result: Type,
    ) : Type()

    // Record type: { field1: T1, field2: T2 }
    // Closed records only - no row polymorphism for now
    data class TRecord(
        val fields: Map<String, Type>,
    ) : Type()

    // Top and Bottom for subtyping lattice
    object TTop : Type()     // All types are subtypes of Top
    object TBottom : Type()  // Bottom is subtype of all types
}
```

### Key Design Decisions

1. **Mutable bounds on TVar**: SimpleSub accumulates bounds during inference rather than solving eagerly. Bounds are refined as inference progresses.

2. **Closed records with width subtyping**: Records have a fixed set of fields. A record with more fields is a subtype of one with fewer (width subtyping), but we don't track "extra" fields through type variables. Row polymorphism may be added later.

3. **Top/Bottom types**: Needed for the subtyping lattice. `TTop` is the supertype of everything, `TBottom` is the subtype of everything.

### TypePrinter.kt - Display Syntax

```kotlin
package klein

object TypePrinter {
    fun print(type: Type): String = when (type) {
        Type.TInt -> "Int"
        Type.TDouble -> "Double"
        Type.TString -> "String"
        Type.TBool -> "Bool"
        Type.TTop -> "Top"
        Type.TBottom -> "Bottom"
        is Type.TVar -> "'${varName(type.id)}"  // 'a, 'b, 'c, etc.
        is Type.TFun -> printFun(type)
        is Type.TRecord -> printRecord(type)
    }

    private fun varName(id: Int): String {
        // 0 -> a, 1 -> b, ..., 25 -> z, 26 -> a1, etc.
        val letter = ('a' + (id % 26))
        val suffix = if (id >= 26) "${id / 26}" else ""
        return "$letter$suffix"
    }

    private fun printFun(fn: Type.TFun): String {
        val params = when (fn.params.size) {
            0 -> "()"
            1 -> print(fn.params[0])
            else -> "(${fn.params.joinToString(", ") { print(it) }})"
        }
        return "$params -> ${print(fn.result)}"
    }

    private fun printRecord(rec: Type.TRecord): String {
        val fields = rec.fields.entries.joinToString(", ") { (k, v) ->
            "$k: ${print(v)}"
        }
        return "{ $fields }"
    }
}
```

### Display Syntax Summary

| Type | Display |
|------|---------|
| Integer | `Int` |
| Double | `Double` |
| String | `String` |
| Boolean | `Bool` |
| Type variable | `'a`, `'b`, `'c` |
| Function (1 param) | `Int -> Int` |
| Function (n params) | `(Int, String) -> Bool` |
| Function (0 params) | `() -> Int` |
| Record | `{ name: String, age: Int }` |
| Top | `Top` |
| Bottom | `Bottom` |

### Tests for Phase 1

```
klein-lib/src/commonTest/kotlin/klein/types/
├── TypePrinterTest.kt   # Test pretty-printing
```

**TypePrinterTest.kt examples:**

```kotlin
@Test
fun printsPrimitives() {
    assertEquals("Int", TypePrinter.print(Type.TInt))
    assertEquals("Bool", TypePrinter.print(Type.TBool))
}

@Test
fun printsFunctionTypes() {
    val fn = Type.TFun(listOf(Type.TInt), Type.TInt)
    assertEquals("Int -> Int", TypePrinter.print(fn))
}

@Test
fun printsRecordTypes() {
    val rec = Type.TRecord(mapOf("name" to Type.TString, "age" to Type.TInt))
    assertEquals("{ name: String, age: Int }", TypePrinter.print(rec))
}

@Test
fun printsTypeVariables() {
    val v = Type.TVar(0)
    assertEquals("'a", TypePrinter.print(v))
}
```

---

## Phase 2: Type Environment and Basic Inference

**Goal**: Infer types for literals and identifiers.

### Files to Create

```
klein-lib/src/commonMain/kotlin/klein/
├── TypeEnv.kt           # Type environment (scope)
├── Inferencer.kt        # Main inference logic
└── TypeError.kt         # Type error representation
```

### TypeEnv.kt - Type Environment

```kotlin
package klein

class TypeEnv(
    private val parent: TypeEnv? = null,
    private val bindings: MutableMap<String, Type> = mutableMapOf(),
) {
    fun lookup(name: String): Type? =
        bindings[name] ?: parent?.lookup(name)

    fun bind(name: String, type: Type) {
        bindings[name] = type
    }

    fun child(): TypeEnv = TypeEnv(parent = this)

    companion object {
        fun builtins(): TypeEnv = TypeEnv().apply {
            // Built-in operators would go here eventually
        }
    }
}
```

### TypeError.kt - Error Representation

```kotlin
package klein

sealed class TypeError {
    abstract val span: SourceSpan
    abstract val message: String

    data class UnboundVariable(
        val name: String,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Unbound variable: $name"
    }

    data class TypeMismatch(
        val expected: Type,
        val actual: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Type mismatch: expected ${TypePrinter.print(expected)}, got ${TypePrinter.print(actual)}"
    }

    data class NotAFunction(
        val actual: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Not a function: ${TypePrinter.print(actual)}"
    }

    data class NotARecord(
        val actual: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Not a record: ${TypePrinter.print(actual)}"
    }

    data class MissingField(
        val field: String,
        val recordType: Type,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Record ${TypePrinter.print(recordType)} has no field '$field'"
    }

    data class ArityMismatch(
        val expected: Int,
        val actual: Int,
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Expected $expected arguments, got $actual"
    }
}
```

### Inferencer.kt - Basic Structure

```kotlin
package klein

class Inferencer {
    private var nextVarId = 0
    private val errors = mutableListOf<TypeError>()

    fun freshVar(): Type.TVar = Type.TVar(nextVarId++)

    fun infer(expr: Expr, env: TypeEnv): Type = when (expr) {
        is IntLiteral -> Type.TInt
        is DoubleLiteral -> Type.TDouble
        is StringLiteral -> Type.TString
        is BoolLiteral -> Type.TBool
        is Ident -> inferIdent(expr, env)
        is BinaryOp -> inferBinaryOp(expr, env)
        is UnaryOp -> inferUnaryOp(expr, env)
        is Lambda -> inferLambda(expr, env)
        is Apply -> inferApply(expr, env)
        is Block -> inferBlock(expr, env)
        is IfThenElse -> inferIfThenElse(expr, env)
        is FieldAccess -> inferFieldAccess(expr, env)
        is ImplicitParam -> inferImplicitParam(expr, env)
        is RecordLiteral -> inferRecord(expr, env)
    }

    private fun inferIdent(expr: Ident, env: TypeEnv): Type {
        return env.lookup(expr.name) ?: run {
            errors.add(TypeError.UnboundVariable(expr.name, expr.span))
            freshVar()  // Return fresh var to continue inference
        }
    }

    // ... more inference methods in later phases
}
```

### Tests for Phase 2

```
klein-lib/src/commonTest/kotlin/klein/types/
├── TypeEnvTest.kt       # Environment scoping
├── LiteralInferTest.kt  # Literal type inference
└── IdentInferTest.kt    # Identifier lookup
```

**LiteralInferTest.kt examples:**

```kotlin
@Test
fun infersIntLiteral() {
    val expr = parse("42")
    val type = Inferencer().infer(expr, TypeEnv.builtins())
    assertEquals(Type.TInt, type)
}

@Test
fun infersBoolLiteral() {
    val expr = parse("true")
    val type = Inferencer().infer(expr, TypeEnv.builtins())
    assertEquals(Type.TBool, type)
}
```

**IdentInferTest.kt examples:**

```kotlin
@Test
fun infersIdentFromEnv() {
    val env = TypeEnv.builtins().child()
    env.bind("x", Type.TInt)
    val expr = parse("x")
    val type = Inferencer().infer(expr, env)
    assertEquals(Type.TInt, type)
}

@Test
fun reportsUnboundVariable() {
    val result = Inferencer().inferWithErrors(parse("unknown"), TypeEnv.builtins())
    assertTrue(result.errors.any { it is TypeError.UnboundVariable })
}
```

---

## Phase 3: Subtyping and Constraint Solving

**Goal**: Implement SimpleSub's core subtyping algorithm.

### Files to Create/Modify

```
klein-lib/src/commonMain/kotlin/klein/
└── Subtyping.kt         # Subtyping checks and constraint solving
```

### Subtyping.kt - Core Algorithm

```kotlin
package klein

/**
 * SimpleSub-style subtyping with recursive constraint solving.
 *
 * Key insight: Instead of generating constraints and solving later,
 * we directly check subtyping relationships and accumulate bounds
 * on type variables as we go.
 */
class Subtyping(private val inferencer: Inferencer) {

    /**
     * Check if `sub` is a subtype of `sup`, updating type variable bounds.
     * Returns true if subtyping holds, false otherwise.
     */
    fun isSubtype(sub: Type, sup: Type): Boolean {
        // Handle identical types
        if (sub === sup) return true

        // Handle Top and Bottom
        if (sup is Type.TTop) return true
        if (sub is Type.TBottom) return true
        if (sub is Type.TTop) return false
        if (sup is Type.TBottom) return false

        // Handle type variables (this is the SimpleSub magic)
        if (sub is Type.TVar) {
            sub.upperBounds.add(sup)
            return true  // Constraint recorded, assumed satisfiable
        }
        if (sup is Type.TVar) {
            sup.lowerBounds.add(sub)
            return true  // Constraint recorded, assumed satisfiable
        }

        // Structural subtyping for records
        if (sub is Type.TRecord && sup is Type.TRecord) {
            return isRecordSubtype(sub, sup)
        }

        // Function subtyping (contravariant in params, covariant in result)
        if (sub is Type.TFun && sup is Type.TFun) {
            return isFunctionSubtype(sub, sup)
        }

        // Primitive types: exact match only
        return sub == sup
    }

    private fun isRecordSubtype(sub: Type.TRecord, sup: Type.TRecord): Boolean {
        // Width subtyping: sup's fields must be present in sub
        // sub can have extra fields (they're just ignored)
        for ((field, supType) in sup.fields) {
            val subType = sub.fields[field] ?: return false
            if (!isSubtype(subType, supType)) return false
        }
        return true
    }

    private fun isFunctionSubtype(sub: Type.TFun, sup: Type.TFun): Boolean {
        // Arity must match
        if (sub.params.size != sup.params.size) return false

        // Contravariant in parameters
        for ((subParam, supParam) in sub.params.zip(sup.params)) {
            if (!isSubtype(supParam, subParam)) return false
        }

        // Covariant in result
        return isSubtype(sub.result, sup.result)
    }
}
```

### Type Simplification

SimpleSub's key innovation is simplifying types to keep them compact:

```kotlin
// Add to Subtyping.kt

/**
 * Simplify a type by resolving type variable bounds.
 * Called after inference to produce readable output types.
 */
fun simplify(type: Type): Type = when (type) {
    is Type.TVar -> simplifyVar(type)
    is Type.TFun -> Type.TFun(
        type.params.map { simplify(it) },
        simplify(type.result)
    )
    is Type.TRecord -> Type.TRecord(
        type.fields.mapValues { simplify(it.value) },
        type.row?.let { simplify(it) }
    )
    else -> type
}

private fun simplifyVar(v: Type.TVar): Type {
    // If bounds determine a concrete type, use it
    val lowers = v.lowerBounds.filterNot { it is Type.TBottom }
    val uppers = v.upperBounds.filterNot { it is Type.TTop }

    // Single concrete lower bound -> use it
    if (lowers.size == 1 && uppers.isEmpty()) {
        return simplify(lowers.first())
    }

    // Single concrete upper bound -> use it
    if (uppers.size == 1 && lowers.isEmpty()) {
        return simplify(uppers.first())
    }

    // Otherwise keep as variable
    return v
}
```

### Tests for Phase 3

```
klein-lib/src/commonTest/kotlin/klein/types/
├── SubtypingTest.kt     # Subtype relationship tests
└── SimplifyTest.kt      # Type simplification tests
```

**SubtypingTest.kt examples:**

```kotlin
@Test
fun intSubtypeOfInt() {
    val sub = Subtyping(Inferencer())
    assertTrue(sub.isSubtype(Type.TInt, Type.TInt))
}

@Test
fun recordWidthSubtyping() {
    val sub = Subtyping(Inferencer())
    val wider = Type.TRecord(mapOf("a" to Type.TInt, "b" to Type.TString))
    val narrower = Type.TRecord(mapOf("a" to Type.TInt))
    assertTrue(sub.isSubtype(wider, narrower))
    assertFalse(sub.isSubtype(narrower, wider))
}

@Test
fun functionContravariance() {
    val sub = Subtyping(Inferencer())
    // (Top -> Int) <: (Int -> Int)
    val wider = Type.TFun(listOf(Type.TTop), Type.TInt)
    val narrower = Type.TFun(listOf(Type.TInt), Type.TInt)
    assertTrue(sub.isSubtype(wider, narrower))
}
```

---

## Phase 4: Operators and Arithmetic

**Goal**: Type inference for binary and unary operators.

### Operator Type Rules

| Operator | Type Rule |
|----------|-----------|
| `+`, `-`, `*`, `/`, `%` | `(Int, Int) -> Int` or `(Double, Double) -> Double` |
| `==`, `!=` | `('a, 'a) -> Bool` |
| `<`, `<=`, `>`, `>=` | `(Int, Int) -> Bool` or `(Double, Double) -> Bool` |
| `and`, `or` | `(Bool, Bool) -> Bool` |
| `-` (unary) | `Int -> Int` or `Double -> Double` |
| `not` | `Bool -> Bool` |

### Implementation

```kotlin
// Add to Inferencer.kt

private fun inferBinaryOp(expr: BinaryOp, env: TypeEnv): Type {
    val leftType = infer(expr.left, env)
    val rightType = infer(expr.right, env)

    return when (expr.op) {
        // Arithmetic operators
        Operator.Add, Operator.Sub, Operator.Mul, Operator.Div, Operator.Mod -> {
            inferArithmetic(expr, leftType, rightType)
        }

        // Comparison operators
        Operator.Lt, Operator.LtEq, Operator.Gt, Operator.GtEq -> {
            inferComparison(expr, leftType, rightType)
        }

        // Equality operators (polymorphic)
        Operator.Eq, Operator.NotEq -> {
            constrain(leftType, rightType, expr.span)
            Type.TBool
        }

        // Boolean operators
        Operator.And, Operator.Or -> {
            constrain(leftType, Type.TBool, expr.left.span)
            constrain(rightType, Type.TBool, expr.right.span)
            Type.TBool
        }
    }
}

private fun inferArithmetic(expr: BinaryOp, left: Type, right: Type): Type {
    // Both must be numeric and same type
    return when {
        left == Type.TInt && right == Type.TInt -> Type.TInt
        left == Type.TDouble && right == Type.TDouble -> Type.TDouble
        left == Type.TInt && right == Type.TDouble -> {
            errors.add(TypeError.TypeMismatch(Type.TInt, right, expr.right.span))
            Type.TDouble
        }
        left == Type.TDouble && right == Type.TInt -> {
            errors.add(TypeError.TypeMismatch(Type.TDouble, right, expr.right.span))
            Type.TDouble
        }
        else -> {
            // At least one is a type variable - constrain to numeric
            if (left is Type.TVar) {
                subtyping.isSubtype(Type.TInt, left) // lower bound
            }
            if (right is Type.TVar) {
                subtyping.isSubtype(Type.TInt, right)
            }
            // Return fresh var that will be resolved
            freshVar().also { result ->
                subtyping.isSubtype(Type.TInt, result)
            }
        }
    }
}

private fun inferUnaryOp(expr: UnaryOp, env: TypeEnv): Type {
    val operandType = infer(expr.operand, env)

    return when (expr.op) {
        UnaryOperator.Neg -> {
            when (operandType) {
                Type.TInt -> Type.TInt
                Type.TDouble -> Type.TDouble
                is Type.TVar -> {
                    // Constrain to numeric
                    subtyping.isSubtype(Type.TInt, operandType)
                    operandType
                }
                else -> {
                    errors.add(TypeError.TypeMismatch(Type.TInt, operandType, expr.span))
                    Type.TInt
                }
            }
        }
        UnaryOperator.Not -> {
            constrain(operandType, Type.TBool, expr.span)
            Type.TBool
        }
    }
}
```

### Tests for Phase 4

```
klein-lib/src/commonTest/kotlin/klein/types/
├── ArithmeticInferTest.kt   # Binary arithmetic operators
├── ComparisonInferTest.kt   # Comparison operators
└── UnaryInferTest.kt        # Unary operators
```

**ArithmeticInferTest.kt examples:**

```kotlin
@Test
fun infersAddition() {
    val type = inferExpr("1 + 2")
    assertEquals(Type.TInt, type)
}

@Test
fun infersDoubleArithmetic() {
    val type = inferExpr("1.5 * 2.0")
    assertEquals(Type.TDouble, type)
}

@Test
fun reportsTypeMismatchInArithmetic() {
    val result = inferExprWithErrors("1 + true")
    assertTrue(result.errors.isNotEmpty())
}
```

---

## Phase 5: Functions and Application

**Goal**: Infer types for lambdas and function calls.

### Lambda Inference

```kotlin
// Add to Inferencer.kt

private fun inferLambda(expr: Lambda, env: TypeEnv): Type {
    val childEnv = env.child()

    // Create fresh type variables for parameters
    val paramTypes = expr.params.map { param ->
        val paramType = freshVar()
        childEnv.bind(param, paramType)
        paramType
    }

    // Infer body type
    val bodyType = infer(expr.body, childEnv)

    return Type.TFun(paramTypes, bodyType)
}
```

### Application Inference

```kotlin
private fun inferApply(expr: Apply, env: TypeEnv): Type {
    val calleeType = infer(expr.callee, env)
    val argTypes = expr.args.map { infer(it, env) }

    return when (calleeType) {
        is Type.TFun -> {
            // Check arity
            if (calleeType.params.size != argTypes.size) {
                errors.add(TypeError.ArityMismatch(
                    calleeType.params.size, argTypes.size, expr.span
                ))
                return calleeType.result
            }

            // Constrain arguments to parameters (args <: params)
            for ((argType, paramType) in argTypes.zip(calleeType.params)) {
                subtyping.isSubtype(argType, paramType)
            }

            calleeType.result
        }

        is Type.TVar -> {
            // Callee is unknown - constrain to be a function
            val resultVar = freshVar()
            val expectedFn = Type.TFun(argTypes, resultVar)
            subtyping.isSubtype(expectedFn, calleeType)
            resultVar
        }

        else -> {
            errors.add(TypeError.NotAFunction(calleeType, expr.callee.span))
            freshVar()
        }
    }
}
```

### Implicit Parameters

```kotlin
private var implicitParamType: Type? = null

private fun inferLambda(expr: Lambda, env: TypeEnv): Type {
    val childEnv = env.child()

    // Handle explicit parameters
    val paramTypes = if (expr.params.isEmpty()) {
        // No explicit params - check for implicit param usage in body
        val implicitType = freshVar()
        val previousImplicit = implicitParamType
        implicitParamType = implicitType

        val bodyType = infer(expr.body, childEnv)

        implicitParamType = previousImplicit

        // If implicit param was used, it's a 1-arg function
        // Otherwise it's a 0-arg function (thunk)
        if (implicitType.lowerBounds.isNotEmpty() || implicitType.upperBounds.isNotEmpty()) {
            return Type.TFun(listOf(implicitType), bodyType)
        } else {
            return Type.TFun(emptyList(), bodyType)
        }
    } else {
        expr.params.map { param ->
            val paramType = freshVar()
            childEnv.bind(param, paramType)
            paramType
        }
    }

    val bodyType = infer(expr.body, childEnv)
    return Type.TFun(paramTypes, bodyType)
}

private fun inferImplicitParam(expr: ImplicitParam, env: TypeEnv): Type {
    return implicitParamType ?: run {
        errors.add(TypeError.ImplicitParamOutsideLambda(expr.span))
        freshVar()
    }
}
```

### Tests for Phase 5

```
klein-lib/src/commonTest/kotlin/klein/types/
├── LambdaInferTest.kt       # Lambda type inference
├── ApplyInferTest.kt        # Function application
└── ImplicitParamInferTest.kt # Implicit parameter inference
```

**LambdaInferTest.kt examples:**

```kotlin
@Test
fun infersIdentityFunction() {
    val type = inferExpr("|x -> x|")
    // Should be 'a -> 'a
    assertTrue(type is Type.TFun)
    val fn = type as Type.TFun
    assertEquals(1, fn.params.size)
    assertEquals(fn.params[0], fn.result)  // Same type var
}

@Test
fun infersConstantLambda() {
    val type = inferExpr("|x -> 42|")
    assertTrue(type is Type.TFun)
    val fn = type as Type.TFun
    assertEquals(Type.TInt, fn.result)
}

@Test
fun infersImplicitParam() {
    val type = inferExpr("|. + 1|")
    assertTrue(type is Type.TFun)
    val fn = type as Type.TFun
    assertEquals(Type.TInt, fn.params[0])
    assertEquals(Type.TInt, fn.result)
}
```

**ApplyInferTest.kt examples:**

```kotlin
@Test
fun infersSimpleApplication() {
    val env = TypeEnv.builtins().child()
    env.bind("f", Type.TFun(listOf(Type.TInt), Type.TString))
    val type = inferExpr("f(42)", env)
    assertEquals(Type.TString, type)
}

@Test
fun infersPolymorphicApplication() {
    // id = |x -> x|
    // id(42) should be Int
    val type = inferProgram("""
        id = |x -> x|
        id(42)
    """.trimIndent())
    assertEquals(Type.TInt, type)
}
```

---

## Phase 6: Records and Field Access

**Goal**: Type inference for record literals and field projection.

### Record Literal Inference

```kotlin
private fun inferRecord(expr: RecordLiteral, env: TypeEnv): Type {
    val fieldTypes = expr.fields.associate { (name, value) ->
        name to infer(value, env)
    }
    return Type.TRecord(fieldTypes)
}
```

### Field Access Inference

```kotlin
private fun inferFieldAccess(expr: FieldAccess, env: TypeEnv): Type {
    val targetType = infer(expr.target, env)

    return when (targetType) {
        is Type.TRecord -> {
            targetType.fields[expr.field] ?: run {
                errors.add(TypeError.MissingField(expr.field, targetType, expr.span))
                freshVar()
            }
        }

        is Type.TVar -> {
            // Unknown type - constrain to be a record with at least this field
            val fieldType = freshVar()
            val requiredRecord = Type.TRecord(mapOf(expr.field to fieldType))
            subtyping.isSubtype(requiredRecord, targetType)
            fieldType
        }

        else -> {
            errors.add(TypeError.NotARecord(targetType, expr.target.span))
            freshVar()
        }
    }
}
```

### Tests for Phase 6

```
klein-lib/src/commonTest/kotlin/klein/types/
├── RecordInferTest.kt       # Record literal inference
└── FieldAccessInferTest.kt  # Field projection inference
```

**RecordInferTest.kt examples:**

```kotlin
@Test
fun infersSimpleRecord() {
    val type = inferExpr("{ name = 'Alice', age = 30 }")
    assertTrue(type is Type.TRecord)
    val rec = type as Type.TRecord
    assertEquals(Type.TString, rec.fields["name"])
    assertEquals(Type.TInt, rec.fields["age"])
}

@Test
fun infersNestedRecord() {
    val type = inferExpr("{ person = { name = 'Bob' } }")
    assertTrue(type is Type.TRecord)
}
```

**FieldAccessInferTest.kt examples:**

```kotlin
@Test
fun infersFieldAccess() {
    val type = inferExpr("{ name = 'Alice' }.name")
    assertEquals(Type.TString, type)
}

@Test
fun infersFieldAccessOnParameter() {
    // |.name| should infer { name: 'a } -> 'a
    val type = inferExpr("|.name|")
    assertTrue(type is Type.TFun)
}

@Test
fun reportsUnknownField() {
    val result = inferExprWithErrors("{ name = 'Alice' }.age")
    assertTrue(result.errors.any { it is TypeError.MissingField })
}
```

---

## Phase 7: Blocks, Bindings, and Control Flow

**Goal**: Type inference for blocks, val bindings, and if/then/else.

### Block Inference

```kotlin
private fun inferBlock(expr: Block, env: TypeEnv): Type {
    val blockEnv = env.child()
    var lastType: Type = Type.TTop  // Empty block would be unit

    for (stmt in expr.stmts) {
        lastType = inferStmt(stmt, blockEnv)
    }

    return lastType
}

fun inferStmt(stmt: Stmt, env: TypeEnv): Type = when (stmt) {
    is Val -> {
        val valueType = infer(stmt.value, env)
        env.bind(stmt.name, valueType)
        valueType
    }
    is FunDef -> inferFunDef(stmt, env)
    is Expr -> infer(stmt, env)
}
```

### Function Definition (with Recursion)

```kotlin
private fun inferFunDef(def: FunDef, env: TypeEnv): Type {
    // For recursion: bind function name to fresh var before inferring body
    val resultVar = freshVar()
    val paramVars = def.params.map { freshVar() }
    val funType = Type.TFun(paramVars, resultVar)

    // Bind in outer env for recursion
    env.bind(def.name, funType)

    // Create body env with params
    val bodyEnv = env.child()
    for ((param, paramType) in def.params.zip(paramVars)) {
        bodyEnv.bind(param, paramType)
    }

    // Infer body
    val bodyType = infer(def.body, bodyEnv)

    // Constrain result
    subtyping.isSubtype(bodyType, resultVar)

    return funType
}
```

### If/Then/Else Inference

```kotlin
private fun inferIfThenElse(expr: IfThenElse, env: TypeEnv): Type {
    val condType = infer(expr.condition, env)
    constrain(condType, Type.TBool, expr.condition.span)

    val thenType = infer(expr.thenBranch, env)

    return if (expr.elseBranch != null) {
        val elseType = infer(expr.elseBranch, env)
        // Both branches must be compatible - find common supertype
        val resultVar = freshVar()
        subtyping.isSubtype(thenType, resultVar)
        subtyping.isSubtype(elseType, resultVar)
        resultVar
    } else {
        // No else branch - result could be then-type or "nothing"
        // For now, require else branch for non-unit results
        thenType
    }
}
```

### Tests for Phase 7

```
klein-lib/src/commonTest/kotlin/klein/types/
├── BlockInferTest.kt        # Block and binding inference
├── FunDefInferTest.kt       # Function definition inference
└── IfThenElseInferTest.kt   # Conditional inference
```

**BlockInferTest.kt examples:**

```kotlin
@Test
fun infersBlockWithBindings() {
    val type = inferExpr("""
        x = 1
        y = 2
        x + y
    """.trimIndent())
    assertEquals(Type.TInt, type)
}

@Test
fun infersShadowing() {
    val type = inferExpr("""
        x = 1
        x = 'hello'
        x
    """.trimIndent())
    assertEquals(Type.TString, type)
}
```

**FunDefInferTest.kt examples:**

```kotlin
@Test
fun infersSimpleFunction() {
    val types = inferProgram("""
        fun double(x) = x * 2
        double(21)
    """.trimIndent())
    // double: Int -> Int, result: Int
}

@Test
fun infersRecursiveFunction() {
    val types = inferProgram("""
        fun factorial(n) = if n <= 1 then 1 else n * factorial(n - 1)
        factorial(5)
    """.trimIndent())
    // factorial: Int -> Int
}
```

**IfThenElseInferTest.kt examples:**

```kotlin
@Test
fun infersSimpleConditional() {
    val type = inferExpr("if true then 1 else 2")
    assertEquals(Type.TInt, type)
}

@Test
fun unifiesBranchTypes() {
    val env = TypeEnv.builtins().child()
    env.bind("x", Type.TInt)
    val type = inferExpr("if x > 0 then { a = 1 } else { a = 2 }", env)
    // Both branches are { a: Int }
}

@Test
fun requiresBoolCondition() {
    val result = inferExprWithErrors("if 42 then 1 else 2")
    assertTrue(result.errors.any { it is TypeError.TypeMismatch })
}
```

---

## Phase 8: Let-Polymorphism

**Goal**: Generalize types at let-bindings for true polymorphism.

### The Problem

Without generalization:
```klein
id = |x -> x|
a = id(1)      # id: Int -> Int (specialized)
b = id('hi')   # ERROR: String not Int
```

With let-polymorphism:
```klein
id = |x -> x|  # id: forall a. a -> a
a = id(1)      # Int
b = id('hi')   # String
```

### Implementation

```kotlin
// Add to Type.kt
data class TScheme(
    val vars: List<Int>,  // Quantified variable IDs
    val type: Type,
)

// Add to Inferencer.kt
private fun generalize(type: Type, env: TypeEnv): TScheme {
    val freeInEnv = freeVarsInEnv(env)
    val freeInType = freeVars(type)
    val generalizable = freeInType - freeInEnv
    return TScheme(generalizable.toList(), type)
}

private fun instantiate(scheme: TScheme): Type {
    val substitution = scheme.vars.associateWith { freshVar() }
    return substitute(scheme.type, substitution)
}

private fun freeVars(type: Type): Set<Int> = when (type) {
    is Type.TVar -> setOf(type.id)
    is Type.TFun -> freeVars(type.result) + type.params.flatMap { freeVars(it) }
    is Type.TRecord -> type.fields.values.flatMap { freeVars(it) }.toSet() +
        (type.row?.let { freeVars(it) } ?: emptySet())
    else -> emptySet()
}
```

### Modified Val Binding

```kotlin
// In inferStmt for Val
is Val -> {
    val valueType = infer(stmt.value, env)
    val scheme = generalize(valueType, env)
    env.bindScheme(stmt.name, scheme)
    valueType
}

// In inferIdent
private fun inferIdent(expr: Ident, env: TypeEnv): Type {
    return env.lookupScheme(expr.name)?.let { instantiate(it) }
        ?: env.lookup(expr.name)
        ?: run {
            errors.add(TypeError.UnboundVariable(expr.name, expr.span))
            freshVar()
        }
}
```

### Tests for Phase 8

```
klein-lib/src/commonTest/kotlin/klein/types/
└── PolymorphismTest.kt      # Let-polymorphism tests
```

**PolymorphismTest.kt examples:**

```kotlin
@Test
fun polymorphicIdentity() {
    val result = inferProgram("""
        id = |x -> x|
        a = id(42)
        b = id('hello')
        { intResult = a, stringResult = b }
    """.trimIndent())
    // Should succeed - id is polymorphic
}

@Test
fun polymorphicMap() {
    val result = inferProgram("""
        apply = |f, x -> f(x)|
        double = |x -> x * 2|
        greet = |x -> 'Hello'|
        a = apply(double, 21)
        b = apply(greet, 'world')
    """.trimIndent())
}
```

---

## Phase 9: CLI Integration

**Goal**: Add `./klein infer` command to show inferred types.

### Main.kt Changes

```kotlin
// Add to CLI commands in Main.kt
"infer", "i" -> {
    val source = readSource(args)
    val tokens = Lexer(source).tokenize().toList()
    val program = Parser(tokens).parseProgram()
    val result = Inferencer().inferProgram(program, TypeEnv.builtins())

    if (result.errors.isNotEmpty()) {
        for (error in result.errors) {
            System.err.println(error.span.formatInSource(source, message = error.message))
        }
        exitProcess(1)
    }

    // Print inferred types for top-level bindings
    for ((name, type) in result.bindings) {
        println("$name : ${TypePrinter.print(type)}")
    }
}
```

### Example Output

```bash
$ cat example.klein
double = |x -> x * 2|
greet = |name -> 'Hello, ' ++ name|
person = { name = 'Alice', age = 30 }

$ ./klein infer example.klein
double : Int -> Int
greet : String -> String
person : { name: String, age: Int }
```

### Error Output

```bash
$ echo "x + true" | ./klein infer --stdin
  1 | x + true
        ^^^^
Error: Type mismatch: expected Int, got Bool
```

---

## Test Organization Summary

```
klein-lib/src/commonTest/kotlin/klein/types/
├── TypePrinterTest.kt       # Phase 1: Type display
├── TypeEnvTest.kt           # Phase 2: Environment
├── LiteralInferTest.kt      # Phase 2: Literals
├── IdentInferTest.kt        # Phase 2: Identifiers
├── SubtypingTest.kt         # Phase 3: Subtyping
├── SimplifyTest.kt          # Phase 3: Simplification
├── ArithmeticInferTest.kt   # Phase 4: Arithmetic
├── ComparisonInferTest.kt   # Phase 4: Comparisons
├── UnaryInferTest.kt        # Phase 4: Unary ops
├── LambdaInferTest.kt       # Phase 5: Lambdas
├── ApplyInferTest.kt        # Phase 5: Application
├── ImplicitParamInferTest.kt# Phase 5: Implicit params
├── RecordInferTest.kt       # Phase 6: Records
├── FieldAccessInferTest.kt  # Phase 6: Field access
├── BlockInferTest.kt        # Phase 7: Blocks
├── FunDefInferTest.kt       # Phase 7: Fun definitions
├── IfThenElseInferTest.kt   # Phase 7: Conditionals
├── PolymorphismTest.kt      # Phase 8: Let-polymorphism
└── IntegrationTest.kt       # End-to-end tests
```

### Test Helpers

```kotlin
// klein-lib/src/commonTest/kotlin/klein/types/TypeAssertions.kt

fun inferExpr(source: String, env: TypeEnv = TypeEnv.builtins()): Type {
    val expr = parse(source)
    return Inferencer().infer(expr, env)
}

fun inferExprWithErrors(source: String, env: TypeEnv = TypeEnv.builtins()): InferResult {
    val expr = parse(source)
    return Inferencer().inferWithErrors(expr, env)
}

fun inferProgram(source: String): Type {
    val tokens = Lexer(source).tokenize().toList()
    val program = Parser(tokens).parseProgram()
    return Inferencer().inferProgramType(program, TypeEnv.builtins())
}

fun assertTypeEquals(expected: Type, actual: Type) {
    assertEquals(TypePrinter.print(expected), TypePrinter.print(actual))
}
```

---

## Implementation Order

| Phase | Deliverable | Estimated Tests |
|-------|-------------|-----------------|
| 1 | Type.kt, TypePrinter.kt | ~10 tests |
| 2 | TypeEnv.kt, TypeError.kt, basic Inferencer | ~15 tests |
| 3 | Subtyping.kt | ~20 tests |
| 4 | Operator inference | ~25 tests |
| 5 | Lambda/Apply inference | ~20 tests |
| 6 | Record/FieldAccess inference | ~15 tests |
| 7 | Block/FunDef/IfThenElse inference | ~20 tests |
| 8 | Let-polymorphism | ~15 tests |
| 9 | CLI integration | ~5 integration tests |

**Total: ~145 tests**

Each phase builds on the previous and can be completed and tested independently.

---

## Future Extensions (Not In Scope)

These are documented for awareness but not part of this plan:

- **Type annotations**: Surface syntax for type hints
- **Nominal types**: `type Person = { name: String }` creating distinct types
- **Sum types**: `type Option(a) = Some { value: a } | None`
- **Row polymorphism**: `{ name: String, ...r }` for tracking extra fields through inference
- **Match expressions**: Pattern matching with exhaustiveness
- **Kleene types**: `T?`, `T*`, `T+` cardinality annotations
