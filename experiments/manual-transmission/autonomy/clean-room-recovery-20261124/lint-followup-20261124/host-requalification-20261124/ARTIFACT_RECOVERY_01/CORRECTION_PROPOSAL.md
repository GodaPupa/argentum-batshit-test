# Proposed bounded correction — not applied

## Smallest concrete production correction

Carry the chosen spell face's characteristics into alternative-base cost matching, while retaining card-level identity and the fact that a card has an Adventure where predicates require them. Beluna's permanent-spell reduction must not apply to Rip the Seams. Preserve valid generic reductions for ordinary/SURGE alternative costs; do not disable reductions globally or add a card-name special case.

Expected production paths, subject to a reviewed implementation diff:

- `rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/mana/CostCalculator.kt`: add/use explicit chosen-face spell characteristics when matching modifier filters and relevant gates.
- `rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/spell/CastSpellHandler.kt`: supply the same chosen-face context in validation and payment, including free-cast paths that still take modifiers.
- `rules-engine/src/main/kotlin/com/wingedsheep/engine/legalactions/enumerators/CastSpellEnumerator.kt`: supply consistent secondary-face characteristics during affordability/enumeration.
- `rules-engine/src/main/kotlin/com/wingedsheep/engine/legalactions/enumerators/CastFromZoneEnumerator.kt`: preserve that context for permissions selecting an Adventure face.

Test additions would belong in a narrowly scoped engine scenario file such as `rules-engine/src/test/kotlin/com/wingedsheep/engine/scenarios/AdventureSpellCostCompositionTest.kt`. Preserve the original WoeCardsBatch12ScenarioTest and every existing assertion. No card definition, mana cost, snapshot, historical skip or acceptance threshold should change to accommodate the failure.

## Required validation under a separate grant

Before a newly frozen full qualification, validate direct cast and enumeration parity: two Plains reject Rip the Seams without spending mana or moving the card; three Plains cast and resolve it; Beluna still discounts the creature face, including its cast from Adventure exile; legitimate instant/sorcery modifiers affect the Adventure face; applicable taxes still apply; normal/SURGE composition and all existing M01–M14 witnesses/controls remain valid. Include permitted-zone and free-cast face paths when changed. Freeze the exact source diff, regression identities, integrity bindings and new attempt authority prospectively.

Any new full qualification must run from zero with all unchanged requirements: 15 compile forms, 67 normal and restored rows, 14 negative witnesses plus 14 controls, 401 registry rows, all 19,429 ordinary rows with 43 exact historical skips, client gates, metadata/Assay and independent original evidence audit/readback. The current failed run and its partial results cannot substitute for that run.

## Additional authority required

This recovery amendment authorizes diagnosis only. Applying the four-path production correction, adding its bounded regressions and necessary successor integrity bindings, and launching any new test or qualification require a separate explicit grant. No such changes or executions were made.

A formal recovery-aware receipt/readback adapter would also need a separately bounded infrastructure grant if formal original-receipt completion is desired. It must preserve unknown original counts, accept this separately recorded recovery identity, and retain every archive/source/raw-semantic check. This does not require another artifact download: the exact original bytes are already durably archived. Do not re-invoke the frozen receiver under the consumed recovery allowance.

