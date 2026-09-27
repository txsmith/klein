import { freezeMap, type kotlin } from "./Kotlin.js";
import type { LanguageVersion, RevisionNumber } from "./Numbering.js";

export class DeclarationHash {
  readonly #text: string;

  private constructor(text: string) {
    this.#text = text;
  }

  /** @internal */
  static fromKotlin(text: string): DeclarationHash {
    return new DeclarationHash(text);
  }

  toString(): string {
    return this.#text;
  }
}

export class Pin {
  readonly #kotlin: kotlin.Pin;

  private constructor(pin: kotlin.Pin) {
    this.#kotlin = pin;
  }

  /** @internal */
  static fromKotlinAll(pins: readonly kotlin.Pin[]): ReadonlyMap<string, Pin> {
    return freezeMap(pins.map((it) => [it.name, new Pin(it)]));
  }

  get revision(): RevisionNumber {
    return this.#kotlin.revision as RevisionNumber;
  }

  #hash?: DeclarationHash;

  get hash(): DeclarationHash {
    return (this.#hash ??= DeclarationHash.fromKotlin(this.#kotlin.hash!));
  }
}

export class Edition {
  readonly #kotlin: kotlin.Edition;

  private constructor(edition: kotlin.Edition) {
    this.#kotlin = edition;
  }

  /** @internal */
  static fromKotlin(edition: kotlin.Edition): Edition {
    return new Edition(edition);
  }

  /** @internal */
  static toKotlin(edition: Edition): kotlin.Edition {
    return edition.#kotlin;
  }

  get environment(): string {
    return this.#kotlin.environment;
  }

  get language(): LanguageVersion {
    return this.#kotlin.language as LanguageVersion;
  }

  #pinsWithHash?: ReadonlyMap<string, Pin>;
  #pins?: ReadonlyMap<string, RevisionNumber>;

  get pinsWithHash(): ReadonlyMap<string, Pin> {
    return (this.#pinsWithHash ??= Pin.fromKotlinAll(this.#kotlin.pinsWithHash));
  }

  get source(): string {
    return this.#kotlin.source;
  }

  get pins(): ReadonlyMap<string, RevisionNumber> {
    return (this.#pins ??= freezeMap(this.#kotlin.pinsWithHash.map((it) => [it.name, it.revision as RevisionNumber])));
  }
}
