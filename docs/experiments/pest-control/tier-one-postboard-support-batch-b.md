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
| Gut Shot | NPH 86 | One red or two life pays the Phyrexian symbol; one damage to creature, player, planeswalker or battle | 9 |

The blasts reuse modal targeting, `EntityMatches` through `Conditions.TargetMatchesFilter`, and
conditional counter/destruction effects. Their tests cast actual matching and nonmatching spells,
target both sides' permanents, change color with Fylamarid in response, reject wrong-mode target
categories without mutation, and remove a target before resolution. They impose no color restriction
on legal target generation.

Gut Shot reuses the existing Phyrexian mana payment and any-target damage paths. Its tests separately
exercise red mana and two-life payment, insufficient and exactly two life, unchanged state after an
illegal cast, all four legal target classes, and loss of the target without refunding the life cost.
The two-life lethal-cost case uses the engine's actual state-based loss; no winner is assigned by the
fixture. These are excluded regression fixtures, not matchup outcomes.

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

`IMPLEMENTED_PENDING_RUNTIME_AND_COMPILED_SNAPSHOT_REVIEW`. Independent source review passed all six
card and scenario files. No local Kotlin runtime pass is claimed. All official counters for this batch
are zero, and no existing frozen deck, original Monster vector, consumed claim or historical artifact
has changed.

The initial PR commit `eaa43a4f6f0daacab12e75871b7b2a27343da1b1` conflicted with concurrent
A integration before any workflow ran. The two-parent successor retains both changes, updates only
current source bindings/queues, and requires the integrated A commit as an ancestor. No failed or
missing workflow is represented as a card result.
