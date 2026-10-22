# Full differential findings: triage without suppression

Original report: `differential.log` inside artifact 12430330947. All 51 rows remain divergent. This triage identifies the differing fields and their disposition; it does not add folds, change cards, or claim behavioral equivalence without evidence. `inspect_differential.py` can extract each original block and raw structural delta. IDs, descriptions and image URLs shown in raw blocks are not automatically the cause of a divergence.

The repair changes only the printer's slot extraction, not any parser build closure or SDK recipe. These model differences therefore do not result from newly changed card semantics. No before/after full-corpus differential execution was performed; the pre-existing README's historical count of 51 is not proof of an identical prior population.

| Cards | Differing meaning or representation | Classification / disposition |
|---|---|---|
| Walking Ballista | Enters-with counter amount `XValue` versus `CastX` | SDK context-sensitive representation. Existing README's dynamic-counter section explains both contexts; retain the divergence and exact entry/cast evidence. Neither value is globally interchangeable. |
| Devout Chaplain; Hand of Justice; Jaspera Sentinel | Tap-payment `excludeSelf`; some card filters also repeat controller/untapped constraints | Cost-context representation requiring semantic review; no fold adopted. A source tap cost may make the extra exclusion redundant, but the raw difference alone does not prove that. |
| Calamitous Tide; Essence Fracture; Second Breakfast | Target iteration versus explicit indexed target effects | Documented supported SDK representation, candidate fold/cleanup. README's target-quantifier section explicitly classifies these three. |
| Pearl of Wisdom; Wizard's Lightning; Wizard's Retort; Mistmeadow Council; Arcane Epiphany; Winged Words; Venom's Hunger; Saved by the Shell; Bolt Bend | `ReduceGeneric` plus `OnlyIf` versus `ReduceGenericBy(FixedIfControlFilter)`; some bare-subtype predicates also narrow to creature | Documented SDK representation family, retained. Creature-only subtype narrowing is an additional potential card defect and must be assessed separately; Saved by the Shell also nests its keyword sequence differently. |
| Bog Rats | Blocker filter permanent versus creature with subtype Wall | Context-dependent narrowing/candidate fold: legal blockers must be creatures. No generic subtype fold is authorized. |
| Goblin Digging Team; Sethron, Hurloon General; Lathril, Blade of the Elves; Quickbeam, Upstart Ent; Krosan Warchief; Grow Extra Arms; Dragon's Prey | Bare subtype read as permanent; card narrows to creature | Card-side over-restriction candidate; README explicitly identifies Quickbeam and the bare-tribal-noun family. Lathril additionally differs on tap-cost exclusion. No card changed in this repair. |
| Scarecrow | Card explicitly includes controller in the damage-recipient group | Recipient-scope semantic difference requiring prevention-path review. Retain as potential parser/card gap, not equivalence. |
| Keeper of Fables | Dedicated batched combat-damage event versus general damage event with batch/combat/recipient/controller fields | SDK event representation; requires exact batch equivalence before a fold. |
| Cryogen Relic | `stun` versus `STUN` counter spelling | Documented normalization finding: README notes `CounterType.fromName` uppercases. Retain report; no fold added. |
| Raise the Past | Collection slot names and explicit `underOwnersControl` | Candidate normalization/contextual fold; owner-control semantics must be checked independently of arbitrary collection naming. |
| Balm of Restoration | Activated modal effect explicitly sets `countsAsModalSpell=false`; target-reference representation differs | Parser lowering/metadata gap candidate. No semantic confirmation from text round trip alone. |
| Merchant Scroll | Card's search filter omits blue | Card-side missing-filter defect indicated directly by the compared models; not changed or waived. |
| Elvish Warmaster | Explicit `ControlledByYou` on batched entry filter | Documented event-default representation family; exact event default must remain part of any future fold review. |
| Boggart Sprite-Chaser; Epic Proportions; Kithkin Greatheart | Split static abilities versus `CompositeStaticAbility`, with conditional wrapper placement where applicable | SDK static-composition representation; retain until layer/dependency equivalence is established. |
| Aragorn, the Uniter; Viridian Longbow | Explicit self damage source; Longbow also uses indexed versus named target | Source/target representation; granted-ability source identity is a concrete risk and cannot be erased by a generic fold. |
| Bloodhunter Bat | Lose-life then gain-life composition versus `DrainLife` | SDK effect representation requiring prevention/replacement and amount semantics review. |
| Ghoulish Procession | Decayed keyword versus decayed counter | Already documented engine realization gap. Prior receiving checkpoint and card/grammar KDocs preserve the reason; not an agreement. |
| Offender at Large; Rakish Scoundrel | Two triggers versus one `AnyOfEvents` trigger | Documented SDK event representation from entry/target-quantifier bands. Retained without a fold. |
| Vendetta | Destruction and life-loss order differs | Card/engine last-known-information workaround candidate with outcome-relevant sequencing risk. Requires scenario evidence; not accepted as equivalent. |
| Taj-Nar Swordsmith | Card's Equipment search adds `IsArtifact` | Card-side extra predicate; subtype/type relationship requires review before treating it as a safe fold. |
| Stronghold Machinist | Card omits noncreature spell predicate | Card-side missing-filter defect indicated by the compared models; not changed or waived. |
| Experimental Synthesizer | Explicit token name and image metadata | Token-construction/default-name representation; verify generated name before any normalization. |
| Ghalta, Primal Hunger | Generic total-power query versus dedicated total-power value | Documented SDK duplicate from spell-cost band. Retained without a fold. |
| Bramblesnap; Shared Discovery | Cost filter repeats controller and/or untapped restrictions | Cost-context representation; requires proof from cost defaults before folding. |
| Stress Dream | Bottom-of-library move explicitly allows controller ordering | Documented pre-existing `TopOfLibrary` grammar gap. Retain as semantic parser finding. |
| Urban Retreat | Three fixed-color mana abilities versus one specific-color choice ability | Documented SDK mana representation family; distinct decision behavior must be reviewed. |
| Seize Opportunity | Stored collection name differs | Documented differential normalization gap; arbitrary slot names are not normalized here. No fold added. |
| Gather the White Lotus | `AggregateBattlefield` versus `Count(Battlefield)` | SDK amount representation; projected-filter behavior must be checked before equivalence. |

Full-game admission remains blocked by card/mechanic/pilot coverage and unresolved relevant semantic findings. These 51 rows do not invalidate the exact seven-failure control or the four repaired printer assertions, but a green differential exit means only zero undecodable goldens. It does not mean zero divergences or complete card qualification.
