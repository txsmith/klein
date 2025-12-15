# Klein Grammar

This document defines the formal grammar for Klein expressions.

```
prog        = stmt*

block       = '{' stmt* '}'

stmt        = IDENT '=' ( block | expr )
            | 'fun' IDENT '(' params? ')' '=' ( block | expr )
            | expr

lambda      = '|' (params? '->')? stmt* expr '|'

params      = IDENT (',' IDENT)*

expr        = call_expr (binop call_expr)*

call_expr   = atom ( '(' args? ')' )*

atom        = literal
            | IDENT
            | unaryop atom
            | '(' expr ')'
            | lambda

args        = expr (',' expr)*

literal     = INT | DOUBLE | STRING | BOOL

unaryop     = '-' | 'not'

binop       = '+' | '-' | '*' | '/' | '%'
            | '==' | '!=' | '<' | '<=' | '>' | '>='
            | 'and' | 'or'
```

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


## Comments

Comments are handled in the lexer and therefore not present in the grammar

```
comment     = '#' (any char except newline)* newline
```
