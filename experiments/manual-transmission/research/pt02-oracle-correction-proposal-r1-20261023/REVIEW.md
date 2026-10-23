# PT02 test-oracle correction — prospective **proposal only**

**UNCOMPILED · UNTESTED · UNADOPTED.** This branch does **not** change the active `PausedTokenInstructionScenarioTest.kt`, and it does not modify any production engine code, frozen decks, policies, protected goldens, or existing qualification evidence.

## Observed source-bound evidence

- Original paused-token R2 source: `06c27e1c31efd974300028c8ce5c1ceeac703280`. R2 run `39946289164` **failed**, with only two focused PT02 target-copy variants failing after the baseline phase passed. Original test assertion at `PausedTokenInstructionScenarioTest.kt:124`: `d.stackSize shouldBe 0`; actual value was 1.
- Original test file Git blob: `51c4162bb2d65fa2dc3a0c007734983e92b4c1ab`, 8,362 UTF-8 characters. All original historical assertions and failures remain untouched at R2.
- Prospective diagnostic source: `859bb61f3f5fdd9a5a4460b2004349e03b47ebdc`, validation `8039ab67687c37f4fe1c8056abc4b08fc2082688`.
- Run `39952157757`: completed success, original artifact `12500948640`, JUnit **4/4 passes**, 0 errors/skips. Separate source-bound receiving review published at branch `checkpoint/manual-pt02-stack-diagnostic-received-r1-20261023`. That receipt retains original JUnit XML and manifest; full original ZIP remains in GitHub Actions artifact.
- Runtime diagnostic finds **one valid resolving `SpellOnStackComponent`** named `Paused Token Copy Action` in the target-copy variants (both serialized and nonserialized), but zero ETB `TriggeredAbilityOnStackComponent` and zero premature tokens. Source-copy variants have zero stack entries and zero premature tokens at the same point.

## Exact proposed assertion change

A standalone proposed *complete test file* is archived as `PROPOSED_PausedTokenInstructionScenarioTest.kt.txt` in this documentation-only checkpoint. It is **not** installed over the original test path.

Compared against original blob `51c4162b...`:

1. Add imports for `CardComponent`, `SpellOnStackComponent`, `TriggeredAbilityOnStackComponent`.
2. Only inside PT02, replace the ambiguous zero-**entire-stack** assertion by:
   - count only `TriggeredAbilityOnStackComponent` objects and require **zero**;
   - require **zero tokens** before the later entry-choice answer;
   - for source-copy, still require `d.stackSize == 0`;
   - for target-copy, require **exactly one** stack object, typed as `SpellOnStackComponent`, with the expected instant-spell name.
3. Leave the original PT02 remaining pending-choice, life total, final stack count and trigger-resolution checks unchanged. Preserve all other original test bodies and declarations.

This is a **test-oracle correction for a confounded measurement**, not an engine fix. The diagnostic establishes why the original test's blanket zero-stack expectation is incompatible with correct resolving-instant semantics. The proposed assertions are *narrower about the actual rule* and more demanding about the identity of the permitted stack object, not simply ignoring an unexpected entry trigger. Nevertheless, they **replace one historical literal assertion in a prospective candidate** and therefore must pass the user's assertion-preservation governance before adoption.

## Authority boundary and next actions

**Do not dispatch a qualification or edit the original test simply to turn R2 green.** The inherited rules require preserving original assertions and rejecting test weakening. The historical R2 assertion stays immutable; this draft is held for explicit review before any prospective amendment.

If separately approved under the governing experiment's acceptance process:

- Archive original fixture byte-for-byte as a protected annex and bind the original source blob.
- Freeze a versioned protocol declaring the correction and its exact source/test identities, including the previously unexercised post-second-choice PT02 checks.
- Restore the proposed semantic assertion only in a **new**, isolated candidate (R3 or later), without changing the original R2 source.
- Test negative controls on a genuinely qualified baseline, using source-bound evidence and no false engine failure interpretation.
- Run focused, engine/card-era broad, ordinary build, Assay, and final original-byte receiving audits; never retroactively reclassify R2.
- No official Phase 2 games, seed generation, or Precision-game consumption; preserve frozen v0.7 and all 23 protected goldens.

There has been no compilation, test dispatch, model-controlled gameplay or qualification from this proposal-only branch.
