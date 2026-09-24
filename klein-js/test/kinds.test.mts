import { test } from "node:test";
import assert from "node:assert/strict";
import type {
  Checked,
  Declaration,
  DecodedEdition,
  LogEntry,
  RunOutcome,
  StaleReason,
} from "../build/dist/js/productionLibrary/index.d.mts";
import {
  answeringEnvironment,
  assertCompiles,
  klein,
  lending,
  lendingWithoutRevision2,
  parkingEnvironment,
  rule,
} from "./fixtures.mts";

type Kinded = { kind: string };
type Cases<U extends Kinded> = { [K in U["kind"]]: () => Kinded | Promise<Kinded> };

function testKinds<U extends Kinded>(union: string, cases: Cases<U>): void {
  for (const [kind, produce] of Object.entries(cases) as [string, () => Kinded | Promise<Kinded>][]) {
    test(`a ${union} of kind ${kind} carries that kind at runtime`, async () => {
      assert.equal((await produce()).kind, kind);
    });
  }
}

const contract = klein.checkContract(lending);
const edition = assertCompiles(contract, rule, 1);
const failing = assertCompiles(contract, "1 / creditScore(customer)", 1);
const editedInPlace = lending.replace("fun creditScore(c: Customer): Num", "fun creditScore(customer: Customer): Num");

async function completed(): Promise<RunOutcome> {
  return answeringEnvironment(contract, 700).run(edition);
}

async function failed(): Promise<RunOutcome> {
  return answeringEnvironment(contract, 0).run(failing);
}

async function entryOf(outcome: Promise<RunOutcome>, kind: LogEntry["kind"]): Promise<LogEntry> {
  const entry = (await outcome).log.entries.find((it) => it.kind === kind);
  assert.ok(entry !== undefined);
  return entry;
}

function decode(contractSource: string, json: string): DecodedEdition {
  return klein.checkContract(contractSource).decodeEditionJson(json);
}

function reasonOf(decoded: DecodedEdition): StaleReason {
  assert.ok(decoded.kind === "stale");
  return decoded.reason;
}

testKinds<RunOutcome>("run outcome", {
  completed,
  failed,
  parked: () => parkingEnvironment(contract).run(edition),
});

testKinds<LogEntry>("log entry", {
  start: () => entryOf(completed(), "start"),
  reply: () => entryOf(completed(), "reply"),
  result: () => entryOf(completed(), "result"),
  failure: () => entryOf(failed(), "failure"),
});

testKinds<DecodedEdition>("decoded edition", {
  intact: () => decode(lending, edition.encodeJson()),
  stale: () => decode(editedInPlace, edition.encodeJson()),
});

testKinds<Exclude<StaleReason, { kind: "languageChanged" | "compilerChanged" }>>("stale reason", {
  checksumMismatch: () => reasonOf(decode(lending, edition.encodeJson().replace("600", "601"))),
  declarationChanged: () => reasonOf(decode(editedInPlace, edition.encodeJson())),
  unknownPins: () => reasonOf(decode(lendingWithoutRevision2, assertCompiles(contract, rule, 2).encodeJson())),
});

testKinds<Checked<string>>("checked result", {
  accepted: () => contract.check(rule, contract.releases[0]),
  rejected: () => contract.check("nope", contract.releases[0]),
});

testKinds<Declaration>("declaration", {
  fun: () => contract.declarations.find((it) => it.name === "creditScore")!,
  value: () => contract.declarations.find((it) => it.name === "customer")!,
});
