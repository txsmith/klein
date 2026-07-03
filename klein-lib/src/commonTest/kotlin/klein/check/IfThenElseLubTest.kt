package klein.check

import klein.check.Type.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Red targets for `if`/`else` branch joining. `synthIfThenElse` accepts a branch only when one
 * branch's type is a subtype of the other's; it does **not** compute a least upper bound. These
 * document the intended behavior once branches are joined via a poly-aware LUB:
 *
 *  - a structural LUB of branches that share a common supertype (records with overlapping fields), and
 *  - polymorphic branch values, which today trip the `isSubtype`/`lub` `!is TForall` guard and crash.
 *
 * All `@Ignore` until `synthIfThenElse` joins branches with `subtyping.lub` (extended to `∀`).
 */
class IfThenElseLubTest {
    @Ignore // red: needs structural LUB — currently a "branches must be the same type" error
    @Test
    fun recordBranches_lubToCommonFields() =
        assertEquals(
            TRecord(mapOf("x" to TNum)),
            infer("if true then { x = 1, y = 2 } else { x = 1, z = 3 }").type,
        )

    @Ignore // red: disjoint records LUB to the empty record; currently rejected
    @Test
    fun disjointRecordBranches_lubToEmptyRecord() =
        assertEquals(
            TRecord(emptyMap()),
            infer("if true then { x = 1 } else { y = 2 }").type,
        )

    @Ignore // red: a ∀-typed branch trips the isSubtype guard and throws; the join should be the scheme
    @Test
    fun polymorphicBranches_sameSchemeJoin() {
        // both branches are `∀T. (T) -> T`; joining them should yield that scheme, not crash.
        val result = infer("fun id(a: 'T): 'T = a\nif true then id else id")
        assertTrue(result.errors.isEmpty())
        assertTrue(result.type is TForall)
    }
}
