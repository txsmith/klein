import type { kotlin } from "./Kotlin.js";
import type { RevisionNumber } from "./Numbering.js";

export class Type<out R extends RevisionNumber | null> {
  readonly #kotlin: kotlin.Type;
  private declare readonly revision: R;

  private constructor(type: kotlin.Type) {
    this.#kotlin = type;
  }

  /** @internal */
  static fromKotlin<R extends RevisionNumber | null>(type: kotlin.Type): Type<R> {
    return new Type<R>(type);
  }

  /** @internal */
  static toKotlin(type: Type<RevisionNumber | null>): kotlin.Type {
    return type.#kotlin;
  }

  static print(type: Type<RevisionNumber | null>): string {
    return type.#kotlin.print();
  }
}

export type ContractType = Type<RevisionNumber>;

export type RuleType = Type<null>;
