# Match Expression Design

## Overview

Klein's match expressions serve two purposes:
1. **Value matching** - pattern matching on a subject value
2. **Condition matching** - boolean condition evaluation (like Kotlin's `when`)

## Two Modes

### Value Matching (with subject)
```klein
match status
  Approved -> processLoan()
  Rejected { reason } -> notify(reason)
  Pending -> wait()
```

### Condition Matching (no subject)
```klein
match
  score >= 90 -> 'A'
  score >= 80 -> 'B'
  else -> 'F'
```

## Key Design Question: AST Structure

The fundamental question is how to represent match arms, given they can be:
- **Pattern arms** (value matching): `pattern -> body` or `pattern if guard -> body`
- **Condition arms** (condition matching): `expression -> body`
- **Else arms** (both modes): `else -> body`

### Option 1: Unified MatchArm with nullable fields
```kotlin
data class Match(
    val subject: Expr?,     // null for condition matching
    val arms: List<MatchArm>,
    override val span: SourceSpan
) : Expr()

data class MatchArm(
    val pattern: Pattern?,  // null for condition arms
    val guard: Expr?,       // guard condition
    val body: Expr,
    val span: SourceSpan
)
```

**Problems:**
- Unclear semantics when `pattern` is null
- Can't distinguish between "else" and "condition arm"
- Requires runtime validation of valid combinations

### Option 2: Sealed class hierarchy for arm types
```kotlin
data class Match(
    val subject: Expr?,
    val arms: List<MatchArm>,
    override val span: SourceSpan
) : Expr()

sealed class MatchArm {
    abstract val body: Expr
    abstract val span: SourceSpan
}

data class PatternArm(
    val pattern: Pattern,
    val guard: Expr?,      // optional if condition
    override val body: Expr,
    override val span: SourceSpan
) : MatchArm()

data class ConditionArm(
    val condition: Expr,   // boolean expression
    override val body: Expr,
    override val span: SourceSpan
) : MatchArm()

data class ElseArm(
    override val body: Expr,
    override val span: SourceSpan
) : MatchArm()
```

**Benefits:**
- Type-safe representation
- Clear distinction between arm types
- Cannot construct invalid combinations

**Issue:**
- Still need semantic validation: PatternArm only valid when subject != null

### Option 3: Arm condition as sealed class
```kotlin
data class Match(
    val subject: Expr?,
    val arms: List<MatchArm>,
    override val span: SourceSpan
) : Expr()

data class MatchArm(
    val condition: ArmCondition,
    val body: Expr,
    val span: SourceSpan
)

sealed class ArmCondition {
    abstract val span: SourceSpan
}

data class PatternCondition(
    val pattern: Pattern,
    val guard: Expr?,      // optional if clause
    override val span: SourceSpan
) : ArmCondition()

data class ExpressionCondition(
    val expr: Expr,
    override val span: SourceSpan
) : ArmCondition()

data class ElseCondition(
    override val span: SourceSpan
) : ArmCondition()
```

**Benefits:**
- Most explicit and type-safe
- Clear naming: "condition" encompasses both patterns and expressions
- Guards are properly scoped to patterns only

**Recommended:** Option 3

## Pattern Types

Klein patterns for value matching:

### MVP Patterns (Simple Implementation)
1. **Variable pattern** - binds value to name: `x`
2. **Wildcard pattern** - ignores value: `_`
3. **Constructor pattern** - matches enum constructor: `Approved`, `Red`
4. **Literal pattern** - matches exact value: `1`, `true`, `'hello'`

### Extended Patterns (Future)
5. **Constructor with record** - `Ok { value }`, `Rejected { reason }`
6. **Record pattern** - `{ name, age }`, `{ name, age: x }`
7. **List patterns** - `[]`, `[x]`, `[x, y]`, `[first, ...rest]`
8. **Tuple patterns** - `(x, y)`, `(x, _, z)`

## Proposed AST (MVP)

```kotlin
// Match expression
data class Match(
    val subject: Expr?,
    val arms: List<MatchArm>,
    override val span: SourceSpan
) : Expr()

data class MatchArm(
    val condition: ArmCondition,
    val body: Expr,
    val span: SourceSpan
)

sealed class ArmCondition {
    abstract val span: SourceSpan
}

data class PatternCondition(
    val pattern: Pattern,
    val guard: Expr?,      // if condition
    override val span: SourceSpan
) : ArmCondition()

data class ExpressionCondition(
    val expr: Expr,
    override val span: SourceSpan
) : ArmCondition()

data class ElseCondition(
    override val span: SourceSpan
) : ArmCondition()

// Patterns
sealed class Pattern {
    abstract val span: SourceSpan
}

data class VariablePattern(
    val name: String,
    override val span: SourceSpan
) : Pattern()

data class WildcardPattern(
    override val span: SourceSpan
) : Pattern()

data class ConstructorPattern(
    val constructor: String,
    override val span: SourceSpan
) : Pattern()

data class LiteralPattern(
    val literal: Expr,  // IntLiteral, StringLiteral, BoolLiteral
    override val span: SourceSpan
) : Pattern()
```

## Grammar

```
match_expr
  = 'match' expr? NEWLINE INDENT match_arms DEDENT

match_arms
  = match_arm (NEWLINE match_arm)*

match_arm
  = pattern guard? '->' expr     # pattern arm (requires subject)
  | expr '->' expr                # condition arm (no subject)
  | 'else' '->' expr              # else arm (both modes)

guard
  = 'if' expr

pattern
  = '_'                           # wildcard
  | LOWER_IDENT                   # variable
  | UPPER_IDENT                   # constructor
  | literal                       # literal pattern

literal
  = INT | DOUBLE | STRING | BOOL
```

## Parsing Strategy

The parser determines the arm type based on the subject:

```kotlin
fun parseMatchArm(hasSubject: Boolean): MatchArm {
    if (check(ELSE)) {
        consume(ELSE)
        consume(ARROW)
        val body = parseExpr()
        return MatchArm(ElseCondition(...), body, ...)
    }

    if (hasSubject) {
        // Parse pattern
        val pattern = parsePattern()
        val guard = if (check(IF)) {
            consume(IF)
            parseExpr()
        } else null
        consume(ARROW)
        val body = parseExpr()
        return MatchArm(PatternCondition(pattern, guard, ...), body, ...)
    } else {
        // Parse condition expression
        val condition = parseExpr()
        consume(ARROW)
        val body = parseExpr()
        return MatchArm(ExpressionCondition(condition, ...), body, ...)
    }
}
```

## Ambiguity Resolution

### Question: `match` followed by identifier

```klein
match x
  y -> 1
```
Here `x` is the subject, `y` is a variable pattern.

```klein
match
  x -> 1
```
Here there's no subject, `x` is a condition expression.

**Resolution:** Presence of subject determines parsing mode completely.

### Question: How to distinguish pattern vs expression?

```klein
match x
  Foo -> 1      # Pattern: constructor
  foo -> 1      # Pattern: variable
```

vs

```klein
match
  Foo() -> 1    # Condition: function call
  foo > 0 -> 1  # Condition: comparison
```

**Resolution:** When parsing arms with a subject, we're in "pattern mode":
- Uppercase identifier → Constructor pattern
- Lowercase identifier → Variable pattern
- `_` → Wildcard
- Literal → Literal pattern

When parsing arms without a subject, we're in "condition mode":
- Parse full expressions

### Question: Guards vs conditions?

Pattern with guard:
```klein
match amount
  x if x > 1000 -> 'large'
```

Condition (no guard):
```klein
match
  amount > 1000 -> 'large'
```

**Resolution:** Guards (`if`) only allowed after patterns, not after condition expressions.

## Semantic Validation

The parser should enforce:

1. **Subject/arm consistency:**
   - If `subject != null`: all arms must be `PatternCondition | ElseCondition`
   - If `subject == null`: all arms must be `ExpressionCondition | ElseCondition`

2. **Else must be last:** `ElseCondition` can only appear as the final arm

3. **No duplicate else:** Only one `ElseCondition` allowed

4. **At least one arm:** Match must have at least one arm

These should produce parse errors, not be deferred to type checking.

## Test Categories

Based on this design:

### Lexer Tests
- Recognize `match` keyword
- `match` not confused with identifiers like `matcher`

### Parser Tests - Structure
- Match with subject and pattern arms
- Match without subject and condition arms
- Else arm in both modes
- Guards with patterns
- Multi-line bodies with indentation

### Parser Tests - Patterns (MVP)
- Variable pattern: `x`
- Wildcard pattern: `_`
- Constructor pattern: `Approved`
- Literal patterns: `1`, `true`, `'hello'`

### Parser Tests - Error Cases
- Missing arrow after pattern/condition
- Missing body after arrow
- Empty match (no arms)
- else not last
- Multiple else clauses
- Inconsistent indentation
- Pattern arm when no subject (should be error)
- Condition arm when subject present (should be error)
- Guard on condition arm (should be error)

## Open Questions

1. **Should variable patterns allow uppercase?**
   - Currently: lowercase only (to distinguish from constructors)
   - Alternative: allow both, resolve in type checking

2. **Should we allow or-patterns?**
   - Example: `Red | Blue | Green -> 'rgb'`
   - Not in reference.md, probably defer

3. **Should literal patterns allow negative numbers?**
   - Example: `-1 -> 'negative one'`
   - Could conflict with unary minus operator
   - Probably need special handling

4. **Multi-line patterns?**
   - Probably not needed for MVP, but consider:
   ```klein
   match person
     {
       name,
       age
     } -> ...
   ```

5. **As-patterns?**
   - Binding pattern and subpatterns: `Ok { value: x } as result -> ...`
   - Not in reference.md, probably future work
