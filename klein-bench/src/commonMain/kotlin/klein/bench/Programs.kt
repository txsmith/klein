package klein.bench

/**
 * The benchmark corpus: named sample programs that together represent the workloads Klein
 * should stay fast on. Every program is benchmarked at every pipeline stage.
 *
 * To track a new program, add an entry here AND list its name in the `@Param` annotation in
 * [ProgramSuiteBenchmark] — the two must stay in sync (there's a test for it in klein-lib's
 * absence; the benchmark setup fails fast on unknown names).
 *
 * Programs must type-check cleanly: the eval stage assumes checked input.
 */
object Programs {
    val suite: Map<String, String> =
        mapOf(
            // Pure operator chains — the sharpest tracker for numeric-model changes (doubles → rationals).
            "arith" to
                """
                a = 19.99 * 3 + 0.1 + 0.2 - 12.75 / 4
                b = a * a - a / 7 + a * 0.0825
                c = (a + b) * (a - b) + (b / a) * 100
                d = c % 17 + c / 3.5 - b * 0.5
                a + b + c + d
                """.trimIndent(),
            // Call-heavy recursion — interpreter dispatch and frame overhead.
            "fib" to
                """
                fun fib(n: Num): Num = if n < 2 then n else fib(n - 1) + fib(n - 2)
                fib(15)
                """.trimIndent(),
            // Linear recursion — accumulation over a long chain.
            "sumTo" to
                """
                fun sumTo(n: Num): Num = if n == 0 then 0 else n + sumTo(n - 1)
                sumTo(200)
                """.trimIndent(),
            // Closure creation and application — higher-order plumbing.
            "closures" to
                """
                fun compose(f: (Num) -> Num, g: (Num) -> Num): (Num) -> Num = |x -> f(g(x))|
                fun applyTimes(f: (Num) -> Num, n: Num, x: Num): Num =
                    if n == 0 then x else applyTimes(f, n - 1, f(x))
                inc = |x: Num -> x + 1|
                double = |x: Num -> x * 2|
                step = compose(inc, double)
                applyTimes(step, 50, 1)
                """.trimIndent(),
            // Records and field access — the shape of real rule data.
            "records" to
                """
                fun total(line: { price: Num, qty: Num }): Num = line.price * line.qty
                fun withTax(amount: Num, rate: Num): Num = amount + amount * rate
                a = { price = 19.99, qty = 3 }
                b = { price = 5.25, qty = 12 }
                c = { price = 199.00, qty = 1 }
                subtotal = total(a) + total(b) + total(c)
                withTax(subtotal, 0.0825)
                """.trimIndent(),
            // A realistic rule: sum types, constructors, branching on domain data.
            "rules" to
                """
                type Tier = Standard | Gold | Platinum

                fun discountRate(tier: Tier, subtotal: Num): Num =
                    if tier == Platinum then 0.15
                    else if tier == Gold then
                        if subtotal > 100 then 0.10 else 0.05
                    else 0

                fun shipping(subtotal: Num): Num =
                    if subtotal > 50 then 0 else 7.95

                fun checkout(tier: Tier, subtotal: Num): Num =
                    discounted = subtotal * (1 - discountRate(tier, subtotal))
                    discounted + shipping(discounted)

                checkout(Standard, 42.50) + checkout(Gold, 120.00) + checkout(Platinum, 899.99)
                """.trimIndent(),
        )
}
