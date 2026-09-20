# Gate 3 policy calibration v1

Status: completed; non-promotional diagnostic.

## Purpose

Determine whether the test-only Industrial Waste advisor makes the existing engine goldfish
capable of exercising the deck's defining lines before spending a fresh experimental seed
namespace. This calibrates the measurement instrument; it does not compare or qualify a deck.

## Inputs

- Decks: frozen v1.0 Control and Pactdoll-A.
- Opponent: 60 Forest with the frozen v0 agent.
- Industrial seat: v0 plus `IndustrialWasteAdvisorModule` only.
- Seeds: exact replay of registered namespace `IW-G3-GOLDFISH-S1` (12 paired seeds).
- Limits: 12 turns per seat and 4,000 accepted actions.
- Mulligans: real London mulligans.

Reusing these seeds is intentional because the output is calibration-only and
`promotionEligible=false`. It must never be pooled with the original Gate 3 screen or any future
fresh-seed deck comparison.

## Required validity

- 24 outcomes are present.
- No exception, rejected action, or non-turn-cap draw occurs.
- The artifact identifies evidence as `non-promotional-policy-calibration` and the Industrial seat
  as `industrial-waste-policy-v1`.

## Decision rule

Advance the policy to a fresh, small paired screen only if it produces an observable improvement
in at least one targeted capability (Tron by turn 5, Retriever-loop availability, or combo-ready
state) without reducing total lethals versus the original screen. Otherwise diagnose or reject the
policy; do not allocate new seeds.

## Result

GitHub Actions run 35540498081 completed all 24 outcomes with no exception or rejected action.
Control and Pactdoll-A each assembled Tron by turn 5 in 1/12 games, versus 0/12 each under the
stock policy. Total lethals were 19/24, versus 18/24 in the original screen. Retriever-loop and
combo-ready rates remained zero for both lists.

Decision: the policy met the narrow calibration rule and advances to one fresh 16-pair screen. It
has not qualified the harness for matchup testing, and none of these replayed outcomes may be
pooled with deck evidence.
