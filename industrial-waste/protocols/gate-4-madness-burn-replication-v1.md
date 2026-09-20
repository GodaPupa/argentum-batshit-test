# Gate 4 Madness Burn replication v1

Status: completed; valid screen; escalation gate failed.

## Inputs

- Namespace `IW-G4-MADNESS-BURN-R1`; eight fresh seeds, disjoint from the pilot.
- Frozen v1.0 Control and exact Pactdoll-A versus the same provenance-locked Madness Burn list.
- Each seed is played from both seats for each Industrial list: 32 games total.
- Industrial policy v2 versus frozen v0; real London mulligans; preboard 60s only.
- 16 turns per seat and 4,000 accepted actions.

## Gate

Reject the entire artifact for any exception, illegal action, digest mismatch, incompleteness, or
non-cap draw. Do not pool these games with the pilot.

The matchup earns a larger qualification sample only if at least one Industrial list wins 6/16
games (37.5%) and Madness Burn wins at least four games against that list. Pactdoll-A earns a
directional advantage only with at least two more wins than Control; a smaller gap is noise at this
screening size. No result from this single opponent can promote a deck.

## Result

GitHub Actions run 35544615045 completed all 32 games with the registered namespace and digest,
zero exceptions, zero rejected actions, and no draws. Control and Pactdoll-A each went 13-3.
Control recorded four combo-ready games and Pactdoll-A three; Tron by turn 5 was 3/16 for each.
Mean colored-mana-failure turns were nearly identical (2.00 Control, 2.06 Pactdoll-A).

Decision: stop sampled escalation. Madness Burn won only 3/16 against each list, one short of the
predeclared two-sided threshold, and Pactdoll-A's win differential versus Control was zero. This
artifact neither promotes Pactdoll-A nor qualifies the matchup for a larger run. Before spending a
new namespace or adding another opponent, audit the stock Burn policy deterministically to verify
that it uses its damage and discard engines credibly.
