# Pest Control Tier-1 coverage — Monster Tron Prototype support gate

## Scope

This gate closes the final exact preboard card-support blocker for the frozen mehanske Monster Tron
identity by implementing **Prototype** as a shared Argentum casting mechanic and defining
Boulderbranch Golem faithfully.

Protocol:
`PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`

Accepted predecessor gates:

- Monster Tron identity admission PR #127 merge:
  `b14e1ded8e6803086892c3e43b323f45bb7c25fd`
- non-Prototype support closure PR #128 merge:
  `98b0f3ecc9e6c65b84acb1086a840d10e7ab08c7`

No deck identity changes are made.

## Prototype implementation boundary

The shared mechanic models the reviewed ordinary hand-cast path required by Boulderbranch Golem:

- Prototype is a cast mode, **not** an alternative cost.
- normal and Prototype casts are separately enumerated;
- Prototype uses its alternate mana cost, color, power and toughness on the stack and battlefield;
- name, type line, rules text and abilities remain unchanged;
- printed characteristics are restored when the spell/permanent moves anywhere other than the
  stack or battlefield;
- cast-event mana value/color and conditional-mana validation use Prototype characteristics.

This gate deliberately fails closed on combinations not needed by the frozen Monster Tron list:

- Prototype plus another true alternative/free casting cost;
- Prototype on another card face;
- targeted Prototype spells;
- Prototype with additional/optional casting costs.

Those combinations may be extended only by a later separately validated general-engine gate.

## Boulderbranch Golem

Normal characteristics:

- mana cost `{7}`;
- colorless Artifact Creature — Golem;
- 6/5.

Prototype characteristics:

- `{3}{G}`;
- green;
- 3/3.

Its enter trigger gains life equal to its current power, so the deterministic scenarios require
normal entry to gain 6 and Prototype entry to gain 3.

## Validation

The dedicated pull-request workflow must prove:

1. the exact frozen Monster Tron maindeck has zero unresolved registry identities;
2. both normal and Prototype Boulderbranch cast actions are offered at their correct costs;
3. the Prototype spell is green, mana value 4 and 3/3 on the stack;
4. it remains 3/3 on the battlefield and gains 3 life on entry;
5. a normal cast remains colorless, mana value 7 and 6/5 and gains 6 life;
6. a Prototype permanent restores printed characteristics after leaving the battlefield;
7. a countered Prototype spell restores printed characteristics in the graveyard;
8. hand-constructed invalid/unsupported Prototype casts fail closed;
9. generated card snapshots are reviewed before merge.

## Experimental boundary

Status: `PROTOTYPE_ENGINE_VALIDATION_PENDING`

This is rules/card support only. It does **not** authorize a Monster Tron matchup experiment.

Official Monster Tron counters remain:

- games authorized: `0`;
- seeds generated: `0`;
- games initialized: `0`;
- actions submitted: `0`;
- outcomes exposed: `0`.

Only after this gate is fully green and merged may the project advance to seedless Monster Tron
rules/policy readiness. No seed freeze or gameplay follows directly from this PR.
