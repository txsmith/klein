# Klein Grammar

This document defines the formal grammar for Klein, covering both expressions and types.

Klein uses indentation-significant syntax. Braces `{}` are reserved for record literals only.

## Expression Grammar

```
prog        = (type_def | fun_def | stmt)*

type_def    = 'type' UPPER_IDENT type_params? '=' constructors

type_params = '<' TYPE_VAR (',' TYPE_VAR)* '>'

constructors = constructor ('|' constructor)*

constructor = UPPER_IDENT constructor_params?

constructor_params = '{' field_decl (',' field_decl)* '}'

field_decl  = IDENT ':' type

fun_def     = 'fun' IDENT '(' params? ')' (':' type)? '=' block_or_expr

stmt        = binding
            | expr

binding     = IDENT (':' type)? '=' block_or_expr
            | record_pattern '=' block_or_expr     # destructuring; must be irrefutable

block_or_expr = block
              | expr

block       = NEWLINE INDENT stmt+ DEDENT

lambda      = '|' (params '->')? block_or_expr '|'

params      = param (',' param)*

param       = IDENT (':' type)?

expr        = apply (binop apply)*

apply       = atom ( '(' args? ')' | '.' IDENT )*

atom        = INT
            | DOUBLE
            | STRING
            | BOOL
            | IDENT
            | unaryop atom
            | '(' expr (':' type)? ')'
            | lambda
            | if_expr
            | match_expr
            | implicit_param
            | record

if_expr     = 'if' expr 'then' block_or_expr ('else' block_or_expr)?

match_expr  = 'match' expr NEWLINE INDENT arm+ DEDENT

arm         = pattern ('if' expr)? '->' block_or_expr

pattern     = '_'                              # wildcard (lexes as IDENT "_")
            | literal                          # 42, -1, 2.5, "yes", true, null
            | IDENT                            # variable — binds the value
            | UPPER_IDENT (IDENT | record_pattern)?  # constructor: bare, binder (Dog d), or destructure
            | record_pattern                   # bare record destructure

record_pattern = '{' field_pat (',' field_pat)* ','? '}'

field_pat   = IDENT ('=' IDENT)?           # pun, or rename: { value = v }

implicit_param = '.' IDENT?

record      = '{' (field (',' field)* ','?)? '}'

field       = IDENT ':' type '=' expr       # annotated field
            | IDENT '=' expr               # field with value
            | IDENT                         # shorthand: { x } means { x = x }

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
| type_def       | `parseTypeDef()` (TODO) |
| type_params    | `parseTypeParams()` (TODO) |
| constructors   | `parseConstructors()` (TODO) |
| constructor    | `parseConstructor()` (TODO) |
| fun_def        | `parseFunDef()`       |
| stmt           | `parseStmt()`         |
| binding        | `parseBinding()`      |
| block_or_expr  | `parseBlockOrExpr()`  |
| block          | `parseBlock()`        |
| lambda         | `parseLambda()`       |
| params         | `parseLambdaParams()` / `parseFunParams()` |
| param          | `parseAnnotatedParam()` |
| expr           | `parseExpr()` / `parseExprAtPrecedence()` |
| apply          | `parseApply()`        |
| atom           | `parseAtom()`         |
| if_expr        | `parseIfThenElse()`   |
| match_expr     | `parseMatch()`        |
| arm            | `parseMatchArm()`     |
| pattern        | `parsePattern()`      |
| record_pattern | `parseRecordPattern()` |
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
IDENT       = (lower | '_') (letter | digit | '_')*
UPPER_IDENT = upper (letter | digit | '_')*
TYPE_VAR    = '\'' upper (letter | digit | '_')*

escape      = '\\' ('"' | '\\' | 'n' | 't')
digit       = '0'..'9'
letter      = 'a'..'z' | 'A'..'Z'
lower       = 'a'..'z'
upper       = 'A'..'Z'
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
  = 'type' TypeName TypeParams? '=' Constructors

TypeName
  = UpperIdent

TypeParams
  = '<' TypeVar % ',' '>'

TypeVar
  = '\'' UpperIdent               # 'A, 'B, 'T

Constructors
  = Constructor % '|'

Constructor
  = UpperIdent ConstructorParams?

ConstructorParams
  = '{' FieldDecl % ',' '}'       # Money { value: Num }, Some { value: 'A }
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
  = '<' Type % ',' '>'

TypeAtom
  = UpperIdent                    # concrete type: Num, String, Person
  | TypeVar                       # type variable: 'A, 'B, 'T
  | RecordType                    # structural record
  | TupleType                     # tuple
  | '(' Type ')'                  # parenthesized
```

