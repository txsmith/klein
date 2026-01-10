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
    object TUnit : Type()  // For expressions with no meaningful value (if without else)

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
        Type.TUnit -> "Unit"
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
| Unit | `Unit` |
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

    data class ImplicitParamOutsideLambda(
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Implicit parameter '.' can only be used inside a lambda"
    }

    data class MixedImplicitExplicit(
        override val span: SourceSpan,
    ) : TypeError() {
        override val message = "Cannot mix implicit parameter '.' with explicit parameters"
    }
}
```

### TypeGen.kt - Basic Structure

```kotlin
package klein

class TypeGen {
    private var nextVarId = 0
    private val errors = mutableListOf<TypeError>()

    fun freshVar(): Type.TVar = Type.TVar(nextVarId++)

    fun infer(expr: Expr, env: TypeEnv): Type = when (expr) {
        is IntLiteral -> Type.TInt
        is DoubleLiteral -> Type.TDouble
        is StringLiteral -> Type.TString
        is BoolLiteral -> Type.TBool
        is Ident -> inferIdent(expr, env)
        is BinaryOp -> TODO("Phase 4")
        is UnaryOp -> TODO("Phase 4")
        is Lambda -> TODO("Phase 5")
        is Apply -> TODO("Phase 5")
        is Block -> TODO("Phase 7")
        is IfThenElse -> TODO("Phase 7")
        is FieldAccess -> TODO("Phase 6")
        is ImplicitParam -> TODO("Phase 5")
        is RecordLiteral -> TODO("Phase 6")
    }

    private fun inferIdent(expr: Ident, env: TypeEnv): Type {
        return env.lookup(expr.name) ?: run {
            errors.add(TypeError.UnboundVariable(expr.name, expr.span))
            freshVar()  // Return fresh var to continue inference
        }
    }

    // Inference methods added incrementally in later phases
}
```

The `TODO()` calls allow the CLI to work immediately. Each expression form is implemented as its phase is completed, and tests can be written incrementally.
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
    val type = TypeGen().infer(expr, TypeEnv.empty())
    assertEquals(Type.TInt, type)
}

@Test
fun infersBoolLiteral() {
    val expr = parse("true")
    val type = TypeGen().infer(expr, TypeEnv.empty())
    assertEquals(Type.TBool, type)
}
```

**IdentInferTest.kt examples:**

```kotlin
@Test
fun infersIdentFromEnv() {
    val env = TypeEnv.empty().child()
    env.bind("x", Type.TInt)
    val expr = parse("x")
    val type = TypeGen().infer(expr, env)
    assertEquals(Type.TInt, type)
}

@Test
fun reportsUnboundVariable() {
    val result = TypeGen().inferWithErrors(parse("unknown"), TypeEnv.empty())
    assertTrue(result.errors.any { it is TypeError.UnboundVariable })
}
```

---

## Phase 3: CLI Integration

**Goal**: Add `./klein infer` command early so we can test incrementally.

The CLI should work even when not all expression forms are implemented. Unimplemented forms throw `NotImplementedError` with a clear message.

### Interface

```bash
# Infer types for a file
./klein infer example.klein

# Infer from stdin
echo "42" | ./klein infer --stdin

# Short form
./klein i example.klein
```

### Main.kt Changes

```kotlin
// Add to CLI commands in Main.kt
"infer", "i" -> {
    val source = readSource(args)
    val tokens = Lexer(source).tokenize().toList()
    val program = Parser(tokens).parseProgram()

    try {
        val result = TypeGen().inferProgram(program, TypeEnv.empty())

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
    } catch (e: NotImplementedError) {
        System.err.println("Type inference not yet implemented: ${e.message}")
        exitProcess(2)
    }
}
```

### Output Format

Success:
```
double : Int -> Int
person : { name: String, age: Int }
```

Type error:
```
  1 | x + true
        ^^^^
Error: Type mismatch: expected Int, got Bool
```

Not yet implemented:
```
Type inference not yet implemented: Phase 5 (Lambda)
```

