import { fromKotlinAll as fromKotlinDiagnostics, toKotlin as toKotlinDiagnostic, type Diagnostic } from "./Diagnostic.js";
import { freeze, freezeMap, kotlin, mapExceptions } from "./Kotlin.js";
import { fromKotlin as fromKotlinValue, fromKotlinAll as fromKotlinValues, toKotlin as toKotlinValue, type Value } from "./Value.js";

export class Call {
  readonly #kotlin: kotlin.Call;

  constructor(name: string, args: readonly Value[]);
  /** @internal */
  constructor(call: kotlin.Call);
  constructor(nameOrCall: string | kotlin.Call, args: readonly Value[] = []) {
    this.#kotlin =
      nameOrCall instanceof kotlin.Call ? nameOrCall : mapExceptions(() => kotlin.createCall(nameOrCall, args.map(toKotlinValue)));
  }

  /** @internal */
  static toKotlin(call: Call): kotlin.Call {
    return call.#kotlin;
  }

  get name(): string {
    return this.#kotlin.name;
  }

  #args?: readonly Value[];

  get args(): readonly Value[] {
    return (this.#args ??= fromKotlinValues(this.#kotlin.args));
  }

  print(): string {
    return mapExceptions(() => this.#kotlin.print());
  }
}

export type LogEntry = LogEntry.Start | LogEntry.Reply | LogEntry.Ending;

export namespace LogEntry {
  export type Ending = Result | Failure;

  export class Start {
    readonly #kotlin: kotlin.LogEntry;

    constructor(inputs: ReadonlyMap<string, Value>);
    /** @internal */
    constructor(entry: kotlin.LogEntry);
    constructor(inputsOrEntry: ReadonlyMap<string, Value> | kotlin.LogEntry) {
      this.#kotlin =
        inputsOrEntry instanceof kotlin.LogEntry
          ? inputsOrEntry
          : mapExceptions(() => kotlin.createStartEntry([...inputsOrEntry.keys()], [...inputsOrEntry.values()].map(toKotlinValue)));
    }

    /** @internal */
    static toKotlin(entry: Start): kotlin.LogEntry {
      return entry.#kotlin;
    }

    #inputs?: ReadonlyMap<string, Value>;

    get inputs(): ReadonlyMap<string, Value> {
      const values = this.#kotlin.inputValues!;
      return (this.#inputs ??= freezeMap(this.#kotlin.inputNames!.map((name, index) => [name, fromKotlinValue(values[index])])));
    }
  }

  export class Reply {
    readonly #kotlin: kotlin.LogEntry;

    constructor(call: Call, answer: Value);
    /** @internal */
    constructor(entry: kotlin.LogEntry);
    constructor(callOrEntry: Call | kotlin.LogEntry, answer?: Value) {
      this.#kotlin =
        callOrEntry instanceof kotlin.LogEntry
          ? callOrEntry
          : mapExceptions(() => kotlin.createReplyEntry(Call.toKotlin(callOrEntry), toKotlinValue(answer)));
    }

    /** @internal */
    static toKotlin(entry: Reply): kotlin.LogEntry {
      return entry.#kotlin;
    }

    #call?: Call;
    #answer?: Value;

    get call(): Call {
      return (this.#call ??= new Call(this.#kotlin.call!));
    }

    get answer(): Value {
      return (this.#answer ??= fromKotlinValue(this.#kotlin.answer));
    }
  }

  export class Result {
    readonly #kotlin: kotlin.LogEntry;

    constructor(value: Value);
    /** @internal */
    constructor(entry: kotlin.LogEntry);
    constructor(valueOrEntry: Value) {
      this.#kotlin =
        valueOrEntry instanceof kotlin.LogEntry ? valueOrEntry : mapExceptions(() => kotlin.createResultEntry(toKotlinValue(valueOrEntry)));
    }

    /** @internal */
    static toKotlin(entry: Result): kotlin.LogEntry {
      return entry.#kotlin;
    }

    #value?: Value;

    get value(): Value {
      return (this.#value ??= fromKotlinValue(this.#kotlin.value));
    }
  }

  export class Failure {
    readonly #kotlin: kotlin.LogEntry;

    constructor(errors: readonly Diagnostic[]);
    /** @internal */
    constructor(entry: kotlin.LogEntry);
    constructor(errorsOrEntry: readonly Diagnostic[] | kotlin.LogEntry) {
      this.#kotlin =
        errorsOrEntry instanceof kotlin.LogEntry
          ? errorsOrEntry
          : mapExceptions(() => kotlin.createFailureEntry(errorsOrEntry.map(toKotlinDiagnostic)));
    }

    /** @internal */
    static toKotlin(entry: Failure): kotlin.LogEntry {
      return entry.#kotlin;
    }

    #errors?: readonly Diagnostic[];

    get errors(): readonly Diagnostic[] {
      return (this.#errors ??= fromKotlinDiagnostics(this.#kotlin.errors!));
    }
  }
}

export function fromKotlin(entry: kotlin.LogEntry): LogEntry {
  switch (entry.kind) {
    case "Start":
      return new LogEntry.Start(entry);
    case "Reply":
      return new LogEntry.Reply(entry);
    case "Result":
      return new LogEntry.Result(entry);
    case "Failure":
      return new LogEntry.Failure(entry);
    default:
      throw new Error(`unknown log entry ${entry.kind}`);
  }
}

export function toKotlin(entry: LogEntry): kotlin.LogEntry {
  if (entry instanceof LogEntry.Start) return LogEntry.Start.toKotlin(entry);
  if (entry instanceof LogEntry.Reply) return LogEntry.Reply.toKotlin(entry);
  if (entry instanceof LogEntry.Result) return LogEntry.Result.toKotlin(entry);
  if (entry instanceof LogEntry.Failure) return LogEntry.Failure.toKotlin(entry);
  throw new TypeError(`${String(entry)} is not a log entry`);
}

export class EffectLog {
  readonly #kotlin: kotlin.EffectLog;

  constructor(start: LogEntry.Start, replies?: readonly LogEntry.Reply[], ending?: LogEntry.Ending | null);
  /** @internal */
  constructor(log: kotlin.EffectLog);
  constructor(startOrLog: LogEntry.Start | kotlin.EffectLog, replies: readonly LogEntry.Reply[] = [], ending: LogEntry.Ending | null = null) {
    if (startOrLog instanceof kotlin.EffectLog) {
      this.#kotlin = startOrLog;
      return;
    }
    if (!(startOrLog instanceof LogEntry.Start)) throw new TypeError(`an effect log opens with a start entry, not ${String(startOrLog)}`);
    for (const reply of replies) {
      if (!(reply instanceof LogEntry.Reply)) throw new TypeError(`an effect log's replies are reply entries, not ${String(reply)}`);
    }
    if (ending !== null && !(ending instanceof LogEntry.Result || ending instanceof LogEntry.Failure)) {
      throw new TypeError(`an effect log ends with a result or a failure, not ${String(ending)}`);
    }
    this.#kotlin = mapExceptions(() =>
      kotlin.createEffectLog(toKotlin(startOrLog), replies.map(toKotlin), ending === null ? null : toKotlin(ending)),
    );
  }

  /** @internal */
  static toKotlin(log: EffectLog): kotlin.EffectLog {
    return log.#kotlin;
  }

  #entries?: readonly LogEntry[];
  #replies?: readonly LogEntry.Reply[];

  get entries(): readonly LogEntry[] {
    return (this.#entries ??= freeze(this.#kotlin.entries.map(fromKotlin)));
  }

  get start(): LogEntry.Start {
    return this.entries[0] as LogEntry.Start;
  }

  get replies(): readonly LogEntry.Reply[] {
    return (this.#replies ??= freeze(this.entries.filter((it) => it instanceof LogEntry.Reply)));
  }

  get ending(): LogEntry.Ending | null {
    const last = this.entries.at(-1);
    return last instanceof LogEntry.Result || last instanceof LogEntry.Failure ? last : null;
  }

  plus(entry: LogEntry): EffectLog {
    return mapExceptions(() => new EffectLog(this.#kotlin.plus(toKotlin(entry))));
  }
}
