# Constructor Type Design: Three Options

**Status:** Partially revisited by [2026-06-24-adopt-operation-bidi.md](./2026-06-24-adopt-operation-bidi.md) — the LUB/union-display machinery (and the simplifier) is removed, but **Option 2's core is retained: constructors keep their own type** (`Dog : Dog`, no auto-upcast). Because Operation Bidi has no anonymous unions, what a *heterogeneous join* yields is reopened (see spec §7). (An earlier draft of the Operation Bidi ADR wrongly said it adopts Option 1 — it does not.)

**Historical status:** Implemented option 2. Option 3 rejected because eager sibling merging destroys structural precision (adding a sibling to a union can break field access). Structural expansion for unrelated refs deferred (see `docs/ideas/type-simplification-future.md`).

## Context

When a user writes `Dog("Fido")`, should the type be `Dog` or `Animal`? This decision affects type simplification, LUB display, pattern matching, and the overall UX of the type system.

## Option 1: Constructors produce parent type

`Dog("Fido")` has type `Animal`. Constructor types don't exist — pattern matching destructures fields without narrowing the type.

### Pros
- Great simplicity for type simplification, constraint solving, and UX
- No widening/narrowing issues in invariant containers (no Scala `.widen` pain)
- Unions and intersections almost never show up to the user
- Error messages are simple — no `Cons<Num> | Nil` confusion
- Pattern matching design is straightforward (destructuring only, no refinement)
- Removes most nominal subtyping from the language (constructor <: parent never fires)

### Cons
- Less flexibility: cannot use a Dog as a Dog without pattern matching
- Cannot express functions that only take a specific constructor
- Feels like it plays less to the synergy of subtyping + type inference (wide flexibility with little annotation)
- Inferred interface must be correct in type simplification for LUB structural expansion to work
- Less expressive type system overall (can't distinguish `Cons` from `List` at type level)

### Implementation
One-line change in `bindConstructors`: result type becomes `TRef(parentType, parentTvars)` instead of `TRef(ctorName, ctorTvars)`. ~65 test updates needed. LUB merging needs to handle inferred interface correcly to do structural expansion of unrelated types.

See also: `docs/ideas/constructors-produce-parent-type.md`

## Option 2: Keep union types, LUB merge only when exhaustive

`Dog("Fido")` has type `Dog`. Unions show as `Dog | Cat`. The LUB only merges to the parent type when ALL constructors of a parent are present in the union (making it exact).

### Pros
- Greater flexibility while avoiding type annotations or needless pattern matching
- Can express more types (functions that only accept specific constructors)
- Natural fit with SimpleSub — unions/intersections are what the algorithm produces
- Partial constructor unions allow field access without matching (`Dog | Cat` supports `.name`)
- More precise error messages and types

### Cons
- Exposes union and intersection types any time the user doesn't return or handle a full type
- Subsequently requires type annotations to get rid of unions
- Making exhaustiveness checking harder to implement (requires annotations)
- Narrow types in invariant containers are a pain (the Scala `.widen` problem)
- Users may find `Dog | Cat` confusing if they don't know the type system

### Implementation
Ref merge rules:
1. Identical refs → identity
2. Same-name refs, different args → merge args by variance
3. Exhaustive siblings → parent type with merged args (positive polarity only)
4. Everything else → union stays as-is

Co-occurrence analysis uses the parent ref's merged args so invariant type parameters simplify correctly.

## Option 3: Merge sibling bounds during constraint solving

`Dog("Fido")` has type `Dog`. But when siblings accumulate as lower bounds on the same variable (e.g., `if true then Dog("Fido") else Cat("Whiskers")`), the constraint system eagerly merges them to the parent type. Merges all sibling sets (not just exhaustive), accepting that partial unions lose field access on the parent — the same tradeoff as option 1, but only when siblings actually meet on a variable.

### Pros
- Single constructors keep their type (`Dog`) — more expressive than option 1
- Functions that only take a specific constructor still work (`fun takeDog(d: Dog) = d.breed`)
- Unions of all siblings collapse to parent automatically — no union types shown in this case
- No changes needed to type simplification or LUB — the merging happens before canonicalization
- Middle ground between option 1 and option 2's expressiveness

### Cons
- Worse error messages: "Animal doesn't have breed" instead of "Cat doesn't have breed"
- Same expressiveness limitation as option 1 when siblings accumulate (partial unions lose field access on the merged parent, same as option 1's limitation)
- Implementation complexity in constraint system: need to scan existing lower bounds on every `constrain` call for merge opportunities
- Merging same-name refs with different args (`Cons<Num>` + `Cons<String>`) requires creating fresh TVars and new constraints
- Invariant type args can't be merged — need special handling
- Mutating bounds during constraint solving requires care (though the existing `.toList()` copy pattern handles this)
- Could generalize to records and functions too (merging lower bounds eagerly), which is essentially moving canonicalization into constraint solving — a bigger architectural shift

### False positive risk

Merging partial sibling sets to the parent rejects valid programs:

```
type Animal = Dog { name: String } | Cat { name: String } | Fish { fins: Num }

x = if true then Dog("Fido") else Cat("Whiskers")
// x :> Dog, x :> Cat → merged to x :> Animal
x.name  // Animal <: {name: String} FAILS — Fish lacks name
// But program is valid: x is only ever Dog or Cat
```

This is the same limitation as option 1 — but only triggered when siblings actually accumulate on a variable, not on every constructor call.

### Implementation
Modify the `rhs is TVar` branch in `Subtyping.constrain`: before adding a TRef lower bound, scan existing lower bounds for siblings/same-name refs and merge. Needs access to `TypeEnv` for constructor/type def lookups (already available). Fresh TVars needed for merged type args.

## Key tension

Option 1 optimizes for simplicity and predictability at the cost of expressiveness.
Option 2 optimizes for expressiveness and flexibility at the cost of complexity in the displayed types.
Option 3 is a middle ground — keeps constructor types but collapses exhaustive unions early, at the cost of implementation complexity in the constraint system.

All options work. The decision depends on what Klein's target users (tech-savvy business rule writers) will find more natural.
