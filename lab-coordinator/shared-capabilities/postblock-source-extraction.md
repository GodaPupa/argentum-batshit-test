# Shared post-block declaration and source-history component

Status: **source extraction prepared; receiving tests not executed; no gameplay admission**.

This component completes the block-declaration round before granting priority. It retains
simultaneous triggered abilities through player-selected ordering, nested mana payment,
target decisions, source departure, and a chooser's concession. Damage uses the original
object's live characteristics while it remains present and its exact departure snapshot
afterward. It composes the existing priority processor, pending-trigger records, and
option decision; the only new continuation carries the actual simultaneous trigger batch.

## Exact source and bounded evidence

The source is published Ferocity qualification head
`d50e66dc12a58e1276b56bdf9ce78daecda51e1a`, tree
`9c3394e67acaa11e847d9994e969de6b068d2b5f`. Its 198 owned files matched the
published source-freeze digest `8cb6896b50bd0a6e6e83bca75a5d010cf68d94b2eb9e3c59f7991857b5ce7f58`.
The separately reviewed receiving base is local merge
`7263a5ed391a964e2f7d640e501cdf62442c37c8`, tree
`15c48d3664c12134b6804c1cc091df22d487d61e`, combining main
`9ca83110f5907e66a0c289f8205ed1e24575535c` and accepted Pest receipt
`4a363dccf13941ee7a1402063738d98bacc9e3e4`. That merge has not itself qualified
this new component.

[Original run 36249219082](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36249219082)
produced 234 passing engine cases and 12 passing server cases on the exact published
source. Both downloaded artifacts were independently reconciled against their actual XML
cases, source receipts, and ZIP digests. The same run's gym job had 174 passing cases and
two Red failures out of 176. Those failures remain failures; the original run did not accept
the entire Ferocity runtime or admit its first cell. All artifact IDs, digests, and suite
counts are in [the extraction manifest](postblock-source-extraction.json).

The receiving patch contains 36 production files. Thirty-one are whole-file equivalents
of the published source; four use only the relevant hunks, and one changes an inherited
project-specific scope comment without changing runtime behavior. The manifest binds both
the published and receiving bytes, the receiving base, and the selected hunks.

The three-way comparison used external ancestor
`2a99c52bcfb869ecc0c32443c2b77765abde4b65`, receiving main, and published source.
Main had removed an older unscoped `sourceLastKnown` fallback in `DamageUtils`,
`DealDamageExecutor`, and `ZoneTransitionService`. This extraction preserves that removal:
the replacement selects `sourceSnapshot` only after checking the exact `ObjectRef` and
captures departure data on pending abilities. It never restores the previous
activation-time or returned-object fallback. The new identity and departure fixtures are
required to distinguish those behaviors.

## Required receiving gate

The push-only shared workflow must execute all **115 engine cases and 12 server cases**
from 17 exact original test files, with no failed, errored, or skipped case. This includes
all 16 post-block cases, particularly PB13 and PB15, which resolve deathtouch/lifelink
damage after token departure and owner concession. It also retains declaration permission,
multidefender and shared-team combat, activation priority, nested payment, optional-effect
timing, typed trigger ordering, concession, source identity, and target-pause serialization
fixtures. The changed older tests correct their former declaration-baton and premature
optional-effect assumptions; they preserve their intended assertions under the actual
timing rules.

The workflow checks exact clean source before execution, requires actual per-class XML
counts afterward, rechecks source hashes and HEAD, and always retains logs and XML when
a gate fails. There is no snapshot generation, source push, gameplay driver, or entropy
allocation in this workflow. Full integration and independent artifact review remain
required before shared component acceptance.

The original 234-case engine block also tested Gift, paid flashback exits, Ferocity's card
definition, and revised combat-damage assignment. Those are separate source capabilities
and are not imported or claimed by this extraction. `StackResolver` remains unchanged:
its source diff contains no required snapshot bridge. `EffectContext` supplies that
bridge. The mana solver, restricted-X spending, and cost enumerator remain under their
separate canonical owners. These source boundaries do not exclude any reachable
interaction from a receiving project's eventual gameplay qualification.

## Cross-layer trace and remaining receiving obligations

The engine handler and legal-action enumerator share the same declaration query. The
server routes that query to the proper connected defender, including shared-turn
teammates. Existing option decisions carry trigger-order choices through the existing
client option UI; no new client decision or event type is introduced. The new internal
continuation is registered in the engine serializer and resumer registry. Departure
fields use the existing snapshot and damage event types. State projection remains the
source for battlefield characteristics; snapshots retain the departure object identity.

The architecture reference documents the declaration boundary and typed ordering. No
SDK card vocabulary changes, so card definitions, the SDK catalog, Assay, and the card
generator receive no new type. The implementation reuses cached projected state and
immutable state copies; it adds no network or file operations to the engine.

The rule checks used the September 25, 2026 text linked by the
[official Comprehensive Rules page](https://magic.wizards.com/en/rules): active-player
priority after declarations, completion of state-based actions before trigger placement,
controller-selected simultaneous ordering, optional choices at resolution, mana abilities
inside payment, and an ability's independent source identity after departure.

Production actor relocation is separate. Published actor observation still has a known
declaration-baton `hasPriority` gap owned by the external canonical source author. No actor
adapter is imported here, and these engine/server fixtures do not establish lawful pilots.
Every receiving project must qualify its exact reachable interaction and decision set,
source compatibility, replay, policies, and admission rules before a game. Other multiplayer
departure choices are unchanged and must be assessed if reachable by that receiving scope.

Source review also identified an unqualified observer-token path: an already captured
trigger can refer to a different event object, then lose its own token source during the
post-cast state-based-action pass. `ZoneTransitionService` stamps block queues and stack
abilities, while `CastPriorityProcessor` retains its local pending batch; no writer was
identified that carries that token's own departure snapshot into this batch before token
cleanup. This is a concrete source-path limitation, not an executed failure or a finding
that a frozen deck can generate it. The 127 selected fixtures do not prove this shape.
Receiving reachability must be checked explicitly; if reachable, it blocks gameplay until
the canonical source repairs and qualifies it. Pilot avoidance does not remove that gate.

Official allocations initialized: **0**. Official outcomes exposed: **0**.
