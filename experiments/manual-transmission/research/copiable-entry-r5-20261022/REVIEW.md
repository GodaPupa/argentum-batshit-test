# Copiable entry and pending-reference component

Status: UNCOMPILED, UNTESTED, UNADOPTED. This document is a design review, not a result.

The candidate preserves all three current preregistered banks verbatim: r3 ten copy-value cases, eight copied-ward cases, and six pending-reference cases. Twenty baseline assertion failures and four passing controls are required at the recorded first failure locations. A compile error, setup error, changed assertion, or different failure does not qualify as a negative control. No test is excluded from the baseline compilation. All original carrier, copied-choice, pending-entry and snapshot tests remain intact.

## Scope and trace

- SDK: no changed declaration, new effect type, card definition, or golden. Existing CreateTokenEffect, EntersAsCopy, WardCost and continuation vocabulary compose the behavior. There is no new mtgish IR tag or Assay grammar rule.
- Runtime identity: CardComponent has optional, serializable creation-time token ability provenance and copied face-down ward. Defaults preserve old serialized inputs. Older tokens without provenance remain unknown; later Permanent grants are not inferred to be creation text.
- Token creation: CreateTokenExecutor records its static, triggered, activated and synthetic prowess text. The synthetic prowess instance is shared with runtime installation. Other producers remain outside this component's coverage.
- Entry: common capture supplies spell and pending direct-entry selection. A face-down object yields public nameless 2/2 characteristics and its mode's ward, independent of concealed definition/art/text. Normal copy exceptions still pass through CopyExceptionApplier. Token status, counters, tap state, later grants and floating pumps are not copied.
- Projection and abilities: copied token statics install through StaticAbilityHandler; existing granted-ability lists feed trigger resolution, activation and legal-action enumerators. The creation-time payload remains on the copy itself, so the original token's departure cannot remove its text. The original ten cases exercise actual static, ETB and activated effects, copy-of-copy and real exit/recast.
- Ward: TriggerAbilityResolver synthesizes a normal ward trigger from the copied public cost. The existing ward suppression and payment paths remain in use. Eight additional cases require {2}, then prove payment allows Bolt to kill the copy and decline counters Bolt. The concealed printed {4} is deliberately different. They round-trip both the pending choice and the entered copy, then remove the original.
- Object identity: PendingEntryCopyContinuation and PendingEntryChoiceContinuation expose and replace plan.context.objectReferences through the existing propagation interface. The resolution key and permitted-primary-move gate remain unchanged. Six tests cover paused copy/choice, serialization, unpaused primary entry, and an unrelated later graveyard visit.
- Lifetime: old departure grants remain available for leaves-trigger information. A new battlefield incarnation clears those grants, then installs its own intrinsic payload. The old original CardComponent remains the revert-on-leave snapshot.
- Events and client state: no new GameEvent or decision type. Existing ZoneChangeEvent, SelectCardsDecision, ward payment decision, projected keyword and grant badges are used. The entry snapshot contains selected characteristics, not the new executable ability payload. ClientStateTransformer reads grants for ability badges; ActivatedAbilityEnumerator and ManaAbilityEnumerator read the same lists. The face-down copy has no hidden original definition identity in its copied CardComponent. Browser interaction has not been independently exercised here and is not claimed qualified.
- Performance: payload construction happens once per token batch; capture is per selected entry. No global registry mutation or extra full projection is introduced. Grant filtering occurs once per new battlefield incarnation.

## Specific new dependencies

The copied-value work necessarily changes ModalAndCloneContinuationResumer (shared capture) and StackResolver (entry payload/lifetime). Their former qualified hashes remain the exact negative baseline in PROTOCOL.json. They are moved from the prior constant binding list into the candidate source manifest, with both old and new hashes; no former assertion is removed. The two snapshot test files enter the constant binding list, retaining 23 protected bindings plus all 23 original goldens. This is a dependency regression bank, not a repeat standalone carrier qualification.

## Limits and gates

This component does not establish full entry semantics, all token producers, all copying executors, characteristic-defining token abilities, intrinsic ability predicates for every producer, double-faced copy mana value, paid-X choices, complete simultaneous replacement/observer timing, played-land timing, Aura attachment, generic OnEnterRunEffect or granted Riot. The ten-case X control checks the retained symbol and battlefield mana value only. Full UI, Metamorph admission, frozen card coverage, multiplayer and competitive readiness remain later gates.

