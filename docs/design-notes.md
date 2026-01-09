# Klein Design Notes

Design rationale and future exploration for Klein's module system and bounded polymorphism.

## Records as Interfaces

The core insight: **records with function fields are interfaces**. A record type describes a shape; any record value matching that shape is an implementation.

```klein
type Policy = {
  fun evaluate { customer: Customer }: Decision
  fun maxAmount { customer: Customer }: Money
}

standardPolicy: Policy = {
  fun evaluate { customer } = ...
  fun maxAmount { customer } = ...
}

aggressivePolicy: Policy = {
  fun evaluate { customer } = ...
  fun maxAmount { customer } = ...
}

# Runtime selection
policy = if customer.segment == Premium then aggressivePolicy else standardPolicy
policy.evaluate { customer }
```

This gives us:

- Swappable implementations (policies, strategies)
- No implicit resolution complexity
- First-class values (pass interfaces around)
- Multiple implementations per "interface"

## Modules vs Records

| Aspect | Module | Record |
|--------|--------|--------|
| Contains types | Yes | No |
| Contains extension methods | Yes | No |
| Multiple instances | No (singleton) | Yes (values) |
| Structural typing | No | Yes |
| First-class value | No | Yes |
| Purpose | Organization, namespacing | Data, interfaces, polymorphism |

### When to Use Which

**Use modules for:**

- Grouping related types and functions
- Smart constructors
- Namespacing to avoid collisions
- Extension methods

**Use records-of-functions for:**

- Swappable implementations (policies, strategies)
- Dependency injection
- Runtime selection of behavior

## Recursion in Record Functions

Lambdas can reference their own binding name for recursion:

```klein
factorial = |n -> if n <= 1 then 1 else n * factorial { n - 1 }|

# Works in records too
myPolicy: Policy = {
  fun evaluate { customer } =
    if needsEscalation { customer } then
      evaluate { escalate { customer } }  # recursive call
    else
      Approved
}
```

## Field Access in Record Definitions

Functions in record values can access fields defined lexically prior:

```klein
config = {
  baseRate = 0.05
  multiplier = 2.0
  fun calculateRate { amount: Number }: Number = 
    baseRate * multiplier * amount  # can see baseRate and multiplier
}
```

## Effects

Effects handle external behavior at the host level:

```klein
# These are effects provided by the environment
name = ask { 'What is your name?', String }
customer = fetch { CustomerId { 123 } }
decision = requestApproval { loan, manager }
```

Different effect handlers for different contexts:

- Production: real database, real UI
- Testing: mocks
- Local dev: stubs

This complements records-of-functions: use records for pure polymorphism within Klein, effects for interaction with the host.

---

## Future Exploration: Type Classes via Records

Records with type parameters can serve as explicit "type classes":

```klein
type Monoid(a) = {
  fun empty {}: a
  fun combine { x: a, y: a }: a
}

intAddMonoid: Monoid(Int) = {
  fun empty {} = 0
  fun combine { x, y } = x + y
}

fun concat { monoid: Monoid(a), on xs: List(a) }: a =
  xs.fold { init = monoid.empty {}, f = |acc, x -> monoid.combine { acc, x }| }

[1, 2, 3].concat { intAddMonoid }  # 6
```

Spread syntax could help with passing multiple instances:

```klein
type Instances(a) = {
  monoid: Monoid(a),
  show: Show(a)
}

intInstances: Instances(Int) = { ... }

[1, 2, 3].concatAndShow { ...intInstances }
```

This remains explicit (no implicit resolution) and needs further design work.

---

## Open Question: Anonymous Unions

Should Klein support anonymous unions of existing types?

```klein
type Primitive = Int | String | Bool  # Union without constructors?

fun foo { x: Int | String }: ...  # Inline union?
```

**Current decision:** No anonymous unions. Use explicit constructors:

```klein
type Primitive = IntVal { value: Int } | StringVal { value: String } | BoolVal { value: Bool }
```

**Rationale:**
- Simpler syntax (no ambiguity with sum types)
- Consistent mental model (every `|` defines constructors)
- More verbose when you want a union, but clearer
