# Pest Control Tier-1 Grixis — 12-game replication execution harness

## Scope

This gate constructs and validates the execution harness for the already-frozen 12-game replication
vector. It does not load the official artifact in CI, initialize an official game, submit an official
action, or expose an official outcome.

## Frozen identities

- vector SHA-256: `5cd8a78fb62a59495d07ed31c4579fab7bafe2bc9c075aa7757a7f67953c75d4`
- assignment CSV SHA-256: `d85a30dcf46132609bdcd29d3a2b2e8621dba82b4e2111d95a385fb7642b4fab`
- manifest SHA-256: `5adea1d05defd4232d5117822c5a0afdf01532d75e05f180ea488b1b83e41a65`
- freeze commit: `5234db81bc87b6061bc3dfb891544205e66acc93`
- artifact ID: `10660335894`

## Harness invariants

The loader requires exactly 12 ordered unique nonzero seeds, exact deck/protocol/runner identities,
and the exact 12-row assignment mapping.

The coordinator requires durable attempt persistence before each game callback, durable completion
persistence after each returned terminal artifact, strict global order 1 through 12, and immediate
fail-closed termination on any exception. There is no reroll, replacement, seed-regeneration, or
continue-after-failure path.

Synthetic tests prove:

- exact 6/6 play-draw balance;
- exact 6/6 seat balance;
- exactly three observations per joint seat × starter cell;
- all 12 synthetic assignments are consumed once in order;
- a synthetic failure at Game 5 attempts only Games 1-5 and records only Games 1-4.

Official execution remains unauthorized. A later gate must validate the exact frozen artifact against
this harness before any production workflow may be created.
