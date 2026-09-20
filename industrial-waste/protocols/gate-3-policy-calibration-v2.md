# Gate 3 policy calibration v2

Status: planned; non-promotional diagnostic.

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
