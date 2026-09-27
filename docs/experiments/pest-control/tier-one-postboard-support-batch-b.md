# Pest Control postboard support batch B

This is a three-card deterministic support batch for the existing five-axis program. It introduces
no deck change, seed generation, boarding policy or official gameplay. The initial source is stacked
on postboard batch A at `8d401bda54a39ad75814b89b04c2bd42b4324843`. The first executable gate also
reconciles A/accepted Spy B4 integration `1cf29885757fae24aac05cfe2d1097e5088e5375`; the same
accepted Fiend support is credited explicitly in both current inventories. A's current source bindings advance explicitly with these three
identities, while A's immutable source commits and earlier artifacts remain unchanged.

## Exact mechanics and qualification

| Card | Canonical printing | Required behavior | New scenarios |
|---|---|---|---:|
| Pyroblast | ICE 213 | Unrestricted spell/permanent targeting, chosen mode fixed at cast, blue condition at resolution | 8 |
| Hydroblast | ICE 72 | Unrestricted spell/permanent targeting, chosen mode fixed at cast, red condition at resolution | 8 |
| Gut Shot | NPH 86 | One red or two life pays the Phyrexian symbol; one damage to creature, player, planeswalker or battle | 12 |

The blasts reuse modal targeting, `EntityMatches` through `Conditions.TargetMatchesFilter`, and
conditional counter/destruction effects. Their tests cast actual matching and nonmatching spells,
target both sides' permanents, change color with Fylamarid in response, reject wrong-mode target
categories without mutation, and remove a target before resolution. They impose no color restriction
on legal target generation.

Gut Shot exercises Phyrexian mana payment and the canonical any-target damage paths. Its tests separately
exercise red mana and two-life payment, insufficient and exactly two life, unchanged state after an
illegal cast, all four legal target classes, and loss of the target without refunding the life cost.
The two-life lethal-cost case uses the engine's actual state-based loss; no winner is assigned by the
fixture. These are excluded regression fixtures, not matchup outcomes.

The first compiled run exposed an illegal ordinary-Forest target and delayed state-based loss
after paying from two life to zero. The target repair checks projected creature/planeswalker/battle
types at cast and resolution and includes battles in legal-action enumeration. Additional scenarios
reject a noncreature artifact, animate an actual Mishra's Factory and damage it legally, and resolve
Imprisoned in the Moon above Gut Shot using Borne Upon a Wind's real timing permission. The latter
retains the target's battlefield object while making it only a land, so resolution must drop it.
The exact-two-life assertion remains unchanged pending the separately qualified post-cast repair.

Canonical Scryfall payloads, ascending printing lists, current Oracle rulings, source digests, common
printings and current Pauper legality are retained in the [source manifest](tier-one-postboard-support-batch-b-sources.json)
and its nine raw payloads. All three canonical images returned HTTP 200. Definitions use their real
earliest printing, including Gut Shot's uncommon NPH printing and later common admission.

## Evidence gate

The dedicated workflow checks exact source bindings and retains each card's XML separately, the full
registry inventory, current and historical readiness regressions, strict snapshots and lint. The
first run deliberately leaves the committed ICE/NPH goldens unchanged and preserves compiled actual
output for review. It does not enable any snapshot blessing flag. Only the two blast trees in ICE and
the Gut Shot tree in NPH may be added; every previous block must remain byte-identical. The strict
snapshot failure must be resolved and rerun before acceptance.

The three cards cover twelve physical sideboard slots across the fixed Mono-Red, Mono-Blue and
Monster Tron opponents. Remaining counts come from the exact compiled artifact, including accepted
predecessor support at its source; they are not assumed from the original inventory. The prospective
current Mono-Blue map removes only the named qualified additions. Its historical map/status and
disabled execution guards remain exact, and missing Annul, Mystic, Gut Shot or Hydroblast must fail.

The separately discovered post-resolution priority defect remains a mandatory gameplay-readiness
repair. These tests do not bless that defect or change its canonical implementation. Complete boarding
plans, exact postboard decks, policy qualification, authorization and one-shot evidence contracts
remain prerequisites to the program's bounded postboard samples.

## Current disposition

`REJECTED_PENDING_BATTLE_PREDICATE_AND_POST_CAST_SBA_REPAIR`. Independent source review passed the initial six
card and scenario files. No local Kotlin runtime pass is claimed. All official counters for this batch
are zero, and no existing frozen deck, original Monster vector, consumed claim or historical artifact
has changed.

The initial PR commit `eaa43a4f6f0daacab12e75871b7b2a27343da1b1` conflicted with concurrent
A integration before any workflow ran. The two-parent successor retains both changes, updates only
current source bindings/queues, and requires the integrated A commit as an ancestor. No failed or
missing workflow is represented as a card result.

