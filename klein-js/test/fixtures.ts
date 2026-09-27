import assert from "node:assert/strict";
import {
  Checked,
  immediate,
  deferred,
  type Edition,
  type Environment,
  type EnvironmentContract,
  type Transactor,
  type Value,
} from "../build/package/index.js";

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

export class Customer {
  static readonly kleinName = "Customer";
  readonly id: number;
  readonly name: string;

  constructor(id: number, name: string) {
    this.id = id;
    this.name = name;
  }
}

export class TieredCustomer {
  static readonly kleinName = "Customer";
  readonly id: number;
  readonly name: string;
  readonly tier: string;

  constructor(id: number, name: string, tier: string) {
    this.id = id;
    this.name = name;
    this.tier = tier;
  }
}

export const acme = new Customer(1, "Acme");

export function assertConstructed(value: Value, kleinName: string): { readonly [field: string]: Value } {
  assert.ok(typeof value === "object" && value !== null);
  assert.equal((value.constructor as { kleinName?: unknown }).kleinName, kleinName);
  return value as { readonly [field: string]: Value };
}

export function parkingEnvironment(contract: EnvironmentContract): Environment {
  return contract.implement(
    immediate("customer", () => acme),
    immediate("customer/2", () => new TieredCustomer(1, "Acme", "gold")),
    deferred("creditScore", () => {}),
    deferred("creditScore/2", () => {}),
  );
}

export function answeringEnvironment(
  contract: EnvironmentContract,
  score: Value = 700,
  transactor: Transactor | null = null,
): Environment {
  const environment = contract.implement(
    immediate("customer", () => acme),
    immediate("customer/2", () => acme),
    immediate("creditScore", () => score),
    immediate("creditScore/2", () => score),
  );
  return transactor === null ? environment : environment.withTransactor(transactor);
}

export function assertCompiles(contract: EnvironmentContract, source: string, releaseNumber: number): Edition {
  const release = contract.releases.find((it) => it === releaseNumber);
  assert.ok(release !== undefined);
  const compiled = contract.compileRule(source, release);
  assert.ok(compiled instanceof Checked.Accepted);
  return compiled.value;
}
