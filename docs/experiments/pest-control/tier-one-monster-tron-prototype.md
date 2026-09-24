# Pest Control Tier-1 coverage — Monster Tron Prototype support gate

## Scope

This gate closes the final **card-definition / Prototype-mechanic** blocker for the frozen mehanske
Monster Tron identity by implementing Prototype as a shared Argentum casting mechanic and defining
Boulderbranch Golem faithfully.

Protocol:
`PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1`

Accepted predecessor gates:

- Monster Tron identity admission PR #127 merge:
  `b14e1ded8e6803086892c3e43b323f45bb7c25fd`
- non-Prototype support closure PR #128 merge:
  `98b0f3ecc9e6c65b84acb1086a840d10e7ab08c7`

No Pest Control or Monster Tron deck identity changes are made.

## Prototype rules model

Prototype is **not an alternative cost**. Choosing Prototype changes the spell/permanent's mana
cost, color, power and toughness while it is on the stack and battlefield; it does not replace
name, types, rules text or abilities. Outside those zones the card uses its printed
characteristics again.

A true alternative cost may coexist with Prototype. The shared engine therefore keeps the
Prototype characteristic choice orthogonal to what cost is actually paid. A generic deterministic
test proves that a spell may be cast “without paying its mana cost” while still using Prototype
characteristics.

The implementation covers:

- separate normal and Prototype hand-cast actions;
- Prototype mana cost, mana value, color, power and toughness on the stack and battlefield;
- cost increases/reductions applied when the Prototype mana cost is the payable base;
- a true alternative/free cost without erasing Prototype characteristics;
- durable Prototype provenance into the resolving permanent;
- reset to printed characteristics whenever the object leaves the stack/battlefield, including
  direct counter/exile stack exits;
- return to hand followed by an ordinary normal recast;
- polymorphic component registration for persisted/replayed GameState.

Multi-face Prototype combinations remain fail-closed because no such interaction is needed for the
frozen list and it has not been separately validated.

## Boulderbranch Golem

Authoritative card characteristics used here:

- mana cost `{7}`;
- Artifact Creature — Golem;
- 6/5;
- Prototype `{3}{G}` — 3/3;
- “When Boulderbranch Golem enters, you gain life equal to its power.”;
- The Brothers' War collector number 197;
- artist Dan Murayama Scott.

The ETB reads source power through the shared dynamic-power path. Deterministic scenarios require
normal entry to gain 6 and Prototype entry to gain 3.

## Newly discovered seedless readiness blocker: Cascade

The exact frozen Monster Tron list contains four Maelstrom Colossus with Cascade. During this gate,
a deterministic attempt to exercise Maelstrom Colossus → Boulderbranch Golem did **not** produce a
Cascade may-cast decision.

Repository inspection found the shared `CascadeExecutor` and the `Keyword.CASCADE` card tag, but
no currently live keyword-to-trigger synthesis was identified on the production cast path. This is
therefore a separate seedless rules/readiness blocker for the exact frozen 60.

This gate does **not** approximate Cascade, hide the gap, or authorize gameplay around it. After
Prototype is accepted, the next justified gate is a generic Cascade wiring/validation gate followed
by the broader seedless Monster Tron readiness matrix. No official seed may be generated until that
work is green.

## Validation required for this gate

The dedicated pull-request workflow must prove:

1. the exact frozen Monster Tron maindeck has zero unresolved registry identities;
2. a generic synthetic Prototype card exposes and resolves Prototype independently of
   Boulderbranch Golem;
3. Prototype GameState survives serialization with its provenance component intact;
4. a free/alternative cast can coexist with Prototype characteristics;
5. both normal and Prototype Boulderbranch hand casts are offered at their correct costs;
6. the Prototype spell is green, mana value 4 and 3/3 on the stack;
7. it remains 3/3 on the battlefield and gains 3 life on entry;
8. a normal cast remains colorless, mana value 7 and 6/5 and gains 6 life;
9. destruction, countering and return-to-hand all restore printed characteristics;
10. a normal recast after return to hand is the ordinary 6/5, mana-value-7 object;
11. polymorphic serialization registration remains complete;
12. the BRO golden snapshot is reviewed and committed before merge.

## Experimental boundary

Status: `PROTOTYPE_ENGINE_VALIDATION_PENDING_CASCADE_READINESS_SEPARATE`

This is rules/card support only. It does **not** authorize a Monster Tron matchup experiment.

Official Monster Tron counters remain:

- games authorized: `0`;
- seeds generated: `0`;
- games initialized: `0`;
- actions submitted: `0`;
- outcomes exposed: `0`.

Only after this gate is fully green and merged may the project advance to the separate seedless
Cascade/readiness gate. No seed freeze or gameplay follows directly from PR #130.