This allows us to use `./klein infer` immediately after Phase 2, with support for literals and identifiers.

---

## Phase 4: Subtyping and Constraint Solving

**Goal**: Implement SimpleSub's core subtyping and type simplification.

### Overview

SimpleSub requires multiple type representations:

1. **Simple types** (Phase 1's `Type`) - What users see in error messages and inference results
2. **Polar types** - Internal representation tracking whether types appear in positive (output) or negative (input) positions
3. **Compact types** - Intermediate representation for the simplification algorithm

The key insight of SimpleSub is that subtyping constraints are accumulated on type variables (as bounds) rather than solved eagerly. After inference, types are *simplified* to remove redundant bounds and produce readable output.

### Key Concepts

- **Polarity**: Function parameters are negative (input), results are positive (output). This affects how bounds propagate.
- **Co-inductive subtyping**: The subtyping check must handle recursive types without infinite loops (using a "seen" set).
- **Occurs check**: Prevent infinite types like `'a = 'a -> Int`.

### Subtyping Rules

| Relationship | Rule |
|--------------|------|
| Primitives | `Int <: Int`, etc. (reflexive only) |
| Top/Bottom | `T <: Top`, `Bottom <: T` for all T |
| Functions | Contravariant in params, covariant in result |
| Records | Width subtyping (more fields = subtype) |
| Type variables | Accumulate bounds, defer resolution |

### Simplification Algorithm

After inference completes, simplification:
1. Computes the transitive closure of bounds
2. Merges equivalent type variables
3. Eliminates variables that have a single concrete bound
4. Produces minimal, readable types

This phase requires careful design of the internal type IRs before implementation.

---

## Phase 5: Operators

**Goal**: Type inference for binary and unary operators.

### Operator Typing Summary

| Category | Operators | Type |
|----------|-----------|------|
| Arithmetic | `+`, `-`, `*`, `/`, `%` | `(Int, Int) -> Int` or `(Double, Double) -> Double` |
| Comparison | `<`, `<=`, `>`, `>=` | `(Int, Int) -> Bool` or `(Double, Double) -> Bool` |
| Equality | `==`, `!=` | `('a, 'a) -> Bool` (polymorphic) |
| Boolean | `and`, `or` | `(Bool, Bool) -> Bool` |
| Unary neg | `-` | `Int -> Int` or `Double -> Double` |
| Unary not | `not` | `Bool -> Bool` |

### Design Considerations

- **Numeric polymorphism**: How to handle `+` working on both Int and Double without full ad-hoc polymorphism. Options: overloading, type classes, or just require same-type operands.
- **Equality**: Should `==` work on all types or only comparable ones?
- **Error recovery**: When operand types don't match, what type does the expression have?

---

## Phase 6: Functions and Application

**Goal**: Infer types for lambdas, function application, and implicit parameters.

### Key Challenges

1. **Lambda parameters**: Create fresh type variables, constrain via body usage
2. **Application**: Check callee is a function, constrain args against params
3. **Implicit parameters**: Detect `|.|` and `|.field|` patterns, synthesize parameter type from usage
4. **Higher-order functions**: Ensure type variables flow correctly through callbacks

### Implicit Parameter Semantics

The implicit parameter `|.|` and `|.field|` syntax needs special handling:
- `|. + 1|` → `Int -> Int`
- `|.name|` → `{ name: 'a } -> 'a`
- `|.x + .y|` → `{ x: Int, y: Int } -> Int`

**Constraints:**
- Implicit parameters cannot be used outside lambdas (error)
- Mixing implicit and explicit parameters is not allowed: `|x -> . + x|` is an error
- A lambda uses either explicit parameters OR implicit parameter, not both

---

## Phase 7: Records and Field Access

**Goal**: Type inference for record literals and field projection.

### Key Behaviors

- **Record literals**: Infer type from field values, produce closed record type
- **Field access on known record**: Look up field, error if missing
- **Field access on type variable**: Constrain variable to have at least that field

### Width Subtyping for Records

Field access on a type variable creates a record constraint. Width subtyping ensures this works with larger records:

```klein
getName = |.name|  # { name: 'a } -> 'a
getName({ name = 'Alice', age = 30 })  # OK! { name: String, age: Int } <: { name: String }
```

The subtyping rule `{ a: T, b: U } <: { a: T }` (more fields = subtype) allows functions that require certain fields to accept records with additional fields. This is the standard structural subtyping approach.

---

## Phase 8: Blocks, Bindings, and Control Flow

**Goal**: Type inference for blocks, val bindings, fun definitions, and if/then/else.

### Key Behaviors

- **Blocks**: Create child scope, return type of last expression
- **Val bindings**: Infer RHS, bind name to type in scope
- **Fun definitions**: Support recursion by pre-binding function name to fresh var
- **If/then/else**: Condition must be Bool, branches must be compatible

### Recursion Support

For `fun factorial(n) = ... factorial(n-1) ...`:
1. Create fresh vars for params and result
2. Bind `factorial` to function type before inferring body
3. Infer body, constrain against result var
4. Final type reflects actual usage

### Branch Compatibility

If branches have different types, find common supertype:
- `if c then 1 else 2` → `Int`
- `if c then { a = 1 } else { a = 2, b = 3 }` → `{ a: Int }` (via width subtyping)

---

## Phase 9: Let-Polymorphism

**Goal**: Generalize types at let-bindings for true polymorphism.

### The Problem

Without generalization, the identity function gets specialized on first use:

```klein
id = |x -> x|
a = id(1)      # id : Int -> Int
b = id('hi')   # ERROR!
```

### The Solution

At let-bindings, *generalize* free type variables not appearing in the environment:

```klein
id = |x -> x|  # id : ∀a. a -> a
a = id(1)      # instantiate: Int -> Int, result: Int
b = id('hi')   # instantiate: String -> String, result: String
```

### Implementation Considerations

- **Type schemes**: `TScheme(vars, type)` wraps types with quantified variables
- **Generalization**: Collect free vars in type, subtract free vars in env
- **Instantiation**: Replace quantified vars with fresh vars at each use site
- **Value restriction**: May need to restrict generalization to syntactic values to maintain soundness with mutable state (not currently an issue for Klein)

---

## Test Strategy

Tests will be organized by phase in `klein-lib/src/commonTest/kotlin/klein/types/`.

Each phase should have tests covering:
- Happy path (correct programs)
- Error cases (type mismatches, unbound variables, etc.)
- Edge cases (empty records, zero-arg functions, etc.)

Test helpers in `TypeAssertions.kt` will mirror the parser test patterns.

---

## Implementation Order

| Phase | Focus | Dependencies |
|-------|-------|--------------|
| 1 | Type representation, printing | None |
| 2 | Environment, basic inference (literals, identifiers) | Phase 1 |
| 3 | CLI integration (`./klein infer`) | Phase 1, 2 |
| 4 | Subtyping, simplification | Phase 1, 2 |
| 5 | Operators | Phase 2, 4 |
| 6 | Functions, application, implicit params | Phase 2, 4 |
| 7 | Records, field access | Phase 2, 4 |
| 8 | Blocks, bindings, control flow | Phase 2-7 |
| 9 | Let-polymorphism | Phase 8 |

CLI comes early (Phase 3) so we can test incrementally. Each expression form is added to TypeGen as its phase is completed.

---

## Future Extensions (Not In Scope)

These are documented for awareness but not part of this plan:

- **Type annotations**: Surface syntax for type hints
- **Nominal types**: `type Person = { name: String }` creating distinct types
- **Sum types**: `type Option(a) = Some { value: a } | None`
- **Row polymorphism**: `{ name: String, ...r }` for tracking extra fields through inference
- **Match expressions**: Pattern matching with exhaustiveness
- **Kleene types**: `T?`, `T*`, `T+` cardinality annotations
