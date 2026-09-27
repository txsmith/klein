import { Edition } from "./Edition.js";
import { Call, EffectLog, fromKotlin as fromKotlinEntry, type LogEntry } from "./EffectLog.js";
import { kotlin, mapExceptions } from "./Kotlin.js";
import { RunOutcome } from "./Runner.js";
import { fromKotlinAll as fromKotlinValues, toKotlin as toKotlinValue, type Value } from "./Value.js";

export class HandlerRegistration {
  readonly #kotlin: kotlin.HandlerRegistration;

  private constructor(registration: kotlin.HandlerRegistration) {
    this.#kotlin = registration;
  }

  get name(): string {
    return this.#kotlin.name;
  }

  /** @internal */
  static fromKotlin(registration: kotlin.HandlerRegistration): HandlerRegistration {
    return new HandlerRegistration(registration);
  }

  /** @internal */
  static toKotlin(registration: HandlerRegistration): kotlin.HandlerRegistration {
    return registration.#kotlin;
  }
}

export function immediate(name: string, answer: (...args: Value[]) => Value | Promise<Value>): HandlerRegistration {
  return HandlerRegistration.fromKotlin(kotlin.immediate(name, async (args) => toKotlinValue(await answer(...fromKotlinValues(args)))));
}

export function perRun(name: string): HandlerRegistration {
  return HandlerRegistration.fromKotlin(kotlin.perRun(name));
}

export function deferred(name: string, initiate: (call: Call) => void | Promise<void>): HandlerRegistration {
  return HandlerRegistration.fromKotlin(
    kotlin.deferred(name, async (call) => {
      await initiate(new Call(call));
    }),
  );
}

export interface Transactor {
  transact<T>(block: () => Promise<T>): Promise<T>;
}

export class Environment {
  readonly #kotlin: kotlin.Environment;

  private constructor(environment: kotlin.Environment) {
    this.#kotlin = environment;
  }

  /** @internal */
  static fromKotlin(environment: kotlin.Environment): Environment {
    return new Environment(environment);
  }

  withTransactor(transactor: Transactor): Environment {
    return new Environment(this.#kotlin.withTransactor(async (block) => transactor.transact(block)));
  }

  withPersister(persist: (entry: LogEntry) => void | Promise<void>): Environment {
    return new Environment(
      this.#kotlin.withPersister(async (entry) => {
        await persist(fromKotlinEntry(entry));
      }),
    );
  }

  run(edition: Edition, ...registrations: HandlerRegistration[]): Promise<RunOutcome>;
  run(edition: Edition, log: EffectLog, ...registrations: HandlerRegistration[]): Promise<RunOutcome>;
  run(edition: Edition, ...args: (EffectLog | HandlerRegistration)[]): Promise<RunOutcome> {
    return mapExceptions(async () => {
      const [first, ...rest] = args;
      const log = first instanceof EffectLog ? EffectLog.toKotlin(first) : null;
      const registrations = (log === null ? args : rest) as HandlerRegistration[];
      const outcome = await this.#kotlin.run(Edition.toKotlin(edition), log, registrations.map(HandlerRegistration.toKotlin));
      return RunOutcome.fromKotlin(outcome);
    });
  }
}
