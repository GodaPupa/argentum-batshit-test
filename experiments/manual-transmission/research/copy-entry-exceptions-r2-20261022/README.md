# Recover and qualify the held copy-entry exception carrier

Status: **RECOVERED FOR QUALIFICATION; RESULTS PENDING.**

The ordinary-build prerequisite is now satisfied at repair source
`ebb1e2892c258dde060b27113aa113fba65dcd66`: 18,135 passed, zero failures/errors and 43 named skips.
The original artifact and independent recount are retained in parent checkpoint
`82e78c7a8796960ac11f082ee0ccf71baf2d95a9`. No production file is adopted merely because that
earlier build passed; the newly restored carrier must pass its own gates.

This source restores the exact ten files from the previously held archive, SHA-256
`21be517dafadc4600cdbae728a3445486d9953a53ddc483b5b141189093299bf`. Every file hash was checked,
and the seven existing base files were identical between the archive's base and the repaired source.
`RECOVERED_DRAFT.json` preserves the original manifest and historical untested disposition. The
original r1 protocol is likewise preserved unchanged; this r2 protocol records the current gate.

The bounded capability is to carry the existing shared `CopyExceptions` value through `EntersAsCopy`
and its two serializable continuation shapes, composing it with legacy riders before invoking the
existing `CopyExceptionApplier`. No additional copy arithmetic, card-specific effect or new IR tag
is introduced. Existing descriptions and serialized shapes remain unchanged when the new value is
empty, including serializers configured to encode defaults. SDK documentation ships in the same slice.

## Source and test preservation

The generic donor file remains exactly blob `d35b6b89bb2e2abf9baab2a9edc585f581ce8cb3`, ten tests.
The two saved SDK serialization tests remain exactly blob `c4c2b6eb7ae9e848c528d8827558d153eceada85`.
No original assertion, test registration or snapshot is changed. Seventeen existing file bindings
protect the Assay repair, original assertions, historical workflow locks, facade fixes and frozen deck.
All twenty-three golden files are verified before and after execution.

The targeted gate runs donor10, SDK2 and snapshot/lint. The next gate runs the ordinary build with
the established routine AI filter, including the full SDK, engine and all nine card-era suites.
The expected delta is twelve cases: SDK529, engine3764, overall18147 passed and the same43 skips.
The Assay CLI must then pass its 2,000-card touchstone; its logs, exit codes and corpus digest are kept.
Every stage retains its original XML before another invocation can overwrite it.

## Layer trace and boundary

| Layer | Trace / evidence |
|---|---|
| SDK | `EntersAsCopy.exceptions` reuses pure serializable `CopyExceptions`; `copyExceptions` composes existing riders with the shared modern value. Two SDK tests cover polymorphic replacement serialization, rendered meaning and empty-field omission. |
| Spell entry | `StackResolver` copies the shared value into `CloneEntersContinuation`. The resumer composes it with legacy riders and applies it through the existing `CopyExceptionApplier` before its existing entry completion. Donor tests check the complete copied component and a noncreature artifact source. |
| Direct land entry | `PermanentEntryReplacements.pauseForEntersAsCopy` carries the value in `CloneEntersOnBattlefieldContinuation`; its resumer calls the same applier. A donor test exercises actual land play and selection. This does not change the pre-existing placement-before-choice behavior. |
| Decline | Both existing paths keep the printed component when no copy is selected. Two donor tests verify that declining does not add the exception. |
| Legacy composition | Existing flat riders remain available; `CopyExceptions.over` supplies their established precedence/union rules. A real-entry donor test checks modern name/P/T precedence and preserved legacy subtype/keyword additions. |
| Copy values / projection | Added types live on the copied base component and feed the existing projector. A subsequent ordinary copy inherits the artifact characteristic; the donor asserts that complete component equality. No new continuous-effect layer is introduced. |
| Serialization | Both continuation types retain the shared value and omit an empty one with defaults enabled. The two continuation donor tests remain unchanged. Full-game pending-entry persistence is a later qualification. |
| Events / triggers | Existing entry completion and event/trigger emission remain unchanged. No new event or registry binding is added; full engine/card suites guard existing consumers. |
| Cleanup | The existing copy revert snapshot restores the printed component on leaving. The donor bounce test asserts restored name, types and base stats. |
| Player interaction | Existing `SelectCardsDecision` and `useTargetingUI` remain unchanged. `DecisionUI.tsx` already routes battlefield selections to `BattlefieldSelectionUI`; this slice adds no action, decision type or UI branch. Public copied types remain ordinary card-component/projection data. |
| Generator / Assay | No new SDK primitive or mtgish IR mapping is introduced. Existing grammar constructors keep their default behavior; the full compile and Assay touchstone verify those consumers. |

The official [rules page](https://magic.wizards.com/en/rules) was checked on October22 and links the
[September25 rules text](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt)
and PDF. Rules205.1b,707.2 and707.9b were read directly: retained-type wording preserves prior types,
copies use copiable characteristics, and changes made as part of copying become copiable. The donor
tests pin retained characteristics, added artifact type and copying the resulting copy. This research
does not renew any official-game rules or legality admission.

The source still needs separate work for pre-entry publication, simultaneous batch eligibility,
copied as-enters choices, token/X/face-down values and Phyrexian Metamorph itself. In particular,
`TokenFromDefinition` explicitly does not apply EntersAsCopy to minted tokens, and this slice does not
claim otherwise. It registers no card and qualifies no pilot, multiplayer game or competitive result.

Frozen v0.7, registry counts, capability0/36, primary0/864 and Precision10used/2reserved remain intact.
No official opt-in, seed generation, sampled-game execution or rerun of prior competitive evidence is
part of this workflow. No second-human/agent review is claimed.
