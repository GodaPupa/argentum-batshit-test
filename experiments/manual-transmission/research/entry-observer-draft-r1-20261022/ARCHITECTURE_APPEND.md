

### Entry observation at instruction boundaries (experimental, unqualified)

The effect registry and prepared-entry committer observe newly emitted battlefield-entry causes after the complete instruction. `ZoneChangeEvent.observedEntryTriggers` is null for an uncaptured cause; a present empty list records that observation found no triggers. The first entry of a simultaneous group carries its pending triggers and the remaining entries carry empty lists. An enclosing composite preserves child observations. Capture does not stack an ability or ask for targets, ordering, or priority.

The ordinary detector merges observed groups with intervening uncaptured causes in event order, retains source-object identities and departure LKI, applies cross-group caps per source visit, then sorts by APNAP. Initial conditions, suppression, and doubling are evaluated at the observation boundary; intervening-if resolution checks remain on the stacked ability. An old source's trigger must not mark the once-per-turn or lifetime tracker on a returned permanent. Raw observations are trusted engine state: the explicit ClientEvent projection does not expose this payload. The nullable field omits its default to preserve historical event bytes.

This experimental seam is under preregistered qualification. It does not assert complete event-time handling for non-entry events, every cast/land/Aura producer, arbitrary externally supplied legacy AbilityRegistry instances, or complete multiplayer and competitive admission. Original saved-state fixtures and all previous assertions remain gates.
