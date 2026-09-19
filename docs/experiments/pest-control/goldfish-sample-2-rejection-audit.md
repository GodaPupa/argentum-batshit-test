# Pest Control v1.0 — Goldfish Sample #2 Rejection Audit

Disposition: **formally rejected**. All 30 games ran exactly once in the frozen order from preflight
head `4dcf6f122bda469017611a9afdaf60c597e37d01`. No game was rerolled, replaced, excluded, repaired,
or replayed. The vector is permanently retired and its runner is hard-disabled.

The automatic rules/state audit completed with zero reported errors. Manual review nevertheless
found clear agent-policy and telemetry defects, so no Sample #2 aggregate is accepted as performance
evidence and no pooled Sample #1 + Sample #2 result is valid.

## Blocking defects

### Game 16 — profitable pre-Weather sequence missed

On T6, Pest controlled Pest Mascot and had three available mana, an unplayed Swamp, Carrier Thrall,
and Weather the Storm. The agent cast Weather at Storm 0, then played Swamp, then cast Carrier Thrall.
Playing the legal land first, casting the useful Carrier, and then casting Weather was executable for
the same four mana. It would have resolved Weather at Storm 1 and generated an additional independent
Mascot counter trigger. There was no survival requirement or other reason to cast Weather first.

The `manaPlausibleUsefulPrecedingSpells` observation recorded no candidate because it considered the
three currently available mana but not the legal land play visible in the same hand. The later action
trace exposed the missed line, but the field itself is underinclusive for the requested actionable
sequencing question. This is both a policy failure and a telemetry defect.

### Game 28 — unsupported Food activation

On T5, the agent activated Food at 21 life with no Researcher, Mascot, survival requirement, pending
lifegain, or same-turn condition payoff recorded. The life event had no concrete strategic utility.
Moreover, Follow the Lumarets was already in the opening hand: after the fifth land play, the turn's
Ent typecycle, Food activation, and Follow were jointly payable, so the Food could have enabled an
enhanced Follow that turn. Instead, the condition expired and Follow was not cast until T6.

### Game 28 — executable enhanced Follow line missed

On T6, the agent cast Follow normally while Weather remained in hand. It began with five untapped
lands and then made its sixth land drop before casting Carrier Thrall. The six-mana sequence of land,
Weather, enhanced Follow, and Carrier was executable, but the agent cast normal Follow before the
land and preserved Weather until T7. The later T7 Weather-to-enhanced-Follow sequence was legal, but
does not cure the missed T5/T6 value.

## Remaining audit

Outside these blockers, all automated invariants and the manual review of the other games found:

- expected and observed Storm-copy counts matched;
- lifegain events remained separate and Researcher/Mascot triggers equaled resolved counters;
- Carrier Thrall deaths equaled Scions created;
- both Scion mana activations were fully consumed by recorded spells with no unused mana;
- Chainer's Edict was not cast into the empty opponent;
- the three Cast Down uses coincided with productive Thrall-to-Scion lines;
- no duplicate card/turn/constraint bottleneck observation occurred;
- Ent cycles were payable development/thinning decisions rather than illegal creature casts; and
- mana legality and terminal reporting were consistent.

The Sample #2 raw JSON and human-readable report are preserved unchanged apart from the report's
rejection banner. Mana-role classification and pooled 60-game analysis were not produced because the
user's clean-sample prerequisite was not met. No correction is authorized by this audit.
