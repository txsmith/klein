; Keywords
[
  "fun"
  "if"
  "then"
  "else"
] @keyword

; Boolean operators as keywords
[
  "and"
  "or"
  "not"
] @keyword.operator

; Literals
(integer) @number
(double) @number.float
(string) @string
(escape_sequence) @string.escape
(boolean) @boolean

; Comments
(comment) @comment

; Identifiers
(identifier) @variable

; Function definitions
(function_definition
  name: (identifier) @function.definition)

(function_definition
  parameters: (parameter_list (identifier) @variable.parameter))

; Lambda parameters
(lambda
  parameters: (lambda_params (identifier) @variable.parameter))

; Function calls
(call_expression
  function: (identifier) @function.call)

; Field access
(field_access
  field: (identifier) @property)

; Implicit parameter
(implicit_param) @variable.builtin
(implicit_param
  field: (identifier) @property)

; Record fields
(record_field
  name: (identifier) @property)

; Val bindings (left-hand side)
(val_binding
  name: (identifier) @variable.definition)

; Operators
[
  "+"
  "-"
  "*"
  "/"
  "%"
  "=="
  "!="
  "<"
  "<="
  ">"
  ">="
] @operator

; Assignment and arrow
[
  "="
  "->"
] @punctuation.special

; Delimiters
[
  "("
  ")"
  "{"
  "}"
  "|"
] @punctuation.bracket

[
  ","
] @punctuation.delimiter

; Dot (field access)
"." @punctuation.delimiter
