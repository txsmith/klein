# DSL Project Summary

## Vision

A cross-platform expression language with algebraic effects (pluggable suspending operations). The language is pure and generic; effects like `ask`, `approve`, `fetch` are provided by the embedding environment. This enables non-engineers to author business logic that runs anywhere.

**Core insight:** The interpreter yields serializable suspension points. Execution can pause, persist, transfer across platforms (backend → mobile → web), and resume. This is algebraic effects made practical for distributed, multi-platform business applications.

## Use Cases

- **Interactive forms** — field officers collecting farmer data, conditional questions, computed values
- **Approval workflows** — loan approval chains that suspend for human decisions (hours/days)
- **Business rules** — pricing logic, eligibility checks, product availability
- **Onboarding/KYC flows** — document collection, verification steps, manual review gates
- **Guided troubleshooting** — branching decision trees with escalation
- **Surveys/assessments** — scored questionnaires with conditional sections

## Language Design

### Core (pure, no effects)
- Variables, bindings, scopes (`x = ...`)
- Types: `int`, `double`, `string`, `boolean`, `List<A>`, `Option<A>`, records, enums
- Arithmetic, comparisons, boolean logic
- Conditionals (`if`/`else`)
- List operations (literals, concat, maybe iteration)
- Option handling with automatic lifting (`double + Option<double>` → `Option<double>`)

### Effects (provided by environment)
Effects are declared by the host system, not hardcoded in the language:

```json
{
  "effects": {
    "ask": {
      "params": [
        { "name": "prompt", "type": "string" },
        { "name": "type", "type": "InputType" }
      ],
      "returnType": "$T",
      "effectKind": "suspend"
    }
  }
}
```

All effects are modeled as suspensions. The interpreter yields; the host handles the effect and resumes with a value. This is maximally general—async I/O, user input, and sync operations all fit the same model.

### Environment Configuration
Each editor/runtime context provides:
- **Schema** — available types, enums, records (e.g., `Customer`, `Product`, `Region`)
- **Globals** — pre-bound variables (e.g., `customer: Customer`)
- **Available effects** — what operations are allowed in this context
- **Expected return type** — what the expression should produce

This allows one language to power different use cases:
- Form builder: `ask`, `show` effects, returns `List<SelectedItem>`
- Approval rules: `fetch`, `request_approval` effects, returns `boolean`
- Pricing logic: no effects (pure), returns `Money`

## Architecture

```
┌─────────────────────────────────────────────────────┐
│  DSL Source                                         │
│  e.g., "acreage = ask('Acres', double)"             │
└─────────────────────┬───────────────────────────────┘
                      │ parse
                      ▼
┌─────────────────────────────────────────────────────┐
│  AST (serializable)                                 │
└─────────────────────┬───────────────────────────────┘
                      │ type check
                      ▼
┌─────────────────────────────────────────────────────┐
│  Type Checker (uses EditorEnvironment)              │
│  - validates types, scopes, effect usage            │
│  - produces errors with source locations            │
└─────────────────────┬───────────────────────────────┘
                      │ interpret
                      ▼
┌─────────────────────────────────────────────────────┐
│  Interpreter (pure state machine)                   │
│  step(ast, state) → Yield(effect, args, resume)     │
│                   | Done(value)                     │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│  Host / UI Adapter                                  │
│  - handles effects (show UI, make requests, etc.)   │
│  - resumes interpreter with result values           │
└─────────────────────────────────────────────────────┘
```

## Technical Stack

### Kotlin Multiplatform (KMP)
Single codebase compiles to all targets:

