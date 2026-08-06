# Klein Grammar

This document defines the formal grammar for Klein, covering both expressions and types.

Klein uses indentation-significant syntax. Braces `{}` are reserved for record literals only.

## Expression Grammar

```
prog        = (type_def | fun_def | fun_decl | stmt)*

type_def    = 'type' UPPER_IDENT revision? type_params? '=' constructors

revision    = '/' INT                              # positive; absent means 1. Contracts only

type_params = '<' TYPE_VAR (',' TYPE_VAR)* '>'

constructors = constructor ('|' constructor)*

constructor = UPPER_IDENT constructor_params?

constructor_params = '{' field_decl (',' field_decl)* '}'

field_decl  = IDENT ':' type

fun_def     = 'fun' IDENT '(' params? ')' (':' type)? '=' block_or_expr

fun_decl    = 'fun' IDENT revision? '(' params? ')' ':' type 'review'?   # declared, not defined — no body

stmt        = binding
            | val_decl
            | expr

binding     = IDENT (':' type)? '=' block_or_expr
            | record_pattern '=' block_or_expr     # destructuring; must be irrefutable

val_decl    = IDENT revision? ':' type 'review'?   # declared, not defined — no value

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
| revision       | `parseRevisionSuffix()` |
| type_params    | `parseTypeParams()` (TODO) |
| constructors   | `parseConstructors()` (TODO) |
| constructor    | `parseConstructor()` (TODO) |
| fun_def        | `parseFunDef()`       |
| fun_decl       | `parseFunDef()`       |
| stmt           | `parseStmt()`         |
| binding        | `parseBinding()`      |
| val_decl       | `parseBinding()`      |
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

## Declarations Without Definitions

`fun_decl` and `val_decl` are the definition forms with the definition removed. These are interface definitions used for interaction between Klein and the host language:

```klein
type Customer = Customer { id: Num, name: String, score: Num }

fun creditCheck(c: Customer): Num
maxRetries: Num
```

They parse into `FunDecl` and `ValDecl` — distinct nodes, not a `FunDef`/`Val` with a null body.

The annotation is what tells a declaration apart from a definition. After the parameter list, `fun`
with `: type` and no `=` is a `fun_decl`, while `fun mystery()` with neither is still the parse
error "Expected '='". A `val_decl` likewise requires its annotation — `IDENT` alone is an
expression, not a declaration.

Neither form consumes anything past its type: a bodiless declaration never absorbs the following
line, even when that line opens an indented block.

The parser accepts both wherever a statement is legal, and has no idea whether it is reading a
program or a contract. Which statements are legal in which — and what a contract is for — is
checker semantics: see [spec/contracts.md](./spec/contracts.md).

## Revisions

A `/N` suffix on a declared name, so two incompatible versions of a capability or a type coexist in
one contract file while the old one drains:

```klein
type Customer = Customer { id: Num }
type Customer/2 = Customer { id: Num, tier: String }

fun creditScore(c: Customer): Num
fun creditScore/2(c: Customer/2): Num

maxRetries: Num
maxRetries/2: Num
```

`N` is a positive integer literal. **Absent means revision 1**, so `Customer` and `Customer/1` are
the same name, and declaring both is a duplicate. The suffix appears in exactly two places:

- on the declared name of a `type_def`, `fun_decl` or `val_decl` — never on a definition
  (`fun f/2(): Num = 1` and `x/2 = 1` are parse errors, as is a revision below 1);
- on a **type reference**, anywhere a type name may be written: parameter and return types,
  constructor field types, record and function types, and type arguments (`List<Customer/2>`).
  A revised type may still be applied and made optional: `Box/2<Num>`, `Customer/2?`.

A revision never appears in an expression — rules do not write them, only contracts do. `Customer`
and `Customer/2` are unrelated nominal types; nothing is inherited between revisions.

### `/` versus division

No new token: a revision reuses `SLASH` and is recognized by position. Nothing else in the grammar
can put a `/` where a revision goes, because a type is never an operand of an arithmetic operator
and a declared name is never an expression. The one lookahead the parser needs is for `val_decl`,
where a statement may begin with either form: `IDENT '/' INT` followed by `:` or `=` starts a
declaration, and anything else is the division it has always been (`total / 2` is unchanged).

## The `review` Marker

A trailing `review` on a `fun_decl` or `val_decl`, after the type and on the same line:

```klein
fun underwrite/2(a: Application): Decision review
maxRetries/2: Num review
```

It records that the *meaning* changed while the types did not — the one thing a signature
comparison cannot detect (see [ideas/host-integration.md](./ideas/host-integration.md)). It is a
**contextual keyword**: `review` remains an ordinary identifier everywhere else — a binding name, a
function name, a parameter, a field, even a declared capability (`review: Num`). Only the position
immediately after a declaration's type, on that declaration's own line, reads as the marker; a
`review` on the following line is an expression statement. It is rejected on a definition
(`x: Num review = 3`).

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
  = 'type' TypeName Revision? TypeParams? '=' Constructors

TypeName
  = UpperIdent

Revision
  = '/' INT                       # positive integer; absent means 1

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
  = UpperIdent Revision?          # concrete type: Num, String, Person, Customer/2
  | TypeVar                       # type variable: 'A, 'B, 'T
  | RecordType                    # structural record
  | TupleType                     # tuple
  | '(' Type ')'                  # parenthesized
```

> `where`-clauses are a planned feature, not yet in the grammar.

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
