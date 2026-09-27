import { fromKotlinAll, type Diagnostic } from "./Diagnostic.js";
import { freeze, type kotlin } from "./Kotlin.js";

export type Checked<T> = Checked.Accepted<T> | Checked.Rejected;

export namespace Checked {
  export class Accepted<T> {
    constructor(readonly value: T) {}

    map<R>(transform: (value: T) => R): Checked<R> {
      return new Accepted(transform(this.value));
    }

    andThen<R>(next: (value: T) => Checked<R>): Checked<R> {
      return next(this.value);
    }

    getOrThrow(): T {
      return this.value;
    }
  }

  export class Rejected {
    readonly diagnostics: readonly Diagnostic[];

    constructor(diagnostics: readonly Diagnostic[]) {
      if (diagnostics.length === 0) throw new RangeError("a rejected result needs at least one diagnostic");
      this.diagnostics = freeze(diagnostics);
    }

    map<R>(transform: (value: never) => R): Checked<R> {
      return this;
    }

    andThen<R>(next: (value: never) => Checked<R>): Checked<R> {
      return this;
    }

    getOrThrow(): never {
      throw new RejectedException(this.diagnostics);
    }
  }
}

export class RejectedException extends Error {
  readonly diagnostics: readonly Diagnostic[];

  constructor(diagnostics: readonly Diagnostic[]) {
    super(diagnostics.map((it) => it.message).join("\n"));
    this.name = "RejectedException";
    this.diagnostics = freeze(diagnostics);
  }
}

export function fromKotlin<T, R>(checked: kotlin.Checked<T>, transform: (value: T) => R): Checked<R> {
  const diagnostics = checked.diagnostics;
  return diagnostics == null ? new Checked.Accepted(transform(checked.value as T)) : new Checked.Rejected(fromKotlinAll(diagnostics));
}
