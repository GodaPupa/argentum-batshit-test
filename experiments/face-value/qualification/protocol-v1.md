# Face Value Tier 1 Qualification Protocol v1

Status: input and capability gates passed; Stage 1 preboard seed freeze authorized. No qualification game had been executed when the Stage 1 vector was generated.

## Immutable control

The permanent control is **Face Value — Temur Chrysalis E — Final Forge 75**. The exact machine-readable copy is `control/fv-temur-chrysalis-e-final-forge-75.dck` (60 main, 15 sideboard). It must remain byte-identical throughout qualification.

The historical workflow `.github/workflows/face-value-permanent-final75-freeze.yml` freezes the older Simic v1.2 list. It is provenance-only and is not the qualification control.

The permanent control may never be overwritten by a challenger. A challenger receives a distinct ID, file, seed vector, and results ledger. Promotion requires a predeclared comparison followed by independent fresh-seed replication.

## Gauntlet selection

The core gauntlet is every archetype marked Tier A by the MTGDecks Pauper snapshot on 2026-09-20. MTGGoldfish is an independent prevalence cross-check and the source for exact downloadable tournament lists. The selection was made before Face Value qualification outcomes existed.

Representative lists favor a current successful MTGO Challenge result, then a current 5-0 League result. Exact URLs, dates, pilots, finishes, source percentages, and local paths are frozen in `metagame-snapshot-2026-09-20.json`.

## Evidence gates

1. **Input gate:** validate the immutable 60/15 control, all eight 60/15 opponents, exact declared hashes, provenance completeness, distinct deck identities, and no qualification results/seeds.
2. **Capability gate:** use deterministic non-official fixtures to prove Forge can load every card and complete play/draw smoke games. Capability results are not matchup evidence.
3. **Preboard freeze:** generate one fresh cryptographic vector per matchup only after the capability gate is green. Audit every seed against the complete repository history. Freeze equal play/draw assignments before outcome exposure.
4. **Preboard execution:** consume every assignment exactly once; retain per-game logs, seat assignment, winner, turns, mulligans when available, key-card use, timeouts, draws, and execution flags.
5. **Gameplay audit:** quarantine the entire affected block for material AI, rules, parsing, timeout, or target-selection defects. Rejected seeds are permanently retired and never rerolled or rehabilitated.
6. **Postboard construction:** declare both players' maps from the frozen sideboards without consulting hidden qualification outcomes. Validate 60/15 legality and engine-density constraints before play.
7. **Postboard execution:** use a new, non-overlapping vector and preserve the same play/draw and telemetry discipline.
8. **Structural diagnosis:** open a challenger only when accepted cross-matchup evidence identifies a repeatable structural weakness. No tuning against quarantined or exposed-but-unaccepted outcomes.
9. **Promotion:** require an accepted discovery block and an independent fresh-seed replication. The permanent control remains preserved even if a challenger becomes the recommended list.

## Qualification interpretation

Simulator evidence can reject Tier 1 readiness or identify coverage gaps; it cannot alone establish human tournament Tier 1 status. The final report must separate simulator results from human-event evidence such as leagues, challenges, win rate, conversion, and metagame share.

No pooled overall win rate is reported until every included matchup block passes audit. Matchup results remain separate, with play/draw splits and uncertainty intervals; any metagame-weighted estimate must state the exact frozen weights and cannot replace matchup-level gates.

## Preboard Stage 1 design

Stage 1 is a coverage screen, not a promotion experiment. It contains 32 games per matchup: 16 with Face Value in seat 1 (play) and 16 with Face Value in seat 2 (draw), for 256 games total. Every game has its own seed. The full assignment order is frozen before any qualification outcome exists.

Stage 1 can reject a block, quarantine a block, identify a candidate structural concern for later confirmation, or authorize a separately frozen confirmatory block. It cannot promote a challenger or establish Tier 1 status. No deck change may be designed from an exposed Stage 1 outcome unless the entire relevant matchup block passes the gameplay audit and the structural signal is independently confirmed under a predeclared fresh-seed experiment.

Capability run `35522374363` at Forge commit `f387ede550e336315637a8b765f0927ac768c113` completed all 16 diagnostic seat-order fixtures with zero audit flags. Diagnostic seeds 710001 through 710008 and their outcomes are permanently ineligible for qualification evidence.

## Seed discipline

Official qualification seeds do not exist at this protocol commit. When created, they must be fresh, unique, nonzero signed 64-bit values from an operating-system cryptographic source; recorded in order; hashed; assigned to matchup and seat before play; checked against every historical Face Value seed discoverable in the repository; and permanently registered regardless of acceptance or rejection.
