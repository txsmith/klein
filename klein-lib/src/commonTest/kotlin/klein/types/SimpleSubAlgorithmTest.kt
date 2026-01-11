package klein.types

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Test cases from the SimpleSub algorithm paper/implementation.
 * These verify that our type simplification produces clean, readable types
 * matching the expected SimpleSub output.
 *
 * Reference: https://github.com/LPTK/simple-sub
 */
class SimpleSubAlgorithmTest {

    // ==========================================
    // Basic Functions
    // ==========================================

    @Test
    fun simpleSub_integerLiteral() {
        assertType("Num", infer("42"))
    }

    @Test
    fun simpleSub_unusedParam() {
        // fun x -> 42 : ⊤ -> int
        assertType("(Any) -> Num", infer("|x -> 42|"))
    }

    @Test
    fun simpleSub_identity() {
        // fun x -> x : 'a -> 'a
        assertType("(a) -> a", infer("|x -> x|"))
    }

    @Test
    fun simpleSub_higherOrderApplication() {
        // fun x -> x 42 : (int -> 'a) -> 'a
        // x is applied to 42, so x must be a function from Num
        assertType("((Num) -> a) -> a", infer("|x -> x(42)|"))
    }

    @Test
    fun simpleSub_appliedIdentity() {
        // (fun x -> x) 42 : int
        assertType("Num", infer("|x -> x|(42)"))
    }

    @Test
    fun simpleSub_constFunction() {
        // fun x -> fun y -> x : 'a -> ⊤ -> 'a
        assertType("(a) -> (Any) -> a", infer("|x -> |y -> x||"))
    }

    // ==========================================
    // Booleans
    // ==========================================

    @Test
    fun simpleSub_booleanLiteral() {
        assertType("Bool", infer("true"))
    }

    @Test
    fun simpleSub_negation() {
        assertType("Bool", infer("not true"))
    }

    @Test
    fun simpleSub_functionOverBools() {
        // fun x -> not x : bool -> bool
        assertType("(Bool) -> Bool", infer("|x -> not x|"))
    }

    @Test
    fun simpleSub_appliedNegation() {
        // (fun x -> not x) true : bool
        assertType("Bool", infer("|x -> not x|(true)"))
    }

    @Test
    fun simpleSub_conditional() {
        // fun x -> fun y -> fun z -> if x then y else z : bool -> 'a -> 'a -> 'a
        assertType("(Bool) -> (a) -> (a) -> a", infer("|x -> |y -> |z -> if x then y else z|||"))
    }

    // ==========================================
    // Records
    // ==========================================

    @Test
    fun simpleSub_fieldAccess() {
        // fun x -> x.f : {f: 'a} -> 'a
        assertType("({ f: a }) -> a", infer("|x -> x.f|"))
    }

    @Test
    fun simpleSub_emptyRecord() {
        assertType("{}", infer("{}"))
    }

    @Test
    fun simpleSub_simpleRecord() {
        // { f = 42 } : {f: int}
        assertType("{ f: Num }", infer("{ f = 42 }"))
    }

    @Test
    fun simpleSub_directFieldAccess() {
        // { f = 42 }.f : int
        assertType("Num", infer("{ f = 42 }.f"))
    }

    @Test
    fun simpleSub_polymorphicFieldAccess() {
        // (fun x -> x.f) { f = 42 } : int
        assertType("Num", infer("|x -> x.f|({ f = 42 })"))
    }

    @Test
    fun simpleSub_functionResultInRecord() {
        // fun f -> { x = f 42 }.x : (int -> 'a) -> 'a
        assertType("((Num) -> a) -> a", infer("|f -> { x = f(42) }.x|"))
    }

    @Test
    fun simpleSub_unusedFieldInRecord() {
        // fun f -> { x = f 42, y = 123 }.y : (int -> ⊤) -> int
        // f is called but result unused, y is returned
        assertType("((Num) -> Any) -> Num", infer("|f -> { x = f(42), y = 123 }.y|"))
    }

