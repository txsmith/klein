import { test } from "node:test";
import assert from "node:assert/strict";
import {
  Checked,
  ContractDeclaration,
  DecodedEdition,
  EffectLog,
  Klein,
  LogEntry,
  RejectedException,
  ReleaseNumber,
  RunOutcome,
  SourceSpan,
  StaleReason,
  Type,
  Value,
  decodeJson,
  encodeEditionJson,
  encodeJson,
  immediate,
  perRun,
} from "../build/package/index.js";
import {
  acme,
  answeringEnvironment,
  assertCompiles,
  assertConstructed,
  lending,
  lendingWithoutRevision2,
  parkingEnvironment,
  rule,
} from "./fixtures.ts";

const editedLending = lending.replace("fun creditScore/2(c: Customer/2): Num", "fun creditScore/2(customer: Customer/2): Num");

function assertAccepted<T>(checked: Checked<T>): T {
  assert.ok(checked instanceof Checked.Accepted);
  return checked.value;
}

async function assertParked(outcome: Promise<RunOutcome>): Promise<RunOutcome.Parked> {
  const parked = await outcome;
  assert.ok(parked instanceof RunOutcome.Parked);
  return parked;
}

test("tokenize gives each token's kind, span and text", () => {
  const tokens = assertAccepted(Klein.tokenize("x = 1"));
  assert.deepEqual(
    tokens.slice(0, 3).map((it) => [it.kind, it.span.start, it.span.end, it.text]),
    [
      ["IDENT", 0, 1, "x"],
      ["EQ", 2, 3, null],
      ["INT", 4, 5, "1"],
    ],
  );
});

test("a checked contract lists its environment, releases and declarations", () => {
  const contract = Klein.checkContract(lending);
  assert.equal(contract.environment, "lending");
  assert.deepEqual(contract.releases, [1, 2]);
  const score = contract.declarations.find((it) => it.name === "creditScore" && it.revision === 2);
  assert.ok(score instanceof ContractDeclaration.Function);
  assert.equal(Type.print(score.answerType), "Num");
  assert.equal(Type.print(score.type), "(Customer/2) -> Num");
  assert.deepEqual(score.parameterTypes.map(Type.print), ["Customer"]);
  assert.ok(contract.declarations.find((it) => it.name === "customer") instanceof ContractDeclaration.Value);
});

test("checking a rule gives its type, or diagnostics with spans", () => {
  const contract = Klein.checkContract(lending);
  assert.equal(Type.print(assertAccepted(contract.check(rule, ReleaseNumber(1)))), "String");
  const bad = contract.check(`creditScore(customer) + "x"`, ReleaseNumber(1));
  assert.ok(bad instanceof Checked.Rejected);
  assert.ok(bad.diagnostics[0].span instanceof SourceSpan);
  assert.match(bad.diagnostics[0].span.formatInSource(`creditScore(customer) + "x"`), /\^/);
});

test("getOrThrow on a rejected result throws its diagnostics", () => {
  const maybe = Klein.checkContract(`environment maybe

limit: Num?

release 1
  limit
`);
  const limit = maybe.declarations[0].answerType;
  assert.equal(maybe.evaluateValue("null", ReleaseNumber(1), limit).getOrThrow(), null);
  const broken = maybe.evaluateValue(`"x"`, ReleaseNumber(1), limit);
  assert.throws(
    () => broken.getOrThrow(),
    (error) => error instanceof RejectedException && error.diagnostics.length > 0,
  );
});

test("an edition carries its pins with hex hashes", () => {
  const edition = assertCompiles(Klein.checkContract(lending), rule, 2);
  assert.equal(edition.environment, "lending");
  assert.equal(edition.source, rule);
  assert.deepEqual(
    new Map(edition.pins),
    new Map([
      ["Customer", 2],
      ["customer", 2],
      ["creditScore", 2],
    ]),
  );
  const score = edition.pinsWithHash.get("creditScore");
  assert.equal(score?.revision, 2);
  assert.match(String(score?.hash), /^[0-9a-f]{16}$/);
});

test("an encoded edition decodes intact, and stale after an edit in place", () => {
  const json = encodeEditionJson(assertCompiles(Klein.checkContract(lending), rule, 2));
  const intact = Klein.checkContract(lending).decodeEditionJson(json);
  assert.ok(intact instanceof DecodedEdition.Intact);
  assert.equal(intact.edition.source, rule);
  const stale = Klein.checkContract(editedLending).decodeEditionJson(json);
  assert.ok(stale instanceof DecodedEdition.Stale);
  assert.ok(stale.reason instanceof StaleReason.DeclarationChanged);
  assert.equal(stale.source, rule);
  assert.equal(stale.pins.get("creditScore")?.revision, 2);
});