```
dsl-lib/
├── commonMain/
│   ├── ast/
│   │   └── AST.kt                  # sealed class hierarchy, serializable
│   ├── parser/
│   │   ├── Lexer.kt
│   │   └── Parser.kt               # hand-rolled recursive descent
│   ├── typechecker/
│   │   ├── Types.kt
│   │   ├── TypeChecker.kt
│   │   └── Errors.kt               # rich error messages with source spans
│   ├── interpreter/
│   │   ├── Interpreter.kt          # pure: (AST, State) → Yield | Done
│   │   └── State.kt                # serializable execution state
│   └── environment/
│       ├── Schema.kt               # type definitions
│       ├── EditorEnvironment.kt    # schema + globals + effects + expected return
│       └── SchemaLoader.kt         # JSON → EditorEnvironment
│
├── jvmMain/                        # JVM-specific (if needed)
├── jsMain/                         # JS exports for npm
├── nativeMain/                     # iOS/macOS/Linux/Windows
│
└── tests/
    └── cases/
        ├── valid/                  # source → expected AST (golden tests)
        └── errors/                 # source → expected error message
```

**Outputs:**
- `dsl-lib-jvm.jar` — Scala backend, any JVM
- `dsl-lib.aar` — Android
- `dsl-lib.js` + `.d.ts` — Browser, Node
- Native binaries — CLI tools, iOS

### Parser: Hand-Rolled Recursive Descent
- Full control over error messages and recovery
- ~800-1200 lines of Kotlin
- Critical: track source positions (`SourceSpan`) on every AST node from day one
- Shared golden test suite validates parser across all platforms

### Editor: Monaco
Browser-based authoring with VS Code-quality UX:

```typescript
// Syntax highlighting via Monarch tokenizer
// Error squiggles via your type checker
// Completions via your Kotlin/JS lib
// Hover for types

import { DSL } from '@yourco/dsl-lib'

editor.onDidChangeModelContent(() => {
  const result = DSL.check(editor.getValue(), environmentJson)
  monaco.editor.setModelMarkers(model, 'dsl', result.errors.map(toMarker))
})

monaco.languages.registerCompletionItemProvider('dsl', {
  provideCompletionItems: (model, position) => {
    return DSL.completions(model.getValue(), position.lineNumber, position.column, envJson)
  }
})
```

**Progression:**
1. V1: Syntax highlighting + error squiggles
2. V2: Completions, hover, go-to-definition
3. V3: Inline type hints, quick fixes, refactoring
4. V4: Real LSP server for VS Code/IntelliJ (optional)

## Interpreter Design

The interpreter is a pure state machine. No UI, no I/O, just data in → data out.

```kotlin
sealed class StepResult {
    data class Yield(
        val effect: String,           // "ask", "fetch", etc.
        val args: List<Value>,        // effect arguments
        val continuation: State       // how to resume
    ) : StepResult()
    
    data class Done(
        val value: Value
    ) : StepResult()
}

class Interpreter(private val ast: AST) {
    fun step(state: State): StepResult {
        // Evaluate until we hit an effect or finish
    }
    
    fun resume(state: State, value: Value): State {
        // Inject value, return new state ready for next step()
    }
}
```

**Host loop (e.g., React):**

```typescript
const interp = new Interpreter(ast)
let state = interp.initialState()

while (true) {
  const result = interp.step(state)
  
  if (result.kind === 'done') {
    return result.value
  }
  
  // Handle the effect
  const value = await effectHandlers[result.effect](...result.args)
  state = interp.resume(result.continuation, value)
}
```

**Key property:** `State` is serializable. You can:
- Save to database, resume tomorrow
- Send from backend to mobile
- Survive app restarts

## Type System

### Built-in Types
- `int`, `double`, `string`, `boolean`
- `List<A>` — generic list
- `Option<A>` — nullable/optional
- `Unit` — void/nothing

### User-Defined (via Schema)
- Records: `Customer { credit_limit: double, region: Region }`
- Enums: `Region = Nakuru | Kisumu | Nairobi`

### Option Lifting
Automatic propagation through operations:

```
spouse_income: Option<double>  // from conditional ask
total = income + spouse_income // automatically Option<double>
```

Semantics: if any operand is `None`, result is `None`.

### Type Checking
- Schema-aware: knows what fields exist on `Customer`
- Effect-aware: knows `ask` isn't available in pure-expression contexts
- Good errors: "Unknown field 'credit_limt' on Customer, did you mean 'credit_limit'?"

## Sample Syntax (Strawman)

