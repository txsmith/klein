import type { kotlin } from "./Kotlin.js";
import { SourceSpan } from "./SourceSpan.js";

export type TokenKind = string;

export class Token {
  readonly #kotlin: kotlin.Token;

  private constructor(token: kotlin.Token) {
    this.#kotlin = token;
  }

  /** @internal */
  static fromKotlin(token: kotlin.Token): Token {
    return new Token(token);
  }

  get kind(): TokenKind {
    return this.#kotlin.kind;
  }

  #span?: SourceSpan;

  get span(): SourceSpan {
    return (this.#span ??= new SourceSpan(this.#kotlin.start, this.#kotlin.end));
  }

  get text(): string | null {
    return this.#kotlin.text ?? null;
  }

  get indent(): number | null {
    return this.#kotlin.indent ?? null;
  }

  get isNewline(): boolean {
    return this.indent !== null;
  }
}
