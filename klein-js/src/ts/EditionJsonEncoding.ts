import { Edition } from "./Edition.js";

export function encodeEditionJson(edition: Edition): string {
  return Edition.toKotlin(edition).encodeJson();
}
