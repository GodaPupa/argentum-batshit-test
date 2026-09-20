# Pest Control Tier-1 coverage — disabled Grixis smoke harness

## Accepted input

- Readiness merge: `d1b55bfe4b4accff025d20fc1fc9b99913338585`
- Readiness CI: run `35544872664` (`CI #563`), all executed shards green
- Protocol: `PEST_CONTROL_V10_VS_PASQUALE_GRIXIS_AFFINITY_2026_09_07_PREBOARD_V1`
- Future smoke block: `PEST_CONTROL_V10_VS_PASQUALE_GRIXIS_AFFINITY_2026_09_07_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4`

The accepted Pest Control v1.0 and Pasquale Grixis Affinity identities are unchanged. This gate adds
only a pure specification and validation boundary. It has no game adapter, execution method,
entropy source, artifact writer, workflow, seed file, or seed-freeze operation.

## Predeclared smoke shape

A future nonexperimental smoke would cover the four joint cells exactly once:

| Game | Pest engine seat | Starting deck |
|---:|:---:|:---|
| 1 | zero | Pest Control |
| 2 | zero | Grixis Affinity |
| 3 | one | Pest Control |
| 4 | one | Grixis Affinity |

This fixes the validation design without creating entropy or assignments. The future seed values,
if separately authorized after the complete harness is green, do not exist.

## Fail-closed state

- Harness state: `DISABLED`
- Vector identity: absent
- Official seeds generated: `0`
- Official games authorized: `0`
- Outcome exposure: `0`
- Game adapter: absent
- Execution method: absent

Activation validation independently rejects a disabled state, missing vector, missing explicit
authorization, unit-test execution, attempts other than one, existing output, the absent adapter,
and the absent execution method. No test in this gate can initialize a game.

The next justified gate is implementation of a deterministic adapter and artifact contract while
keeping execution disabled and the vector absent. No smoke or official gameplay is authorized by
this document.