test("an edition pinning a revision the contract no longer declares is stale with its unknown pins", () => {
  const json = encodeEditionJson(assertCompiles(Klein.checkContract(lending), rule, 2));
  const stale = Klein.checkContract(lendingWithoutRevision2).decodeEditionJson(json);
  assert.ok(stale instanceof DecodedEdition.Stale);
  assert.ok(stale.reason instanceof StaleReason.UnknownPins);
  assert.deepEqual(new Map(stale.reason.pins), new Map([["creditScore", 2]]));
});

test("a stale edition's source re-derives against its recorded pins", () => {
  const edition = assertCompiles(Klein.checkContract(lending), rule, 2);
  const contract = Klein.checkContract(editedLending);
  const stale = contract.decodeEditionJson(encodeEditionJson(edition));
  assert.ok(stale instanceof DecodedEdition.Stale);
  const rederived = assertAccepted(contract.compileRule(stale.source, stale.pins));
  assert.ok(contract.decodeEditionJson(encodeEditionJson(rederived)) instanceof DecodedEdition.Intact);
  assert.ok(contract.compileRule(stale.source, rederived.pins) instanceof Checked.Accepted);
});

test("arrays and records the binding hands out cannot be changed", async () => {
  const contract = Klein.checkContract(lending);
  const edition = assertCompiles(contract, rule, 1);
  assert.throws(() => (contract.releases as ReleaseNumber[]).push(ReleaseNumber(9)), TypeError);
  const parked = await assertParked(parkingEnvironment(contract).run(edition));
  const customer = assertConstructed(parked.log.start.inputs.get("customer"), "Customer");
  assert.throws(() => {
    (customer as { name: Value }).name = "Bolt";
  }, TypeError);
  assert.throws(() => (parked.log.entries as LogEntry[]).pop(), TypeError);
  assert.throws(() => {
    (parked.call.args as Value[])[0] = null;
  }, TypeError);
  const inputs = parked.log.start.inputs as Map<string, Value>;
  assert.throws(() => inputs.set("customer", 5), TypeError);
  assert.deepEqual({ ...assertConstructed(inputs.get("customer"), "Customer") }, { id: 1, name: "Acme" });
  assert.throws(() => (edition.pins as Map<string, unknown>).delete("creditScore"), TypeError);
  assert.throws(() => (edition.pinsWithHash as Map<string, unknown>).clear(), TypeError);
  assert.equal(edition.pins.get("creditScore"), 1);
});

test("reading a property twice gives the same object", async () => {
  const contract = Klein.checkContract(lending);
  const edition = assertCompiles(contract, rule, 1);
  const parked = await assertParked(parkingEnvironment(contract).run(edition));
  assert.equal(parked.log, parked.log);
  assert.equal(parked.log.entries, parked.log.entries);
  assert.equal(parked.log.start, parked.log.start);
  assert.equal(parked.call, parked.call);
  assert.equal(parked.call.args[0], parked.call.args[0]);
  assert.equal(edition.pins, edition.pins);
  assert.equal(edition.pinsWithHash.get("creditScore"), edition.pinsWithHash.get("creditScore"));
  assert.equal(contract.declarations, contract.declarations);
});

test("a log built from entries of the wrong kind is refused with a TypeError", () => {
  const result = new LogEntry.Result(1);
  assert.throws(() => new EffectLog(result as never), TypeError);
  const start = new LogEntry.Start(new Map());
  assert.throws(() => new EffectLog(start, [result as never]), TypeError);
  assert.throws(() => new EffectLog(start, [], start as never), TypeError);
});

test("a run parks on a deferred call and resumes from its log", async () => {
  const contract = Klein.checkContract(lending);
  const environment = parkingEnvironment(contract);
  const edition = assertCompiles(contract, rule, 1);

  const parked = await assertParked(environment.run(edition));
  assert.equal(parked.call.name, "creditScore");
  const customer = assertConstructed(parked.call.args[0], "Customer");
  assert.equal(customer.name, "Acme");
  assert.deepEqual(Object.keys(customer), ["id", "name"]);
  assert.equal(JSON.stringify(customer), `{"id":1,"name":"Acme"}`);
  assert.equal(parked.call.print(), `creditScore(Customer(1, "Acme"))`);

  const done = await environment.run(edition, parked.log.plus(parked.toReply(650)));
  assert.ok(done instanceof RunOutcome.Completed);
  assert.equal(done.value, "approve");
  const [start, reply, result] = done.log.entries;
  assert.ok(start instanceof LogEntry.Start);
  assert.ok(reply instanceof LogEntry.Reply);
  assert.equal(reply.answer, 650);
  assert.ok(result instanceof LogEntry.Result);
});