## First artifact audit and repair boundary

Run [36089250333](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36089250333),
source `059bf9f548813201762e8579fe66018d02494526`, executed **379 cases: 375 passed, four failed**,
with no errors or skips. All 22 original source bindings match the immutable source. The failures
are the two Gut Shot engine defects above and strict ICE/NPH snapshots awaiting review. The
[failure audit](tier-one-postboard-support-batch-b-failure-audit.json) retains all ten raw XML suites,
stage exit statuses, provenance, source manifest and both current coverage reports. The compiled
8-identity/17-slot queue is prospective until this batch is accepted.

Artifact `10845845066` has ZIP SHA-256
`e0dfaa26360ec85e503558befe1f2cc4610ca212288e98adb572a8fe1de31dc7`.
The snapshot review admits exactly Hydroblast and Pyroblast in ICE and Gut Shot in NPH. All 86
previous ICE blocks and all eight previous NPH blocks are byte-identical. Their serialized modes,
unrestricted target requirements, resolution color conditions, Phyrexian cost, one-damage amount,
card metadata and Oracle text were checked. Only these two compiled files are copied; strict CI
must pass on the successor before runtime acceptance. No snapshot blessing flag is enabled.

Compatibility run `36089250327` failed only those same snapshot additions. Full CI `36089250546`
also reported the same two Gut Shot assertions and a separate `FreeForAllLobbyTest` premade-AI
deck-admission assertion. That server failure lacks raw XML in the old CI artifact contract, so
its cause remains unclassified. The successor gate retains this exact class's XML without changing
its assertions or timeouts. A green source check does not waive any of these runtime requirements.

These repairs do not amend the original Monster attempt or qualify an engine for any official
block. Post-resolution priority, post-cast state-based actions, exact pilots and each program's
frozen engine admission remain separate requirements.

## Accepted Spy C integration

While the targeting repair was published, main admitted Spy C at merge
`1c8bc618e8b3ca0b3b154e2ef3c87ca8ff779698` (source `1ad4079b8fbdbc79ec5bb88dad343aad7d417f96`,
run `36091157502`, artifact `10846221712`, 435 passing JUnit cases plus three source-verifier tests).
This successor preserves that canonical Bestow implementation and removes Nyxborn Hydra from
current sideboard gaps. The prospective combined queue is seven identities and sixteen slots;
the accepted main checkpoint remains ten identities and twenty-eight slots until postboard B is
qualified. Only the two current A manifest/coverage conflicts were reconciled. Historical source
manifests and raw evidence are unchanged. Source bindings are refreshed explicitly for this
integration, and required CI must qualify the exact combined tree.

## Integrated a66 artifact audit and projected permanent correction

Run [36092747077](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36092747077),
source `a66c80ba9b664778cde69b670feeb78a241ea890`, executed **396 cases across eleven suites:**
**394 passed and two failed**, with zero errors or skips. All thirty source pins and nine card
payload pins match the exact source. The [successor failure audit](tier-one-postboard-support-batch-b-failure-audit-36092747077.json)
preserves every raw XML, all eleven stage statuses, source provenance and both coverage reports.
Artifact `10846239519` has ZIP SHA-256
`5befa27e001d94bb85101722ff52a31ab735df2e15f9e1076fc03d1b82baf313`.

The strict snapshot stage now passes all 338 cases. The incidental `FreeForAllLobbyTest`
passes all fourteen cases with its original assertions and timeouts; this does not erase or
explain its earlier eventual-assertion failure. The remaining failures are both in Gut Shot:
the unchanged exact-two-life post-cast loss assertion, and the newly required battle legal-action
offer. Ten Gut Shot cases pass, including illegal ordinary-land/artifact rejection, actual
Factory animation and the exact fizzle after Imprisoned in the Moon changes the target's type.

The battle failure exposed a deeper shared predicate: enumeration requests `TargetFilter.Permanent`
before selecting battles, but projected `CardPredicate.IsPermanent` omits `BATTLE`. The other
predicate paths already delegate to `TypeLine.isPermanent`, which includes it. The prospective
correction adds `BATTLE` to that shared projected-type set. It preserves every Gut Shot assertion
and requires exact successor CI; the source review of the outer AnyTarget paths did not prove
this downstream predicate. Post-cast state-based actions remain a separate repair.

The rules manifest binds the official September 25, 2026 archive URL and SHA-256. That digest
and effective header were checked against the separately archived official bytes; the workflow
artifact does not itself include the entire rules file. The combined seven-identity/sixteen-slot
coverage queue remains prospective. No official execution or original Monster claim changed.
