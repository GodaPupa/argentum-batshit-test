# v0.9 Position 1 — Engine Coverage Batch AL Gate

This seed-free successor to accepted Batch AK qualifies three remaining Veteran Beastrider cards
and exact canonical registry aliases required to load two frozen combined-name deck entries.
The accepted finite queue is 14 Veteran identities; Izzet has no unresolved identities.

## Frozen boundaries

Both deck files, official seed quarantine/vector, sampled-game assignments, one-shot Position-1
execution authority, and durable attempt guards are unchanged. Izzet v0.7 SHA-256 remains
`726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01`; frozen Veteran file SHA-256
remains `c3ab6ee8e37a78219e7bbb7c6a75c634d78c1eb4965ab0db3abd5d853398b0be`.
No official seed is read or consumed and no game is initialized or outcome exposed.
Position 1 remains guarded by full engine/pilot readiness; positions 2–12 remain unauthorized.

## Coherent scope

| Card | Canonical printing | Existing rule composition |
| --- | --- | --- |
| Temporal Isolation | TSP #43 | Flash; enchant creature; attached shadow grant; continuous prevention of all damage by enchanted creature |
| Guardian Naga // Banishing Coils | CLB #23 | Vigilance; self damage prevention during controller's turn; Adventure artifact/enchantment exile and ordinary paid creature-from-exile permission |
| Ulvenwald Captive // Ulvenwald Abomination | EMN #175 | Defender; green tap mana; exact seven-mana transform; colorless 4/6 back with two-colorless tap mana |

Current Oracle fields, canonical printing metadata, published rulings, and printing lists were
retrieved before implementation. Sources: [Temporal Isolation](https://scryfall.com/card/tsp/43/temporal-isolation),
[Guardian Naga](https://scryfall.com/card/clb/23/guardian-naga-banishing-coils),
[Ulvenwald Captive](https://scryfall.com/card/emn/175/ulvenwald-captive-ulvenwald-abomination).
The three card definitions introduce no new SDK vocabulary. Snake Umbra was excluded before
implementation because the current engine lacks an umbra-armor destruction replacement; the
card remains unresolved and is not approximated with regeneration.

The frozen opponent spells the two multiface identities using their full printed names.
CardRegistry previously registered only the individual front/back names and could not resolve
those exact entries. One canonical registry change derives combined aliases from actual face
metadata. It must accept the exact real pair, preserve ordinary lookup/overlays, and reject an
incorrect suffix. It must not split arbitrary supplied names and return their prefix. This is
independently tested shared loading behavior, not a deck-name change or new card mechanic.

## Required behavior and artifact contract

1. Temporal Isolation is cast during another player's turn, attaches to the intended creature,
   grants shadow, excludes an ordinary blocker, and prevents combat damage. Its prevention also
   stops the enchanted creature's noncombat damage without preventing damage dealt to it.
   Removing the Aura removes both shadow and its damage prevention.
2. Guardian Naga prevents damage during its controller's turn and takes ordinary damage on another
   player's turn. Banishing Coils rejects ordinary creatures, exiles artifacts and enchantments,
   and grants a subsequent creature cast that still requires the full mana cost. The creature
   resolves with printed 5/6 statistics and vigilance. If the Adventure's only target becomes
   illegal, the Naga goes to the graveyard and receives no future casting permission.
3. Ulvenwald Captive's front respects summoning sickness and tapping, generates one green mana
   without the stack, and has defender. Six mana cannot pay the transformation; seven can.
   Transformation retains the entity, changes statistics to 4/6, removes defender and green color,
   and replaces the front mana ability with two colorless mana from the back.
4. Dedicated registry alias tests, existing CardRegistry regressions, and exact-deck readiness assertions qualify the actual full
   frozen names and reject incorrect names. Expected post-implementation unresolved count is exactly **11**,
   all Veteran; the five separate PDH/ledger blockers remain closed.
5. The three individual card tests, registry tests, and readiness test run in one bounded workflow.
6. Snapshot generation may change exactly TSP.json, CLB.json, and EMN.json, preserving every prior
   card object and adding only the three named front-card records. All canonical snapshots must
   pass again with updating disabled.
7. The success artifact contains exactly a manifest and seven complete transcripts (registry,
   three card tests, readiness, snapshot generation, snapshot verification). It binds the original
   source SHA, snapshot commit/tree, all implementation/test/snapshot file hashes, frozen control
   identities, and zero official execution counters. Independent artifact audit is required.

## Integration

The workflow checks out its triggering source SHA, performs the bounded deterministic tests and
snapshot contract, and commits only the three snapshot files. Before nonforce push it fetches the
live branch and requires it still equals the trigger SHA. Concurrent advancement fails closed;
there is no automatic rebase. A separate source commit isolates the canonical registry repair;
each card also has its own source/test commit. The existing validation-only PR remains draft.

Accepted coverage must not advance from 14 to 11 until the complete artifact passes independent
audit. Workflow success is validation evidence only. Official sampled counters remain 12 generated,
0 consumed, 0 initialized, 0 completed/12, 0 outcome exposure/12. KEEP_V07 / NO CARD CHANGES.

## Independent acceptance audit

**Batch AL is accepted.** The artifact from [validation run 36083351936](https://github.com/GodaPupa/argentum-batshit-test/actions/runs/36083351936)
and job `107909832391` passed independent archive and repository comparison. Source was
`599fbbd45edf7d80d625f233601679d381e190b0`; guarded snapshot integration produced
`5539c7a37e2e7c9febc291f041ee7925d083a309`, tree `316572fecc9a9211ec2ef687bc3c1bc00cf5874f`.
Artifact `10842364915`, `izzet-v09-position1-engine-batch-al`, is 300476 bytes with ZIP SHA-256
`dfc337d8c0c5b581107e2a0d2540a0a9fd1590d08bc7f279dd3a355ba8730674`.

The archive has exactly the manifest and seven successful transcripts. All 24 manifest digests match
the source, snapshot, frozen control files, or archived transcripts as appropriate. The snapshot diff
contains exactly TSP, CLB, and EMN additions for these three cards; every preexisting card object is
unchanged. Exact canonical combined-name lookup tests and existing registry regressions passed.
Accepted unresolved coverage is **14 → 11**, all Veteran; Izzet remains at zero unresolved identities.
The five full-readiness guards remain. This was deterministic qualification only: 12 official seeds
generated, 0 consumed, 0 initialized, 0 completed/12 and 0 outcome exposure/12.