test("a log built from its entries replays like one the run produced", async () => {
  const contract = Klein.checkContract(lending);
  const environment = parkingEnvironment(contract);
  const edition = assertCompiles(contract, rule, 1);
  const parked = await assertParked(environment.run(edition));
  const built = new EffectLog(new LogEntry.Start(parked.log.start.inputs), [parked.toReply(650)]);
  const done = await environment.run(edition, built);
  assert.ok(done instanceof RunOutcome.Completed);
  assert.equal(done.value, "approve");
});

test("a log round-trips through JSON and replays without asking the host", async () => {
  const contract = Klein.checkContract(lending);
  const edition = assertCompiles(contract, rule, 1);
  const parked = await assertParked(parkingEnvironment(contract).run(edition));
  const done = await parkingEnvironment(contract).run(edition, parked.log.plus(parked.toReply(500)));

  const asked: string[] = [];
  const strict = contract.implement(
    immediate("customer", () => {
      asked.push("customer");
      return acme;
    }),
    immediate("customer/2", () => {
      asked.push("customer/2");
      return acme;
    }),
    immediate("creditScore", () => {
      asked.push("creditScore");
      return 0;
    }),
    immediate("creditScore/2", () => {
      asked.push("creditScore/2");
      return 0;
    }),
  );
  const replayed = await strict.run(edition, decodeJson(encodeJson(done.log)));
  assert.ok(replayed instanceof RunOutcome.Completed);
  assert.equal(replayed.value, "decline");
  assert.deepEqual(asked, []);
});

test("a failed run's log replays to the same failure", async () => {
  const contract = Klein.checkContract(lending);
  const edition = assertCompiles(contract, "1 / creditScore(customer)", 1);
  const failed = await answeringEnvironment(contract, 0).run(edition);
  assert.ok(failed instanceof RunOutcome.Failed);
  const replayed = await answeringEnvironment(contract, 0).run(edition, failed.log);
  assert.ok(replayed instanceof RunOutcome.Failed);
  assert.equal(replayed.diagnostics[0].message, "Division by zero");
});

test("per-run registrations come with the run, and persist receives each new entry", async () => {
  const contract = Klein.checkContract(lending);
  const persisted: string[] = [];
  const environment = contract
    .implement(immediate("customer", () => acme), perRun("customer/2"), immediate("creditScore", () => 700), perRun("creditScore/2"))
    .withPersister((entry) => void persisted.push(entry.constructor.name));
  const outcome = await environment.run(
    assertCompiles(contract, rule, 1),
    immediate("customer/2", () => acme),
    immediate("creditScore/2", () => 0),
  );
  assert.ok(outcome instanceof RunOutcome.Completed);
  assert.deepEqual(persisted, ["Start", "Reply", "Result"]);
});

test("transact wraps each unit of host work", async () => {
  const contract = Klein.checkContract(lending);
  let units = 0;
  const environment = answeringEnvironment(contract, 700, {
    async transact(block) {
      units++;
      return block();
    },
  });
  await environment.run(assertCompiles(contract, rule, 1));
  assert.equal(units, 3);
});

test("handlers, persist and transact may wait on promises", async () => {
  const contract = Klein.checkContract(lending);
  const later = <T>(value: T) => new Promise<T>((resolve) => setTimeout(() => resolve(value), 1));
  const trace: string[] = [];
  const environment = contract
    .implement(
      immediate("customer", () => later(acme)),
      immediate("customer/2", () => acme),
      immediate("creditScore", async (customer) => {
        trace.push(`score ${assertConstructed(customer, "Customer").name}`);
        return later(650);
      }),
      immediate("creditScore/2", () => 0),
    )
    .withTransactor({
      async transact(block) {
        trace.push("begin");
        const result = await block();
        await later(null);
        trace.push("commit");
        return result;
      },
    })
    .withPersister(async (entry) => {
      await later(null);
      trace.push(`persist ${entry.constructor.name}`);
    });
  const outcome = await environment.run(assertCompiles(contract, rule, 1));
  assert.ok(outcome instanceof RunOutcome.Completed);
  assert.equal(outcome.value, "approve");
  assert.deepEqual(trace, [
    "begin",
    "persist Start",
    "commit",
    "begin",
    "score Acme",
    "persist Reply",
    "commit",
    "begin",
    "persist Result",
    "commit",
  ]);
});

