# Type Definition Implementation Plan

**⚠️ STATUS: WORK IN PROGRESS - DO NOT IMPLEMENT YET ⚠️**

---

## Overview

This document specifies how type definitions work in Klein. Type definitions create **nominal types** - types with identity beyond their structure.

```klein
type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil
```

This creates:
- `List<'A>` - a sum type
- `Cons<'A>` - a constructor type (subtypes `List<'A>`)
- `Nil` - a bare constructor type (subtypes `List<'A>` for any `'A`)

### Key Properties

1. **Nominal subtyping**: `Cons<Num>` subtypes `List<Num>`, but `{ head: Num, tail: List<Num> }` does NOT subtype `List<Num>`
2. **Structural access**: `List<Num>` subtypes its inferred interface (common fields across constructors)
3. **First-class constructors**: `Cons` is a function `('A, List<'A>) -> Cons<'A>`, `Nil` is a value of type `Nil`
4. **Variance inference**: Type parameters are automatically inferred as covariant, contravariant, or invariant

---

## Data Structures

### TRef (SimpleType.kt)

```kotlin
data class TRef(
    val name: String,
    val typeArgs: List<SimpleType>,
    val structure: SimpleType,
    val span: SourceSpan
) : SimpleType()
```

The result of applying type arguments to a `TypeDefInfo`. Type application is performed eagerly when the TRef is constructed - this validates arity, substitutes type arguments into the structure, and ensures errors are reported at the source location rather than later during subtyping.

**Examples:**
- `List<Num>` → `TRef("List", [TNum], {})`
- `Cons<Num>` → `TRef("Cons", [TNum], { head: Num, tail: List<Num> })`
- `Nil` → `TRef("Nil", [], {})`

### TypeDefInfo (TypeEnv.kt)

```kotlin
data class TypeParamInfo(
    val name: String,
    val variance: Variance,
    val tvar: TVar  // The TVar used in structure
)

data class TypeDefInfo(
    val name: String,
    val typeParams: List<TypeParamInfo>,
    val structure: SimpleType,  // Record type this subtypes to
    val span: SourceSpan
)
```

Metadata for a nominal type. Created for both sum types and constructor types.

The `tvar` in TypeParamInfo is the TVar used in the structure - same pattern as TFun where a TVar can appear in both params and result. When instantiating (creating a TRef), substitute these TVars with the actual type arguments.

**Example 1:** `type List<'A> = Cons { head: 'A, tail: List<'A> } | Nil`

| Name | Type Params | Structure |
|------|-------------|-----------|
| `List` | `[("A", Covariant)]` | `{}` (no common fields) |
| `Cons` | `[("A", Covariant)]` | `{ head: 'A, tail: List<'A> }` |
| `Nil` | `[]` | `{}` |

**Example 2:** `type Weird<'A, 'B> = Weird { in: 'A -> 'B, out: Weird<'B, 'A> }`

| Name | Type Params | Structure |
|------|-------------|-----------|
| `Weird` | `[("A", Invariant), ("B", Invariant)]` | `{ in: 'A -> 'B, out: Weird<'B, 'A> }` |

This example is interesting because:
- `'A` appears in function input (→ contravariant) BUT also in covariant position via swapped recursion
- `'B` appears in function output (→ covariant) BUT also in contravariant position via swapped recursion
- The conflicting positions cause both to become **invariant**
- Demonstrates how fixed-point iteration discovers non-obvious variance

### ConstructorInfo (TypeEnv.kt)

```kotlin
data class ConstructorInfo(
    val name: String,
    val typeParams: List<String>,
    val fields: List<FieldDecl>,
    val parentType: String,
    val span: SourceSpan
)
```

Metadata for pattern matching. Maps constructor names to their parent type.

### Variance

```kotlin
enum class Variance {
    Bivariant,      // ± unused during inference (internal only)
    Covariant,      // + output position
    Contravariant,  // - input position
    Invariant       // = both positions
}
```

