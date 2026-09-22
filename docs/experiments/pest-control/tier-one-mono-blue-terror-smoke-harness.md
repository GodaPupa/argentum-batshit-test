# Pest Control Tier-1 coverage — disabled Mono-Blue Terror smoke harness

## Accepted input

- Readiness merge: `c9d435d429089562701fe57f80c9207198d05911`
- Readiness CI: run `35750852947` (`CI #879`), all executed shards green
- Protocol: `PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1`
- Future smoke block: `PEST_CONTROL_V10_VS_SERPICO_CC_MONO_BLUE_TERROR_2026_09_20_PREBOARD_V1_NONEXPERIMENTAL_SMOKE_4`

The accepted Pest Control v1.0 and Serpico_CC Mono-Blue Terror identities are unchanged. The exact
preboard 60 is fully supported after the accepted Escape/Sleep of the Dead gate. The frozen sideboard
remains outside preboard execution scope.

This gate adds only a pure specification and validation boundary. It has no initializer,
execution method, entropy source, artifact writer, workflow, seed file, or seed-freeze operation.

## Predeclared smoke shape

A future nonexperimental smoke would cover the four joint cells exactly once:

| Game | Pest engine seat | Starting deck |
|---:|:---:|:---|
| 1 | zero | Pest Control |
| 2 | zero | Mono-Blue Terror |
| 3 | one | Pest Control |
| 4 | one | Mono-Blue Terror |

This fixes the validation design without creating entropy or assignments. The future seed values,
if separately authorized after the complete harness is green, do not exist.

## Fail-closed state

- Harness state: `DISABLED`
- Vector identity: absent
- Official seeds generated: `0`
- Official games authorized: `0`
- Outcome exposure: `0`
- Provenance adapter: present, no initialization method
- Game initializer: absent
- Execution method: absent
- Outcome-bearing workflow: absent

Activation validation independently rejects a disabled state, missing vector, missing explicit
authorization, unit-test execution, attempts other than one, existing output, the provenance
adapter's lack of an initialization method, and the absent execution method. No test in this gate can
initialize a game.

The provenance-only adapter and byte-level artifact contract are implemented in the following gate.
They use synthetic in-memory fixtures and expose no game initializer. Execution remains disabled and
the vector remains absent. No smoke or official gameplay is authorized by this document.
