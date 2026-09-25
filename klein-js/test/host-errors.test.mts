import { test } from "node:test";
import assert from "node:assert/strict";
import type { HostError, ReleaseNumber } from "../build/dist/js/productionLibrary/index.d.mts";
import {
  acme,
  answeringEnvironment,
  compile,
  klein,
  lending,
  lendingWithoutRevision2,
  parkingEnvironment,
  rule,
} from "./fixtures.mts";

type Kind = HostError["kind"];

type Case<K extends Kind> = {
  trigger: () => unknown;
  check: (error: Extract<HostError, { kind: K }>) => void;
};

const cases: { [K in Kind]: Case<K> } = {
  invalidContract: {
    trigger: () => klein.checkContract("environment lending\nfun f(x: Nope): Num\n"),
    check: (error) => assert.equal(error.diagnostics[0].start, 29),
  },
  unknownRelease: {
    trigger: () => klein.checkContract(lending).check(rule, 9 as ReleaseNumber),
    check: (error) => {
      assert.equal(error.number, 9);
      assert.deepEqual(error.available, [1, 2]);
    },
  },
  unknownPin: {
    trigger: () => {
      const pins = compile(klein.checkContract(lending), rule, 2).pinsWithHash;
      return klein.checkContract(lendingWithoutRevision2).compileRuleAtPins(rule, pins);
    },
    check: (error) => {
      assert.equal(error.name, "creditScore");
      assert.equal(error.revision, 2);
    },
  },
  wrongEnvironment: {
    trigger: () => {
      const json = compile(klein.checkContract(lending), rule, 1).encodeJson();
      return klein.checkContract(lending.replace("environment lending", "environment other")).decodeEditionJson(json);
    },
    check: (error) => {
      assert.equal(error.edition, "lending");
      assert.equal(error.environment, "other");
    },
  },
  missingHandler: {
    trigger: () => {
      const contract = klein.checkContract(lending);
      const environment = contract.implement([
        klein.immediate("customer", () => acme),
        klein.perRun("customer/2"),
        klein.immediate("creditScore", () => 700),
        klein.immediate("creditScore/2", () => 700),
      ]);
      return environment.run(compile(contract, rule, 1));
    },
    check: (error) => {
      assert.equal(error.name, "customer");
      assert.equal(error.revision, 2);
    },
  },
  logTypeMismatch: {
    trigger: async () => {
      const contract = klein.checkContract(lending);
      const environment = parkingEnvironment(contract);
      const edition = compile(contract, rule, 1);
      const parked = await environment.run(edition);
      assert.ok(parked.kind === "parked");
      const json = parked.log.append(parked.toReply(650)).encodeJson();
      assert.ok(json.includes(`"answer":650`));
      return environment.run(edition, [], klein.decodeLogJson(json.replace(`"answer":650`, `"answer":"high"`)));
    },
    check: (error) => {
      assert.equal(error.at, 1);
      assert.equal(error.name, "creditScore");
      assert.equal(error.answerType, "String");
      assert.equal(error.declaredType, "Num");
    },
  },
  diverged: {
    trigger: async () => {
      const contract = klein.checkContract(lending);
      const environment = parkingEnvironment(contract);
      const parked = await environment.run(compile(contract, rule, 1));
      assert.ok(parked.kind === "parked");
      const other = compile(contract, `creditScore(Customer(2, "Bolt"))`, 1);
      return environment.run(other, [], parked.log.append(parked.toReply(650)));
    },
    check: (error) => {
      assert.equal(error.at, 1);
      assert.equal(error.expected, `a call to creditScore(Customer(1, "Acme"))`);
      assert.equal(error.got, `a call to creditScore(Customer(2, "Bolt"))`);
      assert.equal(error.call?.print(), `creditScore(Customer(2, "Bolt"))`);
    },
  },
  callTypeMismatch: {
    trigger: () => {
      const takesNumber = klein.checkContract("environment calls\n\nfun f(x: Num): Num\n\nrelease 1\n  f\n");
      const takesString = klein.checkContract("environment calls\n\nfun f(x: String): Num\n\nrelease 1\n  f\n");
      return takesString.implement([klein.immediate("f", () => 1)]).run(compile(takesNumber, "f(1)", 1));
    },
    check: (error) => {
      assert.equal(error.call, "f");
      assert.equal(error.got, "Num");
      assert.equal(error.declared, "String");
    },
  },
  handlerTypeMismatch: {
    trigger: () => {
      const contract = klein.checkContract(lending);
      return answeringEnvironment(contract, "high").run(compile(contract, rule, 1));
    },
    check: (error) => {
      assert.equal(error.call, "creditScore");
      assert.equal(error.answerType, "String");
      assert.equal(error.declaredType, "Num");
    },
  },
  registrationError: {
    trigger: () =>
      klein.checkContract(lending).implement([
        klein.immediate("customer", () => acme),
        klein.immediate("customer/2", () => acme),
        klein.immediate("creditScore", () => 700),
        klein.immediate("creditScore/2", () => 700),
        klein.immediate("nope", () => 1),
      ]),
    check: (error) => assert.match(error.message, /'nope'/),
  },
  unreadableEdition: {
    trigger: () => klein.checkContract(lending).decodeEditionJson("{}"),
    check: (error) => assert.match(error.message, /not a Klein edition/),
  },
  unreadableLog: {
    trigger: () => klein.decodeLogJson("{}"),
    check: (error) => assert.match(error.message, /log/),
  },
  unsupportedValue: {
    trigger: () => klein.printValue([1, 2] as never),
    check: (error) => assert.match(error.message, /is not a Klein value/),
  },
  logAlreadyEnded: {
    trigger: async () => {
      const contract = klein.checkContract(lending);
      const edition = compile(contract, rule, 1);
      const environment = parkingEnvironment(contract);
      const parked = await environment.run(edition);
      assert.ok(parked.kind === "parked");
      const done = await environment.run(edition, [], parked.log.append(parked.toReply(650)));
      return done.log.append(parked.toReply(650));
    },
    check: (error) => {
      assert.ok(error.ending.kind === "result");
      assert.equal(error.ending.value, "approve");
    },
  },
  secondStartEntry: {
    trigger: async () => {
      const contract = klein.checkContract(lending);
      const parked = await parkingEnvironment(contract).run(compile(contract, rule, 1));
      return parked.log.append(parked.log.entries[0]);
    },
    check: () => {},
  },
  transactionSkippedBlock: {
    trigger: () => {
      const contract = klein.checkContract(lending);
      return answeringEnvironment(contract, 700, () => {}).run(compile(contract, rule, 1));
    },
    check: () => {},
  },
};

for (const [kind, { trigger, check }] of Object.entries(cases) as [Kind, Case<Kind>][]) {
  test(`the host error ${kind} crosses with its fields`, async () => {
    let thrown: HostError | undefined;
    await assert.rejects(async () => trigger(), (error) => {
      assert.ok(error instanceof klein.KleinError);
      assert.equal(error.errors.length, 1);
      thrown = error.errors[0];
      return true;
    });
    assert.equal(thrown?.kind, kind);
    check(thrown as never);
  });
}

test("a TaggedValue with fields Klein has no form for is refused when it is built", () => {
  for (const build of [
    () => new klein.TaggedValue("Customer", null as never),
    () => new klein.TaggedValue("Customer", [1] as never),
    () => new klein.TaggedValue(7 as never, {}),
  ]) {
    assert.throws(build, (error) => error instanceof klein.KleinError && error.errors[0].kind === "unsupportedValue");
  }
});
