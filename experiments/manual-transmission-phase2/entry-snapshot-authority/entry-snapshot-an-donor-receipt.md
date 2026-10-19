# v0.9 Position 1 — Engine Coverage Batch AN Gate

This seed-free successor follows independently accepted Batch AM: ten unresolved Veteran identities,
zero unresolved Izzet identities. It qualifies the entry-observation and attachment capabilities
required by Colossal Dreadmask. This is one coherent capability batch; no other queued card is
represented as supported by these changes.

## Frozen boundaries

The v0.7 control remains byte-identical at SHA-256
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`, and the frozen Veteran file
remains `c3ab6ee8e37a78219e7bbb7c6a75c634d78c1eb4965ab0db3abd5d853398b0be`.
Official counters remain **12 generated, 0 consumed, 0 initialized, 0 completed/12, 0 outcome
exposure/12**. No official seed is retrieved. Position 1 is still conditional on full readiness;
positions 2–12 remain unauthorized. KEEP_V07 / NO CARD CHANGES. PR 15 remains draft and unmerged.

## Capability and exact card boundary

[Colossal Dreadmask, MH3 #148](https://scryfall.com/card/mh3/148/colossal-dreadmask) composes existing
CreateToken, one-of-created-token selection, and AttachEquipment effects, then grants +6/+6 and
trample to its equipped creature. The equip cost is exactly {3}{G}{G}. Its four published rulings
were retrieved before the preserved AM draft. Living weapon is CR 702.92a; simultaneous entry
observation is CR 603.6a and 603.10; object identity is CR 400.7; zero-toughness cleanup is CR 704.5f.
These were checked against the official Comprehensive Rules effective September 25, 2026, available
from [Wizards' rules page](https://magic.wizards.com/en/rules).

A new optional entrySnapshot is appended to ZoneChangeEvent, preserving existing positional fields.
It is separate from departure-only lastKnown and from resolution-time power. The effect registry
captures projected characteristics after an entire instruction's simultaneous entry group, including
existing static effects and entry counters, before the following instruction. An enclosing Composite
preserves child snapshots. Capture requires the event's current newObject reference and never
fabricates a historical snapshot from an object that has already changed visits.

The entry record contains the projected view and minimal captured mana/layout facts, never an entire
CardComponent or card script. Face-down identity, original set and layout are masked. Card filters
with captured entry facts use the same instant for type, color, subtype, keywords, P/T, name, mana,
layout and token identity; unsupported contextual predicates remain unknown under Not/And/Or.
Controller matching uses the captured controller. Departure filters and dynamic resolution reads
retain their existing paths. The public client event DTO omits the internal snapshot.

This is a scoped effect-entry qualification. Direct initializer/cast producers outside the registry,
relational entry predicates that require a whole historical battlefield, and unqualified event-ledger
consumers are not promoted by it. No full-game readiness guard is removed. Existing state-predicate
paths are unchanged; this gate does not claim their general historical-state fidelity.

AttachEquipment validates its actual sourceId against the captured source object reference and current
battlefield membership. It does not reinterpret sourceId as an iteration-bound Self. A departed or
returned source and an unavailable destination are successful no-ops, preserving any existing legal
attachment and emitting no new attachment event.

## Required deterministic qualification

- Eleven entry scenarios qualify child snapshot retention before a pump, current resolution-time
  power, whole simultaneous token groups with static abilities and counters, existing anthem effects,
  serialization, removed-token observation, all supported scalar P/T relations, later color/type
  changes, unknown propagation, copied mana/base power, face-down masking/client DTOs, missing
  transition identity and leave-return identity rejection.
- Six attachment scenarios qualify the current source, source departure, new battlefield visit,
  legacy off-battlefield source, iteration Self binding and unavailable destination.
- Five exact Dreadmask scenarios qualify living weapon before state-based actions, actual 0/0 entry
  observation, a preexisting anthem's 1/1 entry, doubled Germ selection, full equip payment/legal
  target/movement and removal of the Equipment while its trigger is pending.
- Existing token-departure, permanent-departure, additional-token-batch and token-creation-observer
  regressions must pass, together with the accepted fixed-type and scoped PDH tests.
- Expected post-implementation unresolved count is exactly **9**, all Veteran: Benevolent Blessing,
  Cho-Manno's Blessing, Forge of Heroes, Nyxborn Hydra, Opal Palace, Prismatic Strands, Ram Through,
  Snake Umbra and Vines of Vastwood. All five full-readiness guards remain closed.
- Snapshot generation may only add Colossal Dreadmask to existing MH3.json, with every old record
  identical. Every canonical snapshot must then pass with updating disabled.

## Artifact and guarded integration

The successful artifact has exactly a manifest and twelve transcripts: entry, attachment, Dreadmask,
permanent-departure, token-departure, additional-token, token-creation observer, fixed-type, PDH,
readiness, snapshot generation and snapshot verification. The manifest binds the source SHA, snapshot
commit/tree, frozen files, preceding acceptance, this gate/workflow, every new or changed implementation
and qualification file, queue findings, exact MH3 snapshot, transcripts and zero official counters.

The workflow checks out the trigger SHA, executes the complete qualification, verifies the exact
snapshot addition, and requires the freshly fetched live branch to equal the trigger SHA before its
nonforce snapshot push. Concurrent advancement fails closed. Shared capabilities and the card are
isolated in source commits under the existing validation PR. Independent archive audit is required
before accepting **10 → 9**. A successful workflow is seed-free validation, never an official game.

## Remaining finite-queue findings

The [prospective queue findings](capability-drafts/remaining-queue-findings.md) record two concrete
existing damage defects relevant to Ram Through and Prismatic Strands, including exact source
locations and required distinguishing regressions. They are not fixes or accepted runtime results.
The previously preserved Dreadmask draft remains historical; compiled source and this gate govern AN.

## First validation diagnostic and fixture correction

Run [36088494001](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36088494001), job
`107925559245`, failed one of eleven entry scenarios at source `6716152aa7fcd1932051b889f2e354a513d7cc7e`.
Kotlin compiled and the other ten entry scenarios passed. The failing copy fixture expected a
five-power copy but used the legacy `overridePower` rider alone. `CreateTokenCopyOfTargetEffect`
explicitly retains that legacy rider only when both P/T values are supplied; the supported
half-specified override is `CopyExceptions(powerOverride = 5)`. The fixture is corrected to that
existing API, preserving its expected copiable power 5, projected power 6 with anthem, mana value 3,
and dynamic predicate assertions. Production copying, expected outcomes and safeguard code are not
changed. Attachment/Dreadmask and later stages were skipped by fail-closed CI and remain unqualified.
No official game or seed was used; full AN qualification and independent archive audit are required.

## Independent accepted-artifact audit

**Batch AN is accepted.** Repair run
[36089240215](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36089240215), job
`107927830600`, completed the complete gate at source
`636827cc11dd1f9c93925f3b814aeeb11d42b847`. Its guarded snapshot integration is commit
`882117e4f37cee3a6a1d5c1b393728207e587104`, tree `33f2dca36f795a22d19774e5fa1430bf43b841f3`.
Artifact `10845471915` contains 336,883 bytes; the independently downloaded ZIP has SHA-256
`b9ea87ed0739ba14f65492e6ce4fa0d295f194846c6798628e57e3aa4ebf3a52`, matching GitHub's recorded digest.

The independent archive audit verified exactly thirteen files, all thirty-seven digest bindings,
all twelve successful validation transcripts, the full manifest contract, exact source/parent/tree
identity, and the sole snapshot change: adding Colossal Dreadmask while preserving every previous
MH3 record. All entry, attachment, exact Dreadmask and retained regression stages passed on the
repaired fixture. No production copying rule, expected test result or execution safeguard was waived.
The first failed run remains a diagnostic record, not accepted evidence or a deck loss.

Accepted unresolved registry identities decrease **10 → 9**, all Veteran: Benevolent Blessing,
Cho-Manno's Blessing, Forge of Heroes, Nyxborn Hydra, Opal Palace, Prismatic Strands, Ram Through,
Snake Umbra and Vines of Vastwood. The exact frozen deck hashes and all five full-game readiness
blocks remain intact. Official counters are still **12 generated, 0 consumed, 0 initialized,
0 completed/12, 0 outcome exposure/12**. This acceptance permits the compatible seed-free AO
successor; it does not open Position 1 or promote a hardware change.
