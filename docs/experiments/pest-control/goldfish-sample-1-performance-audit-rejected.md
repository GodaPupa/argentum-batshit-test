# Pest Control v1.0 — Goldfish Sample #1 fresh performance audit

## Disposition

**REJECTED in full.** The 30 frozen seeds executed exactly once, unchanged and in their original
order, from green preflight head `9f3f3c1cc8270c3bbd55b8017c0935dbbee7771f`. Automated rules/state
invariants passed, but manual agent-sanity review found a clear Weather/Follow policy defect in Game
15. No game was removed, replaced, rerolled, or replayed. These aggregates are retained only to
describe the rejected execution and are not Pest Control baseline, optimization, matchup, or variant
comparison evidence.

- Seed-vector SHA-256: `c674ee12b4a3ebce6584d8d2c0c285a57f2400519e99fd08be058d76c3ad7513`
- Raw JSON SHA-256: `845abd2307e4f771c5c31acf6ce44ffe6d4ae6a0e5436e335cfc22ae2c4fe68b`
- Generated Markdown SHA-256: `b54352f146924866db663e7c571ee1eaa7e4cbaef2f0c7fd239d26e8abdd5aaa`
- Automated audit errors: 0
- Sample execution: one complete 30-game run; no retries
- Production deck/policy changes during execution: none

The complete machine-readable trace is preserved in
`goldfish-sample-1-performance-rejected.json`; the complete generated human-readable report is
preserved in `goldfish-sample-1-performance-rejected.md`.

## Rejecting defect

Game 15 (`0x4F16A05932EB13EE`) cast Weather the Storm on turn 5 at Storm 0 and 21 life
against the blank opponent. No Blood Researcher or Pest Mascot was present, no survival pressure
existed, and no enhanced Follow resolved that turn. The agent then activated Food, producing another
strategically null lifegain event. On turn 6, with four available lands and another Weather plus
Follow the Lumarets in hand, it cast Follow in normal mode instead of using Weather to enable the
enhanced mode. This is a clear residual strategic-policy defect under the accepted Weather/Follow
requirements and invalidates the entire sample.

No corrective work or replay is authorized by this audit. Investigation requires a separate
approval. `SHARED ARGENTUM CHANGE: yes` remains the record for the already accepted Weather-policy
correction at `8a13f2d8f9322b85ba6c52de571c591fea966a31`; this rejected execution introduced no change.

## Rejected-run descriptive metrics

- Mulligans: 8/30 games (26.7%), 10 total.
- Opening color access: 26 green+black, 3 green-only, 1 black-only, 0 neither.
- Meaningful development by T1/T2/T3: 8/17/25 games.
- First Warden: median T2 among 18 games that cast one. First Researcher/Mascot payoff: median T4
  among 24 games that cast one.
- Modeled wins: all 30 by combat; median T7; cumulative wins by T4/T5/T6/T7 were 0/0/9/24.
- Lifegain: 152 separate events, 256 total life; 94 events occurred with Researcher/Mascot present.
- Researcher triggers/counters: 45/45. Mascot triggers/counters: 74/74.
- Maximum Researcher sizes: 2/2×1, 3/3×2, 4/4×4, 5/5×2, 6/6×1, 7/7×1, 8/8×3.
- Maximum Mascot sizes: 2/3×1, 3/4×3, 4/5×2, 5/6×3, 6/7×3, 7/8×3, 8/9×2, 9/10×1.
- Coexistence: Warden+Researcher 8 games; Warden+Mascot 10; Researcher+Mascot 8; all three 4;
  any Warden+payoff 14/30 (46.7%). Payoff without Warden occurred in 13 games/50 tracked turns;
  Warden without payoff in 15 games/66 tracked turns.
- Counterfactual Warden opportunity: 39 qualifying creature entries in 13 games while payoff was
  present and Warden absent; descriptively, 26 Researcher and 38 Mascot counters were potentially
  forgone. No Bogwater Lumaret was simulated.
- Weather: 20 casts; Storm-count distribution 0×9 and 1×11; 18 casts in 15 games had a payoff
  present. Of nine Storm-0 casts, eight had concrete payoff/enhanced-Follow value and Game 15's cast
  did not. No useful preceding spell was identified that could profitably raise Storm without
  compromising an otherwise valid line; Game 15 should have held Weather rather than inflated Storm.
- Follow: 19 normal and 5 enhanced casts. Game 15 turn 6 is the clear case where available useful
  lifegain could have enabled enhanced mode; other Weather-in-hand normal casts either lacked the
  mana to cast both or preserved Weather for a materially better payoff line.
- Carrier Thrall/Scion: 2 deaths created exactly 2 Scions. There were 0 Scion mana activations,
  0 mana consumed, 0 funded actions, and 0 unused Scion mana.
- Generous Ent: 18 Forestcycling actions and 1 creature cast (T7 in Game 17).
- Actionable mana bottlenecks: 164 card-instance observations in all 30 games: 146 total-mana,
  10 color, and 8 tapland. No exact game/turn/card-instance/constraint observation was duplicated.
- Jungle Hollow: 35 tapped-entry events in 22 games; 8 were proximate deployment delays in 6 games.
- Solitaire-stranded interaction: 201 observations in 27 games—96 Cast Down, 62 Bone Shards, and
  43 Chainer's Edict. Edict and Bone Shards were never spent against the blank opponent. Cast Down
  was cast twice on Carrier Thrall, producing a Scion and payoff events; those were concrete self-
  sacrifice lines, not false opponent interaction.
- Functional states: 24 engine-functional, 6 fair-creature-functional, 0 interaction-heavy but
  goldfish-constrained, and 0 genuinely nonfunctional. These labels are descriptive only because the
  whole sample is rejected.

## Rules/state and telemetry audit

All 30 serialized `auditErrors` lists were empty. Seed count, order, and values matched the frozen
CSV. Every Weather copy count matched Storm count, and every original/copy produced a separate
lifegain event. Researcher and Mascot trigger totals matched counters added in every game. Every
Carrier death created exactly one Scion; the two Scions remained unsacrificed, so provenance correctly
recorded no production or consumption. Follow mode matched life-gained-this-turn state. Edict/Bone
empty-board behavior, Ent actions, per-card bottleneck deduplication, mana legality, and all 30 combat
terminal reports were internally consistent.

The rules/state and telemetry evidence is clean, but the Game 15 policy defect controls disposition:
Goldfish Sample #1 is rejected and the laboratory stops pending separately authorized investigation.
