# Manual Transmission — Precision Upgrade opt-in research (2026-10-20)

Status: **PUBLISHED DECK INPUTS ONLY — NO RULES PREFLIGHT, CI OR FOUR-PLAYER GAMES EXECUTED**.

Repository: `GodaPupa/argentum-batshit-test`
Isolated branch: `research/manual-precision-upgrade-20261020`
Branch base (main): `37a603617a03dfd2d81b39dae004f7f336a70dfc`
Frozen authority: `experiments/manual-transmission/control/v0.7.decklist.txt`
Frozen blob: `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`
Recorded frozen SHA-256: `6c28f0629d8ff0a859784f8c7dc0a47054b298d17c6b8e041a4a0a1d8744f111`

## Deck inputs

- `CONTROL.v0.7.decklist.txt` is byte-identical to frozen v0.7, *not* a new control definition.
- `CHALLENGER-A.mana.decklist.txt` preserves all 100 slots, replacing exactly seven lands in-place:
  - Path of Ancestry -> Mana Confluence
  - Frontier Bivouac -> City of Brass
  - Dreamroot Cascade -> Breeding Pool
  - Stormcarved Coast -> Steam Vents
  - Sheltered Thicket -> Stomping Ground
  - Evolving Wilds -> Misty Rainforest
  - Terramorphic Expanse -> Wooded Foothills
- `CHALLENGER-B.precision.decklist.txt` retains A's seven substitutions and adds three **declared exploratory cuts**, also in-place:
  - Farhaven Elf -> Bloom Tender (trade permanent land ramp for creature-based colored acceleration)
  - Lifecrafter’s Bestiary -> Worldly Tutor (trade repeatable scry/draw for cheap conditional creature search)
  - Rapid Hybridization -> Fierce Guardianship (trade a cheap creature answer for commander-dependent noncreature protection)

All three are 100-card singleton decks by source-count and name-duplicate checks (six Forests, two Islands and two Mountains are intentional basics), including Animar and treating Disciple of Freyalise as an MDFC. Each has 93 distinct card lines. New replacements are candidates, **not** independently proven improvements or final accepted cuts. Number of dedicated lands is unchanged.

## Verified publication facts

Readback from the isolated branch confirmed:
- CONTROL is byte-for-byte the same as frozen main; blob `c350bdc86eb2d37e5da4eb712e8ee2fa729b7e89`.
- A blob: `7984a2e87d8ddbe023d45620414d25a61691a040`.
- B blob: `fc217e1a282eec67cb0abf46b824fc1cd9e73c66`.
- Exact source differences are seven slots for A versus CONTROL and ten slots for B versus CONTROL, **with original slot positions retained**. Verify the corresponding positions *after* real GameInitializer before treating games as matched.

## Unrun gates before gameplay

1. Confirm compiled `MtgSetCatalog` identities, Temur color identity, Commander legality and lack of banned cards for each arm; fail closed if a card is not implemented.
2. Run concrete, targeted functionality tests for all ten additions: City of Brass/Mana Confluence damage/payment; shockland choice/payment; fetch search/shuffle and legal targets; Bloom Tender colored mana and summoning sickness; Worldly Tutor search-to-top; Fierce Guardianship alternate cost and targets.
3. Run selected previously accepted Pact, Ballista X/payment, Statue, combat and Animar deployment regressions using the same pinned profile for all arms.
4. Perform four-player exact-slot initializer preflights with R19-R22 method, checking seat/ID/slot parity; **same seed** by arm does not prove same slot unless checked.
5. **Then** predeclare two novel gameplay seeds, fixed four-seat assignment, game budget max 6 pilot games (CONTROL/A/B × 2 seeds). If justified, three additional predeclared seeds for CONTROL vs selected challenger, max 6 more. Total max 12 full games; no rerolls or winner-driven reseeding.
6. Report actual decisions, commander deployment, mana access, card casts, combo conversions, meaningful defenses, errors, censored runs, seed/seat identities and exact source/runner/artifact hashes. Maintain conclusions under small-sample uncertainty.

## Safeguards

No `AiProfile.CURRENT`, frozen v0.7, main, accepted qualification, Race overlay or prior gameplay artifact was changed. No workflow was dispatched, no preflight was executed, and no game was played or won by these three configurations at publication time. Wan Shi Tong remains outside this comparison. Any further run requires real compiled card and behavior support; do not substitute approximate or unrelated card behavior.

Accept/reject this isolated challenger only by actual reproducible evidence. No automatic promotion or merge.

## Runtime-composition boundary (verified 2026-10-20)

This deck-only research branch starts from main `37a603617a03dfd2d81b39dae004f7f336a70dfc`. The R22 four-player matched harness `ai/src/test/kotlin/com/wingedsheep/ai/engine/ManualR22MatchedWanGameplayHarnessTest.kt` is **not present on main**; do not claim this branch already contains a runnable matched-game test. The separate opt-in R22 gameplay source `gameplay/manual-r22-matched-wan-gameplay-authorized-20261019` resolves to `10675d8dc3133294c035606f656f9e4d12d50939` and contains the accepted R19-R22 slot/initializer harness components. A runtime candidate must be composed independently from this pinned source and these byte-verified deck inputs; it must remove Wan-specific assumptions without changing frozen controls or existing accepted profile behavior. Only then run compiled rules checks and bounded games. The connected API session can publish GitHub branches/files but has not exposed a workflow-dispatch action, so no execution/CI claim follows from publication alone.
