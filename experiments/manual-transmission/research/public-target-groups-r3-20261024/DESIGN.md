# Public target groups R3 — bounded fixture-path recovery

R2 compiled and passed the exact 34 client cases and all 39 actor/hidden-information safeguards. Its normal targeted gate ran all 20 cases: 18 passed; PG15 and PG16 failed because their setup sent an unpaid normal public cast through `ActionProcessor`. The public validator correctly rejected the missing mandatory sacrifice before `execute` could surface the server-internal unpaid-cost continuation.

R3 changes no production code. It keeps all 20 test identities and assertions. PG15 first asserts the public unpaid cast is rejected atomically with the exact cost error, then enters the already-qualified direct `CastSpellHandler.execute` path used by internal/free casts and continuation resumption. PG16 enters that same legitimate internal path before testing cancellation. This preserves the original intended provenance assertions while explicitly retaining the client trust boundary.

The visit-loss mutant is unchanged. Both normal and mutant forms must compile, and the mutant must produce exactly PG01, PG04, PG05, PG15 and PG17 assertion failures at frames 59, 79, 86, 147 and 166, with all other cases passing. Every positive gate and protected-byte check remains unchanged. R1 and R2 failure archives and assertions remain immutable. No card, resolver, official game, seed, budget or protected source is changed.
