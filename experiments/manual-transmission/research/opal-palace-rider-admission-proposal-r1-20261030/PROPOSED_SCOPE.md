# Opal Palace: bounded rider and C13 registration proposal

The received five-card diagnostic identifies a concrete missing primitive: the saved Opal Palace card uses `ManaSpellRider.CommanderCastEntryCounters`, which is absent from the accepted SDK. Its ten archived scenarios also require a serializable pending counter component. This proposal requests that one primitive and canonical C13 #310 only.

The diagnostic receipt is `d0ada58b64cbd6bde866c89f91b8ce81eca6a068` (run41075326079, artifact12986504200). Its original37,744,655-byte ZIP,1,161 manifest entries and126 archive parts have been independently checked and read back. No new card or behavior was accepted by that diagnostic. The runtime base remains `180b67fb8fee312e576ff7067954584855d40e0f`, with combined Bolt Bend/Fblthp admission at `2d4a2fbac7910b180a7a9b1778f81cbae6a3d230`.

## Requested change

Reuse the existing multiplicity-preserving mana-payment result, commander identity/color resolution, and entry-counter replacement helper. Add the serializable rider and a pending component bound to the exact successfully cast commander spell. Accumulate every consumed Palace unit against that commander's command-zone cast count, including its current cast when applicable. Consume the combined pending counters before entry observers, through the existing counter-modifier path.

Limit existing production edits to the five exact blobs and function boundaries in BOUNDARIES.json: ManaSpellRider.kt, CastSpellHandler.kt, StackResolver.kt, ZoneTransitionService.kt, and Serialization.kt. StackResolver and Serialization are protected runtime bindings. Generic and direct stack exits must discard only the new component; existing source/target occurrence behavior and entry-origin ordering stay unchanged. Spell copies must not inherit paid Palace mana.

Add only the new pending component, canonical C13 #310 card, necessary dedicated tests, and one additive protected C13 snapshot block. The archived card already matches the fresh metadata and all five rulings. C13's existing Angel of Finality and Bane of Progress blocks must remain byte-for-byte unchanged. Removing the new Opal block must recover the archived original entire snapshot exactly.

## Evidence required before acceptance

Preserve all ten archived scenario tests exactly. Add the primitive, negative, serialization, object-identity and cleanup cases in TEST_MATRIX.json. Freeze and compile the normal source and every mutant first. Require exact assertion-based witnesses and clean unaffected controls; compiler errors, missing reports or unrelated failures are not negatives.

Then require affected Fblthp/generic targeting and other dependent checks, all389 original card cases plus the new cases, the19,090 current ordinary cases (including19,074 original rows and16 Bolt additions),43 unchanged historical skips,64-client floor, printing/discovery/metadata/snapshot/lint/build gates and C13 Assay differential. Classify the existing Opal Assay grammar decline honestly. Verify original artifacts and all source/restoration/protected bindings before feature/card admission.

The archived two-mana fixture stages mana because sequential AutoPay may spend the first Palace mana on the second activation. Preserve that fixture and limitation. Verify the existing actual payment/client flow separately; a new payment or UI repair is outside this proposal.

## Scope and authority

This is a source-reviewed proposal, not an implemented or compiled successor and not a qualification result. Original source blobs, original card/test drafts and original snapshot are archived here without modification.

Explicit authorization is needed because the earlier protected resolver grants cover targeting/source characteristics and Fblthp entry-origin behavior, not this mana rider; earlier card grants do not cover Opal Palace or C13. The user's existing prohibition on new protected-boundary changes therefore still applies to this proposal.

No other missing cards, reprints, parser/IR repairs, unrelated protected paths, assertion changes, skips, threshold relaxation, multiplayer admission, official games/seeds, additional spending or workers are requested. Preserve Manual v0.7, all fixtures and accepted/failed evidence, budgets and both reserved Precision games. Keep the sole Work executor and its schedule unchanged.
