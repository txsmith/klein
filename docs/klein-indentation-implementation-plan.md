# Klein Indentation: Implementation Plan

Klein uses significant indentation to delimit blocks, following Scala 3's approach. Braces `{}` are reserved exclusively for record literals.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Braces | Records only, not blocks | Cleaner syntax, no ambiguity |
| Block delimiters | INDENT/DEDENT tokens | Whitespace-significant like Python/Scala 3 |
| Lambda closing `\|` | Required | Keeps thunks like `\|expr\|` clean |
| Bindings | Statement contexts only | `f(x = 1)` invalid, `if c then x = 1` valid |
| If/else branches | Statement contexts (blocks) | Can contain bindings |
| Complexity | Keep in lexer | Smart INDENT/DEDENT emission |
| `do` keyword | Not needed | `=` at EOL already starts blocks |
| Tabs | Tabs OR spaces, not mixed | Pick one per file |

## Current Language Scope

The language currently supports:
- Blocks (sequence of statements, last expression is the result)
- Bindings (`x = expr`)
- Lambdas (`|x -> expr|`)
- Function application (`f(x, y)`)
- Arithmetic expressions

## Core Syntax

### Block-Starting Tokens

These tokens, when followed by a newline, start an indented block:

| Token | Example |
|-------|---------|
| `=` | `x =` (binding), `fun foo() =` |
| `->` | `\|x ->` (lambda body) |
| `\|` | `\|` (lambda with block body, no params) |

### Example Code

```klein
# Function with block body
fun calculate(x, y) =
  temp = x * 2
  temp + y

# Single-line function
fun double(x) = x * 2

# Binding with block body
result =
  temp = y * 2
  temp + 1

# Lambda with block body (has params)
transformer = |x ->
  temp = x * 2
  temp + 1
|

# Lambda with block body (no params, thunk)
deferred = |
  x = expensiveComputation()
  x + 1
|

# Single-line thunk
thunk = |expensiveExpr|

# Nested blocks
outer =
  inner =
    x = 1
    x + 1
  inner * 2
```

## Lexer Implementation

### Additional Token Types

```kotlin
sealed class Token {
    // ... existing tokens ...
    data class Indent(override val span: SourceSpan) : Token()
    data class Dedent(override val span: SourceSpan) : Token()
    // Note: existing StatementEnd becomes Newline conceptually
}
```

### State

The lexer maintains:

```kotlin
class Lexer(private val source: String) {
    private val indentStack = mutableListOf(0)  // Start at column 0
    private var bracketDepth = 0                 // Track () [] nesting (not {} - those are records)
    private var pendingDedents = 0               // DEDENT queue
    private var atLineStart = true
    private var lastTokenWasBlockStarter = false // Track =, ->, | at EOL
    private var indentChar: Char? = null         // ' ' or '\t', set by first indented line
}
```

### Block Starters

The lexer tracks when the last emitted token was a potential block starter:
- `=` (binding or function definition)
- `->` (lambda arrow)
- `|` (lambda open, when not immediately closed)

### Algorithm

Following Scala 3's approach, with smart block-starter tracking:

```kotlin
fun nextToken(): Token {
    // 1. Emit pending DEDENTs first
    if (pendingDedents > 0) {
        pendingDedents--
        return Token.Dedent(...)
    }

    // 2. Handle indentation at line start (only outside brackets)
    if (atLineStart && bracketDepth == 0) {
        val indent = measureIndentation()
        atLineStart = false

        val currentIndent = indentStack.last()

        if (indent > currentIndent && lastTokenWasBlockStarter) {
            // Deeper indentation after block starter: emit INDENT
            indentStack.add(indent)
            lastTokenWasBlockStarter = false
            return Token.Indent(...)
        }
        else if (indent < currentIndent) {
            // Shallower: close one or more blocks
            while (indentStack.last() > indent) {
                indentStack.removeLast()
                pendingDedents++
            }
            if (indentStack.last() != indent) {
                error("Indentation doesn't match any outer block")
            }
            pendingDedents--
            return Token.Dedent(...)
        }
        // Same level after block starter without indent = single-line form
        lastTokenWasBlockStarter = false
    }

    // 3. Handle newlines
    if (currentChar == '\n') {
        advance()
        if (bracketDepth == 0) {
            atLineStart = true
            // Only emit StatementEnd if not after block starter
            // (block starter + newline waits for indentation check)
            if (!lastTokenWasBlockStarter && canStatementEndHere()) {
                return Token.StatementEnd(...)
            }
        }
        // Inside brackets or after block starter: skip newline
        return nextToken()
    }

    // 4. Track bracket depth (parens and brackets only, not braces)
    when (currentChar) {
        '(', '[' -> bracketDepth++
        ')', ']' -> bracketDepth--
    }

    // 5. Lex regular token and track block starters
    val token = lexRegularToken()
    lastTokenWasBlockStarter = isBlockStarter(token)
    return token
}

private fun isBlockStarter(token: Token): Boolean = when {
    token is Token.Symbol && token.text == "=" -> true
    token is Token.Symbol && token.text == "->" -> true
    token is Token.Symbol && token.text == "|" -> true  // opening pipe
    else -> false
}

private fun measureIndentation(): Int {
    var col = 0
    while (currentChar == ' ' || currentChar == '\t') {
        val char = currentChar
        if (indentChar == null) {
            indentChar = char  // First indent char sets the style for this file
        } else if (char != indentChar) {
            error("Cannot mix tabs and spaces for indentation")
        }
        col++
        advance()
    }
    return col
}
```

