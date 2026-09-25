import {
  Accepted,
  Completed,
  Parked,
  Rejected,
  TaggedValue,
  checkContract,
  deferred,
  immediate,
  printValue,
  type Edition,
} from "klein";
import { lendingContract } from "./examples";

export async function runDemo(): Promise<void> {
  const contract = checkContract(lendingContract);
  console.log("contract", contract.environment, "releases", contract.releases);

  const rule = `if creditScore(customer) > 600 then "approve" else "decline"`;
  const compiled = contract.compileRule(rule, contract.releases[0]);
  if (compiled instanceof Rejected) {
    console.log("rule diagnostics", compiled.diagnostics);
    return;
  }
  const edition = (compiled as Accepted<Edition>).value;
  console.log(
    "edition pins",
    Object.entries(edition.pinsWithHash).map(([name, pin]) => `${name}/${pin.revision} ${pin.hash}`),
  );

  const environment = contract.implement([
    immediate("customer", () => new TaggedValue("Customer", { id: 1, name: "Acme" })),
    immediate("customer/2", () => new TaggedValue("Customer", { id: 1, name: "Acme", tier: "gold" })),
    deferred("creditScore", (call) => console.log("asked", call.print())),
    deferred("creditScore/2", (call) => console.log("asked", call.print())),
  ]);

  const parked = await environment.run(edition);
  if (!(parked instanceof Parked)) return;
  console.log("parked at", parked.call.print());

  const resumed = await environment.run(edition, [], parked.log.append(parked.toReply(650)));
  if (resumed instanceof Completed) console.log("completed with", printValue(resumed.value));
  console.log("log", resumed.log.encodeJson());
}
