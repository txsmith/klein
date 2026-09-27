import { test } from "node:test";
import assert from "node:assert/strict";
import {
  CallTypeMismatch,
  Diverged,
  HandlerTypeMismatch,
  InvalidContract,
  Klein,
  KleinException,
  LogAlreadyEnded,
  LogEntry,
  LogTypeMismatch,
  MissingHandler,
  RegistrationError,
  ReleaseNumber,
  RunOutcome,
  SecondStartEntry,
  Type,
  UnknownPin,
  UnknownRelease,
  UnreadableEdition,
  UnreadableLog,
  WrongEnvironment,
  decodeJson,
  encodeEditionJson,
  encodeJson,
  immediate,
  perRun,
  type HostError,
} from "../build/package/index.js";
import {
  acme,
  answeringEnvironment,
  assertCompiles,
  lending,
  lendingWithoutRevision2,
  parkingEnvironment,
  rule,
} from "./fixtures.ts";

type Case<E extends HostError> = {
  type: Function & { prototype: E };
  trigger: () => unknown;
  check: (error: E) => void;
};

function hostError<E extends HostError>(type: Function & { prototype: E }, trigger: () => unknown, check: (error: E) => void): Case<E> {
  return { type, trigger, check };
}

async function parked(): Promise<RunOutcome.Parked> {
  const contract = Klein.checkContract(lending);
  const outcome = await parkingEnvironment(contract).run(assertCompiles(contract, rule, 1));
  assert.ok(outcome instanceof RunOutcome.Parked);
  return outcome;
}

const cases = [
  hostError(
    InvalidContract,
    () => Klein.checkContract("environment lending\nfun f(x: Nope): Num\n"),
    (error) => assert.equal(error.diagnostics[0].span.start, 29),
  ),
  hostError(
    UnknownRelease,
    () => Klein.checkContract(lending).check(rule, ReleaseNumber(9)),
    (error) => {
      assert.equal(error.number, 9);
      assert.deepEqual(error.available, [1, 2]);
    },
  ),
  hostError(
    UnknownPin,
    () => {
      const pins = assertCompiles(Klein.checkContract(lending), rule, 2).pinsWithHash;
      return Klein.checkContract(lendingWithoutRevision2).compileRule(rule, pins);
    },
    (error) => {
      assert.equal(error.name, "creditScore");
      assert.equal(error.revision, 2);
    },
  ),
  hostError(
    WrongEnvironment,
    () => {
      const json = encodeEditionJson(assertCompiles(Klein.checkContract(lending), rule, 1));
      return Klein.checkContract(lending.replace("environment lending", "environment other")).decodeEditionJson(json);
    },
    (error) => {
      assert.equal(error.edition, "lending");
      assert.equal(error.environment, "other");
    },
  ),
  hostError(
    MissingHandler,
    () => {
      const contract = Klein.checkContract(lending);
      const environment = contract.implement(
        immediate("customer", () => acme),
        perRun("customer/2"),
        immediate("creditScore", () => 700),
        immediate("creditScore/2", () => 700),
      );
      return environment.run(assertCompiles(contract, rule, 1));
    },
    (error) => {
      assert.equal(error.name, "customer");
      assert.equal(error.revision, 2);
    },
  ),
  hostError(
    LogTypeMismatch,
    async () => {
      const contract = Klein.checkContract(lending);
      const outcome = await parked();
      const json = encodeJson(outcome.log.plus(outcome.toReply(650)));
      assert.ok(json.includes(`"answer":650`));
      const log = decodeJson(json.replace(`"answer":650`, `"answer":"high"`));
      return parkingEnvironment(contract).run(assertCompiles(contract, rule, 1), log);
    },
    (error) => {
      assert.equal(error.at, 1);
      assert.equal(error.name, "creditScore");
      assert.equal(error.answerType, "String");
      assert.equal(Type.print(error.declaredType), "Num");
    },
  ),
  hostError(
    Diverged,
    async () => {
      const contract = Klein.checkContract(lending);
      const outcome = await parked();
      const other = assertCompiles(contract, `creditScore(Customer(2, "Bolt"))`, 1);
      return parkingEnvironment(contract).run(other, outcome.log.plus(outcome.toReply(650)));
    },
    (error) => {
      assert.equal(error.at, 1);
      assert.equal(error.expected, `a call to creditScore(Customer(1, "Acme"))`);
      assert.equal(error.got, `a call to creditScore(Customer(2, "Bolt"))`);
      assert.equal(error.call?.print(), `creditScore(Customer(2, "Bolt"))`);
    },
  ),
  hostError(
    CallTypeMismatch,
    () => {
      const takesNumber = Klein.checkContract("environment calls\n\nfun f(x: Num): Num\n\nrelease 1\n  f\n");
      const takesString = Klein.checkContract("environment calls\n\nfun f(x: String): Num\n\nrelease 1\n  f\n");
      return takesString.implement(immediate("f", () => 1)).run(assertCompiles(takesNumber, "f(1)", 1));
    },
    (error) => {
      assert.equal(error.call, "f");
      assert.equal(error.got, "Num");
      assert.equal(error.declared, "String");
    },
  ),
  hostError(
    HandlerTypeMismatch,
    () => {
      const contract = Klein.checkContract(lending);
      return answeringEnvironment(contract, "high").run(assertCompiles(contract, rule, 1));
    },
    (error) => {
      assert.equal(error.call, "creditScore");
      assert.equal(error.answerType, "String");
      assert.equal(Type.print(error.declaredType), "Num");
    },
  ),
  hostError(
    RegistrationError,
    () =>
      Klein.checkContract(lending).implement(
        immediate("customer", () => acme),
        immediate("customer/2", () => acme),
        immediate("creditScore", () => 700),
        immediate("creditScore/2", () => 700),
        immediate("nope", () => 1),
      ),
    (error) => assert.match(error.message, /'nope'/),
  ),
  hostError(
    UnreadableEdition,
    () => Klein.checkContract(lending).decodeEditionJson("{}"),
    (error) => assert.match(error.message, /not a Klein edition/),
  ),
  hostError(
    UnreadableLog,
    () => decodeJson("{}"),
    (error) => assert.match(error.message, /log/),
  ),
  hostError(
    LogAlreadyEnded,
    async () => {
      const contract = Klein.checkContract(lending);
      const outcome = await parked();
      const done = await parkingEnvironment(contract).run(assertCompiles(contract, rule, 1), outcome.log.plus(outcome.toReply(650)));
      return done.log.plus(outcome.toReply(650));
    },
    (error) => {
      assert.ok(error.ending instanceof LogEntry.Result);
      assert.equal(error.ending.value, "approve");
    },
  ),
  hostError(
    SecondStartEntry,
    async () => {
      const outcome = await parked();
      return outcome.log.plus(outcome.log.start);
    },
    () => {},
  ),
];

type Tested<C> = C extends Case<infer E> ? E : never;
type Missing = Exclude<HostError, Tested<(typeof cases)[number]>>;
const everyHostErrorHasACase: [Missing] extends [never] ? true : Missing = true;
assert.ok(everyHostErrorHasACase);

for (const { type, trigger, check } of cases) {
  test(`the host error ${type.name} crosses with its fields`, async () => {
    let thrown: HostError | undefined;
    await assert.rejects(
      async () => trigger(),
      (error) => {
        assert.ok(error instanceof KleinException);
        assert.equal(error.errors.length, 1);
        thrown = error.errors[0];
        return true;
      },
    );
    assert.ok(thrown instanceof type);
    (check as (error: HostError) => void)(thrown);
  });
}
