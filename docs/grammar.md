# Klein Grammar

This document defines the formal grammar for Klein, covering both expressions and types.

Klein uses indentation-significant syntax. Braces `{}` are reserved for record literals only.

## Expression Grammar

```
prog        = (fun_def | stmt)*

fun_def     = 'fun' IDENT '(' params? ')' '=' block_or_expr

stmt        = binding
            | expr

binding     = IDENT '=' block_or_expr

block_or_expr = block
              | expr

block       = NEWLINE INDENT stmt+ DEDENT

lambda      = '|' (params '->')? block_or_expr '|'

params      = IDENT (',' IDENT)*

expr        = apply (binop apply)*

apply       = atom ( '(' args? ')' | '.' IDENT )*

atom        = INT
            | DOUBLE
            | STRING
            | BOOL
            | IDENT
            | unaryop atom
            | '(' expr ')'
            | lambda
            | if_expr
            | implicit_param
            | record

if_expr     = 'if' expr 'then' block_or_expr ('else' block_or_expr)?

implicit_param = '.' IDENT?

record      = '{' (field (',' field)* ','?)? '}'

field       = IDENT ('=' expr)?

args        = expr (',' expr)*

unaryop     = '-' | 'not'

binop       = '+' | '-' | '*' | '/' | '%'
            | '==' | '!=' | '<' | '<=' | '>' | '>='
            | 'and' | 'or'
```

## Parser Method Mapping

| Grammar rule   | Parser method         |
|----------------|-----------------------|
| prog           | `parseProgram()`      |
| fun_def        | `parseFunDef()`       |
| stmt           | `parseStmt()`         |
| binding        | `parseBinding()`      |
| block_or_expr  | `parseBlockOrExpr()`  |
| block          | `parseBlock()`        |
| lambda         | `parseLambda()`       |
| params         | `parseLambdaParams()` / `parseFunParams()` |
| expr           | `parseExpr()` / `parseExprAtPrecedence()` |
| apply          | `parseApply()`        |
| atom           | `parseAtom()`         |
| if_expr        | `parseIfThenElse()`   |
| implicit_param | `parseImplicitParam()` |
| record         | `parseRecordLiteral()` |
| args           | `parseArgs()`         |

## Indentation Model

The lexer stamps each token with an `indent: Int?` field:
- `indent >= 0`: token is first on a new line at that column
- `indent == null`: token continues on the same line

The parser interprets indentation contextually:
- `block` starts when the next token has `indent > currentLineIndent`
- `block` ends when the next token has `indent < currentLineIndent` or is a closing delimiter

No synthetic `INDENT`/`DEDENT` tokens are emitted.

## Tokens

```
INT         = digit+
DOUBLE      = digit+ '.' digit+
STRING      = '"' (char | escape)* '"'
BOOL        = 'true' | 'false'
IDENT       = (letter | '_') (letter | digit | '_')*

escape      = '\\' ('"' | '\\' | 'n' | 't')
digit       = '0'..'9'
letter      = 'a'..'z' | 'A'..'Z'
```

## Operator Precedence

From lowest to highest:

| Precedence | Operators       | Associativity |
|------------|-----------------|---------------|
| 1          | `or`            | left          |
| 2          | `and`           | left          |
| 3          | `==` `!=`       | left          |
| 4          | `<` `<=` `>` `>=` | left        |
| 5          | `+` `-`         | left          |
| 6          | `*` `/` `%`     | left          |
| 7          | `-` `not` (unary) | prefix      |

Parentheses `( )` override precedence.

## Indentation Rules

1. **Spaces only** — tabs are a lexer error
2. **Block starters** — `=`, `->`, `then`, `else` followed by increased indent start a block
3. **Closing delimiters** — `|`, `)`, `}`, `]` end the current expression regardless of indent
4. **Braces** — reserved for record literals, not blocks

## Comments

Comments start with `#` and extend to end of line:

```
comment     = '#' (any char except newline)* newline
```

---

## Type Grammar

This section defines Klein's type syntax.

### Notation

```
x        literal "x"
X        non-terminal X
X?       zero or one X
X*       zero or more X
X+       one or more X
X % sep  one or more X separated by sep
|        alternation
( )      grouping
```

### Top-Level Type Definitions

```
TypeDef
  = 'type' TypeName TypeParams? '=' TypeBody

TypeName
  = UpperIdent

TypeParams
  = '(' LowerIdent % ',' ')'

TypeBody
  = RecordType                    # single-constructor: type Person = { ... }
  | PrimitiveOrTypeName           # wrapper: type Money = Double
  | Constructors                  # sum type: type Color = Red | Green | Blue
```

### Constructors (Sum Types)

```
Constructors
  = Constructor % '|'

Constructor
  = UpperIdent RecordType?        # no ticks: Ok { value: t }
```

### Types (used in annotations, fields, etc.)

```
Type
  = FunctionType

FunctionType
  = ParamTypes '->' FunctionType
  | AppliedType

ParamTypes
  = AppliedType                   # single param: Int -> Int
  | '(' Type % ',' ')'            # multiple params: (Int, Int) -> Int
  | '(' ')'                       # zero params: () -> Int

AppliedType
  = TypeAtom TypeArgs?

TypeArgs
  = '(' Type % ',' ')'

TypeAtom
  = UpperIdent                    # concrete type: Int, String, Person
  | LowerIdent                    # type variable: a, b, t
  | RecordType                    # structural record
  | TupleType                     # tuple
  | '(' Type ')'                  # parenthesized
```