    @Test
    fun simpleSub_multiFieldRecord() {
        assertType("{ a: Num, b: Bool, c: String }", infer("{ a = 1, b = true, c = 'hello' }"))
    }

    @Test
    fun simpleSub_nestedRecordAccess() {
        assertType("Num", infer("{ outer = { inner = 42 } }.outer.inner"))
    }

    // ==========================================
    // Self-Application (requires subtyping)
    // ==========================================

    @Test
    fun simpleSub_selfApplication() {
        // fun x -> x x : 'a ∧ ('a -> 'b) -> 'b
        // x is both the function and its argument
        // In Klein, this requires x to be a function that can take itself
        val result = inferWithErrors("|x -> x(x)|")
        // This should type-check in SimpleSub due to subtyping
        assertEquals(0, result.errors.size, "Self-application should type-check: ${result.errors}")
    }

    @Test
    fun simpleSub_tripleSelfApplication() {
        // fun x -> x x x : 'a ∧ ('a -> 'a -> 'b) -> 'b
        val result = inferWithErrors("|x -> x(x)(x)|")
        assertEquals(0, result.errors.size, "Triple self-application should type-check: ${result.errors}")
    }

    @Test
    fun simpleSub_selfApplicationMixed1() {
        // fun x -> fun y -> x y x : 'a ∧ ('b -> 'a -> 'c) -> 'b -> 'c
        val result = inferWithErrors("|x -> |y -> x(y)(x)||")
        assertEquals(0, result.errors.size, "Mixed self-application should type-check: ${result.errors}")
    }

    @Test
    fun simpleSub_selfApplicationMixed2() {
        // fun x -> fun y -> x x y : 'a ∧ ('a -> 'b -> 'c) -> 'b -> 'c
        val result = inferWithErrors("|x -> |y -> x(x)(y)||")
        assertEquals(0, result.errors.size, "Mixed self-application 2 should type-check: ${result.errors}")
    }

    @Test
    fun simpleSub_omegaCombinator() {
        // (fun x -> x x) (fun x -> x x) : ⊥
        // The omega combinator diverges, type is bottom
        val result = inferWithErrors("|x -> x(x)|(|x -> x(x)|)")
        assertEquals(0, result.errors.size, "Omega combinator should type-check: ${result.errors}")
        // Result type should simplify to Nothing (bottom) since it diverges
    }

    @Test
    fun simpleSub_selfApplicationInRecord() {
        // fun x -> { l = x x, r = x } : 'a ∧ ('a -> 'b) -> {l: 'b, r: 'a}
        val result = inferWithErrors("|x -> { l = x(x), r = x }|")
        assertEquals(0, result.errors.size, "Self-application in record should type-check: ${result.errors}")
    }

    // ==========================================
    // Let-Polymorphism
    // ==========================================

    @Test
    fun simpleSub_letPolymorphism_classic() {
        // let f = fun x -> x in { a = f 0, b = f true } : {a: int, b: bool}
        val program = """
            f = |x -> x|
            { a = f(0), b = f(true) }
        """.trimIndent()
        assertType("{ a: Num, b: Bool }", infer(program))
    }

    @Test
    fun simpleSub_letPolymorphism_withCapture() {
        // fun y -> let f = fun x -> x in { a = f y, b = f true } : 'a -> {a: 'a, b: bool}
        val program = """
            |y ->
              f = |x -> x|
              { a = f(y), b = f(true) }
            |
        """.trimIndent()
        assertType("(a) -> { a: a, b: Bool }", infer(program))
    }

