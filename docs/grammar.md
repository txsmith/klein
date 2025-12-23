# Klein Grammar

This document defines the formal grammar for Klein expressions.

Klein uses indentation-significant syntax. Braces `{}` are reserved for record literals only.

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
STRING      = '\'' (char | escape)* '\''
BOOL        = 'true' | 'false'
IDENT       = (letter | '_') (letter | digit | '_')*

escape      = '\\' ('\'' | '\\' | 'n' | 't')
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
