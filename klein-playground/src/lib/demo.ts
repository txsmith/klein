import { Checked, Klein, RunOutcome, Value, deferred, encodeJson, immediate } from "klein";
import { lendingContract } from "./examples";

class Customer {
  static readonly kleinName = "Customer";
  constructor(
    readonly id: number,
    readonly name: string,
  ) {}
}

class GoldCustomer {
  static readonly kleinName = "Customer";
  constructor(
    readonly id: number,
    readonly name: string,
    readonly tier: string,
  ) {}
}

export async function runDemo(): Promise<void> {
  const contract = Klein.checkContract(lendingContract);
  console.log("contract", contract.environment, "releases", contract.releases);

  const rule = `if creditScore(customer) > 600 then "approve" else "decline"`;
  const compiled = contract.compileRule(rule, contract.releases[0]);
  if (compiled instanceof Checked.Rejected) {
    console.log("rule diagnostics", compiled.diagnostics);
    return;
  }
  const edition = compiled.value;
  console.log(
    "edition pins",
    [...edition.pinsWithHash].map(([name, pin]) => `${name}/${pin.revision} ${pin.hash}`),
  );

  const environment = contract.implement(
    immediate("customer", () => new Customer(1, "Acme")),
    immediate("customer/2", () => new GoldCustomer(1, "Acme", "gold")),
    deferred("creditScore", (call) => console.log("asked", call.print())),
    deferred("creditScore/2", (call) => console.log("asked", call.print())),
  );

  const parked = await environment.run(edition);
  if (!(parked instanceof RunOutcome.Parked)) return;
  console.log("parked at", parked.call.print());

  const resumed = await environment.run(edition, parked.log.plus(parked.toReply(650)));
  if (resumed instanceof RunOutcome.Completed) console.log("completed with", Value.print(resumed.value));
  console.log("log", encodeJson(resumed.log));
}
