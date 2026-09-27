import { EffectLog } from "./EffectLog.js";
import { kotlin, mapExceptions } from "./Kotlin.js";

export function encodeJson(log: EffectLog): string {
  return mapExceptions(() => kotlin.encodeJson(EffectLog.toKotlin(log)));
}

export function decodeJson(text: string): EffectLog {
  return mapExceptions(() => new EffectLog(kotlin.decodeJson(text)));
}
