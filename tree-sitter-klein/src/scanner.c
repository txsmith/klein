#include "tree_sitter/parser.h"
#include <string.h>
#include <stdlib.h>

enum TokenType {
  INDENT,
  DEDENT,
  NEWLINE,
};

#define MAX_INDENT_DEPTH 100

typedef struct {
  uint16_t indent_stack[MAX_INDENT_DEPTH];
  uint8_t stack_size;
  uint8_t pending_dedents;
} Scanner;

static void advance(TSLexer *lexer) {
  lexer->advance(lexer, false);
}

static void skip(TSLexer *lexer) {
  lexer->advance(lexer, true);
}

void *tree_sitter_klein_external_scanner_create() {
  Scanner *scanner = malloc(sizeof(Scanner));
  scanner->stack_size = 1;
  scanner->indent_stack[0] = 0;
  scanner->pending_dedents = 0;
  return scanner;
}

void tree_sitter_klein_external_scanner_destroy(void *payload) {
  free(payload);
}

unsigned tree_sitter_klein_external_scanner_serialize(void *payload, char *buffer) {
  Scanner *scanner = (Scanner *)payload;
  size_t size = 0;

  buffer[size++] = scanner->stack_size;
  buffer[size++] = scanner->pending_dedents;

  for (uint8_t i = 0; i < scanner->stack_size; i++) {
    buffer[size++] = scanner->indent_stack[i] & 0xFF;
    buffer[size++] = (scanner->indent_stack[i] >> 8) & 0xFF;
  }

  return size;
}

void tree_sitter_klein_external_scanner_deserialize(void *payload, const char *buffer, unsigned length) {
  Scanner *scanner = (Scanner *)payload;

  if (length == 0) {
    scanner->stack_size = 1;
    scanner->indent_stack[0] = 0;
    scanner->pending_dedents = 0;
    return;
  }

  size_t pos = 0;
  scanner->stack_size = buffer[pos++];
  scanner->pending_dedents = buffer[pos++];

  for (uint8_t i = 0; i < scanner->stack_size && pos < length; i++) {
    scanner->indent_stack[i] = (uint8_t)buffer[pos++];
    if (pos < length) {
      scanner->indent_stack[i] |= ((uint16_t)(uint8_t)buffer[pos++]) << 8;
    }
  }
}

static uint16_t current_indent(Scanner *scanner) {
  return scanner->indent_stack[scanner->stack_size - 1];
}

static void push_indent(Scanner *scanner, uint16_t indent) {
  if (scanner->stack_size < MAX_INDENT_DEPTH) {
    scanner->indent_stack[scanner->stack_size++] = indent;
  }
}

static void pop_indent(Scanner *scanner) {
  if (scanner->stack_size > 1) {
    scanner->stack_size--;
  }
}

bool tree_sitter_klein_external_scanner_scan(
  void *payload,
  TSLexer *lexer,
  const bool *valid_symbols
) {
  Scanner *scanner = (Scanner *)payload;

  // In error recovery mode, all symbols are valid - don't interfere
  if (valid_symbols[INDENT] && valid_symbols[DEDENT] && valid_symbols[NEWLINE]) {
    return false;
  }

  // First, emit any pending dedents
  if (scanner->pending_dedents > 0 && valid_symbols[DEDENT]) {
    scanner->pending_dedents--;
    pop_indent(scanner);
    lexer->result_symbol = DEDENT;
    return true;
  }

  // Check if we're at the end of file
  if (lexer->eof(lexer)) {
    // Emit dedents for any remaining indentation
    if (scanner->stack_size > 1 && valid_symbols[DEDENT]) {
      pop_indent(scanner);
      lexer->result_symbol = DEDENT;
      return true;
    }
    return false;
  }

  // Look for newlines
  bool found_newline = false;
  while (lexer->lookahead == '\n' || lexer->lookahead == '\r') {
    found_newline = true;
    skip(lexer);
    if (lexer->lookahead == '\n') {
      skip(lexer);
    }
  }

  if (!found_newline) {
    return false;
  }

  // Count indentation on the new line
  uint16_t indent = 0;
  while (lexer->lookahead == ' ' || lexer->lookahead == '\t') {
    if (lexer->lookahead == ' ') {
      indent++;
    } else {
      indent += 4; // Tab = 4 spaces
    }
    skip(lexer);
  }

  // Skip blank lines and comment-only lines
  if (lexer->lookahead == '\n' || lexer->lookahead == '\r' || lexer->lookahead == '#') {
    // This is a blank line or comment line, just emit newline if valid
    if (valid_symbols[NEWLINE]) {
      lexer->result_symbol = NEWLINE;
      return true;
    }
    return false;
  }

  // Check for EOF after whitespace
  if (lexer->eof(lexer)) {
    if (scanner->stack_size > 1 && valid_symbols[DEDENT]) {
      pop_indent(scanner);
      lexer->result_symbol = DEDENT;
      return true;
    }
    if (valid_symbols[NEWLINE]) {
      lexer->result_symbol = NEWLINE;
      return true;
    }
    return false;
  }

  uint16_t current = current_indent(scanner);

  if (indent > current) {
    // Indentation increased - emit INDENT
    if (valid_symbols[INDENT]) {
      push_indent(scanner, indent);
      lexer->result_symbol = INDENT;
      return true;
    }
  } else if (indent < current) {
    // Indentation decreased - emit DEDENT(s)
    if (valid_symbols[DEDENT]) {
      // Count how many levels we need to dedent
      uint8_t dedents = 0;
      for (int i = scanner->stack_size - 1; i >= 0; i--) {
        if (scanner->indent_stack[i] <= indent) {
          break;
        }
        dedents++;
      }

      if (dedents > 0) {
        scanner->pending_dedents = dedents - 1; // -1 because we emit one now
        pop_indent(scanner);
        lexer->result_symbol = DEDENT;
        return true;
      }
    }
  }

  // Same indentation level - emit NEWLINE
  if (valid_symbols[NEWLINE]) {
    lexer->result_symbol = NEWLINE;
    return true;
  }

  return false;
}