**Design decision:** After variance inference completes, **collapse Bivariant → Invariant**. This enables phantom types for type-level distinctions (e.g., `UserId<Validated>` vs `UserId<Unvalidated>` remain incompatible). Without this, unused type parameters would allow any substitution, making phantom types useless.

---

## Processing Pipeline

Type definitions are processed in a **pre-pass before type inference**, using three passes to support mutual recursion (similar to how `inferTopLevelStmts` handles forward references for functions).

### Pass 1: Register Placeholders

Register all type and constructor names with placeholder `TypeDefInfo` (bivariant type params, empty structure). This allows forward and mutually recursive references.

### Pass 2: Infer Variance

Compute variance for all type parameters across all types simultaneously using fixed-point iteration. Each type parameter starts as bivariant (⊤) and moves down the lattice:

```
    ± (bivariant)
       / \
      +   -
       \ /
        = (invariant)
```

**Rules:**
- Type variable in covariant position → meet with +
- Type variable in contravariant position (e.g., function parameter) → meet with -
- Applied type `T<X>` → adjust polarity based on T's parameter variance
- Conflict (+ ∧ -) → invariant

Iteration continues until no variance changes.

**Post-processing:** After inference completes, collapse any remaining Bivariant → Invariant. This ensures unused (phantom) type parameters are invariant, enabling type-level distinctions.

**Example trace for `type Weird<'A, 'B> = Weird { in: 'A -> 'B, out: Weird<'B, 'A> }`:**

| Iteration | A | B | What happened |
|-----------|---|---|---------------|
| 0 | ± | ± | Initial state |
| 1 | - | + | `in: 'A -> 'B`: A in input pos (→-), B in output pos (→+) |
| 2 | = | = | `out: Weird<'B,'A>`: B in contravariant slot (→-), A in covariant slot (→+). Conflicts! |

**Result:** Both A and B are **Invariant**

The key insight: the swapped `Weird<'B, 'A>` puts B where A's variance applies (contravariant) and A where B's variance applies (covariant). This creates conflicts that force both to invariant.

### Pass 3: Finalize

For each type definition:
1. Build structure (record of fields) for each constructor
2. Compute inferred interface (common fields across all constructors)
3. Update `TypeDefInfo` with real variance and structure
4. Register `ConstructorInfo` for pattern matching
5. Bind constructors in environment 

---

## Constructor Binding

Constructors are bound as values in the type environment:

**Constructors with fields** → function type
```
Cons: ('A, List<'A>) -> Cons<'A>
```

**Bare constructors** → direct value
```
Nil: Nil
```

Bound polymorphically, so each use site gets fresh type variables.

---

## Subtyping Rules

Three cases to add to `Subtyping.constrainSimple`:

### Case 1: Nominal → Structural

`TRef <: TRecord`

Check the TRef's structure subtypes the record. (Structure is already substituted from type application.)

**Example:** `Cons<Num> <: { head: Num }`
- `Cons<Num>` has structure `{ head: Num, tail: List<Num> }`
- Check `{ head: Num, tail: List<Num> } <: { head: Num }` ✓

### Case 2: Nominal → Nominal (same type)

`TRef("T", args1) <: TRef("T", args2)`

Check type arguments respect variance:
- Covariant: `arg1 <: arg2`
- Contravariant: `arg2 <: arg1`
- Invariant: `arg1 = arg2` (exact match required)

Note: Bivariant never appears here due to post-processing (collapsed to Invariant).

**Example 1:** `List<Dog> <: List<Animal>` where List is covariant
- Check `Dog <: Animal` ✓

**Example 2:** `Weird<Dog, Dog> <: Weird<Dog, Dog>` where both A and B are invariant
- A is invariant: check `Dog <: Dog` AND `Dog <: Dog` ✓
- B is invariant: check `Dog <: Dog` AND `Dog <: Dog` ✓

But `Weird<Dog, Dog> <: Weird<Animal, Animal>` fails (invariant means no subtyping on type args)

### Case 3: Constructor → Parent

