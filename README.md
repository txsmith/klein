# Klein

A tiny language for embedding customizable business rules.

## What is Klein?

Klein is a small, safe expression language designed to be embedded in larger applications. It lets tech-savvy business users — not just engineers — write rules, validations, and simple programs that your application executes.

```klein
# Loan approval logic
riskScore = calculateRisk(application)

if riskScore < 20 and amount < 5000 then
    approve()
else if riskScore < 50 then
    requestReview(assignReviewer(amount))
else
    reject('Risk score too high')
```

## The Key Idea

Klein programs are **pure expressions**. They can't access the network, read files, or modify state on their own. Instead, your application provides:

- **Inputs** — data the program can read (e.g., `customer`, `order`, `currentDate`)
- **Effects** — operations the program can invoke (e.g., `approve()`, `sendEmail()`, `ask()`)
- **Types** — the schema of available data (e.g., what fields exist on `Customer`)

The host application controls everything. Klein just describes the logic.

This separation means:

- **Safe** — users can't break your system; they can only use what you expose
- **Portable** — the same program runs on your backend, mobile app, or browser
- **Testable** — programs are pure functions from inputs to outputs

## Suspendable Effects

Effects in Klein can *suspend*. When a program calls `ask('How many acres?', double)`, execution pauses and your application takes over — showing a UI, waiting for input, even persisting state to a database. When the user responds, execution resumes exactly where it left off.

This enables:

- **Multi-step forms** that span sessions
- **Approval workflows** that wait days for human decisions  
- **Interactive wizards** driven by business logic

## Use Cases

- **Pricing rules** — let business teams adjust pricing logic without deployments
- **Approval workflows** — define who approves what, with what conditions
- **Dynamic forms** — conditional questions, computed fields, validation rules
- **Eligibility checks** — encode complex business rules in readable form
- **Guided troubleshooting** — branching decision trees maintained by support teams

## Features

- **Type inference** — no annotations required, but full type safety
- **Readable syntax** — `and`/`or`/`not` instead of `&&`/`||`/`!`
- **Indentation-based** — clean, Python-like block structure
- **Cross-platform** — compiles to JVM, JavaScript, and native

## Example

```klein
# Calculate shipping cost based on order

baseRate =
    if customer.tier == 'Premium' then 0
    else if customer.tier == 'Standard' then 5.99
    else 7.99

weightSurcharge = if totalWeight > 50 then 15.00 else 0

baseRate + weightSurcharge
```

Your application provides `customer`, `order`, and `totalWeight`. Klein computes the result. No network calls, no side effects — just a calculation.

## Status

Klein is in early development.

