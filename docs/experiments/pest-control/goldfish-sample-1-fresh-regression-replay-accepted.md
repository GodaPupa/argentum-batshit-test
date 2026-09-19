# Pest Control fresh Sample #1 — corrected same-seed regression replay

## Disposition

The corrected replay is **accepted only as regression-validation evidence**. The original fresh
Goldfish Sample #1 remains permanently rejected as performance/baseline evidence. Neither execution's
clock, lifegain rate, Weather distribution, size distribution, or other aggregate may be used for
sampling, optimization, performance inference, or variant comparison.

The exact vector is permanently retired after this clean replay. It must never be executed again.

## Execution identity

- Accepted correction head: `1528ad2906e2b8e85b50f9672efb964622739f71`
- Agent: validated generic Argentum/Pest Control solitaire policy
- Deck: permanent Pest Control v1.0 main deck, unchanged
- Vector: all 30 frozen fresh Sample #1 seeds, once each, unchanged and in original order
- Vector SHA-256: `50d831076ff08c5df70aaa21e6edf7deaf9c269eeb74f0b5f9981b8be51caad8`
- Preserved replay JSON SHA-256: `172e38e6cc02e4ada2a8706c898ea2ce5c302f113198d51e763e43086f03a8e7`
- Automated result: all 30 games completed; every built-in invariant passed

The original rejected execution remains separately preserved in
`goldfish-sample-1-fresh-rejected.{md,json}`. The corrected raw replay is preserved as
`goldfish-sample-1-fresh-regression-replay-accepted.json`.

## Weather audit

`Copies` records expected/observed Storm copies, excluding the original spell. “Concrete reason” is
the game object that consumed the separate lifegain event. No survival-required case can occur against
the blank solitaire opponent; emergency casting remains covered by the accepted deterministic agent
suite.

| Game | Turn | Storm | Copies | Concrete reason | Useful prior action | Useful later cast |
|---:|---:|---:|---:|---|---|---|
| 1 | 5 | 0 | 0/0 | Mascot | none | none |
| 2 | 6 | 1 | 1/1 | Mascot | cast Pest Mascot | none |
| 2 | 7 | 1 | 1/1 | Mascot | cast Carrier Thrall | none |
| 3 | 7 | 0 | 0/0 | Mascot | activate Food | none |
| 5 | 5 | 1 | 1/1 | Researcher + Mascot | cast Blood Researcher; play Swamp | none |
| 7 | 8 | 1 | 1/1 | Mascot | cast Pest Mascot; play Forest | none |
| 8 | 4 | 0 | 0/0 | Mascot | none | none |
| 11 | 4 | 0 | 0/0 | Mascot | none | none |
| 11 | 4 | 1 | 1/1 | Mascot | first Weather; play Swamp | none |
| 13 | 4 | 0 | 0/0 | Researcher | none | none |
| 13 | 6 | 1 | 1/1 | Researcher | cast Fierce Witchstalker; play Forest | none |
| 14 | 5 | 1 | 1/1 | Researcher + Mascot | productive Cast Down lethal setup | none |
| 15 | 4 | 1 | 1/1 | Researcher | cast Essence Warden | none |
| 16 | 6 | 0 | 0/0 | Researcher | none | none |
| 16 | 6 | 1 | 1/1 | Researcher | first Weather | none |
| 17 | 4 | 0 | 0/0 | Mascot | none | none |
| 19 | 4 | 1 | 1/1 | Researcher | cast Carrier Thrall; play Forest | none |
| 20 | 5 | 0 | 0/0 | Researcher | play Jungle Hollow | none |
| 21 | 7 | 0 | 0/0 | enhanced Follow | none | cast Follow the Lumarets |
| 22 | 6 | 1 | 1/1 | Mascot | cast Pest Mascot | none |
| 22 | 7 | 0 | 0/0 | Mascot | none | none |
| 23 | 8 | 1 | 1/1 | Researcher + Mascot | cast Essence Warden | none |
| 24 | 5 | 1 | 1/1 | Researcher | cast Essence Warden | none |
| 25 | 6 | 1 | 1/1 | Researcher + Mascot | cast Essence Warden | none |
| 29 | 5 | 0 | 0/0 | Researcher + Mascot | none | none |
| 29 | 5 | 1 | 1/1 | Researcher + Mascot | first Weather | none |

