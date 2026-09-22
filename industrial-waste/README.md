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
baseline and Pactdoll-A remains unpromoted. Replication then finished 2-14 for Control and 3-13 for
Pactdoll-A, confirming the direction at +1 and producing a pooled +3 edge across 32 games per list.
Burn sampling is now closed: the relative Pactdoll-A edge is real enough to carry forward, but both
lists' absolute Burn matchup remains poor. The next gate is another feasible preboard opponent.
The next opponent is Boros Aggro from the same 99-player event. Its exact published 75 and selection
audit are frozen. All four missing maindeck cards, the Boros-specific policy fixtures, and an exact
deck deterministic smoke are qualified. The smoke made 516 accepted actions with zero exceptions
or illegal actions. The minimal two-seed, eight-game paired capability pilot then completed validly:
Control went 2-2, Pactdoll-A went 1-3, and Boros went 5-3 overall. Both capability floors passed,
but Control's one-win lead closes Boros sampling under the predeclared rule. No replication,
promotion, or postboard work is authorized; the frozen v1.0 Control remains unchanged.
Gate 6 now begins with the exact sourced Carlos Dc Grixis Affinity 75. Its six-card implementation
gap and fail-closed admission criteria are frozen, but no seed namespace or gameplay run is
authorized until card, policy, and deterministic-smoke readiness all pass.


Gate 7 Mono-Blue Terror is complete. Exact-deck readiness and the seven-fixture opponent-policy
audit passed before any official seed exposure. The fresh two-seed paired pilot then completed all
eight games validly: frozen Control went 1-3, Pactdoll-A went 0-4, and Mono-Blue went 7-1 overall.
Opponent pressure and the minimum Industrial capability floor both passed, but Pactdoll-A trailed
Control and therefore failed the predeclared replication trigger. Mono-Blue sampling is closed with
no replication, no promotion, and no postboard authorization. Frozen v1.0 Control remains the
baseline.
