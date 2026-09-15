# Pest Control v1.0 — Goldfish Sample #2 Take 7 Rejection Audit

Status: rejected in full under the predeclared whole-block standard. These aggregates are quarantined
provisional results and are not performance evidence. Do not pool them with accepted Sample #1.

## Execution identity and integrity

- Branch: `pest-control/lab`
- Exact execution/freeze commit: `1b80acf203facb0eed3a4ae1ae165a786d1a7253`
- Freeze validation: CI #238 green
- Agent profile: `production-candidate-expiring`
- Horizon: the unchanged committed `PEST_GOLDFISH_HORIZON`
- Environment: Linux 6.18.35 x86_64; Eclipse Temurin OpenJDK 21.0.12.1 LTS; Gradle 9.6.1
- Ordered seed-value SHA-256: `af294f8238ba796082333994450f6f98d9a5b371e166591ba17c60de76aa7488`
- Seed CSV file SHA-256: `68101667794ffb835027f6217408eeb6b089256aa49c4bf0eaf0029a2b40254a`
- Permanent-control SHA-256: `7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5`
- Raw JSON SHA-256: `81516660c160d3ee2a01c96b6062d622775c92a3c7c121cbe7cdf1eec4edf25a`
- Lossless raw JSON gzip SHA-256: `2eb167312d0a3afe055f2552546f39ccfdf14e8ef0754edb3e76152e97ebc735`
- Generated report SHA-256: `15916b7238574d86cf2fdbdcdac0f922b8863ccae39cf974bb46c84083f5865b`

The pre-execution checkout was clean and matched the exact freeze commit. The committed vector hash,
permanent-control hash, artifact contract, and retired-vector guards passed. Every gameplay runner was
disabled. The Take 7 runner was mechanically enabled for one invocation only, with that one line the
sole transient diff, and returned to disabled immediately afterward.

That single invocation completed all 30 seeds. The artifact seed list, per-game seed list, and frozen
CSV are byte-for-value identical in order; there are exactly 30 games, 30 unique seeds, and no missing,
duplicate, additional, reordered, or retired seeds. Every game ended with `ENGINE_GAME_OVER` and
`COMBAT_LETHAL`. No retry or second gameplay invocation occurred.

## Mandatory artifact-completeness audit

The final JSON passed the predeclared completeness gate before any descriptive interpretation:

- 40 sequencing evaluations and 168 serialized sequence steps contained every mandatory sequence,
  land, payment-source, target, additional-cost, resource, classification, and superiority field.
- 1,257 friendly-removal evaluations contained every mandatory target/controller, cost, alternative,
  pass/hold, death/resource, resulting-state, fair-trade, policy, selection, and reason field.
- Required default values remained explicit: 1,257 nulls, 3,381 false values, 5,129 empty lists, and
  1,279 numeric zeroes occurred in the audited mandatory structures.
- All four executed friendly removals had selected audit records with `policyApplied=true` and positive
  fair-trade surplus: Bone Shards in Games 3 and 14, and Cast Down in Games 17 and 25.
- No built-in observability, schema, rules/state, mana-provenance, or terminal-record error occurred.

## Whole-block rejection finding

The invocation failed only at the final assertion because the completed block contained ten audit
errors across eight games. Each preserved complete-sequence comparison classified a setup-first line
as materially superior, yet production selected Weather first:

| Game | Seed | Turn / Weather | Validated superior setup-first line |
|---:|---:|---|---|
| 7 | `8771925645017969912` | T14 / Weather 1 | Follow the Lumarets → Weather |
| 13 | `4822858595341910878` | T8 / Weather 2 | Follow the Lumarets → Weather |
| 15 | `7921205758802943148` | T6 / Weather 2 | Cast Down → Weather |
| 15 | `7921205758802943148` | T7 / Weather 3 | Chainer's Edict → Weather |
| 16 | `8664108531280451088` | T7 / Weather 1 | Follow the Lumarets → Weather |
| 18 | `5765649008666318481` | T5 / Weather 1 | Follow the Lumarets → Weather |
| 19 | `3283389423480838719` | T5 / Weather 1 | Chainer's Edict → Weather |
| 22 | `645288879775981803` | T9 / Weather 1 | Follow the Lumarets → Weather |
| 22 | `645288879775981803` | T9 / Weather 2 | Cast Down → Weather |
| 30 | `474972711420151264` | T4 / Weather 1 | Forest → Bone Shards → Weather |

The full audit data preserves payment sources, targets/additional costs where relevant, both complete
orders, resources after each step, payoff delta, classification, and material-superiority result.
This is therefore not missing historical evidence. It is a clear disagreement between production
selection and the validated complete-sequence evaluator. The present authorization does not permit
deciding whether the next correction belongs in the planner, the evaluator, or both; no correction
was attempted.

Game 30 is a land-unlocked Game-16-style recurrence and was detected rather than silently lost. The
general setup-first/focal-first comparison also emitted the other nine failures. Follow condition
timing itself remained correct: all seven enhanced casts had prior life gain and all six normal casts
had none. Weather Storm copy accounting was exact for all 24 casts. No separate Take-6-Game-18-style
payoff-deployment omission was observed, but the broader same-turn selection gate remains defective.

Manual review of all 30 traces found no additional clear defect outside these ten sequencing errors.
Rules/state legality, colored-source and mana-payment records, Researcher and Mascot trigger/counter
totals (63/63 and 91/91), Carrier deaths and Scion creation (4/4), Scion provenance, Ent decisions,
actionable bottlenecks, Jungle Hollow delay records, and terminal states were internally consistent.

## Provisional descriptive results — rejected, not evidence

- Mulligans: 7/30 games, 9 total; opening access black-only 3, green-only 9, both 18.
- Meaningful permanent development by T1/T2/T3: 10/15/23.
- Modeled combat-lethal median: T7; by T5/T6/T7: 4/8/17.
- Functional state: 27 engine-functional, 3 fair-creature-functional.
- Lifegain: 157 events, 245 life; Researcher counters 63, Mascot counters 91.
- Warden plus payoff coexistence: 22/30; all three pieces: 7/30.
- Additional-Warden opportunities: 12 games, 37 entries; 18 Researcher and 34 Mascot potential
  counters forgone.
- Weather: 24 casts, 13 at Storm 0 and 11 at Storm 1; payoff present in 14 games.
- Follow: 6 normal and 7 enhanced.
- Mana bottlenecks: 139 total-mana, 19 color, and 20 tapland observations.
- Jungle Hollow: 25 tapped-entry events in 20 games; 20 proximate delay events in 7 games.
- Carrier deaths / Scions / Scion mana sacrifices: 4/4/0.
- Generous Ent: 17 cycles and 1 creature cast.

## Disposition and preserved boundaries

Recommended and recorded disposition: reject Take 7 in full. Its complete vector is permanently
retired and hard-disabled. It must never be replayed, rehabilitated, replaced, compared against,
optimized against, or reused. No pooled analysis is admissible.

Pest Control v1.0, accepted Sample #1, all earlier retired vectors, the audit-only unconstructed
challenger, Batshit Economics, and Project X remain unchanged. No challenger construction,
optimization, opponent self-play, policy correction, engine correction, Gym correction, telemetry
change, seed generation, or additional gameplay was performed.