Every expected Storm copy was observed, and every original/copy produced exactly one separate
three-life event. The Researcher/Mascot trigger and counter totals remained balanced after those
events.

The Storm-0 casts were not the rejected null pattern. Each had a visible Researcher/Mascot payoff or
enabled the enhanced Follow in Game 21. Review of hand, available mana, and same-turn actions found no
useful spell that could precede a Storm-0 Weather while preserving the line. Notably:

- Games 11, 16, and 29 intentionally chained two Weather copies, making the second Weather a
  Storm-one cast while every event fed a visible payoff.
- In Game 21, casting Follow first would increase Storm but lose Follow's enhanced mode; Weather first
  was the concrete sequencing benefit.
- Games 2, 3, 14, 21, 23, and 24 resolved Weather in a same-turn terminal line, confirming the policy
  did not delay a concretely useful cast merely to seek a higher Storm count.

The formerly defective games changed as required: Game 1's Weather waited until Mascot was present;
Game 21's lone Weather enabled enhanced Follow; both Game 22 casts had Mascot present; and Game 23
sequenced Essence Warden before a Storm-one Weather with Researcher and Mascot present. None repeated
the original no-payoff/no-pressure cast.

## Complete Pest audit

- Seed identity/order: the JSON contains the exact 30 CSV seeds in the order, with no duplicates,
  replacements, exclusions, or additional games.
- Rules/state: no engine rejection, illegal action, wedge, unattributed/nonpositive life event, or
  built-in audit error occurred.
- Chainer's Edict: never cast into the empty opposing battlefield; all copies remained correctly
  classified as solitaire-stranded interaction.
- Bone Shards: never cast without a relevant opposing target; its modal/additional-cost shape created
  no false actionable bottleneck.
- Carrier Thrall/Scion: each Thrall death created exactly one Scion. Games 7, 22, and 30 used Cast Down
  on a Thrall as a productive conversion; each Scion entry remained visible to Warden and the payoff.
- Scion mana: no Scion was sacrificed for mana, so there was no production, consumption, funded
  action, unused mana, or provenance imbalance.
- Essence Warden: per-turn creature-entry counts and independent Warden life events matched exactly.
- Researcher/Mascot: every recorded trigger had exactly one corresponding +1/+1 counter; independent
  Weather and creature-entry events remained separate.
- Follow the Lumarets: every enhanced cast had prior life gain in that turn and every normal cast had
  none. Game 21 specifically used Weather to enable the enhanced mode.
- Generous Ent: every cycling and creature-cast action was engine-legal; no ordinary-cycling
  assumption was applied to another card.
- Mana telemetry: no opponent-dependent interaction appeared as an actionable bottleneck, and no
  `(card, turn, constraint)` key was emitted twice. Total-mana, color, and tapland classifications
  remained distinct.
- Mana legality and terminal reporting: every selected action was accepted by the engine, every game
  reached engine game-over, and every terminal received a mechanism classification.
- Solitaire sanity: the four Cast Down casts were manually inspected. Games 7, 22, and 30 converted
  Carrier Thrall into Scion plus normal engine triggers. In Game 14, both payoffs remained present, so
  Cast Down consumed the disposable Warden to create the extra Weather copy in a same-turn lethal
  line. None was an executable-but-null interaction.

## Gate

The replay is clean and accepted solely as regression-validation evidence. The fresh vector is now
permanently retired and may never be reused for sampling, optimization, performance inference, or
variant comparison. No new Sample #1, Sample #2, challenger construction, optimization, or opponent
self-play is authorized. Project Pest Control stops at the fresh-sample-readiness gate.
