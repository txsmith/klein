module.exports = grammar({
  name: 'klein',

  externals: $ => [
    $._indent,
    $._dedent,
    $._newline,
  ],

  extras: $ => [/\s/, $.comment],

  word: $ => $.identifier,

  conflicts: $ => [
    [$.block, $._simple_expression],
  ],

  rules: {
    source_file: $ => repeat($._statement),

    _statement: $ => seq(
      choice(
        $.function_definition,
        $.val_binding,
        $._expression,
      ),
      optional($._newline),
    ),

    function_definition: $ => seq(
      'fun',
      field('name', $.identifier),
      '(',
      field('parameters', optional($.parameter_list)),
      ')',
      '=',
      field('body', $._block_or_expr),
    ),

    parameter_list: $ => seq(
      $.identifier,
      repeat(seq(',', $.identifier)),
    ),

    val_binding: $ => seq(
      field('name', $.identifier),
      '=',
      field('value', $._block_or_expr),
    ),

    _block_or_expr: $ => choice(
      $.block,
      $._expression,
    ),

    block: $ => seq(
      $._indent,
      repeat1($._statement),
      $._dedent,
    ),

    _expression: $ => choice(
      $.if_expression,
      $.lambda,
      $.binary_expression,
      $.unary_expression,
      $._simple_expression,
    ),

    _simple_expression: $ => choice(
      $.call_expression,
      $.field_access,
      $.record_literal,
      $.parenthesized_expression,
      $.implicit_param,
      $._literal,
      $.identifier,
    ),

    if_expression: $ => prec.right(seq(
      'if',
      field('condition', $._expression),
      'then',
      field('then', $._block_or_expr),
      optional(seq('else', field('else', $._block_or_expr))),
    )),

    lambda: $ => seq(
      '|',
      optional(seq(
        field('parameters', $.lambda_params),
        '->',
      )),
      field('body', $._block_or_expr),
      '|',
    ),

    lambda_params: $ => seq(
      $.identifier,
      repeat(seq(',', $.identifier)),
    ),

    binary_expression: $ => choice(
      prec.left(1, seq($._expression, 'or', $._expression)),
      prec.left(2, seq($._expression, 'and', $._expression)),
      prec.left(3, seq($._expression, choice('==', '!='), $._expression)),
      prec.left(4, seq($._expression, choice('<', '<=', '>', '>='), $._expression)),
      prec.left(5, seq($._expression, choice('+', '-'), $._expression)),
      prec.left(6, seq($._expression, choice('*', '/', '%'), $._expression)),
    ),

    unary_expression: $ => choice(
      prec(7, seq('not', $._expression)),
      prec(7, seq('-', $._expression)),
    ),

    call_expression: $ => prec(8, seq(
      field('function', $._simple_expression),
      '(',
      field('arguments', optional($.argument_list)),
      ')',
    )),

    argument_list: $ => seq(
      $._expression,
      repeat(seq(',', $._expression)),
    ),

    field_access: $ => prec(9, seq(
      field('target', $._simple_expression),
      '.',
      field('field', $.identifier),
    )),

    record_literal: $ => seq(
      '{',
      optional($.record_fields),
      '}',
    ),

    record_fields: $ => seq(
      $.record_field,
      repeat(seq(',', $.record_field)),
      optional(','),
    ),

    record_field: $ => choice(
      seq(field('name', $.identifier), '=', field('value', $._expression)),
      field('name', $.identifier),
    ),

    parenthesized_expression: $ => seq('(', $._expression, ')'),

    implicit_param: $ => prec.right(10, seq(
      '.',
      optional(field('field', $.identifier)),
    )),

    _literal: $ => choice(
      $.integer,
      $.double,
      $.string,
      $.boolean,
    ),

    integer: $ => /\d+/,

    double: $ => /\d+\.\d+/,

    string: $ => seq(
      '"',
      repeat(choice(
        /[^"\\]+/,
        $.escape_sequence,
      )),
      '"',
    ),

    escape_sequence: $ => /\\[nrt"\\]/,

    boolean: $ => choice('true', 'false'),

    identifier: $ => /[a-zA-Z_][a-zA-Z0-9_]*/,

    comment: $ => seq('#', /[^\n]*/),
  },
});
