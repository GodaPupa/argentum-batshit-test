# Gate 3 policy-v2 screen v1

Status: completed; diagnostic engine evidence; gate passed.

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

## Result

GitHub Actions run 35543607190 completed all 32 games with the registered namespace and vector
digest, zero exceptions, and zero rejected actions. Control reached loop-available and combo-ready
states in 2/16 games; Pactdoll-A did so in 1/16. All three converted to lethal on the same turn.
Control's lethal rate was 14/16 (87.5%) and Pactdoll-A's was 13/16 (81.25%).

The screen therefore passes its aggregate harness gate. It does not show that Pactdoll-A is better:
Control led both loop incidence and lethal rate in this small diagnostic sample, while Pactdoll-A
had fewer colored-mana-failure turns (2.69 vs 3.44) and one paired loop arrived a turn earlier.

Decision: retain both lists without promotion and authorize only a minimal, predeclared preboard
matchup pilot against provenance-locked contemporary lists. Do not pool this screen with any
calibration or incidental stock-policy rerun.