    @Test
    fun simpleSub_letPolymorphism_withUnionInput() {
        // fun y -> let f = fun x -> y x in { a = f 0, b = f true } : (bool ∨ int -> 'a) -> {a: 'a, b: 'a}
        // y is applied to both int and bool, so y accepts their union
        val program = """
            |y ->
              f = |x -> y(x)|
              { a = f(0), b = f(true) }
            |
        """.trimIndent()
        val result = inferWithErrors(program)
        assertEquals(0, result.errors.size, "Let-polymorphism with union input should type-check: ${result.errors}")
    }

    @Test
    fun simpleSub_letPolymorphism_idAppliedToItself() {
        // let id = fun x -> x in id id : 'a -> 'a
        val program = """
            id = |x -> x|
            id(id)
        """.trimIndent()
        assertType("(a) -> a", infer(program))
    }

    @Test
    fun simpleSub_letPolymorphism_nestedLet() {
        // let f = fun x -> x in let g = f in g 42
        val program = """
            f = |x -> x|
            g = f
            g(42)
        """.trimIndent()
        assertType("Num", infer(program))
    }

    // ==========================================
    // Top and Bottom types
    // ==========================================

    @Test
    fun simpleSub_topType_unusedParameter() {
        // Parameters that are never used simplify to ⊤ (Any)
        assertType("(Any) -> (Any) -> Num", infer("|x -> |y -> 42||"))
    }

    @Test
    fun simpleSub_bottomType_omegaResult() {
        // The omega combinator's result type is ⊥ (Nothing)
        val result = inferWithErrors("|x -> x(x)|(|x -> x(x)|)")
        if (result.errors.isEmpty()) {
            // If it type-checks, the result should be Nothing
            val typeStr = TypePrinter.print(result.type)
            // The result of omega is bottom because it never returns
            assertEquals("Nothing", typeStr)
        }
    }

    @Test
    fun simpleSub_topInFunctionResult() {
        // fun f -> f 42 where f's result is unused would have ⊤ result
        // But if we return the result, it's polymorphic
        assertType("((Num) -> a) -> a", infer("|f -> f(42)|"))
    }

    // ==========================================
    // Intersection types (implicit via constraints)
    // ==========================================

    @Test
    fun simpleSub_intersectionFromConditional() {
        // fun x -> fun y -> if x then y else x : 'a ∧ bool -> 'a -> 'a
        // x must be bool (for condition) and also same type as y (for else branch)
        // This creates an intersection constraint on x
        val result = inferWithErrors("|x -> |y -> if x then y else x||")
        assertEquals(0, result.errors.size, "Intersection from conditional should type-check: ${result.errors}")
    }

    @Test
    fun simpleSub_intersectionRecordBranches() {
        // if true then { a = 1, b = true } else { b = false, c = "hi" }
        // Result type is the intersection: {b: bool}
        assertType("{ b: Bool }", infer("if true then { a = 1, b = true } else { b = false, c = 'hi' }"))
    }

    // ==========================================
    // Twice combinator (classic example)
    // ==========================================

    @Test
    fun simpleSub_twiceCombinator() {
        // fun f -> fun x -> f (f x) : ('a ∨ 'b -> 'a) -> 'b -> 'a
        // f is applied twice, creating union/intersection constraints
        val result = inferWithErrors("|f -> |x -> f(f(x))||")
        assertEquals(0, result.errors.size, "Twice combinator should type-check: ${result.errors}")
    }

    @Test
    fun simpleSub_twiceCombinatorApplied() {
        // let twice = fun f -> fun x -> f (f x) in twice (fun n -> n + 1) 0
        val program = """
            twice = |f -> |x -> f(f(x))||
            twice(|n -> n + 1|)(0)
        """.trimIndent()
        assertType("Num", infer(program))
    }

    @Test
    fun simpleSub_twiceOnIdentity() {
        // let twice = fun f -> fun x -> f (f x) in twice (fun x -> x)
        val program = """
            twice = |f -> |x -> f(f(x))||
            twice(|x -> x|)
        """.trimIndent()
        assertType("(a) -> a", infer(program))
    }
}
