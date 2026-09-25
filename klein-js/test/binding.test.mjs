import { test } from "node:test";
import assert from "node:assert/strict";
import {
  checkContract,
  tokenize,
  decodeLogJson,
  printValue,
  immediate,
  perRun,
  TaggedValue,
  Accepted,
  Rejected,
  RejectedError,
  Intact,
  DeclarationChanged,
  UnknownPins,
  Stale,
  Completed,
  Failed,
  Parked,
  StartEntry,
  ReplyEntry,
  ResultEntry,
} from "../build/dist/js/productionLibrary/klein.mjs";
import {
  acme,
  answeringEnvironment,
  compile,
  lending,
  lendingWithoutRevision2,
  parkingEnvironment,
  rule,
} from "./fixtures.mts";

test("tokenize gives each token's kind and span", () => {
  const tokens = tokenize("x = 1").value;
  assert.deepEqual(
    tokens.map((t) => [t.kind, t.start, t.end]).slice(0, 3),
    [["IDENT", 0, 1], ["EQ", 2, 3], ["INT", 4, 5]],
  );
});

test("a checked contract lists its environment, releases and declarations", () => {
  const contract = checkContract(lending);
  assert.equal(contract.environment, "lending");
  assert.deepEqual(contract.releases, [1, 2]);
  const score = contract.declarations.find((d) => d.name === "creditScore" && d.revision === 2);
  assert.equal(score.kind, "fun");
  assert.equal(score.answerType.print(), "Num");
  assert.equal(score.type.print(), "(Customer/2) -> Num");
});

test("checking a rule gives its type, or diagnostics with spans", () => {
  const contract = checkContract(lending);
  assert.equal(contract.check(rule, 1).value, "String");
  const bad = contract.check(`creditScore(customer) + "x"`, 1);
  assert.ok(bad instanceof Rejected);
  assert.ok(bad.diagnostics.length > 0);
  assert.equal(typeof bad.diagnostics[0].start, "number");
});

test("reading the value of a rejected result throws instead of giving null", () => {
  const contract = checkContract(lending);
  const maybe = checkContract(`environment maybe

limit: Num?

release 1
  limit
`);
  const limit = maybe.declarations[0].answerType;
  const nothing = maybe.evaluateValue("null", 1, limit);
  assert.ok(nothing instanceof Accepted);
  assert.equal(nothing.value, null);
  const broken = maybe.evaluateValue(`"x"`, 1, limit);
  assert.ok(broken instanceof Rejected);
  assert.throws(
    () => broken.value,
    (error) => error instanceof RejectedError && error.diagnostics.length > 0,
  );
  assert.ok(contract.check("nope", 1) instanceof Rejected);
});

test("an edition carries its pins with hex hashes", () => {
  const edition = compile(checkContract(lending), rule, 2);
  assert.equal(edition.environment, "lending");
  assert.equal(edition.source, rule);
  assert.deepEqual({ ...edition.pins }, { Customer: 2, creditScore: 2, customer: 2 });
  const score = edition.pinsWithHash.creditScore;
  assert.equal(score.revision, 2);
  assert.match(score.hash, /^[0-9a-f]{16}$/);
  assert.deepEqual(Object.keys(edition.pinsWithHash).sort(), ["Customer", "creditScore", "customer"]);
});

test("an encoded edition decodes intact, and stale after an edit in place", () => {
  const edition = compile(checkContract(lending), rule, 2);
  const json = edition.encodeJson();
  const intact = checkContract(lending).decodeEditionJson(json);
  assert.ok(intact instanceof Intact);
  assert.equal(intact.edition.source, rule);
  const edited = lending.replace("fun creditScore/2(c: Customer/2): Num", "fun creditScore/2(customer: Customer/2): Num");
  const stale = checkContract(edited).decodeEditionJson(json);
  assert.ok(stale instanceof Stale);
  assert.ok(stale.reason instanceof DeclarationChanged);
  assert.equal(stale.reason.kind, "declarationChanged");
  assert.equal(stale.source, rule);
  assert.equal(stale.pins.creditScore.revision, 2);
  assert.deepEqual([intact.kind, stale.kind], ["intact", "stale"]);
});

