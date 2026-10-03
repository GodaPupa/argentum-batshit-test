# Izzet destruction and protection prerequisites — isolated diagnostic

Base source: `ae209a6f0897f79c5379257091bd36663e0fcf00` (51 registered / 4 missing).
No production or canonical card definitions change. Neither test is an exact-card qualification.
The unchanged Position-1 inventory is deliberately not rerun. Official deltas: 0 allocations,
0 claims, 0 games, 0 outcomes. Workflow-only children must never be promoted.

## Snake Umbra: destruction event and resumable replacement choice

Authoritative current Oracle and rulings were retrieved from Scryfall on 2026-10-03:

- <https://api.scryfall.com/cards/named?exact=Snake%20Umbra&set=roe>
- <https://api.scryfall.com/cards/4a011820-80c6-484f-84be-f07f0e45f1a8/rulings>

The draw ability triggers on **any damage to an opponent of the enchanted creature's
controller**, including noncombat damage. A combat-only trigger would be incomplete.
The modern name is umbra armor; the name change does not alter its behavior.

The destruction replacement must cover effect destruction and lethal/deathtouch destruction,
clear marked damage, preserve combat/tapped status, ignore cannot-regenerate restrictions,
and destroy the chosen Aura. Multiple armor Auras require the enchanted permanent's controller
to choose one. Simultaneous destruction of Aura and host must still save the host. Sacrifice,
zero toughness, and other nondestruction moves are not replaced.

Precise live seam:

- `ZoneMovementUtils.destroyPermanent` directly consumes shield counters, then regeneration,
  then remove-damage shields, then moves to graveyard. It never enters `ReplacementEffectProcessor`.
- `LethalDamageCheck` duplicates regeneration/remove-damage selection in its SBA loop.
- `PendingGameEvent` has no destruction domain. The existing generic processor already supports
  affected-player choice, replacement identities, and suspended continuations, but destruction
  callers have not been routed through it.
- Batch `MoveCollectionExecutor` destruction iterates the collection. A correct repair must
  preserve the remaining batch and original simultaneous-event replacement-source snapshot
  through a choice; reading only a mutated battlefield loses an Aura destroyed earlier in a wipe.

`DestructionReplacementChoiceBoundaryTest` uses existing regeneration and remove-damage shields,
whose different observable outcomes make ordering meaningful. Three controls exercise lone
replacement behavior and cannot-regenerate handling. Three required-behavior probes request
player choice through direct effect, lethal SBA, and batch destruction. They are prerequisite
probes, not simulated Umbra mechanics. A helper that automatically picks the first Aura would
leave precisely this defect in place.

## Benevolent Blessing: effect provenance plus attachment-start snapshot

Oracle: <https://api.scryfall.com/cards/named?exact=Benevolent%20Blessing&set=cmr>

The existing preflight remains authoritative; it is not replaced by these probes. The new
inspection identifies why a small global attachment exemption would be incorrect:

- `EffectApplicator` merges fixed, chosen, and controlled-color grants into the same keyword set.
  `ProjectedValues` exposes no per-protection-instance identity or exemption policy.
- `UnattachedAurasCheck.hostProtectedFromAttachmentColor` checks the attachment's printed statics,
  not the active protection instance. It suppresses every matching color for a fixed grant and
  suppresses all protection-removal for a dynamic grant. A separate same-color protection
  instance therefore cannot remove the granting Aura, although it should.
- `AttachedToComponent` stores only the host entity id. It has no record of which attachments
  were present, their controllers, or their object references when a specific protection effect
  began applying. A future exception must capture that set and bind it to that effect instance;
  detach/reattach or leave/return must not turn it into a blanket exemption.

`ProtectionAttachmentProvenanceBoundaryTest` covers self-retention, unrelated Aura removal,
opponent attachments, later controlled attachments, and independent same-color protection.
Four tests are existing-behavior controls. Two expose existing provenance collisions
(independent floating protection; distinct granting Auras). Retaining other preexisting
controlled Auras and Equipment cannot be encoded with the current AST; these are source-level
API boundaries, not runtime failures asserted against a Ward fixture. The requested future
retention matrix must use an explicit new policy and must not change ordinary Ward semantics.
No synthetic fixture claims to implement Benevolent Blessing.

Required generalized work is therefore per-instance protection provenance, an explicit
source-scoped attachment-retention policy, and a captured attachment/object-reference set at
that policy's activation. Existing targeting restrictions must continue to prohibit new
attachments. Only after that shared contract passes should the exact card be authored.

## Evidence contract

The isolated workflow binds source parent/tree/blob hashes, runs only the two classes (6 + 6
cases), preserves actual XML, failures, Gradle exit status and SHA-256 manifest, and fails if any
required behavior fails. No failed probe is waived to produce a green admission signal.
