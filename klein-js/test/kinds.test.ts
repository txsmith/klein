import { test } from "node:test";
import assert from "node:assert/strict";
import {
  Checked,
  ContractDeclaration,
  DecodedEdition,
  Klein,
  LogEntry,
  ReleaseNumber,
  RunOutcome,
  StaleReason,
  encodeEditionJson,
} from "../build/package/index.js";
import {
  answeringEnvironment,
  assertCompiles,
  lending,
  lendingWithoutRevision2,
  parkingEnvironment,
  rule,
} from "./fixtures.ts";

type Case<E> = {
  name: string;
  produce: () => unknown;
  assertKind: (value: unknown) => E;
};

function produces<E>(type: Function & { prototype: E }, produce: () => unknown): Case<E> {
  const assertKind = (value: unknown): E => {
    assert.ok(value instanceof type);
    return value as E;
  };
  return { name: type.name, produce, assertKind };
}

type Tested<C> = C extends { assertKind: (value: unknown) => infer E } ? E : never;

type Missing<U, C> = Exclude<U, Tested<C>>;

function testCases(union: string, cases: readonly Case<unknown>[]): void {
  for (const { name, produce, assertKind } of cases) {
    test(`the library produces a ${union} ${name}`, async () => {
      assertKind(await produce());
    });
  }
}

const contract = Klein.checkContract(lending);
const edition = assertCompiles(contract, rule, 1);
const failing = assertCompiles(contract, "1 / creditScore(customer)", 1);
const editedInPlace = lending.replace("fun creditScore(c: Customer): Num", "fun creditScore(customer: Customer): Num");

async function findEntry(outcome: Promise<RunOutcome>, type: Function): Promise<LogEntry> {
  const entry = (await outcome).log.entries.find((it) => it instanceof type);
  assert.ok(entry !== undefined);
  return entry;
}

function findReason(contractSource: string, json: string): StaleReason {
  const decoded = Klein.checkContract(contractSource).decodeEditionJson(json);
  assert.ok(decoded instanceof DecodedEdition.Stale);
  return decoded.reason;
}

const runOutcomes = [
  produces(RunOutcome.Completed, () => answeringEnvironment(contract, 700).run(edition)),
  produces(RunOutcome.Failed, () => answeringEnvironment(contract, 0).run(failing)),
  produces(RunOutcome.Parked, () => parkingEnvironment(contract).run(edition)),
];

const logEntries = [
  produces(LogEntry.Start, () => findEntry(answeringEnvironment(contract, 700).run(edition), LogEntry.Start)),
  produces(LogEntry.Reply, () => findEntry(answeringEnvironment(contract, 700).run(edition), LogEntry.Reply)),
  produces(LogEntry.Result, () => findEntry(answeringEnvironment(contract, 700).run(edition), LogEntry.Result)),
  produces(LogEntry.Failure, () => findEntry(answeringEnvironment(contract, 0).run(failing), LogEntry.Failure)),
];

const decodedEditions = [
  produces(DecodedEdition.Intact, () => contract.decodeEditionJson(encodeEditionJson(edition))),
  produces(DecodedEdition.Stale, () => Klein.checkContract(editedInPlace).decodeEditionJson(encodeEditionJson(edition))),
];

const staleReasons = [
  produces(StaleReason.ChecksumMismatch, () => findReason(lending, encodeEditionJson(edition).replace("600", "601"))),
  produces(StaleReason.DeclarationChanged, () => findReason(editedInPlace, encodeEditionJson(edition))),
  produces(StaleReason.UnknownPins, () =>
    findReason(lendingWithoutRevision2, encodeEditionJson(assertCompiles(contract, rule, 2))),
  ),
];

const unreachableStaleReasons = [StaleReason.LanguageChanged, StaleReason.CompilerChanged];

const contractDeclarations = [
  produces(ContractDeclaration.Function, () => contract.declarations.find((it) => it.name === "creditScore")),
  produces(ContractDeclaration.Value, () => contract.declarations.find((it) => it.name === "customer")),
];

const checkeds = [
  produces(Checked.Accepted, () => contract.check(rule, ReleaseNumber(1))),
  produces(Checked.Rejected, () => contract.check("nope", ReleaseNumber(1))),
];

type Covered<M> = [M] extends [never] ? true : M;

const everyRunOutcome: Covered<Missing<RunOutcome, (typeof runOutcomes)[number]>> = true;
const everyLogEntry: Covered<Missing<LogEntry, (typeof logEntries)[number]>> = true;
const everyDecodedEdition: Covered<Missing<DecodedEdition, (typeof decodedEditions)[number]>> = true;
const everyStaleReason: Covered<
  Exclude<Missing<StaleReason, (typeof staleReasons)[number]>, (typeof unreachableStaleReasons)[number]["prototype"]>
> = true;
const everyContractDeclaration: Covered<Missing<ContractDeclaration, (typeof contractDeclarations)[number]>> = true;
const everyChecked: Covered<Missing<Checked<unknown>, (typeof checkeds)[number]>> = true;
assert.ok(everyRunOutcome && everyLogEntry && everyDecodedEdition && everyStaleReason && everyContractDeclaration && everyChecked);

testCases("run outcome", runOutcomes);
testCases("log entry", logEntries);
testCases("decoded edition", decodedEditions);
testCases("stale reason", staleReasons);
testCases("contract declaration", contractDeclarations);
testCases("checked result", checkeds);
