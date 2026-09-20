# Gate 3 policy screen v1

Status: planned; diagnostic engine evidence.

## Purpose

Measure Control and Pactdoll-A with the validated Industrial Waste line policy on fresh,
non-overlapping seeds. This is the last inexpensive goldfish gate before either improving the
policy again or constructing a matchup gauntlet.

## Inputs

- Namespace: `IW-G3-POLICY-S1`.
- Sample: 16 fresh paired seeds; 32 total games.
- Decks: frozen v1.0 Control and exact Pactdoll-A.
- Opponent: 60 Forest with frozen v0 AI.
- Industrial seat: `industrial-waste-policy-v1`.
- Mulligans: real London mulligans.
- Limits: 12 turns per seat and 4,000 accepted actions.

## Validity and decision rule

Reject the run if any outcome is missing, throws, records a rejected action, or ends for a reason
other than lethal or the declared turn cap.

Do not promote a deck from this diagnostic. Advance the harness toward a small preboard gauntlet
only if it records at least one Retriever-loop or combo-ready state and Pactdoll-A does not show a
material lethal regression versus Control. If both core-line metrics remain zero, stop sampling
and diagnose the policy. If only one list exercises the line, preserve that as a directional signal
for a fresh replication rather than treating it as a win-rate result.
