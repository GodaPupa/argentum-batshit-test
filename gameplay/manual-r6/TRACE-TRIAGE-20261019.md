# R6 Commander deployment — exact R5 trace triage

Scope: isolated **read-only** R5 archival audit. No gameplay policy change or official activation.

- Parent evidence source: `e2e92a199bb2c165625bf1505089e8420d91d8b2`
- Passing four-player run: [39293363796](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39293363796)
- Immutable GitHub artifact: `12206343752`; archive SHA-256: `0562942a8b66a803e6e35265b5005220d3676b21869cca935e5cb1f2f66f71a7`
- Complete game: 2,571 accepted / 0 rejected actions, Seat 1 (`manual-mirror-0`) won on global turn 58 with 12 life
- `scoredCommanderOpportunityDecisions.manual-mirror-0 = 13`, `scoredCommanderDeclinedDecisions.manual-mirror-0 = 13`; these are **scored priority windows**, not 13 independent turns or policy defects.
- Source module: `ManualAnimarDevelopmentAdvisorModule.kt`, blob `b995dfc8d27dbc71da7785f7f708b0b0a442c3e8`. Credits *only* a measured same-turn legal, productive hand-creature follow-up. Does not assume future untaps, draws, untapped land drops, or multiplayer passes.

## All thirteen recorded seat-one offers

| Action | Global turn | Original chosen action | Animar adjusted score | Chosen score | Margin chosen over Animar |
| ---: | ---: | --- | ---: | ---: | ---: |
| 434 | 13 | Wood Elves | -133.538 | -131.718 | 1.820 |
| 816 | 21 | Wretched Gryff (emerge) | -67.641 | -63.801 | 3.840 |
| 1025 | 25 | Decimator of the Provinces (emerge) | -4.345 | 0.556 | 4.901 |
| 1316 | 29 | Fauna Shaman | -44.720 | -42.685 | 2.035 |
| 1517 | 33 | Eternal Witness | -49.542 | -48.267 | 1.275 |
| 1702 | 37 | Malevolent Hermit | -61.806 | -60.881 | 0.925 |
| 1813 | 40 | Fyndhorn Elves | -61.884 | -61.784 | 0.100 |
| 1921 | 43 | Temur Sabertooth | -63.803 | -60.208 | 3.595 |
| 2214 | 49 | Glen Elendra Archmage | -53.083 | -49.871 | 3.212 |
| 2368 | 52 | Vizier of the Menagerie | 16.747 | 16.915 | 0.167 |
| 2428 | 54 | Nulldrifter | 28.790 | 35.205 | 6.415 |
| 2494 | 56 | Bane of Progress | 57.083 | 61.755 | 4.672 |
| 2556 | 58 | Soul of the Harvest | 93.502 | 100.023 | 6.520 |

Numbers are extracted from the original `decisions.jsonl`, matched to the exact `actions.jsonl` submitted action index. Rounding is presentation only; unrounded source scores remain in the preserved archive.

### Initial decision classification — not yet a defect finding

- Opening/midgame (13–37): strong mana development, emerge payoff, recovery or interaction. Existing advisor reports no immediate legal, productive same-turn follow-up credit. These declines can be rational.
- Turn 40: narrow Fyndhorn-vs-Animar margin requires actual colored source and future-play review. No fixed commander bonus is authorized.
- Turn 43–49: higher-scoring resilience and interaction.
- Turn 52: narrow 0.167 margin vs Vizier; inspect current hand, board, priority and real source payment before judging.
- Turn 54–58: large margins for high-impact creature actions in a late-game finishing sequence.

The next evidence gate is `ManualR6OpportunityReplayTest`: checksum-bound actual R5 replay, exactly thirteen matched states, legal affordabilities, color-producing sources, tax, battlefield and opposing threats, and immediate legal scoring. **No policy adjustment should be accepted from raw counts or score margin alone.**

Preserve frozen deck, `AiProfile.CURRENT`, R5 Pact survival/payment and Ballista protections. No automatic 40-game batch. C4/D2 remains a distinct official qualification track.
