import assert from "node:assert/strict";
import * as runtime from "../build/dist/js/productionLibrary/klein.mjs";
import type { Contract, Edition, Environment, ReleaseNumber } from "../build/dist/js/productionLibrary/index.d.mts";

export const klein = runtime as unknown as typeof import("../build/dist/js/productionLibrary/index.d.mts");

export const lending = `environment lending

type Customer = Customer { id: Num, name: String }
type Customer/2 = Customer { id: Num, name: String, tier: String }

customer: Customer
customer/2: Customer/2
fun creditScore(c: Customer): Num
fun creditScore/2(c: Customer/2): Num

release 1
  Customer
  customer
  creditScore

release 2
  Customer/2
  customer/2
  creditScore/2
`;

export const lendingWithoutRevision2 = `environment lending

type Customer = Customer { id: Num, name: String }
type Customer/2 = Customer { id: Num, name: String, tier: String }

customer: Customer
customer/2: Customer/2
fun creditScore(c: Customer): Num

release 1
  Customer
  customer
  creditScore
`;

export const rule = `if creditScore(customer) > 600 then "approve" else "decline"`;

export const acme = new klein.TaggedValue("Customer", { id: 1, name: "Acme" });

export function parkingEnvironment(contract: Contract): Environment {
  return contract.implement([
    klein.immediate("customer", () => acme),
    klein.immediate("customer/2", () => new klein.TaggedValue("Customer", { id: 1, name: "Acme", tier: "gold" })),
    klein.deferred("creditScore", () => {}),
    klein.deferred("creditScore/2", () => {}),
  ]);
}

export function answeringEnvironment(
  contract: Contract,
  score: number | string = 700,
  transact: ((block: () => void) => void) | null = null,
): Environment {
  return contract.implement(
    [
      klein.immediate("customer", () => acme),
      klein.immediate("customer/2", () => acme),
      klein.immediate("creditScore", () => score),
      klein.immediate("creditScore/2", () => score),
    ],
    transact,
  );
}

export function releaseOf(contract: Contract, number: number): ReleaseNumber {
  const release = contract.releases.find((it) => it === number);
  assert.ok(release !== undefined);
  return release;
}

export function compile(contract: Contract, source: string, release: number): Edition {
  const compiled = contract.compileRule(source, releaseOf(contract, release));
  assert.ok(compiled.kind === "accepted");
  return compiled.value;
}
