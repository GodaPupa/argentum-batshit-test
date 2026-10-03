# Per-slot target visits

Internal stack identity repair built on source 690c61c16325761f2c7e830c2e5828cc5ed978d6. Each announced target position carries its own serialized TargetVisit (ObjectRef and battlefield-entry stamp). Retargeting refreshes exactly the selected slot. Inherited copies retain visits; explicitly replaced copy targets capture current visits. Resolution preserves invalid slots as nulls. Modal/splice continuation entries serialize aligned visits and validity without target-equality reconstruction.

Focused qualification includes independent same-entity visits for spell, activated and triggered resolution, inherited/explicit-copy targets, serialized queue entries, and an actual ZoneTransitionService exile/return with whole GameState round-trip. Source remains isolated until qualification audit. Registry admission and gameplay authority unchanged. Official delta: 0 allocations / 0 claims / 0 games / 0 outcomes.
