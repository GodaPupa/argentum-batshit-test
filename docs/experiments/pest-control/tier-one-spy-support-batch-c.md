# Pest Control: canonical Bestow and the final Spy main identity

## Scope and accepted input

The accepted Batch A source `d0705a90f30241f7b6509669ab9e952fd558c051` reduced the Spy
main inventory from ten unresolved identities to five. Batch B, PR #148, addresses Land Grant,
Winding Way, Mesmeric Fiend and Wall of Roots; its exact source must independently pass its own
runtime, registry and snapshot gates before integration. This successor addresses only the
prospectively excluded **Nyxborn Hydra**, including the missing canonical Bestow infrastructure.
It does not admit Batch B based on a sibling test result or import Industrial gameplay evidence.

The exact frozen Spy 60/15 and Pest identities remain unchanged. No official seed, game,
execution claim, pilot policy or boarding plan is created by this change. Registry closure is
necessary but does not qualify the opponent pilot or authorize a sampled game. The failed
Monster Tron block retains its original frozen source, claim, retirement and separate repair
process; this code is not installed underneath it.

## Canonical implementation and qualified behavior

The starting implementation and Hydra definition are reused selectively from Industrial source
`abfd806f6332c0da311e40b01c9d85eeb0962165`. Existing Pest Prototype, Cascade and Escape
changes are preserved. The source manifest records the original card blob, current exact Oracle,
MH3 #164 first-printing metadata, current Pauper legality and effective September 25 rules source.
Reusable code is transferred once; experimental outcomes are not transferred.

Bestow declares an explicit alternative cost and a separate legal action. The chosen spell is an
Enchantment — Aura with enchant creature before cost, targeting and filtered cast-prohibition
checks. Original supertypes are preserved. Normal XG casting and Bestow XGG casting have separate
affordability bounds. The handler validates and actually pays the selected alternative cost, and
restricted mana sees the Aura characteristics. The inherited implementation validated Bestow but
could execute the ordinary printed payment; this defect is corrected rather than encoded into an
expected result.

If the target becomes illegal while the spell is on the stack, the bestowed effect ends and the
spell resolves as its creature. Losing the bestowed effect does not erase the actual alternative
cost, X or mana-spent history. When the host leaves, the Aura becomes a creature with its existing
counters. When the Hydra itself leaves stack or battlefield, a canonical cleanup helper restores
its printed type and removes transient Bestow state, including on counter-to-graveyard,
counter-to-hand, counter-to-exile and non-counter exile paths. Existing Prototype cleanup remains
intact. Card and engine state serialization retain the Bestow marker while it is applicable.

The Nyxborn suite has fifteen deterministic cases: ordinary X casting; actual Bestow cost and
host bonus; illegal-target creature resolution with cast provenance; protection from creatures;
host detachment; bouncing the Aura itself and normal recast; distinct X bounds and rejection of
underpayment; creature-restricted mana; four real counter/return/exile paths; serialized stack and
permanent state; and both creature-only and noncreature-only cast prohibitions. The prohibition
fixtures use actual static restriction execution, not a mocked legality result. Real spells resolve
the removal and counter scenarios. These are excluded regression fixtures, not official seeds.

Copying, phasing and casting through permissions from other zones are not qualified by these
cases. No broad Bestow or universal rules-conformance claim is made. The exact admitted Pest/Spy
main lists do not contain those interactions; any future deck needing them must qualify them
before sampled play.

## Evidence and acceptance boundary

The dedicated workflow checks out the exact PR source head. It independently retains each
stage's XML: component registration, SDK card serialization, card linting, all fifteen Hydra
cases, existing Prototype and Cascade scenarios, the historical Batch B monotonic support gate,
the new strict main registry closure, frozen admission, and strict corpus snapshots. Snapshot
comparison never uses update or blessing flags; generated actual output is preserved on mismatch.
Only the new MH3 Hydra block is added, with all prior card blocks byte-identical.

The new C registry gate requires all nineteen frozen main identities / sixty cards to resolve
and the precise parameterized Bestow cost to be present. It records the current sideboard queue
without admitting a boarding plan. Batch B's current-code regression becomes monotonic: its four
cards must remain resolved and no new unresolved identity may appear outside its historical queue.
That prospective successor assertion does not rewrite any earlier accepted or rejected artifact.

Acceptance requires the exact candidate source to pass required CI and an independent artifact
and snapshot audit. Only then can the main-card queue move from the accepted prior checkpoint to
zero. Exact opponent policy, interaction, deterministic replay, guarded authorization, fresh seeds
and later postboard stages remain required by the existing five-axis protocol. A green registry
report alone cannot advance official gameplay.

## Qualification checkpoint

Batch B was independently accepted and merged in PR #148 as
`c6953354b1b799fe4506eb9e44ff25523a132d8e`, from exact source
`7a3f1429c027925c30ed7c1e329c3879e674574a`. Dedicated run **36087086060**, artifact
**10843959361**, ZIP SHA-256
`1c670bb133fc26e5ddbbabde6a7cfc3d2b32cecde8e23a1b3b08dae23de50d9f`, retained 362 passing
JUnit cases with no failures/skips, including 338 strict snapshot cases. All five required
workflows passed. The accepted main queue is therefore exactly Nyxborn Hydra ×2 before C.

Initial C run **36088119316** at `158d4ce4a02ca65815f784d58c1ea2528979adb7` correctly
failed when compiling the new 2024 scenario's direct JSON import: that shard consumes the engine
fixtures but does not expose kotlinx.serialization on its own test compile classpath. The test now
uses the existing `SerializationTestSupport.roundTrip` bridge already provided for this purpose.
Both full-state equality and all Bestow counter/attachment/type assertions remain required; no
new dependency or production change is introduced. The rejected artifact **10844708612** has ZIP
SHA-256 `fc844601410767b442ba5add048f329d34e176678d9fb0391a12cc9b3506ca31`.
No C coverage or gameplay result is accepted from this failed compilation.
