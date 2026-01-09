# Syntax Migration Plan

Migrating function syntax from old `()` style to new `{}` style.

## Current State

The parser currently uses:
- `fun f(x, y) = ...` for function definitions
- `f(x, y)` for function calls

Target syntax (from `klein-reference.md`):
- `fun f { x, y } = ...` for function definitions
- `f { x = 1, y = 2 }` for function calls (record-style)

## Migration Tasks

### 1. Function Definitions: `fun f(x)` → `fun f { x }`

**File:** `Parser.kt` - `parseFunDef()`

**Current code:**
```kotlin
expectAndAdvance(LPAREN, message = "Expected '('")
val params = parseFunParams()
expectAndAdvance(RPAREN, message = "Expected ')'")
```

**Change to:**
```kotlin
expectAndAdvance(LBRACE, message = "Expected '{'")
val params = parseFunParams()
expectAndAdvance(RBRACE, message = "Expected '}'")
```

**Also update:**
- `parseFunParams()` - currently expects `RPAREN` for empty check, change to `RBRACE`

**Tests to update:**
- All tests in `klein-lib/src/commonTest/kotlin/klein/parser/` that define functions

---

### 2. Function Calls: Juxtaposition

**Change:** Function application is juxtaposition, not special syntax.

```klein
greet person                    # apply to existing record
greet { name = 'Thomas' }       # apply to inline record literal
double x                        # apply to identifier
process (a + b)                 # apply to parenthesized expr
```

**File:** `Parser.kt` - `parseApply()`

**Current code:**
```kotlin
LPAREN -> {
    expr = parseFunctionCallOn(expr)
}
```

**Change to:**
```kotlin
// If next token can start an expression (and not on dedented line), it's an argument
else -> {
    if (canStartExpr(peek())) {
        val arg = parseAtom()  // or parseExpr() with precedence?
        expr = Apply(expr, arg, expr.span + arg.span)
    } else {
        break
    }
}
```

**Tokens that can start an expression:**
- `IDENT`, `INT`, `DOUBLE`, `STRING`, `TRUE`, `FALSE`
- `LBRACE` (record literal)
- `LPAREN` (grouped expression)
- `PIPE` (lambda)
- `DOT` (implicit param)
- `IF`
- `NOT`, `MINUS`, `MINUS_TIGHT` (unary prefix)

**Apply takes a single argument:**
The AST `Apply(callee, arg)` now takes one `Expr` argument, not a list. Multiple "arguments" are just record fields:
```klein
add { a = 1, b = 2 }  # Apply(add, RecordLiteral(...))
```

**Tests to update:**
- All tests that call functions
- Change `f(x, y)` to `f { x = ..., y = ... }`

---

### 3. Precedence and Associativity

Function application binds tighter than binary operators:
```klein
double x + 1       # (double x) + 1
not isEmpty list   # not (isEmpty list)
```

Application is left-associative (for currying, if we add it later):
```klein
f a b              # (f a) b
```

**Key insight:** `parseApply()` loops, consuming arguments until it hits something that can't start an expression (operator, newline, closing bracket, etc.).

---

## Test Migration

Run after each change:
```bash
./gradlew :klein-lib:jvmTest
```

Files to update:
```
klein-lib/src/commonTest/kotlin/klein/parser/
├── FunctionTest.kt      # function definitions
├── LambdaTest.kt        # may have function calls
├── ...
```

---

## Rollout Order

1. Update `parseFunDef()` and `parseFunParams()` for `{}`
2. Update function definition tests
3. Verify: `./gradlew :klein-lib:jvmTest`
4. Update `parseApply()`, `parseFunctionCallOn()`, `parseArgs()` for `{}`
5. Update function call tests
6. Verify: `./gradlew :klein-lib:jvmTest`
7. Update CLI examples and documentation