test("a handler's own error or rejection reaches the caller unchanged", async () => {
  const contract = Klein.checkContract(lending);
  const boom = new TypeError("boom");
  const failing = (score: () => Value | Promise<Value>) =>
    contract.implement(
      immediate("customer", () => acme),
      immediate("customer/2", () => acme),
      immediate("creditScore", score),
      immediate("creditScore/2", () => 0),
    );
  const throwing = failing(() => {
    throw boom;
  });
  await assert.rejects(throwing.run(assertCompiles(contract, rule, 1)), (error) => error === boom);
  const rejecting = failing(() => Promise.reject(boom));
  await assert.rejects(rejecting.run(assertCompiles(contract, rule, 1)), (error) => error === boom);
});

test("a handler that throws or rejects with something other than an Error reaches the caller unchanged", async () => {
  const contract = Klein.checkContract(lending);
  const environment = (score: () => Value | Promise<Value>) =>
    contract.implement(
      immediate("customer", () => acme),
      immediate("customer/2", () => acme),
      immediate("creditScore", score),
      immediate("creditScore/2", () => 0),
    );
  const edition = assertCompiles(contract, rule, 1);
  const thrown = environment(() => {
    throw "plain string";
  });
  await assert.rejects(thrown.run(edition), (error) => error === "plain string");
  await assert.rejects(environment(() => Promise.reject("plain string")).run(edition), (error) => error === "plain string");
  await assert.rejects(environment(() => Promise.reject(undefined)).run(edition), (error) => error === undefined);
  const persisting = environment(() => 700).withPersister(() => Promise.reject(42));
  await assert.rejects(persisting.run(edition), (error) => error === 42);
});

test("a rule failing at runtime is a failed outcome with diagnostics", async () => {
  const contract = Klein.checkContract(lending);
  const outcome = await answeringEnvironment(contract, 0).run(assertCompiles(contract, "1 / creditScore(customer)", 1));
  assert.ok(outcome instanceof RunOutcome.Failed);
  assert.equal(outcome.diagnostics[0].message, "Division by zero");
});

test("an answer written in Klein is checked against the expected type and evaluated", () => {
  const contract = Klein.checkContract(lending);
  const customer = contract.declarations.find((it) => it.name === "customer" && it.revision === 1)!.answerType;
  assert.equal(Type.print(customer), "Customer");
  const value = assertConstructed(assertAccepted(contract.evaluateValue(`Customer(2, "Bolt")`, ReleaseNumber(1), customer)), "Customer");
  assert.deepEqual({ ...value }, { id: 2, name: "Bolt" });
  const wrong = contract.evaluateValue(`"Bolt"`, ReleaseNumber(1), customer);
  assert.ok(wrong instanceof Checked.Rejected);
  const score = contract.declarations.find((it) => it.name === "creditScore")!.answerType;
  const failing = contract.evaluateValue("1 / 0", ReleaseNumber(1), score);
  assert.ok(failing instanceof Checked.Rejected);
  assert.equal(failing.diagnostics[0].message, "Division by zero");
});

test("records cross as plain objects", async () => {
  const contract = Klein.checkContract(`environment shapes

fun area(r: { w: Num, h: Num }): Num

release 1
  area
`);
  const environment = contract.implement(
    immediate("area", (r) => {
      const { w, h } = r as { w: number; h: number };
      return w * h;
    }),
  );
  const outcome = await environment.run(assertCompiles(contract, "area({ w = 2, h = 3 })", 1));
  assert.ok(outcome instanceof RunOutcome.Completed);
  assert.equal(outcome.value, 6);
});

test("a constructed value is read like a record with the same fields", async () => {
  const contract = Klein.checkContract(`environment shapes

type Rect = Rect { w: Num, h: Num }

fun area(r: { w: Num, h: Num }): Num

release 1
  Rect
  area
`);
  const areas: string[] = [];
  const environment = contract.implement(
    immediate("area", (r) => {
      const { w, h } = r as { w: number; h: number };
      areas.push((r as object).constructor.name);
      return w * h;
    }),
  );
  const outcome = await environment.run(assertCompiles(contract, "area(Rect(2, 3))", 1));
  assert.ok(outcome instanceof RunOutcome.Completed);
  assert.equal(outcome.value, 6);
  assert.deepEqual(areas, ["Rect"]);
});

test("values print the way Klein writes them", () => {
  assert.equal(Value.print(acme), `Customer(1, "Acme")`);
  assert.equal(Value.print({ w: 2, h: 3 }), "{ w = 2, h = 3 }");
  assert.equal(Value.print(undefined), "()");
});

test("something that is not a Klein value is refused with a TypeError", () => {
  class Unnamed {
    readonly id = 1;
  }
  assert.throws(() => Value.print([1, 2]), TypeError);
  assert.throws(() => Value.print(new Date()), TypeError);
  assert.throws(() => Value.print(new Unnamed()), /static kleinName/);
  assert.throws(() => ReleaseNumber(1.5), TypeError);
});
