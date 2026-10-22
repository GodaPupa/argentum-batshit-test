# Manual Transmission / Phase 2 — current-source composed R1 qualification

**Date:** 2026-10-21 (U.S. Pacific)  
**Status:** `SOURCE_COMPONENT_QUALIFIED_AND_REGISTRY_AUDITED_HOLD_OFFICIAL_GAMES`  
**Disposition:** `KEEP_V07`; no full game, official seeds, main merge, default AI change or win-rate inference.

## Executed source qualification

- Exact canonical-main ancestor: `37a603617a03dfd2d81b39dae004f7f336a70dfc`. Exact isolated composed candidate source: `b8ba463263eae5563e4c70aaa0704b100378180b`.
- **Original three-card** Animar/Ancestral Statue/Walking Ballista receiving [run 39687557103](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39687557103): 10/10 deterministic source tests.
- Original nine development cards plus exact seven-line CR 305 land-cast guard [run 39692286081](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39692286081): 72/72 original focused cases and preserved first RED causes.
- Standalone existing Pact and Swan Song source checks: [Pact four tests](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39690329695) and [Swan four tests](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39690334288), source-only independently green.
- Actual integrated composed [run **39695841356**](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39695841356): **SUCCESS**; exact 12-file additive delta at frozen parent `33778a86016c7160a890dd89dbecb6ebef1a6a77`. Reexecuted and independently revalidated **90/90 actual original JUnit scenarios** across four test eras: 8 (1993–1999), 20 (2003–2007), 26 (2008–2016), 36 (2017–2022), all zero failures/errors/skips. The new Pact and Swan donor definitions and tests remained byte-identical.
- The original distinct artifact `manual-composed-r1-final-39695841356` ID **12391231623**, SHA-256 **`3ec137f79fd3b68f76467fb21cadf07886ebdf1eb0d25a01e7a8078cc545aa0e`** was independently downloaded and rehashed. It binds the 90 source mechanics cases and complete catalog receipt.
- Full actual 800-slot Kotlin `MtgSetCatalog`/`CardRegistry` [run 39695841356](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39695841356), artifact ID **12390796506**, ZIP SHA-256 **`648a9e680c280a7b85e96115eee5d13e82f9d347758f38af6e32b8ca96495115`**, independently rehashed: **467/800** actual registered entries, **333** unresolved slots, **214** distinct missing identities. Exact Manual v0.7 **75/100** registered, 25 missing; exact opponents collectively **392/700** registered, 308 unresolved.

## Falsifiable catalog discrepancy

**Spire Garden is the sole missing Manual card among the 13 intended original receiving identities:** the source/ScenarioSpec were byte-identical and the ten original Spire scenarios passed because the fixture explicitly registers a `CardDefinition`. The real catalog did not contain a `BBD` `MtgSet` declaration, and its `MtgSetCatalog.findSets` did not discover the Spire definition. The frozen original 467-slot registry shows Spire Garden unresolved in Manual and **not in any of the seven opponent lists**.

One-file remediation is isolated at `candidate/manual-phase2-battlebond-catalog-receiving-20261021`, based directly on original green source `b8ba463...`, with the donor's exact `BattlebondSet.kt` Git blob `893f8641b7736098257f3b296822d2bb08fb68ef`. Protocol predeclares **12** tests (ten original Spire scenarios plus two real catalog/negative-control tests) and one fresh independent 800-slot audit, requiring **468/800 / Manual 76/100 / opponents 392/700** if the one-card registration diagnosis is correct. **Do not mark this repair green without the new run's original artifacts, JUnit and catalog receipt.**

## Forward source availability, explicitly NOT admission

The sibling [remaining-24 donor source map](./REMAINING_24_DONOR_SOURCE_MAP.json) names all 24 prospective Manual v0.7 missing identities *after* the one-card BBD remediation. It provides one exact pre-existing `CardDefinition` file and one original scenario-test file per identity, drawn from September Phase 2 (10), October 3 compositions (13), and the Amphin isolated source (1). This is source existence only; none of those 24 definitions is authorized as current-main compiled/executable without independently qualified source/dependencies and fresh scoped rules cases.

## Frozen experimentation constraints

- Same original frozen **100-card, zero-Game-Changer** Animar v0.7. Both CONTROL and any historical Challenger A remain unchanged. `main`, `AiProfile.CURRENT`, R5, CRUISE R9 and baseline mana solver are unchanged.
- The official cEDH capability program is **0/36**, primary **0/864**. Earlier Precision full-game sample **10/12** (dependent self-mirrors) retains **two reserved**; the new source-only gates consumed **zero**.
- Generic current-main multiplayer/Commander six banks separately passed **49/49** [run 39686636416](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/39686636416). Synthetic mechanics and registry presence are necessary but not sufficient for exact roster game admission.
- Opponent action competence, CRUISE/SPORT/RACE real masked pilot instantiation, original hidden-information boundaries, applied official rules, full per-transition replay/durability and canonical one-shot claims remain unqualified. The source evidence **does not** demonstrate cEDH win rate, win probability, or a deck upgrade.

**Next:** Verify BBD +1 catalog repair, then receive remaining exact mechanical identity batches with source-pinned negative controls; source-validate opponent card identities and pilots before authorizing any actual Phase 2 game.
