# Pest Control v1.0 — Goldfish Sample #2 Take 8 Acceptance Audit

Status: accepted as Pest Control Goldfish Sample #2 independent performance/engine replication
evidence. It may join accepted Sample #1 in later pooled descriptive analysis. This remains goldfish
evidence, not matchup evidence.

## Execution identity and integrity

- Branch: `pest-control/lab`
- Exact execution/freeze commit: `3894ed0dbcad38b61fd2ac0640823dfe3b8778e8`
- Agent profile: `production-candidate-expiring`
- Horizon: 20 turns
- Starting player: Pest Control, fixed `startingPlayerIndex = 0`
- Environment: Linux 6.18.35 x86_64; Oracle OpenJDK 21.0.2; Gradle 9.6.1
- Ordered seed-value SHA-256: `d20e57b5e588546911d6f16b63d274651038bd49b53580f27f9a208448859f2f`
- Seed CSV SHA-256: `02dca916c2d4b6d5b921517f6dadc97c5bb4b515c25daf72a0ffe9f6cc32c45f`
- Permanent-control SHA-256: `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`
- Raw JSON SHA-256: `030c16b744e4690e0b3c6692eb22c458adf2fb66f49de7ea3a900001e8464d12`
- Lossless raw JSON gzip SHA-256: `3895097cca986b3497aa200dac5769bedd6446bdcaad2ba40bba3dabaf06deb8`
- Generated human report SHA-256: `8d49293aad44a7afbe1ec8c3cae7c6d08934b75bad9a8a4b229486d876bf093a`

The pre-execution checkout was clean and exactly matched the freeze commit. The ordered vector,
seed CSV, permanent control, final-JSON contract, and retired-vector guards all matched their
committed identities under JDK 21. Every gameplay runner was disabled during preflight. Only the
Take 8 runner was transiently enabled for one invocation, then returned to disabled immediately
afterward.

The single authorized invocation completed every frozen seed. The CSV seed list, block seed list,
and per-game seed list are identical in order: exactly 30 games and 30 unique seeds, with no missing,
duplicate, additional, reordered, retired, or replacement seed. No invocation failed, no retry or
second invocation occurred, and no seed executed more than once. All 30 games ended normally with
`ENGINE_GAME_OVER` and `COMBAT_LETHAL`.

## Mandatory artifact-completeness audit

The preserved final JSON passed the mandatory completeness gate before performance interpretation:

- 22 sequencing evaluations and 90 serialized sequence steps preserve the complete setup and
  focal-first lines, semantic and physical identities, target/cost selections, source-specific mana
  payments, resource states, production admissibility, strategic adjustment, pass value, equivalent
  continuation horizon, classification, and superiority result.
- 778 friendly-removal evaluations preserve every required target/controller, cost, alternative,
  pass/hold, death/resource, resulting-state, fair-trade, policy, selection, and reason field.
- Mandatory default-valued data is explicit in the audited structures: 1,945 null values, 2,729
  false values, 3,039 empty lists, and 2,231 numeric zeroes.
- All three executed friendly removals have corresponding selected records with
  `policyApplied=true` and positive fair-trade surplus.
- Every built-in audit list is empty. No schema, serialization, observability, evidence-integrity,
  rules/state, mana-provenance, or terminal-record error was found.

## Strategic audit

Manual and structured review of all 30 games found no clear defect under the predeclared whole-block
standard.

- Weather: all 13 casts have exact expected/observed Storm-copy counts (nine at Storm 0 and four at
  Storm 1) and a live Researcher or Mascot payoff. The two Weather-then-enhanced-Follow lines remain
  production-preferred after the shared expiring-condition adjustment. Game 23's two interchangeable
  Weather copies retain distinct physical IDs but compare as the same complete semantic action; the
  two complete orders score equally and create no false deferral or missed-superior result.
- Same-turn sequencing: no production-admissible materially superior candidate was skipped. All 22
  evaluated alternatives use equal bounded continuation lengths. Below-margin friendly removal and
  empty Chainer's Edict candidates are explicitly rejected rather than credited for Storm. Game 18
  plays the land, deploys Pest Mascot, and then casts Weather with Storm 1, preserving the corrected
  payoff-deployment-first structure. No Take-7-style truncated or policy-disconnected comparison
  recurred. No land-unlocked missed-superior event occurred in this block.
- Follow: all six enhanced casts have prior same-turn lifegain; all ten normal casts have none. The
  Food/Jungle Hollow and Weather enablement records preserve the relevant temporary condition and
  continuation order.
- Friendly/modal removal: the only three executed removals are selected Bone Shards lines in Games
  1, 13, and 17. Each applies the general policy. Game 1 sacrifices its chosen Carrier Thrall target
  to create one Scion and one Researcher counter (fair-trade surplus 9.8383). Game 13 sacrifices one
  Carrier while targeting the other, creating two Scions, two lifegain events, and two Researcher
  counters (surplus 0.1575). Game 17 discards Chainer's Edict and removes Carrier, creating one
  Scion, three Warden lifegain events, and six Researcher counters (surplus 7.51875). No below-margin
  friendly removal or strategically null forced sacrifice executes.
- Accounting: Researcher triggers/counters are 80/80 and Mascot triggers/counters are 44/44.
  Carrier deaths and Scions created are 4/4; no Scion was sacrificed for mana, so no missing mana
  provenance exists. Explicit setup payment sources are unique, untapped before use, and satisfy
  colored requirements. Ent decisions, bottleneck/tapland records, and all terminal reports are
  internally consistent.

## Accepted Sample #2 descriptive result

- Mulligans: 7/30 games, 11 total; opening access black-only 1, green-only 4, both 24, neither 1.
- Meaningful permanent development by T1/T2/T3: 9/19/26.
- Modeled combat-lethal median: T7; by T5/T6/T7: 3/13/24.
- Functional state: 25 engine-functional, 5 fair-creature-functional.
- Lifegain: 137 events and 189 life; Researcher counters 80, Mascot counters 44.
- Warden plus payoff coexistence: 17/30; all three pieces: 3/30.
- Additional-Warden opportunities: 15 games, 42 qualifying entries; 34 Researcher and 34 Mascot
  potential counters forgone.
- Weather: 13 casts, nine at Storm 0 and four at Storm 1; a payoff was present for every cast.
- Follow: ten normal and six enhanced casts.
- Mana bottlenecks: 140 total-mana, five color, and nine tapland observations.
- Jungle Hollow: 31 tapped-entry events in 22 games; nine proximate delays in five games.
- Carrier deaths / Scions / Scion mana sacrifices: 4/4/0.
- Generous Ent: 12 cycles and zero creature casts.

## Disposition and preserved boundaries

Take 8 is formally accepted as Sample #2 independent goldfish performance/engine evidence. Its
complete vector is nevertheless permanently retired and hard-disabled after its one authorized
execution; it may never be replayed, rehabilitated, replaced, optimized against, or reused. The
accepted results may be pooled descriptively with accepted Sample #1 only in later authorized work.

Pest Control v1.0, accepted Sample #1, Take 7 and every earlier retired vector, the audit-only
unconstructed challenger, Batshit Economics, and Project X remain unchanged. No correction, seed
generation, challenger construction, optimization, opponent self-play, or cross-project write was
performed.
