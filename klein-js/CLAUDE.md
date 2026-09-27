# klein-js

Klein's JavaScript and TypeScript binding. Its API is the Kotlin host API of `klein-lib`: the same
names, the same shapes, and the same file names. It sees only the library's public API.

## Two layers

1. **The Kotlin layer** in `src/jsMain/kotlin/klein/jsbinding`. It exports the library to JavaScript as
   plainly as it can: handles that wrap a library object, plain fields, and strings where the
   library has a sealed case. It makes no effort to look like JavaScript. Kotlin writes its type
   declarations, and nothing outside this module sees them.
2. **The TypeScript layer** in `src/ts`. This is the package's API. Each file matches a library
   file by name (`EnvironmentContract.ts`, `Runner.ts`, `EffectLogJsonEncoding.ts`) and declares
   what that file declares. It is compiled against the Kotlin layer's declarations, so a renamed
   or retyped member of the Kotlin layer that the TypeScript does not follow is a compile error. A
   new case of a sealed type is not: the Kotlin layer passes cases as strings or as its own
   classes, and the TypeScript only fails at run time on one it does not know. The tests close
   that gap (see below).

`Kotlin.ts` is the TypeScript layer's bridge: it imports the Kotlin module as `kotlin` and turns a
thrown `KleinException` into the TypeScript one.

Every class is a view: it holds only its Kotlin object, in a `#kotlin` field, and every property is
a getter that reads from it, so there is no second copy to fall out of step. A getter that builds
an object keeps it after the first read, so reading twice gives the same object; this is safe
because the Kotlin objects never change. Arrays come out
frozen and maps as read-only maps that refuse changes at run time. Only what a host keeps in
storage has a public constructor: `EffectLog`, the four `LogEntry` kinds and `Call`. Each builds its
Kotlin object at once. Everything else is made through a static `fromKotlin` (or a constructor
overload taking the Kotlin object), marked `@internal` so it is left out of the published
declarations.

## Where JavaScript cannot say what Kotlin says

- **`is` checks** are `instanceof`. Every class that shares a union with a class of the same
  shape carries a `#` field, so TypeScript tells them apart by identity, not by shape.
- **Data objects** (`StaleReason.ChecksumMismatch`) are classes with one instance, checked with
  `instanceof` instead of `==`.
- **Handler arguments** arrive as separate parameters, `immediate("area", (r) => ...)`, where
  Kotlin passes one list, because a Kotlin lambda cannot take a variable number of parameters.
- **Maps** are `ReadonlyMap`, and **lists** are frozen `readonly` arrays.
- **Value classes** (`ReleaseNumber`, `RevisionNumber`, `LanguageVersion`) are branded numbers with
  no `.value`, and a function of the same name makes one: `ReleaseNumber(2)`.
- **Suspending functions** return a promise. Handlers and `persist` may be plain or async
  functions; the TypeScript layer always hands the Kotlin layer a promise, and the Kotlin layer
  awaits it. A `Transactor`'s `transact` returns a promise of its block's result; nothing checks
  at run time that it waited for the block. Whatever the host's own code throws reaches the caller unchanged, even when it
  is not an `Error`: the Kotlin layer carries such a value in a `ThrownValue` and `mapExceptions`
  takes it out again.

## Values

Klein values cross as plain JavaScript: numbers, strings, booleans, `null` for Klein's null,
`undefined` for unit, and objects for records and constructed values.

A record is a plain object, `{ w: 2, h: 3 }`. A constructed value is an instance of a class whose
static `kleinName` is the constructor's name:

```ts
class Customer {
  static readonly kleinName = "Customer";
  constructor(readonly id: number, readonly name: string) {}
}
```

Its fields are its own properties, so a constructed value reads exactly like a record with the same
fields, as Klein's subtyping allows. The name lives on the class, so it is not a field, not in
`Object.keys`, and not in JSON, and minifying the class name changes nothing. Values coming out of
Klein are frozen instances of a class the binding makes for each name, with the same static
`kleinName`. Any other object, a class instance without `kleinName` included, is refused with a
`TypeError`.

`Value.ts` converts both ways; the Kotlin layer sees records and constructed values as `Struct`.
The TypeScript `Value` type admits any object, so field types are checked when the value crosses,
not at compile time.

Unit as `undefined` has one known gap, accepted because unit is rarely carried as data: JavaScript
treats a field set to `undefined` as missing, so `JSON.stringify` drops a unit field from a record.

## When the library changes

- Change the Kotlin layer. A new case of a sealed type, host errors included, is a
  non-exhaustive `when` there, so the module stops compiling until it is handled.
- Change the matching TypeScript file.
- A new host error needs a case in `test/host-errors.test.ts`, and a new case of any other union
  (run outcome, log entry, decoded edition, stale reason, contract declaration, checked result)
  one in `test/kinds.test.ts`. Each table's type check fails, naming the missing case, until it has
  one, and each case is produced by the real library, so a case the TypeScript does not know
  fails its test. Two stale reasons, `LanguageChanged` and `CompilerChanged`, cannot be produced:
  changing the recorded language or compiler also breaks the checksum, which is checked first.
  They are listed as unreachable instead.
- Update the playground (`klein-playground`) if it uses what changed.

## Building and testing

```bash
./gradlew :klein-js:assemble   # the package, in build/package
./gradlew :klein-js:check      # the Node tests, and a type check of the tests against the package
```

The tests are TypeScript that Node runs directly. Both tasks use the Node that the Kotlin plugin
downloads, and the TypeScript compiler that the build installs, so every machine uses the same
versions. The package's `package.json` is the one in this directory.
