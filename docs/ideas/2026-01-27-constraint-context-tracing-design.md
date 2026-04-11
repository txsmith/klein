# Constraint Context Tracing Design

## Goal

Improve type error messages by adding a "stack trace" of context explaining how the constraint solver reached each error.

## Example Output

```
 8 >| VAnimal(cov)
    | ^^^^^^^^^^^^ Type mismatch: expected Dog, got Animal
    |   In argument of VAnimal
    |   └> Expecting a V<Animal>, got Cov<Dog>
    |   └> Note: Cov is a constructor of V
    |   └> Note: V<'A> is invariant in 'A
    |   └> Therefore: Dog must equal Animal, which it doesn't
```

## Approach

Thread a `List<ConstraintContext>` through `constrain()` as an explicit parameter. Each recursive call can append context entries. When an error occurs, the current context stack is captured in the error.

## Data Model

```kotlin
sealed class ConstraintContext {
    data class FunctionCall(val name: String?)
    data class Argument(val paramIndex: Int, val expected: String, val actual: String)
    data class ConstructorToParent(val constructorName: String, val parentName: String)
    data class VarianceCheck(val typeDisplay: String, val paramName: String, val variance: Variance)
}
```

## Context Push Points

| Context | Pushed by | Where |
|---------|-----------|-------|
| FunctionCall | Typer | inferApply, before constrain |
| Argument | Subtyping | TFun vs TFun, per parameter |
| ConstructorToParent | Subtyping | TRef vs TRef when names differ |
| VarianceCheck | Subtyping | TRef vs TRef variance loop |

## Rendering

- Primary error line: the leaf TypeMismatch message
- Context entries rendered as indented notes below
- "Therefore" line derived from VarianceCheck + leaf error when invariant

## Scope

Start with function call context only. Other Typer call sites (if-cond, binop, etc.) can be added later.
