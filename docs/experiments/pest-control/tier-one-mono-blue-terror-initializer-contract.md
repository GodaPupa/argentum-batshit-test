# Pest Control Tier-1 coverage — Mono-Blue Terror construction initializer contract

## Boundary

This gate adds exactly one initializer for fixed construction entropy
`0x7e770b1e00000001`. That value is nonexperimental, excluded from all current and future seed
registries, and cannot be supplied by a caller. The initializer has no overload that accepts a future
frozen assignment. It initializes an opening state only; no mulligan choice or game action is taken.

There is still no official initializer, seed vector, seed file, runner, execution method, workflow,
or authorization path. The smoke harness remains `DISABLED`.

## Conservation fixture

The fixture instantiates the frozen Pest Control v1.0 main deck in seat zero and the accepted
Serpico_CC Mono-Blue Terror main deck in seat one, with Pest Control starting. It fails closed unless:

- readiness and all 60/15 identity checks pass against the registry;
- each instantiated card-name multiset equals its frozen 60-card main deck exactly;
- each opening hand contains 7 cards and each library contains 53;
- battlefield, graveyard, stack, exile, command, and sideboard counts remain zero; and
- each player owns exactly 60 instantiated cards.

The fixture is explicitly excluded from experimental evidence and future seed-overlap registries.

## Telemetry fixture

The pure Mono-Blue Terror telemetry index references canonical raw action/event text by action
sequence and event offset. Its channels cover cantrip/setup actions, graveyard and Escape behavior,
Tolarian Terror/Cryptic Serpent threat development, permission, tempo/bounce, Artful Dodge evasion,
and lands/Islandcycling. Missing or reordered action sequences fail closed. Synthetic text fixtures
validate the taxonomy without advancing a game.

## Current state

- Harness: `DISABLED`
- Vector identity: absent
- Official seeds generated: `0`
- Official games authorized: `0`
- Outcome exposure: `0`
- Official initializer: absent
- Execution method: absent

The next justified gate is a disabled turn-zero preflight contract binding initializer conservation,
telemetry schema, artifact reconciliation, and activation blockers. It must remain vectorless and
must not advance or execute a smoke game.