test("an edition pinning a revision the contract no longer declares is stale with its unknown pins", () => {
  const json = compile(checkContract(lending), rule, 2).encodeJson();
  const stale = checkContract(lendingWithoutRevision2).decodeEditionJson(json);
  assert.ok(stale instanceof Stale);
  assert.ok(stale.reason instanceof UnknownPins);
  assert.deepEqual({ ...stale.reason.pins }, { creditScore: 2 });
  assert.equal(stale.pins.creditScore, undefined);
});

test("a stale edition's source re-derives against its recorded pins", () => {
  const edition = compile(checkContract(lending), rule, 2);
  const edited = lending.replace("fun creditScore/2(c: Customer/2): Num", "fun creditScore/2(customer: Customer/2): Num");
  const contract = checkContract(edited);
  const stale = contract.decodeEditionJson(edition.encodeJson());
  const rederived = contract.compileRuleAtPins(stale.source, stale.pins);
  assert.ok(rederived instanceof Accepted);
  assert.ok(contract.decodeEditionJson(rederived.value.encodeJson()) instanceof Intact);
});

test("arrays and records the binding hands out cannot be changed", async () => {
  const contract = checkContract(lending);
  const edition = compile(contract, rule, 1);
  assert.throws(() => {
    edition.pins.creditScore = 9;
  }, TypeError);
  assert.throws(() => {
    delete edition.pinsWithHash.creditScore;
  }, TypeError);
  assert.equal(edition.pins.creditScore, 1);
  assert.throws(() => {
    contract.releases[0] = 9;
  }, TypeError);

  const parked = await parkingEnvironment(contract).run(edition);
  const [start] = parked.log.entries;
  assert.throws(() => {
    start.inputs.customer.fields.name = "Bolt";
  }, TypeError);
  assert.throws(() => parked.log.entries.pop(), TypeError);
  assert.throws(() => {
    parked.call.args[0] = null;
  }, TypeError);
});

test("a run parks on a deferred call and resumes from its log", async () => {
  const contract = checkContract(lending);
  const environment = parkingEnvironment(contract);
  const edition = compile(contract, rule, 1);

  const parked = await environment.run(edition);
  assert.ok(parked instanceof Parked);
  assert.equal(parked.call.name, "creditScore");
  assert.ok(parked.call.args[0] instanceof TaggedValue);
  assert.equal(parked.call.args[0].fields.name, "Acme");
  assert.equal(parked.call.print(), `creditScore(Customer(1, "Acme"))`);

  const log = parked.log.append(parked.toReply(650));
  const done = await environment.run(edition, [], log);
  assert.ok(done instanceof Completed);
  assert.equal(done.value, "approve");
  const [start, reply, result] = done.log.entries;
  assert.ok(start instanceof StartEntry);
  assert.equal(start.inputs.customer.tag, "Customer");
  assert.ok(reply instanceof ReplyEntry);
  assert.equal(reply.answer, 650);
  assert.ok(result instanceof ResultEntry);
  assert.deepEqual([parked.kind, done.kind], ["parked", "completed"]);
  assert.deepEqual(done.log.entries.map((entry) => entry.kind), ["start", "reply", "result"]);
});

test("a log round-trips through JSON and replays without asking the host", async () => {
  const contract = checkContract(lending);
  const edition = compile(contract, rule, 1);
  const parked = await parkingEnvironment(contract).run(edition);
  const done = await parkingEnvironment(contract).run(edition, [], parked.log.append(parked.toReply(500)));

  const asked = [];
  const strict = contract.implement([
    immediate("customer", () => asked.push("customer")),
    immediate("customer/2", () => asked.push("customer/2")),
    immediate("creditScore", () => asked.push("creditScore")),
    immediate("creditScore/2", () => asked.push("creditScore/2")),
  ]);
  const replayed = await strict.run(edition, [], decodeLogJson(done.log.encodeJson()));
  assert.equal(replayed.value, "decline");
  assert.deepEqual(asked, []);
});

test("per-run registrations come with the run, and persist receives each new entry", async () => {
  const contract = checkContract(lending);
  const environment = contract.implement([
    immediate("customer", () => acme),
    perRun("customer/2"),
    immediate("creditScore", () => 700),
    perRun("creditScore/2"),
  ]);
  const persisted = [];
  const outcome = await environment.run(
    compile(contract, rule, 1),
    [immediate("customer/2", () => acme), immediate("creditScore/2", () => 0)],
    null,
    (entry) => persisted.push(entry.constructor.name),
  );
  assert.ok(outcome instanceof Completed);
  assert.deepEqual(persisted, ["StartEntry", "ReplyEntry", "ResultEntry"]);
});

