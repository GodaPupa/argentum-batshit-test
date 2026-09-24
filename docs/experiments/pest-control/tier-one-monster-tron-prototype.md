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

No Pest Control or Monster Tron deck identity changes are made.

## Rules model

Prototype is **not an alternative cost**. Choosing Prototype changes the spell/permanent's mana
cost, color, power and toughness while it is on the stack and battlefield; it does not replace
name, types, rules text or abilities. Outside those zones the card uses its printed
characteristics again.

A true alternative cost may coexist with Prototype. In particular, “cast without paying its mana
cost” can cast either the normal object or the prototyped object for free. This is outcome-relevant
to the exact frozen Monster Tron 60 because Maelstrom Colossus has cascade and can hit the
mana-value-7 Boulderbranch Golem. The cascade resolution rail therefore exposes three explicit
choices: normal/free, Prototype/free, or decline.

The shared implementation now covers:

- ordinary normal and Prototype hand-cast actions;
- Prototype cost/color/power/toughness on the stack and battlefield;
- cost increases/reductions applied to the Prototype mana cost when that is the payable base;
- true alternative/free-cost precedence without erasing Prototype characteristics;
- durable Prototype cast provenance into the resolving permanent;
- zone-change reset to printed characteristics;
- normal recasting after a reset;
- the exact Cascade → Prototype free-cast choice needed by the frozen Monster Tron list;
- component serialization needed by persisted/replayed GameState.

Multi-face Prototype combinations remain fail-closed. The ordinary hand enumerator also declines
cast-payload shapes it cannot faithfully describe rather than emitting incomplete UI actions; the
core CastSpell/stack semantics themselves do not impose a hand-only or no-alternative-cost rule.

## Boulderbranch Golem

Authoritative card characteristics used here:

- mana cost `{7}`;
- Artifact Creature — Golem;
- 6/5;
- Prototype `{3}{G}` — 3/3;
- “When Boulderbranch Golem enters, you gain life equal to its power.”;
- The Brothers' War collector number 197;
- artist Dan Murayama Scott.

The ETB reads source power through the rules engine's normal dynamic-power path. Deterministic
scenarios require normal entry to gain 6 and Prototype entry to gain 3.

## Validation

The dedicated pull-request workflow must prove:

1. the exact frozen Monster Tron maindeck has zero unresolved registry identities;
2. a generic synthetic Prototype card exposes and resolves Prototype independently of
   Boulderbranch Golem;
3. Prototype GameState survives serialization with its provenance component intact;
4. both normal and Prototype Boulderbranch hand casts are offered at their correct costs;
5. the Prototype spell is green, mana value 4 and 3/3 on the stack;
6. it remains 3/3 on the battlefield and gains 3 life on entry;
7. a normal cast remains colorless, mana value 7 and 6/5 and gains 6 life;
8. a Prototype permanent/countered spell restores printed characteristics after leaving the
   stack/battlefield;
9. Maelstrom Colossus cascade into Boulderbranch offers normal/free and Prototype/free separately;
10. both cascade modes resolve with the correct characteristics and ETB life;
11. polymorphic serialization registration remains complete;
12. generated BRO card snapshots are reviewed and committed before merge.

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
