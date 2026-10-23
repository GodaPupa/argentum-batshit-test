# Target-slot planning — first Spellskite dependency, not card admission

**Prepared source only. No compilation, test success, live retargeting, or card registration is claimed by this source commit.**

## Contract

Represent the originally selected targeting instances and their individual object visits, then produce an immutable exactly-one-recipient change without altering any other occurrence, division, group, or selected count. The plan is deliberately not a legality checker, new GameState component, source-card ability, or player decision handler.

The prior Spellskite specification at `88cd69c88628e0dfd78c2e45c067c91d727aecb8` calls for 22 complete gameplay scenarios. The 22 tests in this prerequisite are a **different set**: 20 structural cases and two executable mutation witnesses. They cannot substitute for the card's 22 acceptance scenarios.

## Evidence and architecture

The qualified source is `0b5018a0df392acc96d8b38e20dd72ccae3ff48a` (tree `69bd3d85ff043c4f262dafe176d45b6d75752bed`). Read-only source-intake run `40007352951`, artifact `12524238468`, has ZIP SHA-256 `130dd7aa6d9f085cbd253f9175c30f0b41edd6f5402f75e3ff425fb07f091e86`. Its payload hashes and 2,523 tracked source blobs were independently recounted by this assistant. This is not an external peer review.

The current `TargetsComponent` stores battlefield-entry stamps by physical entity ID. Its `capture` method snapshots every selected permanent again; therefore it is not a lossless adapter for changing one target while retaining an unchanged target's old visit. The existing contested-retarget implementation also flattens targeting requirements and globally excludes duplicates. Neither operation can safely supply Spellskite's full behavior unchanged.

This additive engine-level representation preserves explicit targeting-instance indices, including empty optional instances. Each occurrence stores its own chosen target, optional captured `ObjectRef`, and optional divided amount. A repeated physical entity with two different captured visits remains two distinct occurrences. An untracked historical visit stays untracked; the helper does not read live state or silently refresh it. Duplicate protection is per targeting instance, not global. Unknown historical duplicates are conservatively rejected rather than assumed distinct.

The official Comprehensive Rules text effective September 25, 2026 was consulted for target-instance distinctness, changing exactly one target, final target-set restrictions, and unchanged allocations/modes. This representation only makes those requirements expressible. Full targeting legality and rule-faithful execution remain integration gates.

## Cross-layer boundary

| Layer | This slice | Remaining integration |
|---|---|---|
| Engine data | New serializable immutable planning values and one-recipient replacement | Capture original target-instance counts and object visits at selection |
| Existing GameState and serializers | No changed component or existing serialized field | Review any future optional component against all 82 legacy saved-state fixtures |
| Target validation and resolution | Not called or modified | Use original stack controller/source, preserve already-illegal unchanged targets and all relational restrictions |
| Modal, splice and division views | Divisions remain attached to occurrences in the new plan | Bind exact original addresses and apply one accepted replacement consistently across every live view |
| Continuations and legal actions | No new action/decision | Serialize and validate a real slot-choice prompt and its stale-answer behavior |
| Trigger/ward events | No state mutation, hence no events | Emit exactly the newly targeted occurrence after accepted mutation |
| Client/server/AI | Not exposed, no UI change needed for this internal prerequisite | Trace real activation/choice/payment, masking, legal-action enumeration and pilot decisions before card admission |
| SDK and Assay | No SDK vocabulary added and no existing code changed | Any later new effect must update the SDK catalog and applicable parser/generator consumers |

Existing source, cards, tests, policies, snapshots and original experiment evidence remain unchanged. The standalone plan is not installed into a live resolver. Because its caller supplies groups rather than asking the helper to infer them from flattened targets, the next adapter can fail explicitly when original grouping evidence is unavailable instead of guessing.

## Cost and immutability

The update allocates a new selected group and outer list while retaining the exact nonselected slot/group objects. Group construction checks distinctness pairwise; this is bounded by the selected group size, and the helper is not yet in any search/priority hot path. Integration must review pathological target counts before exposing it in performance-critical enumeration. No cached projection is recomputed, no mutable engine state escapes, and no current GameState is consulted.

## Prospective qualification

`PROTOCOL.json` freezes all 22 exact case identities and SHA-256s. The two negative witnesses run against a compiled mutation of the new helper that deliberately discards the selected replacement. They must fail with the exact asserted first frames; compilation failures, missing tests, or a green mutant are not valid controls. The mutation is **not** described as the older qualified engine, which has no such type.

Restore the exact source before positive testing. Run the prior 690 focused cases plus these 22 (712), all engine/card-era regressions (14,775, including 4,000 engine tests), ordinary project build (18,383 passes and the original 43 skips), and the inherited Assay gates. Keep all 51 original unresolved differential findings. Preserve every original row, including the 11 known duplicated parameterized labels and their exact multiplicities; never deduplicate reports into a desired count. The separately received artifact remains required for acceptance.

No official games, official seeds, deck promotion, protected golden edits, main merge, or Precision allocations. A successful run can qualify only this structural prerequisite. It cannot increase card coverage or establish full Spellskite, multiplayer, or competitive readiness.

## Next source-bound slice

After receiving this component, trace a target-instance/slot adapter through actual selection and resolution before introducing a fixed-recipient retarget effect. Preserve original per-slot visits and divisions; do not infer optional group boundaries from counts after flattening. Then implement and qualify original-controller/source legality, no-op and departed-source behavior, exactly-one-slot decisions, modal/splice synchronization, targeting events, mana/life payment, the actual card's 22 scenarios, and canonical printing metadata. Reuse the accepted evidence, but never transfer it across changed executable source without the required regression gates.
