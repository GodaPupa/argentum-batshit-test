# Manual Transmission — R8b mandatory Keep only (corrected successor)

**Status:** isolated opt-in seed-free capability extension, not a complete three-gear pilot or gameplay admission.

## Why R8b supersedes the unadopted R8 precursor
The first prospective R8 component incorrectly allowed **Bottom(count=7)** on the theory that bottoming the entire physical hand removes all choice. The real engine proves the opposite:
- Source: \`rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/MulliganHandler.kt\`, method \`handleBottomCards\`.
- It explicitly moves the selected cards to the library bottom **in the order specified** (original lines 227–244).
- Consequently choosing even all seven cards still chooses their order and can influence future library draws and access.
- R8b corrects this: **every Bottom(count) is rejected** until the accepted R3 Balanced Mulligan strategic ordering rules are qualified.

Do not promote or use R8 precursor commit \`ebca1744848cc4bb508980ee1e1bfb0d6f68838d\` as a pilot. Its standalone R8 proof, whether green or not, cannot establish strategic correctness for bottoming. Preserve it as a superseded research attempt.

## Source and scope
- Parent: R8 precursor \`ebca1744848cc4bb508980ee1e1bfb0d6f68838d\`.
- Original R7 accepted source \`5ca8ee8d9cd798322a40fea34fd16ba3a8cfa517\`, run \`39459805337\` success (27 seed-free composition tests).
- Frozen v0.7 Git blob \`c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89\`.
- CURRENT Git blob \`180918b2d7a59309e7b0617f2579fb2a6d06b7d5\`.
- Phase 1 KEEP_V07 accepted, zero authorized card changes.
- Phase 2 protocol \`MT_V07_PHASE2_CEDH_MULTIPLAYER_R1_2026_09_24\` forbids official games and seeds until all admission conditions pass.
- No production engine change, no PR merge, no official capability/primary allocation.

The corrected R8b opt-in policy implements exactly one narrowly **forced** pregame case for Cruise/Sport/Race: \`Mulligan(canMulligan=false)\` → \`Keep\`, when the trusted actor-masked observation exposes exactly seven physical own-hand cards and the exact public Animar commander. It does not require that the designated pregame actor hold regular game priority.

It explicitly fails closed for \`Mulligan(canMulligan=true)\`, **all** \`Bottom(count)\`, hidden/partial own hands, leaked opponent hand or library, wrong actor or commander, typed decisions, ordinary-stack decisions or opponent-role adoption. It does not implement historical R3 keep/mulligan or bottom ranking.

## Validation
- This successor changes only: prior R8 policy, prior R8 test, this protocol, and this new R8b workflow.
- Positive forced-keep cases for Cruise/Sport/Race and negative cases for voluntary mulligan and all bottom ordering.
- Inherited 27 R7 cases unchanged.
- Zero actual-deck games, new seeds, stored game outcomes, changes to v0.7, CURRENT, main, Phase 1 or Phase 2 admission authority.
- Result must be \`PASS_FORCED_KEEP_ONLY\`, not "R3 complete" or "gear complete".
- Any failed source/compilation/test step remains evidence; do not waive negative tests.

## Next strategic gate
Find exact original R3 Balanced Mulligan keep/bottom predicates from its accepted authority or build a separately labeled prospective challenger with predeclared tests and independent admission. Then qualify full strategic opening behavior for all three gears and opponents through masked trusted boundaries before any official game.
