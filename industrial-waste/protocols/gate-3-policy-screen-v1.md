# Gate 3 policy screen v1

Status: completed; diagnostic engine evidence; harness gate failed.

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

## Result

The first run, 35541012276, is rejected: it played the intended vector but wrote the old Gate 3
digest into its artifact. The corrected run, 35541293028, reported the registered digest and
completed all 32 outcomes with no exception or rejected action.

Both lists reached Tron by turn 5 in 5/16 games and lethal in 16/16. Control reached Tron by turn 3
once and by turn 4 three times; Pactdoll-A reached it by turn 4 twice. Pactdoll-A had fewer mean
colored-failure turns (2.375 vs 2.875), slightly more redundant payoff draws (0.625 vs 0.563), less
non-infinite Pactdoll loss (6.625 vs 7.188), and more combat damage (15.813 vs 14.625).

Neither list produced a Retriever-loop or combo-ready state. The declared gate therefore fails:
do not construct the matchup gauntlet and do not allocate replication seeds. Diagnose the policy's
failure to seed the first Retriever into the graveyard.