Before dispatch, require the original artifact audit of snapshot run 39837054951, all duplicate-run dispositions, and a fresh repository/local activity inventory. Bind the immutable candidate source only after all uploaded files and the source tree are read back. Use one guarded push-only run. Publish original artifacts, every assertion failure and skip, all hashes and the independent recount. Never sum focused, broad and ordinary totals as independent test samples.

Frozen v0.7 and the 23 protected golden files are unchanged. Official games and seeds consumed: zero. Reserved Precision games: two. No competitive admission or performance claim.

## Before-execution fixture correction

The r2 ten-case bank incorrectly expected the recast original copier to project power0 while its second copy still supplies an anthem. The r3 checkpoint50fca7662490fd3a02b4a01e7c7a796e5b26846a preserves the prior files and corrects only this newly added check: printed base0/5 plus projected1. All64 original r1 assertion lines remain in order, with11 additional lines. This was static review before compilation or testing, not a response to a failing run. The face-down negative first frame shifts from171 to173 because two assertions were added earlier; the expected behavior is unchanged.

## r2 import correction after retained failed compilation

Run39843796015 did not execute a test: the supplemental ward fixture was missing its DynamicAmount import. Receipt55ae2a2d7552c735660f7a4a3402803f61fad940 retains the original artifact and failure. The only test change is that import; every non-import line is identical, including all assertions and case names. The ward first-frame location becomes83 instead of82. Production remains byte-identical to failed r1, and the baseline remains qualified snapshot source7a2fbbbf8118ae8c6410c9e234f9cc866bdb926a. All24 negative controls must still meet the same verdicts before any positive gate.

## r3 source repair after valid negative controls and four focused failures

Receipt1872e3a72fc3fc4c802b5d88b33d3e492918aa70 preserves run39845505980 and all original artifact bytes. Baseline24 matched exactly; focused409 passed and4 failed at the unchanged final ally-power assertion165. The previous copy's ContinuousEffectSourceComponent survived printed-card restoration and became active again on recast. ZoneTransitionService now removes that compiled source when reverting a permanent copy. Normal later entry installs the new object's printed or copied static text. Last-known snapshots were captured before this cleanup. No assertion or test file changed. This is a source correction, not a waived failure. Both cast and direct initial-copy paths, with/without serialized pending state, retain the original exit/recast checks.

The existing ability-grant executors reject stack targets (the static executor additionally permits graveyard cards and players). CR400.7a/b general stack-to-permanent grant support is not claimed by this component; no supported stack-grant regression was established by the read-only trace. Full other-marker copy cleanup and all copy executors remain later completeness work.

Before this r3 dispatch, require failed-r2 original-artifact audit and fresh activity inspection. The baseline remains qualified snapshot source7a2fbbbf8118ae8c6410c9e234f9cc866bdb926a; failed r2 is never called a qualified source. All24 original controls, focused413 and later broad/ordinary/Assay gates remain mandatory.


## r5 serialization repair after broad regression

Receipt10a8ff903602b08bac3beb3ee622445996646c73 retains the whole original r4 artifact and failure. The24 negatives matched and focused413 passed, including the four old anthem checks. Broad failed four original saved-state comparisons because the new nullable copiable fields emitted null under encodeDefaults=true. The fields now use EncodeDefault.NEVER: missing data remains omitted, while non-null intrinsic abilities and public ward still serialize. This follows the existing optional-provenance pattern; no migration allowlist, assertion, trace fixture, card golden or snapshot is altered. All24 copiable tests remain byte-identical.

Both original compatibility test classes are now in focused qualification:19 LegacySuspensionMigrationTest and5 SuspensionTraceTest cases, raising focused413 to437 without adding tests. Their source and every file under both historical fixture directories are constant bindings. Broad and ordinary totals remain14597 and18205+43 because these24 were already part of the engine. The baseline remains qualified snapshot7a2fbbbf8118ae8c6410c9e234f9cc866bdb926a. This is not evidence from the failed ordinary/Assay stages, which were skipped. Require the failure receipt audit and fresh activity check before dispatch. Full-history checkout and baseline-tree preflight are mandatory.
