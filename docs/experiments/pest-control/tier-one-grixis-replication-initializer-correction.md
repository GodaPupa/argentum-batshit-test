# Pest Control Tier-1 Grixis replication — initializer correction

## Scope

This is a seedless infrastructure correction following the terminal Game 1 initialization incident.
It does not authorize continuation gameplay.

The original four-game smoke initializer is intentionally unchanged. A separate replication-specific
initializer now binds only the frozen 12-game replication identity:

- freeze commit `5234db81bc87b6061bc3dfb891544205e66acc93`;
- vector `5cd8a78fb62a59495d07ed31c4579fab7bafe2bc9c075aa7757a7f67953c75d4`;
- assignments `d85a30dcf46132609bdcd29d3a2b2e8621dba82b4e2111d95a385fb7642b4fab`;
- manifest `5adea1d05defd4232d5117822c5a0afdf01532d75e05f180ea488b1b83e41a65`.

The initializer structurally rejects replication Game 1 because its attempt was consumed by official
run `35654021529`. Only Games 2-12 can pass the game-number boundary.

Deterministic synthetic tests verify that:

- Game 1 is rejected;
- Games 2-12 can initialize under the replication identity;
- the old four-game smoke identity is rejected;
- a durable attempt marker remains mandatory before initialization.

No official replication seed is supplied to these tests and no production pilot is invoked.
Games 2-12 remain frozen and unattempted.
