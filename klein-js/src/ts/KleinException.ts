import { fromKotlin, type HostError } from "./HostError.js";
import { freeze, type kotlin } from "./Kotlin.js";

export class KleinException extends Error {
  readonly errors: readonly HostError[];

  constructor(errors: readonly HostError[]) {
    super(errors.map((it) => it.message).join("\n"));
    this.name = "KleinException";
    this.errors = freeze(errors);
  }

  /** @internal */
  static fromKotlin(errors: readonly kotlin.HostError[]): KleinException {
    return new KleinException(errors.map(fromKotlin));
  }
}