> **Not part of the type system.** Anonymous union (`A | B`) and intersection
> (`A & B`) may still be parsed, but the checker **rejects** them as types — use a
> nominal `type` for "either" and (planned) bounded polymorphism for "both".
> `where`-clauses are a planned feature, not yet in the grammar. See
> [type-system.md](./type-system.md) and
> [decisions/2026-06-24-adopt-operation-bidi.md](./decisions/2026-06-24-adopt-operation-bidi.md).

### Record Types

```
RecordType
  = '{' RecordFields '}'   # at least one field; the empty record type `{}` is rejected — use `Any`

RecordFields
  = FieldDecl % ','

FieldDecl
  = LowerIdent ':' Type
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
  = [a-z_] [a-zA-Z0-9_]*          # name, age, a, t, _foo, _
```

## Type Grammar Examples

### Single-Constructor Type

```klein
type Money = Money { value: Num }
type Person = Person { name: String, age: Num }
```

```
TypeDef
├─ 'type'
├─ TypeName: "Money"
├─ TypeParams: (none)
├─ '='
└─ Constructors
   └─ Constructor
      ├─ UpperIdent: "Money"
      └─ ConstructorParams
         └─ FieldDecl: value: Num
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
└─ Constructors
   ├─ Constructor: "Red" (no params)
   ├─ Constructor: "Green" (no params)
   └─ Constructor: "Blue" (no params)
```

### Sum Type with Parameters

```klein
type Result<'T, 'E> = Ok { value: 'T } | Err { error: 'E }
```

```
TypeDef
├─ 'type'
├─ TypeName: "Result"
├─ TypeParams: <'T, 'E>
├─ '='
└─ Constructors
   ├─ Constructor
   │  ├─ UpperIdent: "Ok"
   │  └─ ConstructorParams
   │     └─ FieldDecl: value: 'T
   └─ Constructor
      ├─ UpperIdent: "Err"
      └─ ConstructorParams
         └─ FieldDecl: error: 'E
```

### Mixed Constructors

```klein
type Option<'A> = Some { value: 'A } | None
```

```
TypeDef
├─ 'type'
├─ TypeName: "Option"
├─ TypeParams: <'A>
├─ '='
└─ Constructors
   ├─ Constructor
   │  ├─ UpperIdent: "Some"
   │  └─ ConstructorParams
   │     └─ FieldDecl: value: 'A
   └─ Constructor: "None" (no params)
```

### Recursive Type

```klein
type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
```

```
TypeDef
├─ 'type'
├─ TypeName: "List"
├─ TypeParams: <'A>
├─ '='
└─ Constructors
   ├─ Constructor
   │  ├─ UpperIdent: "Cons"
   │  └─ ConstructorParams
   │     ├─ FieldDecl: head: 'A
   │     └─ FieldDecl: tail: List<'A>
   └─ Constructor: "Nil" (no params)
```

### Structural Record Type (in annotations)

```klein
{ name: String, age: Num }
```

```
RecordType
└─ RecordFields
   ├─ FieldDecl: name: String
   └─ FieldDecl: age: Num
```

### Function Types

```klein
Num -> Num
(Num, Num) -> Num
() -> Num
```

```
FunctionType (single param)
├─ ParamTypes: Num
├─ '->'
└─ FunctionType
   └─ AppliedType: Num

FunctionType (multiple params)
├─ ParamTypes: (Num, Num)
├─ '->'
└─ FunctionType
   └─ AppliedType: Num

FunctionType (zero params)
├─ ParamTypes: ()
├─ '->'
└─ FunctionType
   └─ AppliedType: Num
```

### Applied Type

```klein
Option<Num>
Result<String, Error>
List<'A>
```

```
AppliedType
├─ TypeAtom: "Option"
└─ TypeArgs
   └─ Type: Num

AppliedType
├─ TypeAtom: "Result"
└─ TypeArgs
   ├─ Type: String
   └─ Type: Error

AppliedType
├─ TypeAtom: "List"
└─ TypeArgs
   └─ Type: 'A (TypeVar)
```

### Tuple Type

```klein
(String, Num)
('A, 'B, 'C)
```

```
TupleType
├─ '('
├─ Type: String
├─ ','
├─ Type: Num
└─ ')'

TupleType
├─ '('
├─ Type: 'A
├─ ','
├─ Type: 'B
├─ ','
├─ Type: 'C
└─ ')'
```

## Type Grammar Disambiguation

### Parens: Tuple vs Grouping vs Function Params

- `(Type)` with single type → grouping/precedence
- `(Type, Type, ...)` with multiple types → tuple type OR multi-param function input
- `()` empty parens → zero-param function input

Context determines meaning:
- Before `->` → function parameter list: `(Num, Num) -> Num`
- Elsewhere → tuple type: `x: (Num, Num)`
