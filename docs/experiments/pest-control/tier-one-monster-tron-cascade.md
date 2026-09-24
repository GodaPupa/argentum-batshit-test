# Pest Control Tier-1 coverage — Monster Tron Cascade support gate

## Scope

This seedless gate follows merged Prototype PR #130 and closes the next rules blocker found while
validating the exact frozen mehanske Monster Tron maindeck.

Protocol:
`PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`

Authoritative predecessor:
- Prototype merge: `ed04d8ee7581537f893712c4f914d7a4c8571238`

No deck identity, seed, gameplay, sideboarding or outcome is changed or authorized.

## Rules boundary

The frozen list contains four Maelstrom Colossus with Cascade. The shared engine already had a
`CascadeExecutor`, but the cast path did not synthesize a Cascade trigger from the
`Keyword.CASCADE` ability.

This gate wires one printed Cascade instance, plus existing runtime keyword grants, into the normal
spell-cast trigger pipeline. Focused rules coverage proves that:

- Cascade triggers when the spell is cast and resolves before that spell;
- equal-mana-value nonlands are skipped; the stopping card must have strictly lower mana value;
- the stopping card may be cast without paying its mana cost;
- the trigger remains independent if the source spell is countered;
- Maelstrom Colossus produces the trigger in the actual set definition;
- when Maelstrom Colossus cascades into Boulderbranch Golem, the free cast may use either ordinary
  or Prototype characteristics because Prototype is not an alternative cost;
- the normal/free path is the 6/5, mana-value-7 object and gains 6 life;
- the Prototype/free path is the green 3/3, mana-value-4 object and gains 3 life.

This gate does not claim complete support for every historical Cascade corner case. It establishes
the shared behavior required by the exact frozen Monster Tron 60 without a Monster-Tron-specific
rule or AI heuristic.

## Experimental boundary

Status: `CASCADE_RULES_VALIDATION_PENDING`

Official Monster Tron counters remain:
- seeds generated: `0`
- games initialized: `0`
- actions submitted: `0`
- outcomes exposed: `0`

Only after this gate is green and merged may the project advance to the broader exact-60 seedless
Monster Tron sequencing/policy readiness matrix. No smoke seed may be generated from this gate.