test("transact wraps each unit of host work", async () => {
  const contract = checkContract(lending);
  let units = 0;
  const environment = answeringEnvironment(contract, 700, async (block) => {
    units++;
    await block();
  });
  await environment.run(compile(contract, rule, 1));
  assert.equal(units, 3);
});

test("handlers, persist and transact may wait on promises", async () => {
  const contract = checkContract(lending);
  const later = (value) => new Promise((resolve) => setTimeout(() => resolve(value), 1));
  const trace = [];
  const environment = contract.implement(
    [
      immediate("customer", () => later(acme)),
      immediate("customer/2", () => acme),
      immediate("creditScore", async ([customer]) => {
        trace.push(`score ${customer.fields.name}`);
        return later(650);
      }),
      immediate("creditScore/2", () => 0),
    ],
    async (block) => {
      trace.push("begin");
      await block();
      await later(null);
      trace.push("commit");
    },
  );
  const outcome = await environment.run(compile(contract, rule, 1), [], null, async (entry) => {
    await later(null);
    trace.push(`persist ${entry.kind}`);
  });
  assert.equal(outcome.value, "approve");
  assert.deepEqual(trace, [
    "begin", "persist start", "commit",
    "begin", "score Acme", "persist reply", "commit",
    "begin", "persist result", "commit",
  ]);
});

test("a handler's own error or rejection reaches the caller unchanged", async () => {
  const contract = checkContract(lending);
  const boom = new TypeError("boom");
  const throwing = contract.implement([
    immediate("customer", () => acme),
    immediate("customer/2", () => acme),
    immediate("creditScore", () => {
      throw boom;
    }),
    immediate("creditScore/2", () => 0),
  ]);
  await assert.rejects(throwing.run(compile(contract, rule, 1)), (error) => error === boom);
  const rejecting = contract.implement([
    immediate("customer", () => acme),
    immediate("customer/2", () => acme),
    immediate("creditScore", () => Promise.reject(boom)),
    immediate("creditScore/2", () => 0),
  ]);
  await assert.rejects(rejecting.run(compile(contract, rule, 1)), (error) => error === boom);
});

test("a rule failing at runtime is a failed outcome with diagnostics", async () => {
  const contract = checkContract(lending);
  const outcome = await answeringEnvironment(contract, 0).run(compile(contract, "1 / creditScore(customer)", 1));
  assert.ok(outcome instanceof Failed);
  assert.equal(outcome.diagnostics[0].message, "Division by zero");
});

test("an answer written in Klein is checked against the expected type and evaluated", () => {
  const contract = checkContract(lending);
  const customer = contract.declarations.find((d) => d.name === "customer" && d.revision === 1).answerType;
  assert.equal(customer.print(), "Customer");
  const value = contract.evaluateValue(`Customer(2, "Bolt")`, 1, customer);
  assert.ok(value instanceof Accepted);
  assert.ok(value.value instanceof TaggedValue);
  assert.deepEqual(value.value.fields, { id: 2, name: "Bolt" });
  const wrong = contract.evaluateValue(`"Bolt"`, 1, customer);
  assert.ok(wrong instanceof Rejected);
  assert.ok(wrong.diagnostics.length > 0);
  const failing = contract.evaluateValue("1 / 0", 1, contract.declarations.find((d) => d.name === "creditScore").answerType);
  assert.equal(failing.diagnostics[0].message, "Division by zero");
});

test("records cross as plain objects", async () => {
  const contract = checkContract(`environment shapes

fun area(r: { w: Num, h: Num }): Num

release 1
  area
`);
  const environment = contract.implement([immediate("area", ([r]) => r.w * r.h)]);
  const outcome = await environment.run(compile(contract, "area({ w = 2, h = 3 })", 1));
  assert.equal(outcome.value, 6);
});

test("values print the way Klein writes them", () => {
  assert.equal(printValue(acme), `Customer(1, "Acme")`);
  assert.equal(printValue({ w: 2, h: 3 }), "{ w = 2, h = 3 }");
  assert.equal(printValue(undefined), "()");
});
