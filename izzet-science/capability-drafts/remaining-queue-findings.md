# Remaining finite-queue capability findings

Source inspection against accepted AM snapshot `203a5cb6f278443b121f90eac474cbc879755677`.
These are prospective deterministic qualification requirements, with no official seeds or outcomes.
No listed card is accepted by this inventory. No deck change is proposed or promoted.

## Ram Through — source damage and excess conservation

`rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/DamageUtils.kt`, the
`dealDamageToTarget` creature branch, computes `markedOnCreature = effectiveAmount - creatureExcessDamage`
when `excessToController` is true. Later accounting still uses full `effectiveAmount` for per-player
creature damage, source damage, emitted DamageDealtEvent and lifelink, then recursively deals the
excess again to the controller. The primitive is therefore not qualified merely because it can
express the card's surface syntax.

A distinguishing fixture must use a five-power trample/lifelink source against an undamaged 2/2:
creature and controller damage events must be 2 and 3, total lifelink 5, total source damage 5,
and creature damage-by-player accounting 2. Current source inspection predicts double-counting of
three excess damage in the ledger and lifelink; this is not yet an executed failing-test receipt.
Further cases must cover no trample, preexisting damage, deathtouch, protection/prevention, source
and target becoming illegal, and no synthetic assignment of damage when a source is absent.

## Prismatic Strands — all damage versus combat damage

`rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/effects/combat/PreventDamageExecutor.kt`,
the `PreventionSourceFilter.FromGroup` branch, unconditionally constructs
`SerializableModification.PreventCombatDamageFromGroup`, without preserving an AllDamage scope.
A source-color group therefore cannot currently establish the printed all-damage shield.

Distinguishing fixtures must prevent both combat damage and noncombat spell/ability damage from the
chosen color for all recipients, allow other colors and unpreventable damage, preserve the chosen
color at source-damage time, and expire at the correct turn boundary. Flashback must tap a white
creature under the acting player's control using the exact alternate cost and exile the spell.
No combat-only approximation is accepted, and no existing prevention regression may be weakened.

## Other explicit prerequisites

- Nyxborn Hydra: shared Bestow capability is owned by the Pest program; qualify a canonical accepted
  implementation separately in this exact Izzet opponent before counting it resolved.
- Snake Umbra: umbra armor is a destruction replacement, not regeneration.
- Benevolent Blessing and Cho-Manno's Blessing: protection exceptions for preexisting Auras/Equipment
  must preserve their printed scope.
- Vines of Vastwood: a caster-relative opponent-targeting restriction cannot be replaced by hexproof
  on an opponent's creature.
- Forge of Heroes: exact commander designation and this-turn entry conditions are required.
- Opal Palace: commander cast counts and the mana-spend counter rider require executable support.

Accepted coverage remains ten unresolved through AM. AN may reduce that to nine only after its full
artifact audit. Full frozen-deck initialization, executable pilots, event-ledger extraction and
Position-1 provenance remain separate prerequisites regardless of registry count.
