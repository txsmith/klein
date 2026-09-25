# klein-js

Klein's JavaScript binding. It mirrors the Kotlin host API of `klein-lib` for JavaScript and
TypeScript hosts, and sees only the library's public API.

## The three layers

1. **Kotlin mirror classes** in `src/jsMain/kotlin/klein/js`. Each exported class wraps one library
   value in an `internal` field and has an `internal` constructor. JavaScript never builds one,
   except `TaggedValue`; `types.check.mts` asserts which classes are constructible. Shapes follow
   the library: an edition's `pins` map names to revisions and its `pinsWithHash` map names to
   pins, as in Kotlin.
2. **Generated declarations** (`klein.d.mts` in the build output). Kotlin writes them. They are an
   internal detail: they cannot express unions, fixed words or a Klein value.
3. **Handwritten declarations** in `src/jsMain/resources/index.d.mts`. This is the package's
   `types` entry, so it is what TypeScript users see. It declares every export by hand.

A sealed type in the library becomes an abstract base class in Kotlin with `abstract val kind`,
and one subclass per case with a fixed kind word. In `index.d.mts` the base is not declared.
Instead a union type of the same name lists the cases, and each case declares its kind as a
literal. Every declared class carries `#private;`, so TypeScript compares it by identity and not
by shape.

## When you change something, change its partners

When the library adds, removes or changes a type, a field or a function that crosses to
JavaScript:

- Update the Kotlin mirror. A new case of a sealed type, host errors included, shows up as a
  non-exhaustive `when` in the matching `toJs`, so the binding stops compiling until it is mirrored.
- Update `index.d.mts`: the class, its members, and, for a new case, the union it belongs to.
- For a new sealed type, add its union to `Unions` in `test/types.check.mts`.
- Add or update a Node test in `test/binding.test.mjs`.
- For a host error, add its case to the table in `test/host-errors.test.mts`. The table's type
  requires one case per kind in the `HostError` union, so the type check fails, naming the kind,
  until you do. Each case triggers the error through the real library and checks every field it
  carries.
- Update the playground (`klein-playground`) if it uses what changed.

## What the build checks, and what it does not

`./gradlew :klein-js:check` runs the Node tests and type-checks the TypeScript test files
(`types.check.mts`, `fixtures.mts`, `host-errors.test.mts`). `fixtures.mts` holds the contract,
rule and environments the tests share. The type check walks
every export of the generated module on its own and fails, naming the export, when:

- an export exists in one declaration file and not the other;
- a class's members differ in name, or a handwritten member does not fit the generated one;
- a function's signature does not fit;
- a union does not fit its generated base class.

It does not catch:

- a new case missing from its union. The export check forces you to declare the class, but not
  to add it to the union, and a missing case makes every `switch` over that union silently
  incomplete;
- a kind word that differs between Kotlin and `index.d.mts`, because a literal fits `string`.
  Assert kinds in the Node tests;
- a member hidden on purpose. `HiddenMembers` in the type check lists them (`Rejected.value`
  throws at runtime and is left out of the types so TypeScript forces a check of the kind first).

## Values

Klein values cross as plain JavaScript: numbers, strings, booleans, `null` for Klein's null,
`undefined` for unit, plain objects for records, and `TaggedValue` for constructed values.
`fromJs` and `toJs` in `Values.kt` are the only conversion; `KleinValue` in `index.d.mts` is its
type.

Unit as `undefined` has one known gap, accepted because unit is rarely carried as data: JavaScript
treats a field set to `undefined` as missing, so `JSON.stringify` drops a unit field from a
record. A handler whose answer type is unit returns nothing, which is why unit stays `undefined`.

Everything the binding hands out is frozen, so a caller cannot change what other readers see.
Arrays are `JsReadonlyArray` built with `frozen()`, which TypeScript sees as `readonly T[]`.
Records come from `toJsObject`, which freezes them. Array parameters are `JsReadonlyArray` too,
so callers may pass either kind of array.

## Release and revision numbers

At runtime they are plain numbers. `index.d.mts` brands them, as `ReleaseNumber` and
`RevisionNumber`, so TypeScript keeps them apart and refuses a bare number literal. Hosts get
them from the contract (`contract.releases`, a declaration's revision) and pass them back. The
brand markers are type-only, so `types.check.mts` leaves them out of the export comparison. The
binding does not check the numbers itself: a release or revision the contract lacks, fractions
included, is refused by the library as an unknown release or an unknown pin.

## Waiting

The library's run is a suspending function, and so are its handlers, `persist` and `transact`.
The binding bridges them without a coroutines library, in `Async.kt`: `promise` turns a suspending
block into a JavaScript promise, so `run` returns one, and `awaitResult` waits on whatever a
JavaScript function returns when it is a promise, so a handler, `persist` or `transact` may be
async. A JavaScript `transact` receives a block that returns a promise and must await it. An error
or rejection from the host's own code reaches the caller unchanged.

## Building and testing

```bash
./gradlew :klein-js:check                                  # Node tests and the type check
./gradlew :klein-js:jsBrowserProductionLibraryDistribution   # the package for browsers
```

The module builds for both browsers and Node. Both targets write the package to
`build/dist/js/productionLibrary`, and their output is the same apart from ordering. The tests
use the Node build, and the playground the browser build. Browser tests are turned off, since
they need a browser the build does not provide. Rebuild the package, then run `npm install` in
`klein-playground` to pick up changes.

The package's `package.json` declares an `exports` map pointing at `index.d.mts` and `klein.mjs`,
and `sideEffects: false`, so bundlers such as Vite can drop what an app does not use.
