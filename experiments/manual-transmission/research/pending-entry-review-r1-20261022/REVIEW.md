# Next entry boundary: read-only source review

Status: DESIGN REVIEW ONLY. No implementation, test execution, card admission or competitive evidence.

Reviewed source: `84994114025d0138305ed9931c00b957f9d80597`, qualified copied-choice receipt `cea98e91953c66d96ad71da133121e930214495f`. The duplicate push run `39817109162` is being received separately. No further build is to start while it is active or its result is unaudited.

## Boundary

Represent the pending entry of one or more physical cards as serializable engine data; collect copy and printed as-enters choices while the original objects remain in their source zones, then publish their prepared identities through the canonical zone-transition path.

This is engine scheduling and continuation state. It does not require a new card-specific SDK effect, a fake cast, an alternate game state exposed to clients, or a new choice UI.

## Observed gaps

- `MoveToZoneEffectExecutor` handles Aura attachment separately, then moves the card before applying counters and `OnEnterRunEffect`. It has no `EntersAsCopy` or `EntersWithChoice` stage.
- `MoveCollectionExecutor` publishes non-Auras before Aura questions. Its ordinary loop moves each card and applies counters immediately. It has no copy/printed-choice stage and explicitly documents an omitted `OnEnterRunEffect` path. Per-destination group pauses may also strand later groups.
- `PlayLandHandler` publishes the land and consumes its land drop before copy/choice suspensions. Delaying only its event does not delay battlefield membership or entry tracking.
- `ZoneTransitionService` captures the entering CardComponent early, performs cleanup, publishes the entity, initializes continuous/replacement payloads, records entry, then emits the event. Prepared identity/choices must be installed after old-object cleanup and before initialization and tracking. Event naming must use the entering identity while LKI remains tied to the old object.
- Existing copy/choice continuations for already-published permanents finalize events/triggers themselves. They cannot simply be reused unchanged for a pending entry.
- `StateProjector.projectForEntry` already supports hypothetical entry against the pre-entry battlefield. Existing token-copy continuations already preserve a `beforeEntry` state for entry predicates. Those qualified predicate protections must remain intact.
- Copy resumers read the target's base CardComponent. Complete face-down/token/X copiable-value coverage is still a separate open dependency.

## Proposed design constraints, subject to implementation review

1. Capture exact source ObjectRefs, intended controllers and the complete batch before any question. A resumed answer must never move a later incarnation of an entering card or exile a later incarnation of a copied graveyard card.
2. Keep prepared CardComponent, CopyOfComponent, fresh choice bag, copy-counter/tap riders and applied-replacement progress in the pending plan. Do not overwrite the still-existing graveyard/library object with hypothetical copy characteristics during a pause.
3. Exclude the entire co-entering batch from battlefield-copy and graveyard-exile candidate pools; preserve projected filtering for legitimate pre-existing targets.
4. Collect every relevant copy/printed choice before any batch member is published. Save and restore the complete state at each decision. Choosing one copy must not make it eligible for the next copy in the same batch.
5. Reuse existing server-authoritative SelectCards/Color/Option/Number questions. Extract common choice construction/recording where needed, without weakening existing spell or token assertions. Verify private-zone masking and the visible chooser flow.
6. Commit through `ZoneTransitionService`, installing prepared data at the correct post-cleanup/pre-entry boundary. Preserve copy restoration on exit, continuous/replacement abilities, tapped entry, intrinsic/printed/global counters, object identities, entry timestamps and exactly one entry event/trigger occurrence.
7. Retain the full EffectContext, targets, named targets and pipeline collections. Resume outer work exactly once and propagate `storeMovedAs`, identity-authorized moves, link/unlink flags, counters, reveals and other executor bookkeeping. Do not replay the original gather/filter against a changed zone.
8. Keep Aura attachment, other entry replacement families, granted Riot and generic `OnEnterRunEffect` explicit in the scope ledger. A bounded copy/printed-choice stage does not by itself qualify complete entry mechanics or exact Metamorph.

## Required next regression coverage

Preregister exact testcase identities and file hashes before execution. Preserve all existing seven copied-choice assertions, ten carrier assertions and two SDK assertions unchanged.

- Direct library collection entry and graveyard single-target entry, with copy exceptions and copied abilities preserved.
- No battlefield membership, entry timestamps, entry counters or entry events while copy and chained printed choices remain pending; original source identity remains intact.
- Complete-state serialization before every choice, fresh copied choices rather than inherited choices, decline and no eligible candidate.
- Multiple co-entering copies with a pre-existing target; no coentrant in any copy choice and no publication until all choices complete.
- Copied counters/static abilities/ETB triggers, one final transition per card, correct old/new ObjectRefs and controller, once-only outer pipeline continuation and output collection.
- Stale source and copied graveyard target ObjectRefs after serialization, ensuring later zone visits are not moved/exiled.
- Copy leaving restores the printed identity. Entry predicates see the proper pre-entry boundary, not a partially published batch.

Only actual assertion failures count as baseline negative controls. Compilation errors, missing cases, skips and incomplete artifacts do not qualify. The baseline is the exact qualified copied-choice source, not main or an older carrier. A source-bound diagnostic baseline may expose already-working cases; report the observed verdicts without rewriting assertions to manufacture failures.

## Exact Metamorph intake retained

The original ten scenarios are preserved from source `99b5ddcfffeb3f919664ac3ab0ecfd1945b86f44`, test blob `1075f22c9c9639c49f7d78dc0b5dcc37e26df365`. They have not been adopted. The NPH #42 source intake is preserved in checkpoint `6a530db1c7b967c9604963750c2927c4b64be38f`, artifact `12401637647`, SHA-256 `6f2a85e9b08d85dd1fe244d3509b96cded0a246d9039b5fcaa79bd6666d5ea32`. Its parse/emitter results were diagnostic declines/scaffolds, not semantic qualification. Reverify current exact card data before registration; add the unresolved token/X/face-down and simultaneous-entry cases without changing the original ten.

## Rules checked

Official Comprehensive Rules effective 2026-09-25: https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf . Checked 614.12/614.12a (hypothetical characteristics and pre-entry choices), 614.13a (coentrants cannot be exiled for the same entry), 707.5/707.6 (copy on entry and fresh choices). Copiable values, face-down treatment and other families require their own precise rule/ruling checks before implementation.

## Safeguards

Frozen v0.7, the 23 protected golden files, original tests/protocols and both reserved Precision games remain unchanged. No official seeds or games. Main remains `37a603617a03dfd2d81b39dae004f7f336a70dfc`. Component qualification never changes competitive admission or mission coverage counts by itself.
