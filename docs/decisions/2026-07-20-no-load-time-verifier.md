# No load-time IR verifier

**Status:** Current (reversed an earlier lean, 2026-07-20)

Stored Core IR is not verified at load time. The machine's per-operation checks (`asNum`,
field-miss, arity) stay — but they are understood as *unboxing*, not as a safety system: in a
boxed machine they are the unavoidable tag tests needed to read a value at all, so removing
them buys no speed and keeping them costs nothing beyond a better error on an impossible
branch.

A shipped verifier was rejected on three grounds:

- **A checksum beats it for corruption.** A verifier misses value-preserving corruption and
  costs more than a checksum; the version stamp already prevents lowerer/machine skew.
- **It fails correlated with the bugs it would catch.** A verifier written by the same
  authors against the same mental model as the lowerer fails on the same inputs. This is
  unlike GHC's Core Lint, which checks type preservation *across a transform* — an
  independent property with independent failure modes. We have no optimizer yet, so no
  independent property to check.
- **Memory safety is already Kotlin's.** The JVM verifier exists to protect memory safety
  against hostile bytecode; Kotlin gives us that for free.

A real validator earns its place only if Klein ever runs IR from untrusted third parties —
same shelf as a JIT. The surviving form today is well-formedness assertions in the lowering
pass's own tests: normal development hygiene, not a shipped component.
