# Pest Control Tier-1 Grixis — replication official-artifact validation

## Scope

This gate performs one read-only validation of the exact frozen 12-game replication artifact against
the merged execution-harness parser. It is validation-only and cannot initialize a game, submit an
action, invoke the production driver, or write an outcome.

## Exact artifact

- artifact ID: `10660335894`
- archive SHA-256: `49597c4a3011464e1d4bb707dfa1b554090054059e0675254524c92fcce8d6fd`
- ordered vector SHA-256: `5cd8a78fb62a59495d07ed31c4579fab7bafe2bc9c075aa7757a7f67953c75d4`
- assignments SHA-256: `d85a30dcf46132609bdcd29d3a2b2e8621dba82b4e2111d95a385fb7642b4fab`
- manifest SHA-256: `5adea1d05defd4232d5117822c5a0afdf01532d75e05f180ea488b1b83e41a65`

The workflow first verifies the archive digest and internal checksum inventory, then invokes only the
replication input loader. The validation test checks 12 ordered unique nonzero seeds, exact seed-to-row
binding, exact Games 1-12 order, exact frozen hashes, and exact assignment-cell mapping.

No seed value is printed by the test. No coordinator, initializer, game environment, pilot, production
driver, or outcome-bearing API is invoked.

Official replication counters remain zero throughout this gate.
