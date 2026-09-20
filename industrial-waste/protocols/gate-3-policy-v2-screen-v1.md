# Gate 3 policy-v2 screen v1

Status: planned; diagnostic engine evidence.

## Inputs

- Namespace: `IW-G3-POLICY-V2-S1`.
- Sample: 16 fresh paired seeds; 32 total games.
- Decks: frozen v1.0 Control and exact Pactdoll-A.
- Industrial seat: `industrial-waste-policy-v2`; opponent: 60 Forest with frozen v0.
- Real London mulligans; 12 turns per seat; 4,000 accepted actions.

## Purpose and gate

Replicate v2's single replay-only loop observation on fresh, non-overlapping seeds. Reject any
incomplete or contaminated artifact. This diagnostic cannot promote a deck.

Advance toward a minimal preboard matchup pilot only if at least two total games reach
Retriever-loop or combo-ready state, at least one list maintains a 75% lethal rate, and no validity
failure occurs. With one observation, preserve a weak signal but do not escalate. With zero, stop
sampling and return to deterministic policy diagnosis.
