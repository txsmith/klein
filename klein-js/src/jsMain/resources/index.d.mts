declare const releaseNumber: unique symbol;
declare const revisionNumber: unique symbol;

export type ReleaseNumber = number & { readonly [releaseNumber]: true };
export type RevisionNumber = number & { readonly [revisionNumber]: true };

export type KleinValue =
  | number
  | string
  | boolean
  | null
  | undefined
  | TaggedValue
  | { readonly [field: string]: KleinValue };

export declare class TaggedValue {
  #private;
  constructor(tag: string, fields: { readonly [field: string]: KleinValue });
  readonly tag: string;
  readonly fields: { readonly [field: string]: KleinValue };
}

export declare function printValue(value: KleinValue): string;

export declare class Diagnostic {
  #private;
  private constructor();
  readonly message: string;
  readonly start: number;
  readonly end: number;
}

export type Checked<T> = Accepted<T> | Rejected<T>;

export declare class Accepted<T> {
  #private;
  private constructor();
  readonly kind: "accepted";
  readonly value: T;
}

export declare class Rejected<T> {
  #private;
  private constructor();
  readonly kind: "rejected";
  readonly diagnostics: readonly Diagnostic[];
}

export declare class RejectedError extends Error {
  #private;
  private constructor();
  readonly diagnostics: readonly Diagnostic[];
}

export declare class Token {
  #private;
  private constructor();
  readonly kind: string;
  readonly start: number;
  readonly end: number;
}

export declare function tokenize(source: string): Checked<readonly Token[]>;

export declare function checkContract(source: string): Contract;

export declare class Declaration {
  #private;
  private constructor();
  readonly kind: "fun" | "value";
  readonly name: string;
  readonly revision: RevisionNumber;
  readonly type: ContractType;
  readonly answerType: RuleType;
}

export declare class ContractType {
  #private;
  private constructor();
  print(): string;
}

export declare class RuleType {
  #private;
  private constructor();
  print(): string;
}

export declare class Contract {
  #private;
  private constructor();
  readonly environment: string;
  readonly releases: readonly ReleaseNumber[];
  readonly declarations: readonly Declaration[];
  check(rule: string, release: ReleaseNumber): Checked<string>;
  compileRule(rule: string, release: ReleaseNumber): Checked<Edition>;
  compileRuleAtPins(rule: string, pins: Pins): Checked<Edition>;
  evaluateValue(source: string, release: ReleaseNumber, expected: RuleType): Checked<KleinValue>;
  decodeEditionJson(json: string): DecodedEdition;
  implement(
    registrations: readonly HandlerRegistration[],
    transact?: ((block: () => Promise<void>) => void | Promise<void>) | null,
  ): Environment;
}

export declare class Pin {
  #private;
  private constructor();
  readonly revision: RevisionNumber;
  readonly hash: string;
}

export type Pins = { readonly [name: string]: Pin };
export type Revisions = { readonly [name: string]: RevisionNumber };

export declare class Edition {
  #private;
  private constructor();
  readonly environment: string;
  readonly language: number;
  readonly source: string;
  readonly pins: Revisions;
  readonly pinsWithHash: Pins;
  encodeJson(): string;
}

export type DecodedEdition = Intact | Stale;

export declare class Intact {
  #private;
  private constructor();
  readonly kind: "intact";
  readonly edition: Edition;
}

export declare class Stale {
  #private;
  private constructor();
  readonly kind: "stale";
  readonly language: number;
  readonly source: string;
  readonly pins: Pins;
  readonly reason: StaleReason;
}

export type StaleReason = ChecksumMismatch | LanguageChanged | CompilerChanged | UnknownPins | DeclarationChanged;

export declare class ChecksumMismatch {
  #private;
  private constructor();
  readonly kind: "checksumMismatch";
}

export declare class LanguageChanged {
  #private;
  private constructor();
  readonly kind: "languageChanged";
}

export declare class CompilerChanged {
  #private;
  private constructor();
  readonly kind: "compilerChanged";
}

export declare class UnknownPins {
  #private;
  private constructor();
  readonly kind: "unknownPins";
  readonly pins: Revisions;
}

export declare class DeclarationChanged {
  #private;
  private constructor();
  readonly kind: "declarationChanged";
}

export declare class Call {
  #private;
  private constructor();
  readonly name: string;
  readonly args: readonly KleinValue[];
  print(): string;
}

export declare class HandlerRegistration {
  #private;
  private constructor();
  readonly name: string;
}

export declare function immediate(
  name: string,
  answer: (args: readonly KleinValue[]) => KleinValue | Promise<KleinValue>,
): HandlerRegistration;
export declare function deferred(name: string, initiate: (call: Call) => void | Promise<void>): HandlerRegistration;
export declare function perRun(name: string): HandlerRegistration;

export declare class Environment {
  #private;
  private constructor();
  run(
    edition: Edition,
    registrations?: readonly HandlerRegistration[],
    log?: EffectLog | null,
    persist?: ((entry: LogEntry) => void | Promise<void>) | null,
  ): Promise<RunOutcome>;
}

