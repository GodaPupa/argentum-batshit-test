# Multikicker authoring boundary

Source inspected: `c53d9621557c26b46dc946c20a9c072349af0374`.
The isolated `MultikickerCostingBoundaryTest` probes engine behavior using synthetic
fixtures. It does not author or register Everflowing Chalice. Its two controls and
five required-behavior probes must be evaluated separately; a passing control is
not shared-seam qualification or exact-card admission.

## Required behavior

- Repetition count is announced before payment, independently of spell X. Three
  repetitions of an additional `{2}` cost consume six mana, rather than two.
- The optional additional mana belongs to the total cost before reductions. A
  `{0}` base plus `{2}` additional mana minus `{1}` costs one mana.
- Free and alternative bases replace only the base cost; declared additional
  costs still apply. A free base plus `{2}` consumes two; flashback `{1}` plus
  `{2}` consumes three.
- The enumerated optional-cost cast retains its original alternative permission.

Authoritative card/ruling reference:
[Wizards' Lost Caverns of Ixalan release notes](https://media.wizards.com/2023/downloads/LCI_Release_Notes_kUj28nYwbydD/EN_MTGLCI_ReleaseNotes_20231107.pdf),
Everflowing Chalice, Fist of Suns, and Discover. These pin the zero-kick option,
one charge counter per kick, additional kicker on alternative costs, and elected
additional costs when casting without the mana cost.
No unverified rule numbers are introduced by this probe.

## Source trace at the inspected head

| Location | Existing boundary |
|---|---|
| `mtg-sdk/.../scripting/KeywordAbility.kt:320-352` | `OptionalAdditionalCost.multi` is authored, but the declaration is keyed only by `declaredSlot`. |
| `rules-engine/.../core/GameAction.kt:70-86` | `CastSpell` has a slot declaration but no repetition-count input. |
| `rules-engine/.../handlers/actions/spell/CastSpellHandler.kt:177-185` | The declared costs are selected by slot only. |
| `CastSpellHandler.kt:1283-1291,2715-2723` | Validation and execution append one optional mana cost after calculating effective costs; they skip it for free/alternative casts. |
| `rules-engine/.../mechanics/mana/CostCalculator.kt:175-193` | The normal calculator applies increases/reductions to the printed base before the handler appends optional mana. |
| `CostCalculator.kt:1846-1895` | Alternative-base costing has a separate pipeline which explicitly omits reductions; it cannot express the required combined pre-reduction total. |
| `rules-engine/.../legalactions/enumerators/CastSpellEnumerator.kt:2207-2234` | One optional-cost branch per declared slot; one additional mana payment. |
| `rules-engine/.../legalactions/enumerators/CastFromZoneEnumerator.kt:2675-2680,2701-2710,2828-2866` | Optional variants are discovered from only `CastSpell` actions and rebuilt from bare actions instead of copying the original permission; flashback variants are `CastWithFlashback`. |
| `rules-engine/.../state/components/stack/StackComponents.kt:25-30` | Stack spell state retains only the declared slot. |
| `rules-engine/.../mechanics/stack/StackResolver.kt:1554-1559` | Battlefield cast choices receive `ChoiceValue.Flag`, not multiplicity. |

The existing `ChooseNumberDecision`/number response UI could render a count choice,
but announcement must precede both automatic and manual mana payment. Reusing an
X input or adding a post-payment prompt would not satisfy that order. Exact
multikicker needs a retained count across action, announcement continuation,
stack/copy state, entry choices, and last-known information; a shared combined
cost pipeline; and optional variants copied from every applicable original cast
permission. A cost-only helper cannot qualify that end-to-end mechanic.

Authority remains authoring diagnostics only. Official allocation, claim, game,
and outcome deltas are zero; CI grants no gameplay or promotion authority.
