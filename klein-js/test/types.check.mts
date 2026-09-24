import type * as Hand from "../build/dist/js/productionLibrary/index.d.mts";

type GeneratedModule = typeof import("../build/dist/js/productionLibrary/klein.mjs");
type HandModule = typeof import("../build/dist/js/productionLibrary/index.d.mts");

type IsAny<T> = 0 extends 1 & T ? true : false;
type Fits<A, B> = [A] extends [B] ? true : false;
type SameKeys<H, G> = [keyof G] extends [keyof H] ? ([keyof H] extends [keyof G] ? true : false) : false;
type Mirrors<H, G> = [H] extends [G] ? SameKeys<H, G> : false;
type InstanceOf<C> = C extends { prototype: infer P } ? (IsAny<P> extends true ? never : P) : never;
type NoneOf<T extends never> = T;

type Unions = {
  Checked: Hand.Checked<unknown>;
  DecodedEdition: Hand.DecodedEdition;
  StaleReason: Hand.StaleReason;
  RunOutcome: Hand.RunOutcome;
  LogEntry: Hand.LogEntry;
  HostError: Hand.HostError;
};

type HiddenMembers = { Rejected: "value" };

type GeneratedOnly = keyof Unions | "KtSingleton";
type Exported = Exclude<keyof GeneratedModule, GeneratedOnly>;
type Classes = { [K in Exported]: [InstanceOf<GeneratedModule[K]>] extends [never] ? never : K }[Exported];
type Functions = Exclude<Exported, Classes>;

type Visible<K, T> = K extends keyof HiddenMembers ? Omit<T, HiddenMembers[K]> : T;

export type MissingFromHandwritten = NoneOf<Exclude<Exported, keyof HandModule>>;
type TypeOnlyMarkers = "releaseNumber" | "revisionNumber";

export type MissingFromGenerated = NoneOf<Exclude<keyof HandModule, keyof GeneratedModule | TypeOnlyMarkers>>;

export type ClassesThatDrifted = NoneOf<
  {
    [K in Classes & keyof HandModule]: Mirrors<InstanceOf<HandModule[K]>, Visible<K, InstanceOf<GeneratedModule[K]>>> extends true
      ? never
      : K;
  }[Classes & keyof HandModule]
>;

export type FunctionsThatDrifted = NoneOf<
  { [K in Functions & keyof HandModule]: Fits<HandModule[K], GeneratedModule[K]> extends true ? never : K }[Functions &
    keyof HandModule]
>;

export type UnionsThatDrifted = NoneOf<
  { [K in keyof Unions]: Fits<Unions[K], InstanceOf<GeneratedModule[K]>> extends true ? never : K }[keyof Unions]
>;

function unreachable(value: never): never {
  throw new Error(`unhandled ${String(value)}`);
}

export function describeOutcome(outcome: Hand.RunOutcome): string {
  switch (outcome.kind) {
    case "completed":
      return String(outcome.value);
    case "failed":
      return outcome.diagnostics[0].message;
    case "parked":
      return outcome.call.print();
    default:
      return unreachable(outcome);
  }
}

export function describeEntry(entry: Hand.LogEntry): string {
  switch (entry.kind) {
    case "start":
      return Object.keys(entry.inputs).join(", ");
    case "reply":
      return entry.call.print();
    case "result":
      return String(entry.value);
    case "failure":
      return entry.diagnostics[0].message;
    default:
      return unreachable(entry);
  }
}

export function describeDecoded(decoded: Hand.DecodedEdition): string {
  switch (decoded.kind) {
    case "intact":
      return decoded.edition.source;
    case "stale":
      return decoded.reason.kind === "unknownPins" ? Object.keys(decoded.reason.pins).join(", ") : decoded.reason.kind;
    default:
      return unreachable(decoded);
  }
}

export function describeChecked(checked: Hand.Checked<string>): string {
  switch (checked.kind) {
    case "accepted":
      return checked.value;
    case "rejected":
      return checked.diagnostics[0].message;
    default:
      return unreachable(checked);
  }
}

export function describeError(error: Hand.HostError): string {
  switch (error.kind) {
    case "diverged":
      return `entry ${error.at}`;
    case "missingHandler":
      return `${error.name}/${error.revision}`;
    default:
      return error.message;
  }
}

export function valueWithoutNarrowing(checked: Hand.Checked<string>): string {
  // @ts-expect-error a checked result has a value only once it is known to be accepted
  return checked.value;
}

export function changeWhatTheBindingHandsOut(contract: Hand.Contract, edition: Hand.Edition): void {
  // @ts-expect-error the arrays the binding hands out are read-only
  contract.declarations.push(contract.declarations[0]);
  // @ts-expect-error the records the binding hands out are read-only
  edition.pins.creditScore = edition.pins.customer;
}

type Constructible<C> = C extends abstract new (...args: never[]) => unknown ? true : false;

export type OnlyTaggedValuesAreBuiltByHand = [
  NoneOf<Constructible<typeof Hand.Diagnostic> extends false ? never : "Diagnostic">,
  NoneOf<Constructible<typeof Hand.Token> extends false ? never : "Token">,
  NoneOf<Constructible<typeof Hand.Pin> extends false ? never : "Pin">,
  NoneOf<Constructible<typeof Hand.KleinError> extends false ? never : "KleinError">,
  NoneOf<Constructible<typeof Hand.TaggedValue> extends true ? never : "TaggedValue">,
];

export function releasesComeFromTheContract(contract: Hand.Contract, declaration: Hand.Declaration): void {
  contract.check("1", contract.releases[0]);
  // @ts-expect-error a plain number is not a release number
  contract.check("1", 1);
  // @ts-expect-error a revision number is not a release number
  contract.check("1", declaration.revision);
}

// @ts-expect-error a number is not a run outcome
export const notAnOutcome: Hand.RunOutcome = 42;

// @ts-expect-error a plain object is not a log entry
export const notAnEntry: Hand.LogEntry = { kind: "start", inputs: {} };