`TRef("Cons", args) <: TRef("List", args)`

Constructors subtype their parent. Check using parent's variance.

**Bare constructors** subtype polymorphically: `Nil <: List<'A>` for any 'A (no constraints generated).

---

## Inferred Interface

When all constructors share common fields, those fields are directly accessible on the sum type:

```klein
type Light =
    Red { duration: Num, intensity: Num }
  | Yellow { duration: Num }
  | Green { duration: Num, direction: String }

light.duration  // Works! Light <: { duration: Num }
```

Computed by intersecting field names across all constructors. When a common field has different types across constructors, unify them using SimpleSub: create a fresh TVar and constrain each constructor's field type to subtype it. For example, if `A { x: Num }` and `B { x: String }`, the interface gets `{ x: α }` where `Num <: α` and `String <: α`.

---

## Examples

### Simple Nominal Type

```klein
type Money = Money { value: Num }

m = Money(100)      // m: Money
m.value             // Works: Money <: { value: Num }
{ value: 50 }       // Does NOT subtype Money
```

### Generic Type

```klein
type Option<'A> = Some { value: 'A } | None

x: Option<Num> = Some(42)
y: Option<Num> = None
```

`'A` is covariant (appears only in output position).

### Invariant via Recursion (Weird)

```klein
type Weird<'A, 'B> = Weird { in: 'A -> 'B, out: Weird<'B, 'A> }
```

Both `'A` and `'B` are **invariant** because:
- `in: 'A -> 'B` makes A contravariant (-) and B covariant (+)
- `out: Weird<'B, 'A>` swaps the parameters, putting B in A's slot and A in B's slot
- This causes B to also appear in contravariant position, and A in covariant position
- Conflict (+ ∧ -) → invariant for both

**Subtyping consequences:**
- `Weird<Dog, Dog> <: Weird<Dog, Dog>` ✓ (same types)
- `Weird<Dog, Dog> <: Weird<Animal, Animal>` ✗ (invariant, no subtyping)
- `Weird<Animal, Dog> <: Weird<Dog, Animal>` ✗ (invariant, no subtyping)

**Constructor binding:**
```
Weird: ('A -> 'B, Weird<'B, 'A>) -> Weird<'A, 'B>
```

### Invariant Example

```klein
type Ref<'A> = Ref { get: () -> 'A, set: 'A -> () }
```

`'A` is invariant (appears in both input and output positions).

No subtyping between `Ref<Dog>` and `Ref<Animal>` - they are unrelated types.

---

## Edge Cases

1. **Mutual recursion**: Types can reference each other
   ```klein
   type Foo<'A> = Foo { bar: Bar<'A> }
   type Bar<'A> = Bar { foo: Foo<'A> }
   ```

2. **Bare constructors**: No type parameters, polymorphic subtyping
   ```klein
   type Bool = True | False
   // True: True, False: False
   // True <: Bool, False <: Bool
   ```

3. **Empty sum types**: Should probably be disallowed

4. **Duplicate names**: Constructor names must be unique across all types

5. **Same-named constructor**:
   - **Sum type**: `type Foo = Foo { x: Num } | Bar` → error (ambiguous)
   - **Single constructor**: `type Money = Money { value: Num }` → collapse into one TypeDefInfo (no parent/constructor distinction)

6. **Recursive variance**: `type T<'A> = T { x: T<'A> }` - fixed-point iteration handles this

---

## Open Questions

1. **Inferred interface scope**: Should the inferred interface include only common fields (intersection), or all fields with non-common ones made optional (union)? Intersection is stricter and encourages pattern matching; union allows more direct access but requires null handling.

---

## Implementation Order

1. Add data structures (`Variance`, `TypeDefInfo`, `ConstructorInfo`, `TRef`)
2. Update `TypeEnv` with storage and lookup methods
3. Implement three-pass processing in `Typer`
4. Implement variance inference
5. Implement subtyping rules
6. Update `TypePrinter` for TRef
7. Write tests
