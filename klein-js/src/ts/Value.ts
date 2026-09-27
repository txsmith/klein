import { freeze, kotlin, mapExceptions } from "./Kotlin.js";

export type Value = number | string | boolean | null | undefined | object;

export namespace Value {
  export function print(value: Value): string {
    return mapExceptions(() => kotlin.printValue(toKotlin(value)));
  }
}

type Fields = { readonly [field: string]: unknown };

const classesByKleinName = new Map<string, new () => object>();

export function toKotlin(value: Value): unknown {
  if (value === undefined || value === null) return value;
  if (typeof value === "number" || typeof value === "string" || typeof value === "boolean") return value;
  if (isRecord(value)) return toKotlinStruct(null, value);
  const kleinName = getKleinName(value);
  if (kleinName !== null) return toKotlinStruct(kleinName, value as Fields);
  throw new TypeError(
    `${String(value)} is not a Klein value: use a number, string, boolean, null, undefined, a plain object, ` +
      `or an instance of a class with a static kleinName`,
  );
}

export function fromKotlin(value: unknown): Value {
  if (!(value instanceof kotlin.Struct)) return value as Value;
  const fields = value.tag == null ? {} : new (getClass(value.tag))();
  value.names.forEach((name, index) => {
    (fields as { [field: string]: Value })[name] = fromKotlin(value.values[index]);
  });
  return Object.freeze(fields);
}

export function fromKotlinAll(values: readonly unknown[]): readonly Value[] {
  return freeze(values.map(fromKotlin));
}

function toKotlinStruct(kleinName: string | null, fields: Fields): kotlin.Struct {
  const names = Object.keys(fields);
  return new kotlin.Struct(kleinName, names, names.map((name) => toKotlin(fields[name] as Value)));
}

function getKleinName(value: object): string | null {
  const kleinName = (value.constructor as { kleinName?: unknown } | undefined)?.kleinName;
  return typeof kleinName === "string" ? kleinName : null;
}

function getClass(kleinName: string): new () => object {
  let kleinClass = classesByKleinName.get(kleinName);
  if (kleinClass === undefined) {
    kleinClass = class {
      static readonly kleinName = kleinName;
    };
    Object.defineProperty(kleinClass, "name", { value: kleinName });
    classesByKleinName.set(kleinName, kleinClass);
  }
  return kleinClass;
}

function isRecord(value: object): value is Fields {
  const prototype = Object.getPrototypeOf(value);
  return prototype === null || prototype === Object.prototype;
}
