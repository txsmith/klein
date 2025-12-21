# Klein Grammar

This document defines the formal grammar for Klein expressions.

Klein uses indentation-significant syntax. Braces `{}` are reserved for record literals only.

```
prog        = (fun_def | stmt)*

fun_def     = 'fun' IDENT '(' params? ')' '=' block

stmt        = binding
            | expr

binding     = IDENT '=' block

block       = INDENT stmt* expr DEDENT
            | expr

lambda      = '|' (params '->')? block '|'

params      = IDENT (',' IDENT)*

expr        = call_expr (binop call_expr)*

call_expr   = atom ( '(' args? ')' )*

atom        = literal
            | IDENT
            | unaryop atom
            | '(' expr ')'
            | lambda
            | if_expr

if_expr     = 'if' expr 'then' block ('else' block)?

args        = expr (',' expr)*

literal     = INT | DOUBLE | STRING | BOOL

unaryop     = '-' | 'not'

binop       = '+' | '-' | '*' | '/' | '%'
            | '==' | '!=' | '<' | '<=' | '>' | '>='
            | 'and' | 'or'
```

## Virtual Tokens

The lexer emits these virtual tokens based on indentation:

```
INDENT      = (emitted when indentation increases after block starter)
DEDENT      = (emitted when indentation decreases)
STATEMENT_END = (emitted at newlines in statement context)
```

**Block starters**: `=`, `->`, `|`, `then`, `else` at end of line trigger INDENT on next line if indented.

**Auto-DEDENT**: DEDENT is automatically inserted before closing tokens `)`, `]`, `|`.

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

1. **Tabs or spaces, not mixed** — pick one per file, mixing is an error
2. **Block starters** — `=`, `->`, `|` at end of line start an indented block
3. **Inside parens/brackets** — indentation is ignored
4. **Braces** — reserved for record literals, not blocks
5. **Alignment** — DEDENT must align with a previous indentation level

## Comments

Comments are handled in the lexer and therefore not present in the grammar

```
comment     = '#' (any char except newline)* newline
```
