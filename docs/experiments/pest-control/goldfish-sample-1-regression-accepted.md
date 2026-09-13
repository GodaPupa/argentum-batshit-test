# Pest Control v1.0 — Sample #1 regression replay

## Disposition

**Regression gate accepted. Seed vector permanently retired.**

This replay is regression-validation evidence only. Goldfish Sample #1 remains formally rejected
for performance, baseline, matchup, optimization, or future performance inference. No win-clock or
engine aggregate from this replay is accepted for those purposes.

- Seed-vector SHA-256: `87d8624e407286bfa9b1c9d4629fd29163ae8bbec4b98ab51ace7f9c9e1d764f`
- Correction CI: runs 196 and 197, fully green
- Frozen deck: permanent Pest Control v1.0, unchanged
- Replay order: exact CSV order, 30 distinct seeds
- Rerolls, replacements, exclusions, deck changes, and seed substitutions: none
- Harness audit errors: 0
- Manual audit errors: 0
- Shared Argentum change: **yes**

## Targeted audit

- Games 9, 13, 19, and 27 held Chainer's Edict against the empty opposing creature board.
- Game 25 recorded one Carrier Thrall death, one Scion created, zero Scion mana activations, zero
  funded actions, and zero provenance errors.
- Opponent-dependent interaction produced zero actionable mana-bottleneck records against the blank
  opponent. Total-mana, color, and tapland records were structurally relevant and deduplicated.
- Every Weather original and Storm copy corresponded to a separate three-life event; copy counts
  matched recorded Storm counts.
- Researcher and Mascot trigger totals equaled counters added. Games with multiple payoff copies
  retained independent trigger fan-out rather than aggregating life events.
- Carrier Thrall deaths and Scions created matched in every game. All sacrifice-mana provenance
  balanced produced mana against consumed plus unused mana.
- Follow reported only normal or enhanced engine state; all life events had known positive sources;
  Ent decisions and mana actions were legal.
- All 30 games reached engine-reported terminals without wedges, action-cap failures, illegal
  actions, or missing terminal classifications.

## Per-game regression ledger

`Thrall/Scion/Mana` records Carrier Thrall deaths, Scions created, and Scions sacrificed for mana.
“Edict held” means at least one Chainer's Edict remained stranded solely because the solitaire
opponent had no creature. `n/a` means that check did not arise in the game.

| Game | Seed | Audit | Edict | Thrall/Scion/Mana | Weather | Counters | Terminal |
|---:|---|---|---|---|---|---|---|
| 1 | `0x877DB7317239105` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 2 | `0xE734EF6E2AE6905` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 3 | `0x6535C962D0425F1` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 4 | `0x3AE231C40D961AF` | clean | held | 0/0/0 | ok | ok | combat lethal |
| 5 | `0x589AB42AF5A4108` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 6 | `0xD41E88D1E6A0ACB` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 7 | `0x009082CF1C35D7C` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 8 | `0x86912BBA77EAE52` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 9 | `0x492298A5DA7DAC7` | clean | held | 0/0/0 | ok | ok | combat lethal |
| 10 | `0xAB55D19D3E8F9EC` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 11 | `0xE27196A0B3A9D06` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 12 | `0x761B45A1E75EE5D` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 13 | `0x5501A3AF9739C6D` | clean | held | 0/0/0 | ok | ok | combat lethal |
| 14 | `0x84FE09D7E162465` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 15 | `0x97BE6932F3424E6` | clean | held | 0/0/0 | ok | ok | combat lethal |
| 16 | `0x72E4CFDB07BDDED` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 17 | `0xE911C74469D9900` | clean | held | 0/0/0 | ok | ok | combat lethal |
| 18 | `0x24D876891266C15` | clean | held | 0/0/0 | ok | ok | combat lethal |
| 19 | `0x418113833A2FFEC` | clean | held | 0/0/0 | ok | ok | combat lethal |
| 20 | `0xF92C6C66D1C227B` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 21 | `0xF7646EA9E3BB42E` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 22 | `0xA2549E1A1FAAC7E` | clean | n/a | 1/1/0 | ok | ok | combat lethal |
| 23 | `0x2AEA10F9A51B8ED` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 24 | `0x8C38EE062DE7396` | clean | n/a | 0/0/0 | ok | ok | combat lethal |
| 25 | `0xD82019B3AD968F1` | clean | n/a | 1/1/0 | ok | ok | combat lethal |
| 26 | `0x09BB211DABD1F49` | clean | n/a | 1/1/0 | ok | ok | combat lethal |
| 27 | `0xE788E35A769F5AD` | clean | held | 0/0/0 | ok | ok | combat lethal |
| 28 | `0xF35096C55EC515F` | clean | held | 0/0/0 | ok | ok | combat lethal |
| 29 | `0x218EB85A5D80BED` | clean | held | 1/1/0 | ok | ok | combat lethal |
| 30 | `0xC2D50F1C8F23077` | clean | n/a | 0/0/0 | ok | ok | combat lethal |

The vector is permanently retired after this acceptance. A fresh performance Sample #1 requires a
new, independently generated and frozen seed vector plus separate authorization.
