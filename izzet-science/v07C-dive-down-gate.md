# v0.7-C Dive Down — Frozen Challenger Gate

Control: `izzet-science/v0.6-control.md`
Control SHA256: `a6acc3e0a00fa1bab0eb3ad7d981a449b8ce0e8c6bf4ed384968e46ca081fac5`
Challenger: `izzet-science/challengers/v07C-dive-down.md`
Change: `-1 Skred, +1 Dive Down`

Repository legality data identifies both cards as Pauper Commander legal. Skred deals
damage equal to the number of snow permanents its controller controls. The original
freeze stated that v0.6 contained no snow permanents. A later audit found Volatile
Fjord, a snow land, so the correct Skred damage ceiling is one. This factual
correction does not change the frozen gate or completed rejection. Dive Down costs
one blue mana and gives a controlled creature hexproof and +0/+3 until end of turn.

The readiness model credits Dive Down only against the declared event in which a
targeted spell would remove Izzet Guildmage. It receives no credit against a spell
countering Lava Spike.

## Hypothesis

Replacing the nonfunctional Skred slot with one-mana protection will improve legal
primary-combo launches that survive targeted Guildmage removal while preserving Lose
Focus and the entire accepted combo, selection, mana, and permission packages.

## Frozen execution

- Paired control and challenger trajectories
- Pilot samples: 10,000 per deck
- Pilot seed: `0x1A22E7006`
- Confirmation samples: 100,000 per deck, only after pilot pass
- Confirmation seed: `0x1A22E7007`
- Horizon: T1–T10
- One execution per stage; no rerolls or replacement seeds

The 10,000-game pilot cannot promote the challenger.

## Pilot pass criteria

At T10, all conditions must hold:

1. Guaranteed protection against targeted Guildmage removal improves by at least
   0.30 percentage points.
2. Generic guaranteed stack protection does not decrease by more than 0.10
   percentage points.
3. Conditional stack protection does not decrease by more than 0.10 percentage
   points.
4. Immediate taxed commander recovery does not decrease by more than 0.10
   percentage points.
5. Current-turn lethal state does not decrease by more than 0.10 percentage points.
6. Pair assembly does not decrease by more than 0.10 percentage points.
7. U and R execution guardrails do not decrease by more than 0.25 percentage points.
8. Dramatic Reversal positive-mana readiness does not decrease.
9. Deck identity, output completeness, source, seed, sample count, and artifact
   hashes all validate.

Failure of any criterion rejects v0.7-C and permanently ends this challenger
identity. A pass authorizes exactly one 100,000-game confirmation on the
preregistered seed; only that confirmation may earn promotion.

Disposition: `V07C_REJECTED_AT_PILOT`

The single authorized pilot completed in GitHub Actions run `35535687806` and
failed criterion 7: T10 R execution decreased from 84.28% to 81.39% (-2.89
percentage points). No confirmation run is authorized. See
`v07C-dive-down-rejected.md` for the complete audit.
