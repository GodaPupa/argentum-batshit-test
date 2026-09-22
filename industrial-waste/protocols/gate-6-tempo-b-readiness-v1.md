# Gate 6 Tempo B readiness v1

Status: challenger identity frozen for seedless readiness only; no Tempo B seed namespace is reserved and no Tempo B gameplay has been executed.

## Evidence motivating the refinement

The corrected Tempo A Grixis capability pilot (GitHub Actions run 35686203679) was valid on its frozen two-seed paired vector after the Myr Retriever rules fix. Frozen Control finished 3-1 and Tempo A finished 2-2. Tempo A had no paired Tempo-only win, so it did not earn replication or promotion. It did reach Tron by turn 5 in 3/4 games versus 2/4 for Control, but its mean colored-mana-failure turns were 3.25 versus Control's 2.25.

The exact formerly stuck Tempo A game was replayed after correcting Myr Retriever's "another target artifact card" restriction. GitHub Actions run 35685616789 completed legally with zero illegal actions and an Industrial Waste win, so the earlier stuck result is quarantined as an engine-rules defect rather than deck evidence. Myr Retriever capability run 35685269125 independently passed the focused regression.

Stability A previously showed that an additional Conduit Pylons plus an additional Crop Rotation could reduce game-level colored-failure pressure in its Grixis pilot, while the structural diagnostic also showed that adding another green spell increased green-access burden. Tempo B therefore isolates the lower-risk half of that idea: add the Pylons, not the third Crop Rotation.

## Frozen Tempo B hypothesis

Relative to Tempo A:

- +1 Conduit Pylons
- -1 Eviscerator's Insight
- sideboard unchanged

Relative to frozen Control:

- maindeck: +2 Ancient Grudge, +1 Conduit Pylons; -2 Giant's Boulder, -1 Eviscerator's Insight
- sideboard: +2 Weather the Storm; -2 Ancient Grudge

No other card or count may drift.

## Capability reuse

Tempo B introduces no new card capability. Ancient Grudge target-policy qualification from GitHub Actions run 35664462705 remains applicable because the card, advisor policy, and opponent target set are unchanged. The Myr Retriever rules regression from run 35685269125 remains mandatory engine provenance.

## Readiness rule

Tempo B may not receive a gameplay seed namespace until all of the following are true:

1. The exact submitted Control remains hash-locked.
2. The central validator accepts Tempo A's declared sideboard transfer rather than incorrectly requiring sideboard equality.
3. Tempo B validates as exactly 60/15 and exactly the declared one-card refinement of Tempo A.
4. No existing seed vector is modified or reused.
5. Any later Grixis pilot is fresh, disjoint, paired play/draw, and non-promotion-eligible.

The transparent Gate 1 structural model is not used to judge Tempo B because it does not model Ancient Grudge casting or access. Treating that model as evidence for this challenger would understate the exact colored-mana question being tested.
