# Historical spell-copy state

Base: 58e937fdc3776013e572d34e1c261d8799d84a2c. No canonical card or inventory change.

`SpellCopySnapshot` retains only CardComponent, SpellOnStackComponent, TargetsComponent,
original ObjectRef, and the cannot-be-copied restriction. It excludes arbitrary entity
components such as tapped/targeted/runtime markers. Owner and controller are replaced
when the new copy is created. The entire structured spell-choice component is retained,
so modes, X, optional-cost choices, splice choices and target identities survive.

GameState records that snapshot at stack removal, including normal spell finalization,
counter variants and generic zone transitions. Entries are keyed by globally unique
object generation and retained for the game; no turn-based expiry can erase a delayed
copy's source. This adds one immutable record per departed spell. Live source reads
remain authoritative until departure, so changes before departure are not frozen at cast.

Storm's reusable copy executor resolves the exact captured source reference, then carries
its snapshot through flat and modal target-choice continuations. Legacy callers without
references may use the most recent departure; captured missing references fail closed.
A recast of the same card cannot satisfy an earlier captured copy source.

Target legality uses the existing TargetFinder against a temporary prospective-copy
entity with the copied characteristics and the new controller. This view is discarded;
it neither inserts a real copy nor advances the actual state allocator. Actual copies
still use StackResolver.putSpellCopy and emit ordinary copy/targeting events, never cast
or payment events. Existing inherited-target identity behavior is preserved.

Focused tests cover actual countering, serialization, repeated cast identity, ownership,
last departure choices, cannot-be-copied, and serialized flat/modal target choices.
Existing copy and inherited-target regressions accompany qualification. This primitive
does not by itself implement replicate payment or admit Lose Focus.

Official counters remain 0 allocations / 0 claims / 0 games / 0 outcomes.
