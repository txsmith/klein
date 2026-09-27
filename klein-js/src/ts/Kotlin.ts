import * as kotlin from "./kotlin/klein-kotlin.mjs";
import { KleinException } from "./KleinException.js";

export { kotlin };

export function mapExceptions<T>(call: () => T): T {
  try {
    const result = call();
    if (result instanceof Promise) {
      return result.catch((error) => {
        throw fromKotlinError(error);
      }) as T;
    }
    return result;
  } catch (error) {
    throw fromKotlinError(error);
  }
}

function fromKotlinError(error: unknown): unknown {
  if (error instanceof kotlin.ThrownValue) return error.value;
  const errors = kotlin.getHostErrors(error);
  return errors == null ? error : KleinException.fromKotlin(errors);
}

export function freeze<T>(items: Iterable<T>): readonly T[] {
  return Object.freeze([...items]);
}

export function freezeMap<K, V>(entries: Iterable<readonly [K, V]>): ReadonlyMap<K, V> {
  return new FrozenMap(new Map(entries));
}

class FrozenMap<K, V> implements ReadonlyMap<K, V> {
  readonly #map: Map<K, V>;

  constructor(map: Map<K, V>) {
    this.#map = map;
    Object.freeze(this);
  }

  get size(): number {
    return this.#map.size;
  }

  get(key: K): V | undefined {
    return this.#map.get(key);
  }

  has(key: K): boolean {
    return this.#map.has(key);
  }

  forEach(callback: (value: V, key: K, map: ReadonlyMap<K, V>) => void, thisArg?: unknown): void {
    this.#map.forEach((value, key) => callback.call(thisArg, value, key, this));
  }

  entries(): MapIterator<[K, V]> {
    return this.#map.entries();
  }

  keys(): MapIterator<K> {
    return this.#map.keys();
  }

  values(): MapIterator<V> {
    return this.#map.values();
  }

  [Symbol.iterator](): MapIterator<[K, V]> {
    return this.#map[Symbol.iterator]();
  }
}
