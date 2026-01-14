# Double-Quote String Literals

**Date:** 2026-01-14

## Context

Klein needed to choose a delimiter for string literals. The initial implementation used single quotes (`'hello'`), which is common in some languages like Python and JavaScript. However, Klein's type system uses type variables that need a distinctive syntax.

Three options for type variable syntax were considered:

1. **Lowercase alphanumeric** (like Haskell/OCaml): `a`, `b`, `list`, `result`
   - **Problem:** Visually conflicts with record field names in type annotations
   - Example: `{ result: result }` - Is this a field of type `result` or a type variable?

2. **Uppercase with tick prefix** (like MLscript): `'A`, `'B`, `'List`, `'Result`
   - Maintains "types start with capital" convention
   - Single quote prefix clearly distinguishes type variables from nominal types
   - No visual ambiguity with field names

3. **Keep single quotes for strings, use other syntax for type variables**
   - Would require more complex type variable syntax (e.g., `<T>`, `$T`, etc.)

## Decision

**Use double quotes for string literals and reserve single quotes for type variable prefixes.**

### String Literals

```klein
greeting = "Hello, world!"
name = "Alice"
message = "Error: ${code}"
escaped = "She said \"hello\""
```

Escape sequences: `\"`, `\\`, `\n`, `\t`

### Type Variables

```klein
# Generic function types
fun map(f: 'A -> 'B, xs: List('A)): List('B) = ...
fun filter(pred: 'T -> Bool, xs: List('T)): List('T) = ...

# Type annotations
id: 'A -> 'A
first: ('A, 'B) -> 'A
```

## Rationale

### Visual Clarity in Record Types

The uppercase-with-tick syntax eliminates ambiguity in complex type annotations:

```klein
# Clear distinction between field names and type variables
user: { name: String, data: 'T }
result: { status: Status, value: 'A }

# Compare with lowercase type variables (confusing):
user: { name: String, data: t }  # Is 't' a field or type variable?
result: { status: Status, value: a }
```

### Consistency with Type Capitalization

Klein's convention is that types start with capital letters:
- Nominal types: `String`, `Int`, `Customer`, `List`
- Type variables: `'A`, `'B`, `'T` (capital after the tick)
- Record field names and identifiers: `name`, `age`, `calculate` (lowercase)

This creates a clear visual hierarchy where anything capitalized is type-related.

### Familiar String Syntax

Double quotes for strings are common in major languages:
- Java, C, C++, C#: `"hello"`
- Rust, Go: `"hello"`
- JSON: `"hello"`

Single quotes in JavaScript/Python serve double duty for both strings and character literals, which Klein doesn't need.

### Follows MLscript Precedent

MLscript (a research language exploring type system innovations) uses the same convention:
- Strings: `"text"`
- Type variables: `'a`, `'b`

This makes Klein more familiar to users coming from that ecosystem.

## Consequences

### Positive

- Clear visual distinction between type variables and record fields
- Familiar string literal syntax for most developers
- Type variables are immediately recognizable
- Supports the "types start with capital" convention
- Aligns with modern language trends (double-quote strings)

### Negative

- Breaking change for existing Klein code (requires migration)
- Developers from Python/JavaScript might expect single quotes to work
- Type variable syntax may be unfamiliar to developers from C-family languages (which use `<T>`)

## Alternatives Considered

### Single quotes for strings, other syntax for type variables

- `<T>` syntax: Conflicts with less-than operator, requires lookahead parsing
- `$T` syntax: Unconventional, looks like string interpolation
- `@T` syntax: Unconventional, looks like decorators/annotations
- `#T` syntax: Unconventional, looks like comments

None of these felt as natural as the tick prefix used in ML family languages.

### Both single and double quotes for strings

Some languages (JavaScript, Python) allow both. This was rejected because:
- Klein values simplicity and consistency
- Only one string literal syntax is needed
- Having two ways increases cognitive load for no clear benefit

### Lowercase type variables despite ambiguity

Could rely on context to distinguish `{ data: t }` (field vs type variable). This was rejected because:
- Hurts readability, especially for newcomers
- Makes type signatures harder to parse visually
- Forces readers to maintain more context while reading code
- Klein prioritizes clarity and readability
