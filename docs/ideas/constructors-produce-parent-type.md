# Constructors Produce Parent Type

**Status:** Idea, not planned for implementation yet. Depends on pattern matching design.

## Current behavior

Constructor calls produce constructor-typed refs:

```
type List<'A> = Nil | Cons { head: 'A, tail: List<'A> }

Cons(1, Nil)  // type: Cons<Num>
Nil           // type: Nil
```

This means unions of constructors appear in positive position (`Cons<Num> | Nil`), requiring sibling-to-parent merging in the LUB simplification to recover `List<Num>`.

## Proposed behavior

Constructor calls produce parent-typed refs:

```
Cons(1, Nil)  // type: List<Num>
Nil           // type: List<'A>
```

The constructor binding changes from `('A, List<'A>) -> Cons<'A>` to `('A, List<'A>) -> List<'A>`.

## Why

- **No sibling merging needed.** `if true then Cons(1, Nil) else Nil` is `List<Num>` trivially — both branches already have the parent type.
- **No constructor-parent merging.** `Cons(1, Nil) | someList` is `List<Num>` without special cases.
- **No constructor refs in positive position.** TypeComponents only sees parent refs on the output side. Constructor refs only appear in negative position (pattern match refinement).
- **Matches ML-family languages.** Haskell, OCaml, Rust all work this way — constructors are introduction forms for the parent type.
- **Simplifies LUB.** The entire `mergeSiblingRefs`, `mergeConstructorWithParent` machinery goes away.

## Inferred interface

The parent type's inferred interface (common fields across all constructors) becomes directly useful:

```
type Animal = Dog { name: String } | Cat { name: String } | Fish { fins: Num }

x = if true then Dog("Fido") else Cat("Whiskers")
// x: Animal
// x.name works — inferred interface says all Animal constructors have `name`...
// ...but Fish doesn't have `name`, so this would fail
```

This is the correct tradeoff: if you want field access on a specific constructor, pattern match first. The parent type only exposes fields common to ALL constructors.

## Pattern matching interaction

Constructor refs still appear in negative position for pattern match refinement:

```
fun describe(a: Animal) = match a with
  | Dog d -> d.name     // d: Dog, has `name`
  | Cat c -> c.name     // c: Cat, has `name`
  | Fish f -> "a fish"  // f: Fish, has `fins` but not `name`
```

The match narrows `Animal` to the specific constructor in each branch. This is where constructor types earn their keep — as refinement types, not as expression types.

## Refinement escape

If pattern matching includes type refinement (narrowing to the constructor type inside match branches), refined types can escape and re-enter positive position:

```
fun getdog(a: Animal) = match a with
  | Dog d -> d          // d: Dog escapes the match
  | _ -> Dog("default")

// getdog: (Animal) -> Dog   -- constructor type is back in positive position
```

This would reintroduce constructor unions and require the same sibling merging machinery.

However, refinement is not a requirement. Pattern matching can work without it — the matched value simply keeps the parent type, and constructor fields are accessed by name:

```
fun describe(a: Animal) = match a with
  | Dog { name } -> name       // name: String, but a is still Animal
  | Cat { name } -> name
  | Fish { fins } -> "a fish"
```

Without refinement, constructor refs never appear in positive position, and the full simplification holds. Refinement can be added later as an opt-in feature if needed.

## Implementation sketch

1. **`bindConstructors` in TypeDefPreprocessor**: change result type from `TRef(ctor.name, tvars)` to `TRef(ctor.parentType, parentTvars)`
2. **Constructor TypeDefInfo**: may no longer need separate type defs for constructors (they're not types in their own right, just functions)
3. **TypeComponents**: constructor refs disappear from positive position. `mergeSiblingRefs`, `mergeConstructorWithParent` in `mergeTightBounds` become dead code.
4. **Pattern matching (future)**: destructures fields without narrowing the type. Refinement is a separate, optional feature.

## Open questions

- Should bare constructors (no fields, like `Nil` or `True`) still be usable as types in annotations? E.g., `x: Nil` vs `x: List<Nothing>`.
- How does this interact with single-constructor types like `type Money = Money { value: Num }`? Currently `Money` the type and `Money` the constructor are the same. This should continue to work unchanged.
- Does removing constructor type defs break anything in the current type system (e.g., constraint solving, variance computation)?