export type RunOutcome = Completed | Failed | Parked;

export declare class Completed {
  #private;
  private constructor();
  readonly kind: "completed";
  readonly log: EffectLog;
  readonly value: KleinValue;
}

export declare class Failed {
  #private;
  private constructor();
  readonly kind: "failed";
  readonly log: EffectLog;
  readonly diagnostics: readonly Diagnostic[];
}

export declare class Parked {
  #private;
  private constructor();
  readonly kind: "parked";
  readonly log: EffectLog;
  readonly call: Call;
  toReply(answer: KleinValue): ReplyEntry;
}

export declare class EffectLog {
  #private;
  private constructor();
  readonly entries: readonly LogEntry[];
  append(entry: LogEntry): EffectLog;
  encodeJson(): string;
}

export declare function decodeLogJson(json: string): EffectLog;

export type LogEntry = StartEntry | ReplyEntry | ResultEntry | FailureEntry;

export declare class StartEntry {
  #private;
  private constructor();
  readonly kind: "start";
  readonly inputs: { readonly [name: string]: KleinValue };
}

export declare class ReplyEntry {
  #private;
  private constructor();
  readonly kind: "reply";
  readonly call: Call;
  readonly answer: KleinValue;
}

export declare class ResultEntry {
  #private;
  private constructor();
  readonly kind: "result";
  readonly value: KleinValue;
}

export declare class FailureEntry {
  #private;
  private constructor();
  readonly kind: "failure";
  readonly diagnostics: readonly Diagnostic[];
}

export declare class KleinError extends Error {
  #private;
  private constructor();
  readonly errors: readonly HostError[];
}

export type HostError =
  | InvalidContract
  | UnknownRelease
  | UnknownPin
  | WrongEnvironment
  | MissingHandler
  | LogTypeMismatch
  | Diverged
  | CallTypeMismatch
  | HandlerTypeMismatch
  | RegistrationError
  | UnreadableEdition
  | UnreadableLog
  | UnsupportedValue
  | LogAlreadyEnded
  | SecondStartEntry
  | TransactionSkippedBlock;

export declare class InvalidContract {
  #private;
  private constructor();
  readonly kind: "invalidContract";
  readonly message: string;
  readonly diagnostics: readonly Diagnostic[];
}

export declare class UnknownRelease {
  #private;
  private constructor();
  readonly kind: "unknownRelease";
  readonly message: string;
  readonly number: ReleaseNumber;
  readonly available: readonly ReleaseNumber[];
}

export declare class UnknownPin {
  #private;
  private constructor();
  readonly kind: "unknownPin";
  readonly message: string;
  readonly name: string;
  readonly revision: RevisionNumber;
}

export declare class WrongEnvironment {
  #private;
  private constructor();
  readonly kind: "wrongEnvironment";
  readonly message: string;
  readonly edition: string;
  readonly environment: string;
}

export declare class MissingHandler {
  #private;
  private constructor();
  readonly kind: "missingHandler";
  readonly message: string;
  readonly name: string;
  readonly revision: RevisionNumber;
}

export declare class LogTypeMismatch {
  #private;
  private constructor();
  readonly kind: "logTypeMismatch";
  readonly message: string;
  readonly at: number;
  readonly name: string;
  readonly answerType: string;
  readonly declaredType: string;
}

export declare class Diverged {
  #private;
  private constructor();
  readonly kind: "diverged";
  readonly message: string;
  readonly expected: string;
  readonly got: string;
  readonly at: number;
  readonly call: Call | null;
}

export declare class CallTypeMismatch {
  #private;
  private constructor();
  readonly kind: "callTypeMismatch";
  readonly message: string;
  readonly call: string;
  readonly got: string;
  readonly declared: string;
}

export declare class HandlerTypeMismatch {
  #private;
  private constructor();
  readonly kind: "handlerTypeMismatch";
  readonly message: string;
  readonly call: string;
  readonly answerType: string;
  readonly declaredType: string;
}

export declare class RegistrationError {
  #private;
  private constructor();
  readonly kind: "registrationError";
  readonly message: string;
}

export declare class UnreadableEdition {
  #private;
  private constructor();
  readonly kind: "unreadableEdition";
  readonly message: string;
}

export declare class UnreadableLog {
  #private;
  private constructor();
  readonly kind: "unreadableLog";
  readonly message: string;
}

export declare class UnsupportedValue {
  #private;
  private constructor();
  readonly kind: "unsupportedValue";
  readonly message: string;
}

export declare class LogAlreadyEnded {
  #private;
  private constructor();
  readonly kind: "logAlreadyEnded";
  readonly message: string;
  readonly ending: ResultEntry | FailureEntry;
}

export declare class SecondStartEntry {
  #private;
  private constructor();
  readonly kind: "secondStartEntry";
  readonly message: string;
}

export declare class TransactionSkippedBlock {
  #private;
  private constructor();
  readonly kind: "transactionSkippedBlock";
  readonly message: string;
}
