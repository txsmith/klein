import { fromKotlinAll as fromKotlinDiagnostics, type Diagnostic } from "./Diagnostic.js";
import { Call, EffectLog, LogEntry } from "./EffectLog.js";
import type { kotlin } from "./Kotlin.js";
import { fromKotlin as fromKotlinValue, type Value } from "./Value.js";

export type RunOutcome = RunOutcome.Completed | RunOutcome.Failed | RunOutcome.Parked;

export namespace RunOutcome {
  export class Completed {
    readonly #kotlin: kotlin.RunOutcome;

    private constructor(outcome: kotlin.RunOutcome) {
      this.#kotlin = outcome;
    }

    /** @internal */
    static fromKotlin(outcome: kotlin.RunOutcome): Completed {
      return new Completed(outcome);
    }

    #value?: Value;
    #log?: EffectLog;

    get value(): Value {
      return (this.#value ??= fromKotlinValue(this.#kotlin.value));
    }

    get log(): EffectLog {
      return (this.#log ??= new EffectLog(this.#kotlin.log));
    }
  }

  export class Failed {
    readonly #kotlin: kotlin.RunOutcome;

    private constructor(outcome: kotlin.RunOutcome) {
      this.#kotlin = outcome;
    }

    /** @internal */
    static fromKotlin(outcome: kotlin.RunOutcome): Failed {
      return new Failed(outcome);
    }

    #diagnostics?: readonly Diagnostic[];
    #log?: EffectLog;

    get diagnostics(): readonly Diagnostic[] {
      return (this.#diagnostics ??= fromKotlinDiagnostics(this.#kotlin.diagnostics!));
    }

    get log(): EffectLog {
      return (this.#log ??= new EffectLog(this.#kotlin.log));
    }
  }

  export class Parked {
    readonly #kotlin: kotlin.RunOutcome;

    private constructor(outcome: kotlin.RunOutcome) {
      this.#kotlin = outcome;
    }

    /** @internal */
    static fromKotlin(outcome: kotlin.RunOutcome): Parked {
      return new Parked(outcome);
    }

    #call?: Call;
    #log?: EffectLog;

    get call(): Call {
      return (this.#call ??= new Call(this.#kotlin.call!));
    }

    get log(): EffectLog {
      return (this.#log ??= new EffectLog(this.#kotlin.log));
    }

    toReply(answer: Value): LogEntry.Reply {
      return new LogEntry.Reply(this.call, answer);
    }
  }

  /** @internal */
  export function fromKotlin(outcome: kotlin.RunOutcome): RunOutcome {
    switch (outcome.kind) {
      case "Completed":
        return Completed.fromKotlin(outcome);
      case "Failed":
        return Failed.fromKotlin(outcome);
      case "Parked":
        return Parked.fromKotlin(outcome);
      default:
        throw new Error(`unknown run outcome ${outcome.kind}`);
    }
  }
}