### Automatic DEDENT Before Closing Tokens (Scala 3 Rule)

Scala 3 automatically inserts `<outdent>` when it sees tokens that close an indentation region. For Klein:

```kotlin
// Tokens that close an indentation region
val closingTokens = setOf(')', ']', '|')  // Note: not braces, those are records

fun nextToken(): Token {
    if (pendingDedents > 0) {
        // Peek at next real token
        val upcoming = peekNextRealToken()

        // Auto-close indentation before closing tokens
        if (upcoming.type in closingTokens) {
            pendingDedents--
            return Token.Dedent(...)
        }

        pendingDedents--
        return Token.Dedent(...)
    }
    // ... rest of lexer
}
```

This handles cases like:

```klein
transformer = |x ->
  temp = x * 2
  temp + 1
|  # <-- DEDENT auto-inserted before closing |

# Also works for thunks with block bodies
deferred = |
  x = 3
  x + 1
|  # <-- DEDENT auto-inserted here too
```

## Parser Implementation

The parser is simplified since the lexer handles INDENT/DEDENT emission.

### Block Parsing

```kotlin
fun parseBlock(): Expr {
    return if (match(INDENT)) {
        val stmts = mutableListOf<Stmt>()

        while (!check(DEDENT) && !isAtEnd()) {
            stmts.add(parseStmt())
            while (match(STATEMENT_END)) { }  // consume separators
        }

        expect(DEDENT)
        Block(stmts.dropLast(1), stmts.last() as Expr, ...)
    } else {
        // Single expression, no block
        parseExpr()
    }
}
```

### Binding

```klein
x = 1           # single expression
x =             # block body (= at EOL triggers INDENT)
  a = 1
  a + 1
```

```kotlin
fun parseBinding(): Stmt {
    val name = expectIdentifier()
    expect(EQUALS)
    val value = parseBlock()  // handles both INDENT and single expr
    return Val(name, value, ...)
}
```

### Lambda

All lambda forms:

```klein
|x -> x + 1|              # single expression with params
|expr|                    # thunk (no params, single expression)
|x ->                     # block body with params
  temp = x * 2
  temp + 1
|
|                         # block body, no params (thunk)
  x = expensiveComputation()
  x + 1
|
```

```kotlin
fun parseLambda(): Expr {
    expect(PIPE)

    // Check for params (look for -> or INDENT)
    val params = parseParamsIfPresent()

    // Body is either single expr or block
    val body = parseBlock()

    expect(PIPE)
    return Lambda(params, body, ...)
}
```

## Grammar Summary

```
program     = stmt*

stmt        = binding | expr

binding     = IDENT '=' block

block       = INDENT stmt* expr DEDENT
            | expr

lambda      = '|' (params '->')? block '|'

params      = IDENT (',' IDENT)*

expr        = apply (binop apply)*

apply       = atom ('(' args ')')*

atom        = IDENT | NUMBER | STRING | BOOL
            | '(' expr ')'
            | lambda
            | unaryop atom

args        = expr (',' expr)*
```

## Key Rules

1. **INDENT/DEDENT** are virtual tokens inserted by the lexer
2. **Block starters**: `=`, `->`, `|` at end of line
3. **Inside parens/brackets**: indentation is ignored, no INDENT/DEDENT
4. **Braces**: reserved for record literals only, not blocks
5. **Closing tokens**: DEDENT auto-inserted before `)`, `]`, `|`, EOF
6. **Tabs**: rejected, spaces only
7. **Alignment**: DEDENT must align with a previous indentation level
8. **Bindings**: only valid in statement contexts, not as function arguments
