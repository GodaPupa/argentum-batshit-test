# Manual Transmission — R8 forced-choice masked pregame seam

**Status:** isolated opt-in, seed-free capability extension of R7; **not** a full R3 Balanced Mulligan and **not** gameplay admission.

## Authority and protection
- R7 source: 5ca8ee8d9cd798322a40fea34fd16ba3a8cfa517, run 39459805337 SUCCESS, 27 synthetic composition tests (not actual decks).
- Receiving identity: original Manual Transmission v0.7, Git blob c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89; KEEP_V07; zero authorized hardware changes and inherited Game Changer count 0.
- Accepted Phase 1: PASS_BOUNDED_POLICY_ELASTICITY_KEEP_V07. Cruise / Sport / Race use the *same 100*, distinct policy surfaces, and Race's seven historic accepted overlays are timing-classification evidence only.
- Phase 2 protocol: MT_V07_PHASE2_CEDH_MULTIPLAYER_R1_2026_09_24, exact blob f605dc2ce4273a26a6e2387a2c6e4048e4012a79, gameplay_authorized=false, official counters all zero.
- R6 opponent-responsive micro-forks did not prove a deployment defect; the historical R5 opt-in is not a Cruise/Sport/Race pilot. No reopening of already closed deployment work.

## New tested behavior
The R7 manual partial composition gains one R8 opt-in masked-observation pregame seam:
1. On a **Mulligan(canMulligan=false)** prompt, with exactly seven physical visible own-hand cards and the exact public Animar commander identity, select **Keep** because no further mulligan is legal.
2. On a **Bottom(count=7)** prompt, with the same seven distinct physical own-hand cards, select exactly all seven IDs. This is forced selection without arbitrary priority; more usual contested bottom counts remain unimplemented.
3. Reject **Mulligan(canMulligan=true)**, **Bottom(count 1..6)**, hidden or incomplete own hands, leaked opponent hands/library, wrong actor, missing public Animar identity, opponent role, pending typed decisions, nonempty stack or extraneous ordinary legal action advertisements.

The trusted PhaseTwoPilotBoundary handles pregame outside ordinary priority: the active mulligan actor is **not required to equal priorityPlayerId**. R8 explicitly respects that distinction and does not relax R7's engine-prompt checks.

This is a safe forced-choice slice, not the accepted Balanced Mulligan strategic keep/bottom criteria. No pretend strategic R3 inference from similarly named unrelated Commander projects. No policy fallbacks or RACE-only overlay inheritance into lower gears.

## Validation
- Compile and run both the unchanged R7 PartialGearPilotTest and new ForcedPregamePolicyTest against synthetic masked four-seat observations only.
- New tests verify all three positive forced seats and a set of fail-closed negative cases. No real 100-card initialization, GameInitializer, new seed, game outcome or rating.
- Assert single isolated commit from R7; file diff only five R8 paths; R7 policy support source blobs and v0.7, CURRENT, qualification, Phase2 protocol stay byte-for-byte unchanged.

## Next work
To make R3 genuinely executable, retrieve exact accepted R3 historical keep/bottom predicates or independently predeclare and qualify a new opt-in R3 challenger without representing it as the original. Then add real ActionProcessor-bound masked pregame tests and competent Sport/Race strategic decisions. Only after whole exact opponent pilots, exact-deck mechanics, replay, collector, durability and independent capability admission can official capability games be contemplated.

**No games, no policy or card promotion, no default behavior changed.**
