# Gate 4 Madness Burn preboard pilot v1

Status: completed; capability pilot passed; not promotion evidence.

## Inputs

- Namespace `IW-G4-MADNESS-BURN-PILOT-V1`; four fresh seeds.
- Frozen v1.0 Control and exact Pactdoll-A versus the provenance-locked Madness Burn list.
- Every seed is played twice per Industrial list, swapping play/draw seats: 16 games total.
- Industrial seat uses policy v2; Madness Burn uses frozen v0.
- Real London mulligans; preboard 60s only; 16 turns per seat; 4,000 accepted actions.

## Validity and decision rule

Reject the entire pilot for any exception, illegal action, unregistered card, wrong seed digest,
or non-cap draw. The pilot passes only if all 16 games are valid, each Industrial list wins at least
one game, and Madness Burn wins at least one game. This establishes only that the matchup harness
exercises a live two-sided race.

On a pass, allocate one small fresh-seed replication against Madness Burn before adding a second
opponent. On a failure, diagnose engine or policy behavior without spending another namespace.

## Result

GitHub Actions run 35544249719 completed all 16 games with the registered namespace and digest,
zero exceptions, zero rejected actions, and no draws. Control and Pactdoll-A each went 4-4; each
won twice on the play and twice on the draw. Madness Burn therefore supplied a live clock and both
Industrial lists demonstrated fair-game wins. Neither list reached a combo-ready state.

Decision: the capability gate passes. Neither list has an advantage in this sample. Allocate the
single predeclared fresh replication; do not pool the pilot and replication or add another opponent
until that replication completes.
