# Industrial Waste

Industrial Waste is an autonomous, evidence-gated Pauper Altar Tron development
project. Competitive strength is the primary objective; a distinct identity is
retained only when its cards earn their slots.

## Immutable baseline

`control/industrial-waste-v1.0-submitted.dck` is the exact list submitted on
2026-09-20. It is immutable experimental provenance.

The submitted list contains **60 maindeck cards and 15 sideboard cards**. Gate 1
uses three legal, isolated two-for-two challengers to test the requested
directions:

| Candidate | Exact swap from v1.0 | Direction |
|---|---|---|
| `turbo-a` | -2 Giant's Boulder, +2 Crop Rotation | Turbo Tron |
| `pactdoll-a` | -2 Eviscerator's Insight, +2 Ichor Wellspring | Pactdoll/attrition velocity |
| `recursive-a` | -1 Eviscerator's Insight, -1 Giant's Boulder, +1 Blood Fountain, +1 Haunted Fengraf | Recursive Tron |

These are challengers, not edits to the frozen control. None can be promoted
from Gate 1 alone.

## Cost discipline

1. Validate list counts, hashes, legality shape, seed namespace, and simulator
   invariants locally.
2. Run paired deterministic structural screens. These are model evidence, not
   full Argentum gameplay evidence.
3. Reject clearly inferior directions before card implementation or matchup
   work.
4. Implement only cards required by surviving candidates.
5. Build a sourced contemporary gauntlet only after a legal maindeck earns
   promotion through replicated executable evidence.

See `protocols/gate-1-structural-screen-v1.md` for the current gate.

## Current decision

Pactdoll-A remains the only surviving challenger, but it is not promoted. The original Madness Burn
pilot and replication rates remain quarantined because frozen v0 misses lethal Fireblast. An exact
spent-seed replay changing only Burn to `PRODUCTION_CANDIDATE_EXPIRING` produced 14 Burn wins in 16
games against each Industrial list and changed 22/32 results, passing the opponent-policy
calibration. The authorized fresh screen then completed 2-14 for Control and 4-12 for Pactdoll-A,
meeting the predeclared +2-win directional threshold and authorizing one fresh replication. No
postboard work or deck promotion is authorized. The frozen Control remains the experimental
baseline and Pactdoll-A remains unpromoted.