```
// Form for selling farm inputs

acreage = ask("How many acres?", double, range(0.5, 8.0))

seed = ask("Seed variety", select(seed_options))
show("${ceil(acreage * 5)} packets needed")

fertilizer = ask("Fertilizer type", select(fertilizer_options))
show("${acreage * 2} bags needed")

items = []
items = items + [item(seed.id, ceil(acreage * 5), seed.price)]
items = items + [item(fertilizer.id, acreage * 2, fertilizer.price)]

if is_kichawi_recommended and seed not in western_seeds {
  kichawi = ask("Kichawi Kill packs", double, range(0, ceil(acreage * 3)))
  if kichawi > 0 {
    items = items + [item(KICHAWI_ID, kichawi, kichawi_price)]
  }
}

return items
```

## Migration Path

### From Existing Scala eDSL
The current system has:
- Scala eDSL with `ChoiceExpr[A]` AST
- React interpreter (entangled with UI)
- Android interpreter (entangled with UI)

Migration:
1. Build new Kotlin lib with clean interpreter
2. Keep existing AST format initially (or translate)
3. Replace React interpreter with new Kotlin/JS + thin adapter
4. Replace Android interpreter with new Kotlin + thin adapter
5. Add parser for new surface syntax
6. Gradually migrate forms from Scala eDSL to new DSL source

### Separation of Concerns
```
dsl-lib/                         (OSS, generic, yours)
├── parser
├── typechecker
├── interpreter
└── environment

your-company-app/                (proprietary)
├── schemas/                     (Customer, Product, etc.)
├── forms/                       (DSL source files)
├── effect-handlers/             (React UI for ask, Android UI, etc.)
└── editor-integration/          (Monaco setup)
```

## Open Source Considerations

This is generic infrastructure, not specific to microfinance:

**Potential name:** TBD (exploring options: short, elegant, slightly cute, implies "small")

**Pitch:**
> A cross-platform expression language with algebraic effects.
> Define business logic once. Run it anywhere. Let non-engineers author it.

**What makes it viable:**
- Real production use (your company validates it)
- Cross-platform from day one (not an afterthought)
- Good editor story (Monaco integration)
- Extensible (effects are pluggable, schema is data)

**Comparison:**
- vs CEL: adds effects/suspension, not just pure expressions
- vs workflow engines (Temporal, etc.): lighter weight, embeddable, non-engineer friendly
- vs low-code platforms: real language with types, not hitting walls

## Next Steps

1. **Set up Kotlin Multiplatform project** — gradle, targets (jvm, js, native)
2. **Define AST** — sealed classes, serialization with kotlinx.serialization
3. **Implement lexer** — tokens, source positions
4. **Implement parser** — recursive descent, error recovery, good messages
5. **Implement type checker** — environment-aware, effect-aware
6. **Implement interpreter** — pure state machine, yield/resume
7. **Set up golden test suite** — shared cases across platforms
8. **Monaco integration** — syntax highlighting, then errors, then completions
9. **Build thin UI adapters** — React, Android
10. **Migrate one real form** — prove it works end-to-end

## Key Design Decisions (Already Made)

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Implementation language | Kotlin Multiplatform | Single codebase, all targets |
| Parser approach | Hand-rolled recursive descent | Full control over errors, no dependencies |
| Effect model | All effects are suspensions | Maximally general, simple interpreter |
| Type system | Dynamic with schema validation | Simpler than full static types, catches real errors |
| Option handling | Automatic lifting | Ergonomic for non-engineers |
| Editor | Monaco | VS Code quality in browser, good extension points |
| AST serialization | JSON (kotlinx.serialization) | Universal, debuggable |
| Environment config | JSON schema served by host | Flexible, multi-context support |

## References

- **Algebraic Effects:** Plotkin & Pretnar, "Handlers of Algebraic Effects" (2009)
- **Koka language:** practical algebraic effects, good docs
- **Free Monads:** "Free Monads for Less" series (Haskell)
- **CEL:** Google's Common Expression Language (for comparison)
- **Monaco Editor:** https://microsoft.github.io/monaco-editor/
