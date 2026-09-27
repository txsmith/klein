import { fromKotlinAll as fromKotlinDiagnostics, type Diagnostic } from "./Diagnostic.js";
import { Call, fromKotlin as fromKotlinEntry, type LogEntry } from "./EffectLog.js";
import { freeze, kotlin } from "./Kotlin.js";
import type { ReleaseNumber, RevisionNumber } from "./Numbering.js";
import { Type, type RuleType } from "./Type.js";

export type HostError =
  | InvalidContract
  | UnknownRelease
  | UnknownPin
  | RegistrationError
  | WrongEnvironment
  | MissingHandler
  | LogTypeMismatch
  | Diverged
  | HandlerTypeMismatch
  | CallTypeMismatch
  | UnreadableLog
  | UnreadableEdition
  | LogAlreadyEnded
  | SecondStartEntry;

export class InvalidContract {
  readonly #kotlin: kotlin.InvalidContract;

  private constructor(error: kotlin.InvalidContract) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.InvalidContract): InvalidContract {
    return new InvalidContract(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }

  #diagnostics?: readonly Diagnostic[];

  get diagnostics(): readonly Diagnostic[] {
    return (this.#diagnostics ??= fromKotlinDiagnostics(this.#kotlin.diagnostics));
  }
}

export class UnknownRelease {
  readonly #kotlin: kotlin.UnknownRelease;

  private constructor(error: kotlin.UnknownRelease) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.UnknownRelease): UnknownRelease {
    return new UnknownRelease(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }

  get number(): ReleaseNumber {
    return this.#kotlin.number as ReleaseNumber;
  }

  #available?: readonly ReleaseNumber[];

  get available(): readonly ReleaseNumber[] {
    return (this.#available ??= freeze(this.#kotlin.available.map((it) => it as ReleaseNumber)));
  }
}

export class UnknownPin {
  readonly #kotlin: kotlin.UnknownPin;

  private constructor(error: kotlin.UnknownPin) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.UnknownPin): UnknownPin {
    return new UnknownPin(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }

  get name(): string {
    return this.#kotlin.name;
  }

  get revision(): RevisionNumber {
    return this.#kotlin.revision as RevisionNumber;
  }
}

export class RegistrationError {
  readonly #kotlin: kotlin.RegistrationError;

  private constructor(error: kotlin.RegistrationError) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.RegistrationError): RegistrationError {
    return new RegistrationError(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }
}

export class WrongEnvironment {
  readonly #kotlin: kotlin.WrongEnvironment;

  private constructor(error: kotlin.WrongEnvironment) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.WrongEnvironment): WrongEnvironment {
    return new WrongEnvironment(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }

  get edition(): string {
    return this.#kotlin.edition;
  }

  get environment(): string {
    return this.#kotlin.environment;
  }
}

export class MissingHandler {
  readonly #kotlin: kotlin.MissingHandler;

  private constructor(error: kotlin.MissingHandler) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.MissingHandler): MissingHandler {
    return new MissingHandler(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }

  get name(): string {
    return this.#kotlin.name;
  }

  get revision(): RevisionNumber {
    return this.#kotlin.revision as RevisionNumber;
  }
}

export class LogTypeMismatch {
  readonly #kotlin: kotlin.LogTypeMismatch;

  private constructor(error: kotlin.LogTypeMismatch) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.LogTypeMismatch): LogTypeMismatch {
    return new LogTypeMismatch(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }

  get at(): number {
    return this.#kotlin.at;
  }

  get name(): string {
    return this.#kotlin.name;
  }

  get answerType(): string {
    return this.#kotlin.answerType;
  }

  #declaredType?: RuleType;

  get declaredType(): RuleType {
    return (this.#declaredType ??= Type.fromKotlin(this.#kotlin.declaredType));
  }
}

export class Diverged {
  readonly #kotlin: kotlin.Diverged;

  private constructor(error: kotlin.Diverged) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.Diverged): Diverged {
    return new Diverged(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }

  get expected(): string {
    return this.#kotlin.expected;
  }

  get got(): string {
    return this.#kotlin.got;
  }

  get at(): number {
    return this.#kotlin.at;
  }

  #call?: Call | null;

  get call(): Call | null {
    const call = this.#kotlin.call;
    return (this.#call ??= call == null ? null : new Call(call));
  }
}

export class HandlerTypeMismatch {
  readonly #kotlin: kotlin.HandlerTypeMismatch;

  private constructor(error: kotlin.HandlerTypeMismatch) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.HandlerTypeMismatch): HandlerTypeMismatch {
    return new HandlerTypeMismatch(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }

  get call(): string {
    return this.#kotlin.call;
  }

  get answerType(): string {
    return this.#kotlin.answerType;
  }

  #declaredType?: RuleType;

  get declaredType(): RuleType {
    return (this.#declaredType ??= Type.fromKotlin(this.#kotlin.declaredType));
  }
}

export class CallTypeMismatch {
  readonly #kotlin: kotlin.CallTypeMismatch;

  private constructor(error: kotlin.CallTypeMismatch) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.CallTypeMismatch): CallTypeMismatch {
    return new CallTypeMismatch(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }

  get call(): string {
    return this.#kotlin.call;
  }

  get got(): string {
    return this.#kotlin.got;
  }

  get declared(): string {
    return this.#kotlin.declared;
  }
}

export class UnreadableLog {
  readonly #kotlin: kotlin.UnreadableLog;

  private constructor(error: kotlin.UnreadableLog) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.UnreadableLog): UnreadableLog {
    return new UnreadableLog(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }
}

export class UnreadableEdition {
  readonly #kotlin: kotlin.UnreadableEdition;

  private constructor(error: kotlin.UnreadableEdition) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.UnreadableEdition): UnreadableEdition {
    return new UnreadableEdition(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }
}

export class LogAlreadyEnded {
  readonly #kotlin: kotlin.LogAlreadyEnded;

  private constructor(error: kotlin.LogAlreadyEnded) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.LogAlreadyEnded): LogAlreadyEnded {
    return new LogAlreadyEnded(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }

  #ending?: LogEntry.Ending;

  get ending(): LogEntry.Ending {
    return (this.#ending ??= fromKotlinEntry(this.#kotlin.ending) as LogEntry.Ending);
  }
}

export class SecondStartEntry {
  readonly #kotlin: kotlin.SecondStartEntry;

  private constructor(error: kotlin.SecondStartEntry) {
    this.#kotlin = error;
  }

  /** @internal */
  static fromKotlin(error: kotlin.SecondStartEntry): SecondStartEntry {
    return new SecondStartEntry(error);
  }

  get message(): string {
    return this.#kotlin.message;
  }
}

export function fromKotlin(error: kotlin.HostError): HostError {
  if (error instanceof kotlin.InvalidContract) return InvalidContract.fromKotlin(error);
  if (error instanceof kotlin.UnknownRelease) return UnknownRelease.fromKotlin(error);
  if (error instanceof kotlin.UnknownPin) return UnknownPin.fromKotlin(error);
  if (error instanceof kotlin.RegistrationError) return RegistrationError.fromKotlin(error);
  if (error instanceof kotlin.WrongEnvironment) return WrongEnvironment.fromKotlin(error);
  if (error instanceof kotlin.MissingHandler) return MissingHandler.fromKotlin(error);
  if (error instanceof kotlin.LogTypeMismatch) return LogTypeMismatch.fromKotlin(error);
  if (error instanceof kotlin.Diverged) return Diverged.fromKotlin(error);
  if (error instanceof kotlin.HandlerTypeMismatch) return HandlerTypeMismatch.fromKotlin(error);
  if (error instanceof kotlin.CallTypeMismatch) return CallTypeMismatch.fromKotlin(error);
  if (error instanceof kotlin.UnreadableLog) return UnreadableLog.fromKotlin(error);
  if (error instanceof kotlin.UnreadableEdition) return UnreadableEdition.fromKotlin(error);
  if (error instanceof kotlin.LogAlreadyEnded) return LogAlreadyEnded.fromKotlin(error);
  if (error instanceof kotlin.SecondStartEntry) return SecondStartEntry.fromKotlin(error);
  throw new Error(`unknown host error: ${error.message}`);
}