### Record Types

```
RecordType
  = '{' RecordFields? '}'

RecordFields
  = RecordField % ','

RecordField
  = FieldDecl                     # name: String
  | RowVariable                   # ...r or ...
  | SpreadType                    # ...Person

FieldDecl
  = LowerIdent ':' Type

RowVariable
  = '...' LowerIdent?             # ...r (named) or ... (anonymous)

SpreadType
  = '...' UpperIdent              # ...Person, ...Named
```

### Tuple Types

```
TupleType
  = '(' Type ',' Type (',' Type)* ')'   # at least two elements
```

Note: Single-element parens `(Type)` are just grouping, not a tuple. Tuples require at least two elements.

### Identifiers

```
UpperIdent
  = [A-Z] [a-zA-Z0-9_]*           # Person, Int, Ok

LowerIdent
  = [a-z] [a-zA-Z0-9_]*           # name, age, a, t
```

## Type Grammar Examples

### Single-Constructor Type

```klein
type Person = { name: String, age: Int }
```

```
TypeDef
├─ 'type'
├─ TypeName: "Person"
├─ TypeParams: (none)
├─ '='
└─ TypeBody: RecordType
   └─ RecordFields
      ├─ FieldDecl: name: String
      └─ FieldDecl: age: Int
```

### Wrapper Type

```klein
type Money = Double
```

```
TypeDef
├─ 'type'
├─ TypeName: "Money"
├─ '='
└─ TypeBody: PrimitiveOrTypeName
   └─ "Double"
```

### Sum Type

```klein
type Result(t, e) = Ok { value: t } | Err { error: e }
```

```
TypeDef
├─ 'type'
├─ TypeName: "Result"
├─ TypeParams: (t, e)
├─ '='
└─ TypeBody: Constructors
   ├─ Constructor
   │  ├─ UpperIdent: "Ok"
   │  └─ RecordType: { value: t }
   └─ Constructor
      ├─ UpperIdent: "Err"
      └─ RecordType: { error: e }
```

### Bare Constructors

```klein
type Color = Red | Green | Blue
```

```
TypeDef
├─ 'type'
├─ TypeName: "Color"
├─ '='
└─ TypeBody: Constructors
   ├─ Constructor: "Red" (no payload)
   ├─ Constructor: "Green" (no payload)
   └─ Constructor: "Blue" (no payload)
```

### Record with Row Variable

```klein
{ name: String, age: Int, ...r }
```

```
RecordType
└─ RecordFields
   ├─ FieldDecl: name: String
   ├─ FieldDecl: age: Int
   └─ RowVariable: r
```

### Record Intersection (Spread)

```klein
type Person = { ...Named, ...Aged, title: String }
```

```
TypeDef
├─ 'type'
├─ TypeName: "Person"
├─ '='
└─ TypeBody: RecordType
   └─ RecordFields
      ├─ SpreadType: ...Named
      ├─ SpreadType: ...Aged
      └─ FieldDecl: title: String
```

### Function Types

```klein
Int -> Int
(Int, Int) -> Int
() -> Int
```

```
FunctionType (single param)
├─ ParamTypes: Int
├─ '->'
└─ FunctionType
   └─ AppliedType: Int

FunctionType (multiple params)
├─ ParamTypes: (Int, Int)
├─ '->'
└─ FunctionType
   └─ AppliedType: Int

FunctionType (zero params)
├─ ParamTypes: ()
├─ '->'
└─ FunctionType
   └─ AppliedType: Int
```

### Applied Type

```klein
Option(Int)
Result(String, Error)
List(a)
```

```
AppliedType
├─ TypeAtom: "Option"
└─ TypeArgs
   └─ Type: Int

AppliedType
├─ TypeAtom: "Result"
└─ TypeArgs
   ├─ Type: String
   └─ Type: Error

AppliedType
├─ TypeAtom: "List"
└─ TypeArgs
   └─ Type: a (LowerIdent, type variable)
```

### Tuple Type

```klein
(String, Int)
(a, b, c)
```

```
TupleType
├─ '('
├─ Type: String
├─ ','
├─ Type: Int
└─ ')'

TupleType
├─ '('
├─ Type: a
├─ ','
├─ Type: b
├─ ','
├─ Type: c
└─ ')'
```

## Type Grammar Disambiguation

### TypeBody Variants

When parsing `TypeBody` after `=`:

1. Starts with `{` → `RecordType` (single-constructor)
2. Contains `|` → `Constructors` (sum type)
3. Otherwise → `PrimitiveOrTypeName` (wrapper)

### Row Variable vs Spread

Both use `...` prefix but are distinguished by what follows:

| Syntax | What follows | Meaning |
|--------|--------------|---------|
| `...r` | LowerIdent | Row variable (captures extra fields) |
| `...` | nothing | Anonymous row variable |
| `...Person` | UpperIdent | Spread (expands type's fields) |

The case of the identifier disambiguates. After `...`:
- Lowercase or nothing → row variable
- Uppercase → spread

### Parens: Tuple vs Grouping vs Function Params

- `(Type)` with single type → grouping/precedence
- `(Type, Type, ...)` with multiple types → tuple type OR multi-param function input
- `()` empty parens → zero-param function input

Context determines meaning:
- Before `->` → function parameter list: `(Int, Int) -> Int`
- Elsewhere → tuple type: `x: (Int, Int)`
