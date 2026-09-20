# Gate 3 policy calibration v2

Status: completed; non-promotional diagnostic; failed.

## Purpose

Check whether the corrected policy can stage the first Retriever into the graveyard before any new
experimental seeds are allocated. The v2 change is isolated to Altar activation and sacrifice
selection when a payoff is present and at least two Retrievers are accessible across battlefield,
hand, and graveyard.

## Inputs and provenance

- Exact replay of `IW-G3-GOLDFISH-S1`; no new namespace and no new deck evidence.
- Frozen v1.0 Control and Pactdoll-A, paired over 12 seeds.
- Industrial seat: `industrial-waste-policy-v2`; opponent: 60 Forest with v0.
- Real London mulligans; 12 turns per seat; 4,000 accepted actions.
- Output is `promotionEligible=false` and must not be pooled with any screen.

## Decision rule

The calibration passes only if the artifact is valid and at least one game records Retriever-loop
availability or combo readiness. Otherwise stop gameplay and return to seed-free diagnosis. A pass
permits one new small policy-v2 screen; it does not qualify either deck or authorize matchups.

## Result

GitHub Actions run 35542066782 completed a valid 24-game replay. Its summaries and every core-line
metric were identical to v1 calibration: both decks recorded zero Retriever-loop and combo-ready
states. The calibration failed, so no new policy-v2 namespace is allocated.

The passing sacrifice-response fixture only proved behavior after an activation was submitted. The
next seed-free test moves upstream and compares Altar activation directly against passing from a
staged board. No further gameplay is permitted until that action-choice fixture passes.
