# Industrial Waste — final experimental conclusion

Date: 2026-09-24

Status: **PROGRAM COMPLETE — FROZEN CONTROL RETAINED / TIER-1 QUALIFICATION NOT EARNED / STRUCTURAL REDESIGN REQUIRED FOR ANY FUTURE REOPEN**

## Frozen control

- Control: `industrial-waste/control/industrial-waste-v1.0-submitted.dck`
- Maindeck/sideboard: 60/15
- SHA-256: `9b8a2932d78c99dd13f5c4834066bfa644f065137ee90efa75f3418ab56d1f1a`
- Disposition: `IMMUTABLE_SUBMITTED_CONTROL_FROZEN`

No experiment in the completed program earned authority to overwrite this control.

## Stopping rule

The program stops after Gate 11 because:

1. the contemporary preboard gauntlet has covered materially different pressure axes rather than repeatedly resampling one opponent;
2. every Gate 5–11 capability block has reached its predeclared decision rule;
3. Gate 11 Spy Combo is closed, its vector is retired, and no Gate 12 was predeclared;
4. no surviving incremental challenger has earned promotion;
5. continuing to add opponents or two-card tweaks after these outcomes would change the sample plan after seeing results.

Any future Industrial Waste work must therefore be opened as a **new structural-redesign program**
with a new frozen candidate, protocol, sample plan, and stopping rule. It must not continue Gate 11,
reuse retired seeds, or outcome-tune the existing control.

## Challenger disposition

### Pactdoll-A

Pactdoll-A remains **not promoted**.

Its strongest evidence was the production-profile Madness Burn program, where it showed a replicated
directional edge over Control, but both lists still performed poorly in absolute terms. That signal
did not generalize across the later gauntlet:

- Boros: Control led Pactdoll-A, 2 wins to 1.
- Mono-Blue Terror: Control led 1 to 0.
- Jund Wildfire: tied 2 to 2.
- Monster Tron: Control led 1 to 0.
- Elves: Control led 1 to 0.
- Spy Combo: tied 2 to 2.

These small blocks are capability screens, not matchup-percentage estimates and are not pooled as
independent Bernoulli samples. Their role here is narrower: the predeclared promotion triggers did
not fire, and the Burn-specific improvement did not establish a broad replacement case.

### Grixis interaction challengers

Tempo A and Tempo B also remain **not promoted**. In the fresh Gate 6 Grixis pilot, frozen Control
finished 4-0 while Tempo A and Tempo B each finished 3-1; Tempo B also failed to improve the
colored-mana metric that motivated it. Gate 6 editing closed without maindeck Ancient Grudge.

Other exploratory structural challengers remain non-promoted and are not silently carried forward.

## Competitive evidence

The completed program demonstrates that Industrial Waste v1.0 is executable and can win real games
across several archetype families, but it does **not** earn a Tier-1 qualification claim.

Most importantly, several qualified capability blocks exposed difficult pressure axes:

- production-profile Madness Burn remained strongly unfavorable even after the only replicated
  Pactdoll-A directional improvement;
- Mono-Blue Terror went 7-1 overall against Control + Pactdoll-A;
- Monster Tron went 7-1 overall;
- Elves went 7-1 overall;
- Spy Combo finished 4-4 overall, showing fair-game capability but no challenger advantage.

The project also repeatedly observed slow or absent early Tron/combo conversion in the difficult
blocks. Those observations are diagnostic evidence, not exact population rates.

The evidence therefore supports this bounded conclusion:

> **v1.0 is the best-evidenced retained control among the tested incremental variants, but the current
> architecture has not demonstrated the broad matchup performance required for a Tier-1 claim.
> Further improvement should begin from a structural redesign rather than another outcome-conditioned
> two-card tweak.**

This is not a claim that Altar Tron as a concept is impossible. It is the conclusion for this frozen
Industrial Waste architecture under the completed experimental program.

## Gate 11 final evidence

Spy Combo Gate 11 completed validly under
`IW-G11-SPY-COMBO-PILOT-V1`.

- Control: 2-2.
- Pactdoll-A: 2-2.
- Spy Combo overall: 4-4.
- Valid games: 8/8.
- Exceptions: 0.
- Illegal actions: 0.
- Replication trigger: **not met**.
- Pactdoll-A promotion: **not authorized**.
- Deck modification: **not authorized**.
- Postboard work: **not authorized**.
- Vector: retired; no reruns/rerolls/replacement seeds.

Canonical pilot run: `36059100002`.
Canonical pilot artifact: `industrial-waste-g11-spy-combo-pilot`, artifact ID
`10834191899`, ZIP SHA-256
`955320513863f3d9cdec2b9b6ff749ef5474d1850956dda391a469340b7cc7b0`.

## Infrastructure limitation

Repository-wide CI still reports the inherited `FrozenBaselineTest / LEGACY_V0` mismatch.
This is not accepted as deck-strength evidence and is not reblessed here.

Dedicated equivalence run `36058339345` proved that accepted lab baseline
`0cf0818434ddccf06baaa1fd1c06cdaa341ebdf3` and the Gate 11 source produce the same failure
signature: action-stream hash `399321c248898b96`, 20 turns, winner seat 1, life -8/16.

All Gate 11-specific card capability, opponent policy, exact-deck readiness, snapshot, pilot-result,
and retirement checks are green at the closing branch head.

## Final disposition

- Frozen v1.0: **retain as historical/best-evidenced control**.
- Pactdoll-A: **reject promotion**.
- Tempo A / Tempo B: **reject promotion**.
- Gate 11: **closed**.
- Existing seed namespaces: **retired/executed as recorded; never reuse**.
- Tier-1 qualification: **not earned**.
- Next work, if explicitly reopened: **new structural redesign program only**.
