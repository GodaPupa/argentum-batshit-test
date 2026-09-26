# v0.9 Position 1 — Engine Coverage Batch AM Gate

This is the seed-free successor to independently accepted Batch AL. The accepted finite queue is
11 unresolved Veteran identities and zero Izzet identities. This gate qualifies Cosmic Hunger,
one canonical parameterized card-type predicate, and focused PDH mechanics fixtures in one run.
It does not execute the frozen sample or remove any full-game readiness guard.

## Frozen boundaries

The exact v0.7 control file SHA-256 remains
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`;
the frozen Veteran Beastrider file remains
`c3ab6ee8e37a78219e7bbb7c6a75c634d78c1eb4965ab0db3abd5d853398b0be`.
No official seed is retrieved or consumed. No official game is initialized, completed, or exposed.
The existing Position-1 authority remains conditional on complete readiness; positions 2–12
remain unauthorized. KEEP_V07 / NO CARD CHANGES.

## Scope and prospectively excluded capability

[Cosmic Hunger, MOM #182](https://scryfall.com/card/mom/182/cosmic-hunger) uses the controlled
creature as the actual damage source and reads its power at resolution. Its distinct second
target may be any creature, planeswalker, or battle, including a friendly permanent. Either
illegal target prevents damage. Current Oracle text, first-print metadata and the published
2023-04-14 ruling were retrieved before implementation.

`CardPredicate.HasCardType(cardType: CardType)` is the single new SDK vocabulary. It composes
with ordinary filters and uses projected battlefield types, snapshot types, or cast-record types
according to context. Every existing predicate consumer receives the same fixed-type meaning.
Serialization keeps `cardType` separate from the polymorphic `type` discriminator. The mtgish
library-selection emitter maps Battle to this parameterized predicate; unknown types still
scaffold. Existing card predicates, the event schema, and shared attachment behavior are unchanged.
The SDK catalog is updated. There is no new event, decision, action, keyword, UI flow, or named
pipeline variable; existing legal actions and target handling consume the filter.

Colossal Dreadmask was prospectively excluded before this gate and remains unresolved. Its existing
composition cannot yet qualify entry-time 0/0 observation before attachment. The uncompiled
[capability draft](capability-drafts/colossal-dreadmask/README.md) preserves the exact card/test work,
a separately reviewed but unapplied source-identity attachment patch, and the required successor
regressions. No departure-only `lastKnown` field is repurposed, and no draft is counted as support.

## Required deterministic qualification

1. SDK fixed-type predicates serialize and round-trip for every CardType, including a composed
   creature/planeswalker/battle alternative. Engine tests distinguish all cast-record types,
   reject face-down records, use actual added projected types, and preserve unknown snapshot
   semantics under negation. Existing PredicateEvaluatorRecordTest also passes.
2. The mtgish emitter qualifies Battle selection and imports, while rejecting an unknown type.
3. Ten Cosmic scenarios exercise actual damage to friendly and opposing creatures, planeswalkers,
   and Battles, with loyalty/defense counters and source lifelink. They reject duplicate targets,
   an uncontrolled source, players and unsupported permanents; evaluate a response-time power
   change; and deal no damage if either target becomes illegal.
4. PDH mechanics fixtures use the exact Izzet Guildmage and Veteran Beastrider definitions with
   99 basic lands per seat, explicitly outside the frozen decks and official sample. They check
   both nonlegendary commanders in separate command zones, 30 starting life overriding the ordinary
   player default, legal command-zone casting, printed mana costs, owner return/decline choices,
   two additional mana on recast, and serialization/replay of the exact paid recast action.
   Actual Guildmage combat distinguishes cumulative 15 from 16 damage while the defender still has
   positive life; noncombat commander-source damage does not add commander combat damage.
   These fixtures do not establish complete frozen-deck initialization, executable pilots,
   unrestricted replay, or Phase-29 event-ledger readiness. All five current guards stay closed.
   The 30-life/16-damage settings follow the frozen program and the current
   [PDH Home Base rules](https://pdhhomebase.com/rules/). Commander tax/owner choice/source object
   identity were checked against the official Comprehensive Rules effective September 25, 2026,
   available from [Wizards' rules page](https://magic.wizards.com/en/rules).
5. Expected post-implementation unresolved count is exactly **10**, all Veteran. Colossal Dreadmask,
   Benevolent Blessing, Cho-Manno’s Blessing, Forge of Heroes, Nyxborn Hydra, Opal Palace,
   Prismatic Strands, Ram Through, Snake Umbra, and Vines of Vastwood remain unresolved.
6. Snapshot generation may add only Cosmic Hunger to existing MOM.json. Every existing card object
   must remain identical. All canonical snapshots then pass with updating disabled.

## Artifact and integration contract

The complete success artifact has exactly a manifest plus nine transcripts: SDK serialization,
fixed-type engine tests, existing record regressions, emitter, Cosmic, PDH mechanics, exact registry
readiness, snapshot generation, and snapshot verification. The manifest binds the trigger source SHA,
resulting snapshot commit/tree, frozen files, gate/workflow, all new or changed implementation and
qualification files, MOM snapshot, transcripts, and zero official execution counters.

The workflow checks out the trigger SHA. It integrates only MOM.json, after all required tests and
semantic snapshot checks. Before nonforce push it fetches the live branch and requires exact equality
with the trigger SHA; concurrent advancement fails closed without automatic rebase. SDK vocabulary,
Cosmic definition/test, and program qualification material are isolated by source commits under the
existing draft validation-only PR. Independent archive audit is required before **11 → 10** acceptance.

Official counters remain **12 generated, 0 consumed, 0 initialized, 0 completed/12, 0 outcome
exposure/12**. This gate cannot authorize official gameplay.

## Independent acceptance audit — September 25, 2026

**Batch AM is accepted.** Validation run [36086281992](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36086281992),
job `107918802939`, completed successfully at source `79e21463d2e7b0760a70f9a994073abd6a076b4a`.
The guarded snapshot integration produced `203a5cb6f278443b121f90eac474cbc879755677`, tree
`41bed3c5d5f30aa73fff72c7ecd14c4b658523e8`. Artifact `10843892659` was independently downloaded:
306,457 bytes, archive SHA-256 `964accae4f06bf615f3f72d79fa0024c429d2be9c5979faf0e652900ac75645c`.

The audit verified the exact ten-file contract, all 38 manifest digests against source/snapshot/archive
bytes, nine successful transcripts, frozen identities and zero official execution counters. The only
snapshot change was the new Cosmic Hunger record in MOM.json; every preceding record was unchanged.
The accepted unresolved count is **11 → 10**, all Veteran, with zero Izzet identities. Scoped PDH
fixtures passed; all five full-readiness guards remain closed. This was seed-free validation, with
**0/12 initialized, completed, or exposed** official games. Position 1 remains unexecuted.
