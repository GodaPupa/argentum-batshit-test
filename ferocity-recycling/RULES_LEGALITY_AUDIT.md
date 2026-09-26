# Ferocity Recycling — rules and legality audit v0.1

Audit retrieval date: **2026-09-26 UTC / 2026-09-25 America/Los_Angeles**. This is an admission and rules record, not evidence of wins or of engine qualification. Card data is archived in `sources/`; implementation behavior must pass the project's executable qualification separately.

## Admission decision

**Ferocity of the Hunt is admitted for verified-text prerelease research.** Its complete Scryfall record and printed card image agree: mana cost `{1}{B/G}`, Enchantment — Aura, flash, enchant creature, a continuous +1/+0 bonus and deathtouch, and an Aura-owned death trigger returning the enchanted card tapped under its owner's control. The image was inspected, including its `C 0134` / `FRA` common-printing identification. The official Wizards gallery's page data also lists this card. No Ferocity-specific ruling appears in the retrieved Scryfall ruling feed; an empty feed is not a rules exemption. [Wizards gallery](https://magic.wizards.com/en/products/reality-fracture/card-image-gallery), [card record](https://scryfall.com/card/fra/134/ferocity-of-the-hunt).

Sanctioned legality is **not inferred from the research admission**. The retrieved Scryfall record says `pauper: not_legal`, `future: legal`, and `released_at: 2026-10-02`. Its `games` array lists intended platforms, not a guarantee that the card is currently playable on them. Wizards gives prerelease dates September 25–October 1, Arena release September 29, and worldwide/tabletop release October 2. Wizards' general policy ties tabletop legality to prerelease; however, the latest MTR linked by the retrieved WPN directory is dated February 27, 2026 and does not list FRA's specific admission date. The records therefore support a **September 25 tabletop-legality inference under the general policy**, with an explicit stale/incomplete-source discrepancy, rather than an unqualified platform claim. Exact FRA MTGO deployment/admission remains unverified. The program retains the conservative prerelease-research label while these discrepancies remain. [Collecting Reality Fracture](https://magic.wizards.com/en/news/feature/collecting-reality-fracture), [tabletop prerelease policy](https://magic.wizards.com/en/news/announcements/format-legality-shifts-to-prerelease-with-phyrexia-all-will-be-one), [WPN rules directory](https://wpn.wizards.com/en/rules-documents), [retrieved MTR](https://media.wizards.com/ContentResources/WPN/MTG_MTR_2026_Feb27_EN.pdf).

The independent research authorization does not override any explicit admission rule belonging to another project. No other project's controls, seed allocations, or gameplay claims are inherited.

**The reconstructed existing Ferocity project also has a binding post-release admission gate.** Its prior audit at commit `3afd83c8741c5b3d89f231543e951c5f204ca4ef`, preserved under `history/initialization-3afd83c8/` and recorded in `sources/concurrent-initialization.json`, requires a fresh post-release check before live Pauper-legality or sanctioned-play claims. That gate remains in force. The paper-policy inference above records a source discrepancy only; it does not bypass that gate. Verified-text prerelease fixtures and development may proceed under their explicit labels, while any later sanctioned-legality claim must await the required fresh check.

## Current Pauper pool corrections

The live official Pauper page requires a qualifying released common printing in paper or Magic Online; a familiar uncommon printing does not disqualify a card with an earlier qualifying common. The main deck minimum is 60, sideboard maximum 15, and the ordinary combined copy limit is four. `sources/pauper-ban-snapshot.json` records the retrieved official bans. **Deadly Dispute is banned and is excluded.** Kuldotha Rebirth, Basking Broodscale, Narcomoeba, and Price of Progress are also on that live list. [Pauper rules](https://magic.wizards.com/en/formats/pauper), [banned list](https://magic.wizards.com/en/banned-restricted-list).

An initially suspected discrepancy about The Zeta Set was resolved using the newer official announcement: Wizards reversed its earlier exclusion. The SLZ downshifts became legal in tabletop Pauper September 7 and on MTGO September 23, 2026; Narcomoeba and Price of Progress were banned. **Baleful Strix, Cut Down, Dispatch, Vandalblast, and Soul-Guide Lantern must not be excluded using the older social-media statement.** This very recent pool change also limits how completely an older benchmark represents the present format. [On The Zeta Set in Pauper, September 4, 2026](https://magic.wizards.com/en/news/announcements/on-the-zeta-set-in-pauper).

Printing examples that required an explicit older common witness:

| Card | Retrieved representative printing | Verified common witness |
|---|---|---|
| Crypt Rats | MH1 #84, uncommon | VIS #55, 1997-02-03 |
| Cast Down | CLB #119, uncommon | 2XM #79, 2020-08-07 |
| Darksteel Citadel | C21 #285, uncommon | DST #164, 2004-02-06 |
| Galvanic Blast | 2XM #125, uncommon | SOM #91, 2010-10-01 |
| Pulse of Murasa | C21 #202, uncommon | OGW #141, 2016-01-22 |
| Nadier's Nightblade | MH3 #275, uncommon | CMM #175, 2023-08-04 |
| Soul-Guide Lantern | EOC #143, uncommon | SLZ #115, with explicit September legality announcement |

The exact source objects, query URLs, and response hashes are retained in each `*-common-witness.json`. The suffix `*-canonical.json` denotes the named API/Oracle record retrieved for this audit, not an earliest printing or the engine's canonical implementation module. The five basics' representative records happen to name future TRK printings; separate released Alpha common witnesses establish eligibility. Myr Retriever is common in 2XM #277 despite its familiar uncommon printings. **Hunter of Eyeblights and Syr Konrad, the Grim are not admitted**: their retrieved Pauper records are `not_legal`. They were audit possibilities, not selected candidates. Sources establish eligibility, not a requirement to play a card.

`sources/card-source-manifest.json` consolidates 169 exact card records: 165 eligible support records, the one Ferocity prerelease-research exception, and three excluded audit records (Deadly Dispute, Hunter of Eyeblights, and Syr Konrad). It identifies 35 separate common-printing witness files and the 29 retrieved ruling feeds where selected. Every selected deck identity has a source; mechanical implementation and pilot support remain separate gates.

## Verified rules identity

The official rules landing page linked **Magic Comprehensive Rules effective September 25, 2026**. The exact downloaded TXT response is identified by SHA-256 in `sources/official-source-audit.json`. Rule numbers below were verified against that response, not recalled from older numbering. The repository records scoped interpretation and provenance; it does not need a redistributed copy of the entire rules book. [Official rules](https://magic.wizards.com/en/rules), [dated TXT](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt), [dated PDF](https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf).

| Topic | Verified CR references | Required interpretation |
|---|---|---|
| Hybrid cost and flash | 107.4e; 702.8 | Pay one generic plus either one black or one green. Flash grants instant timing, not a free spell or permission during cost payment. |
| Aura target and attachment | 303.4a–c; 608.2b; 704.5m | Cast targeting a legal creature; it enters attached only if resolution succeeds. A departed or otherwise illegal sole target makes the spell fail. An unattached/illegal Aura goes to its owner's graveyard. |
| Aura ability ownership | 113.1a; 603.3a; 603.6e | Deathtouch is granted to the creature; the return trigger is on the Aura. Its controller is the Aura's controller when it triggers. Return control is nevertheless specified as the creature card's owner. |
| Continuous bonus | 611.3a–b | The +1/+0 and deathtouch apply while the Aura's effect applies. There is no toughness bonus and no persistence through a zone change. |
| Death and look-back | 700.4; 603.6c; 603.10 | Battlefield-to-graveyard movement is death. Exile, bounce, or replacement of the graveyard move is not. Simultaneous departure uses the appropriate prior-state trigger information. |
| Source-independent abilities and LKI | 113.7a; 608.2h; 702.2e; 702.15c | A pending damage ability survives removal of its source. If the original source is gone, its last battlefield characteristics determine deathtouch/lifelink. A returned card is a different source. |
| New identity and tokens | 400.7; 111.8 | Returned creatures lose old damage, counters, and attachments. A departed token cannot return. Moving a card away from its first graveyard object prevents this return trigger from finding it. |
| Priority, triggers, and costs | 117.2e; 117.5; 601.2h; 602.2b; 603.3b | Costs are paid atomically; nobody can respond midway. State-based actions and queued triggers are handled before priority. Same-controller triggers can be ordered, but not placed beneath the already-announced spell/ability that caused them. |
| Artifact and mana accounting | 118.3; 118.10; 601.2f | One sacrifice cannot pay two costs. Each new spell uses actual artifact counts, while an already determined spell cost remains locked. |
| Damage, deathtouch, indestructible | 120.8; 702.2; 702.12; 704.5g–h | Zero/prevented damage gives no deathtouch destruction. Indestructible prevents destruction, not damage or sacrifice. Own creatures are affected if the sweeper text includes them. |
| Lifelink and Clues | 702.15; 111.10f; 701.16a | Lifelink gains life from actual damage to all applicable recipients. Investigate creates an artifact, not a free card draw. Sacrificing that Clue for Shaman does not also activate its draw ability. |
| Returned creature actions | 302.6 | A return tapped is not a usable blocker or an immediate attacker. Rats/Shaman can still activate their abilities immediately because those costs contain no tap or untap symbol. |

## Card-specific qualification obligations

1. Resolve Ferocity with black payment and with green payment; reject an unpaid hybrid cost, an illegal target, or an attempt to act during another spell's resolution.
2. Destroy/bounce/exile the Aura's target while the spell is pending. Verify the Aura never enters and no return ability was established. Countering the Aura must likewise not preserve the creature.
3. Remove only the Aura while the creature remains; remove creature and Aura simultaneously; remove the creature after the Aura is already gone. The results must differ.
4. Separate control of the Aura, creature, and owner. Verify trigger control and final ownership-based control. Removing the creature's abilities must not incorrectly delete the Aura-owned trigger.
5. Resolve a creature death, graveyard-hate response, and failed return. Also move the card out of the graveyard and back before the trigger: a new graveyard identity is not the original return object.
6. Verify token death, replacement-to-exile, bounce, and actual death independently. Verify multiple Ferocity Auras do not return the same departed object twice.
7. Verify a returned creature is tapped, has no old damage/counters/Auras/+1 power/deathtouch, and has a new game object identity. An engine may reuse an internal entity identifier only if its incarnation/reference semantics correctly distinguish those objects. Its own non-tap activated ability remains legal when its actual costs can be paid.
8. Resolve a Ferocity Shaman pulse against friendly and opposing ground creatures plus flyers. The ordinary 1/1 Shaman also dies; Familiar and Bone Picker survive because they fly, while Myr Enforcer does not survive deathtouch merely by being 4/4.
9. Resolve Ferocity Rats at X=1 against ground creatures, flyers, and both players. The ordinary Rats dies and returns tapped only after damage and state-based actions; its controller still loses life. Pay X using only black mana. An Eldrazi Spawn's colorless mana cannot pay that X cost.
10. Test queued original-source pulses after the creature returns. The old source's LKI can kill the returned unenchanted new creature. Distinguish a fresh activation by that returned creature, which has no inherited deathtouch.
11. Test zero damage, prevention, protection, indestructible, and lethal life totals. When damage ends the game, do not resolve a return afterward and invent stabilization.
12. Verify sacrifice-cost death triggers, Wellspring draw, Familiar entry/discard-or-draw, Ghast modal choice, and return triggers in actual stack order. Opponents must receive every legal response window and none during payment.
13. Qualify Toxin's own target failure, deathtouch/lifelink duration, Clue artifact count, paid Clue draw, and sacrifice exclusivity. Rats' lifelink must include damage to friendly creatures and both players and process the life result before state-based loss checks.
14. Qualify Not Dead After All separately: its death ability is granted to the creature, it has an end-of-turn duration, and successful return creates a Wicked Role. That Role changes the returned creature's statistics and later drains when put into a graveyard. Ferocity supplies none of those Role benefits.
15. For Auramancer, verify the departed Ferocity is already in the graveyard when Auramancer's new entry trigger chooses its enchantment target. For Evolution Witness, a plain return does not place counters and therefore does not itself recover Ferocity.

This list is a specification. A test count, green build, or card-definition inspection alone does not certify every item. The qualification receipt must name what actually ran and identify remaining unsupported obligations.

## Retrieval limitations and discrepancy log

- Gatherer homepage and direct card routes could be indexed but returned inaccessible/403 responses through available retrieval paths. They are references, not falsely claimed successful archives.
- Initial Scryfall requests without an explicit descriptive User-Agent and JSON Accept header returned HTTP 400. Requests with those headers succeeded; their actual raw responses are archived. Failed requests were retrieval failures, not games.
- The official gallery's server page contains the Ferocity card identity, while dynamic card data was not available in the text-rendered web view. The Scryfall printed image supplied independently inspected text and rarity. Failed gallery-data retrieval did not generate substitute card text.
- Current source dates distinguish the prerelease/FRA issue from already-resolved SLZ legality. Neither database flags nor older social statements silently override newer primary announcements.
- No sanctioned tournament result is produced by any simulator, regardless of the eventual legality resolution.
