# Klein Type Grammar

A formal grammar for Klein's type syntax.

## Notation

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

## Grammar

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
  = AppliedType '->' FunctionType
  | AppliedType

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

## Examples Parsed

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

### Function Type

```klein
{ x: Int, y: Int } -> Int
```

```
FunctionType
├─ AppliedType: RecordType { x: Int, y: Int }
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

## Disambiguation

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

### Parens: Tuple vs Grouping

- `(Type)` with single type → grouping/precedence
- `(Type, Type, ...)` with multiple types → tuple type
