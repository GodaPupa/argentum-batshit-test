# Ferocity Recycling — card coverage audit v0.1

Audit date: **2026-09-26 UTC**. Source base: `d0c78bb4cca79b7402ba65bd62b5cb621230a054`; deck and implementation content hashes below scope the uncommitted project snapshot independently of that base commit.

**All 135 distinct identities in the frozen initial deck pool have archived card text.** Of these, 134 have a released qualifying common witness and a current source-qualified Pauper record. Ferocity has verified common prerelease text; source qualification does not override the existing project’s post-release admission gate. The static main-source inventory finds 106 card identities and is missing 29. It does not establish compiled registry admission or interactive gameplay correctness.

The machine-readable companion, [sources/card-coverage.json](sources/card-coverage.json), records every selected identity, each exact source path and SHA-256, common witness, current legality and platform distinction, deck membership, canonical assignment path/hash/symbol, and dedicated scenario source/hash. This report supersedes the provisional 136-identity inventory made while candidate files were still being edited. No randomized outcomes informed those edits.

## Frozen scope

| Pool | Lists | Unique identities | Archived text | Released common witnesses | Prerelease only | Static definitions present | Missing definitions | Dedicated scenario sources |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| Ferocity candidates | 9 | 56 | 56 | 55 | 1 | 42 | 14 | 18 |
| Within-shell comparators | 9 | 55 | 55 | 55 | 0 | 42 | 13 | 19 |
| Established 60/15 benchmarks | 6 | 124 | 124 | 124 | 0 | 100 | 24 | 37 |
| Deduplicated union | 24 | 135 | 135 | 134 | 1 | 106 | 29 | 38 |

The three groups overlap. Every main deck is exactly 60 cards; the six source benchmarks include their exact 15-card sideboards. Candidate and within-shell-comparator sideboards have not been frozen. Metadata JSON files are excluded from the deck count. All 24 deck-file content hashes are recorded in the companion, alongside each deck’s missing-card set. The count of dedicated test sources describes source declarations, not passed cases or qualified cards.

## Source text, printing, legality, and platform are separate

The audit matched source identities using the actual Scryfall `name` and, for transforming cards, `card_faces[].name`. Case and diacritics are normalized only for matching: deck spellings such as `Lorien Revealed` and `Troll of Khazad-dum` retain their original spelling, while canonical source names are also retained. No different card identities were merged by a filename resemblance. Tithing Blade correctly matches the front of `Tithing Blade // Consuming Sepulcher`.

The source archive contains 169 canonical card records for the wider investigated pool and 35 explicit common-witness objects. Only the 135 deck identities in this audit contribute to its coverage totals. This task added 74 missing benchmark canonical records and 20 common-witness objects, with 74 successful retrievals and zero failures in [benchmark-source-retrieval-log.json](sources/benchmark-source-retrieval-log.json). Earlier retrieval failures and source discrepancies remain documented by the rules audit.

Current legality requires a released paper/MTGO common witness, the archived current Pauper flag, and an official-ban check. Every selected non-Ferocity identity clears those source checks. None is on the archived named banned list and none has a sticker/Attraction rule requiring the generic category-ban exception. In particular, **Deadly Dispute appears in no selected list**. An uncommon latest printing alone does not make Crypt Rats, Cast Down, Myr Retriever, Cut Down, or Dispatch ineligible. The full dated witnesses are retained. [Official Pauper page](https://magic.wizards.com/en/formats/pauper), [official banned list](https://magic.wizards.com/en/banned-restricted-list), [scoped ban snapshot](sources/pauper-ban-snapshot.json).

Ferocity’s Scryfall record remains `pauper: not_legal`, `future: legal`, with a 2026-10-02 printing release date. The [rules and legality audit](RULES_LEGALITY_AUDIT.md) distinguishes verified prerelease text from gameplay admission under the reconstructed existing project’s post-release gate. It separately records a September 25 tabletop-legality inference under Wizards’ general prerelease policy, a stale/incomplete MTR discrepancy, and unverified exact MTGO admission. This inventory carries those distinctions forward. It does not authorize prerelease gameplay in conflict with that gate or convert intended-platform tags into sanctioned legality or gameplay evidence.

The Zeta Set exception is resolved by the newer official announcement: tabletop admission September 7 and MTGO admission September 23. The selected benchmark uses whose common witness is SLZ—Baleful Strix, Cut Down, Dispatch, and Vandalblast—occur in September 24–25 MTGO events, after the stated MTGO admission. The selected September 20 Gardens and Tron lists have no SLZ-only witness dependency. The older two benchmark results still predate the most recent MTGO pool change and cannot alone establish a settled contemporary metagame. [Wizards, On The Zeta Set in Pauper](https://magic.wizards.com/en/news/announcements/on-the-zeta-set-in-pauper), [benchmark provenance](BENCHMARK_AUDIT.md).

## Canonical implementation inventory

The scanner inspected 18555 main-source Kotlin definition files and read literal card-name arguments in unindented top-level `val = card(...)` and `val = basicLand(...)` declarations. Comments, reprint `Printing` rows, filenames, and mentions in tests do not count as canonical implementations. This is an explicit static convention scan, not a Kotlin compiler or a registry-load test.

For the 106 identities with matching source, the companion records the exact path, content SHA-256, Kotlin symbol, package, declaration line, and visibility. No duplicate nonbasic canonical assignments were found in this selected pool. Basic lands legitimately have multiple definitions for art/printing variants.

**Tithing Blade is conditionally source-present through its public double-faced wrapper.** Its literal `card("Tithing Blade")` declaration is the private `TithingBladeFront`; the inspected public `TithingBlade: CardDefinition = CardDefinition.doubleFacedPermanent(frontFace = TithingBladeFront, backFace = ConsumingSepulcher)` provides the discoverable card-shaped value. The private front is not represented as a standalone public registry entry. Correct transform, craft, back-face rules, discovery, and runtime availability all remain unqualified by this audit.

The repository discovery implementation filters for public static getters with CardDefinition/Printing types. Its exact content hash is retained, so the presence interpretation does not silently assume another engine revision. Main-definition path/hash digest: `f2182c451c005ad0427f1f0bfeb5609b1dfe913a1b9b025c74ed41535a0870d3`. Scenario path/hash digest: `f0c418101a2835834834a415c45db57aef3da0ca70dbc4447fcadec87282555a`. The companion defines both digest encodings.

### All missing identities and implementation queue

There are **14 missing identities used by at least one Ferocity candidate**, 13 used by its no-Ferocity comparators, and **15 additional benchmark-only missing identities**. These total 29 unique missing identities. Source implementation is only the first requirement; each card’s actual supported modes must subsequently compile, load, and pass the relevant interaction scenarios.

| Missing card identity | Ferocity candidate IDs | No-Ferocity comparator IDs | Benchmark IDs |
|---|---|---|---|
| Accursed Marauder | B1-F1 | B1-N0 | Gardens |
| Agony Warp | — | — | Dimir |
| Arms of Hadar | — | — | Dimir |
| Augur of Bolas | — | — | Dimir |
| Avenging Hunter | B2-F2, B3-F3 | B2-N0, B3-N0 | Gardens |
| Bone Picker | A2-F3 | A2-N0 | — |
| Contaminated Landscape | — | — | Dimir |
| Crimson Fleet Commodore | — | — | Red |
| Crypt Rats | B1-F1, B2-F2, B3-F3 | B1-N0, B2-N0, B3-N0 | Gardens |
| Cut Down | B1-F1, B2-F2, B3-F3 | B1-N0, B2-N0, B3-N0, C1-N0 | Dimir |
| Defile | B1-F1 | B1-N0 | Gardens |
| Dispatch | C1-F2, C2-F3, C3-F4 | C1-N0, C2-N0, C3-N0 | Esper |
| Drown in Sorrow | — | — | Gardens |
| Eviscerator's Insight | A1-F2, A2-F3, B1-F1, B2-F2, B3-F3, C1-F2, C2-F3, C3-F4 | A1-N0, A2-N0, B1-N0, B2-N0, B3-N0, C1-N0, C2-N0, C3-N0 | Gardens |
| Faerie Macabre | — | — | Gardens |
| Ferocity of the Hunt | A1-F2, A2-F3, A3-F4, B1-F1, B2-F2, B3-F3, C1-F2, C2-F3, C3-F4 | — | — |
| Glint Hawk | C1-F2, C2-F3, C3-F4 | C1-N0, C2-N0, C3-N0 | — |
| Journey to Nowhere | C1-F2, C2-F3, C3-F4 | C1-N0, C2-N0, C3-N0 | — |
| Kor Skyfisher | C1-F2, C2-F3, C3-F4 | C1-N0, C2-N0, C3-N0 | — |
| Moon-Circuit Hacker | — | — | Dimir, Esper |
| Ninja of the Deep Hours | — | — | Dimir |
| Relic of Progenitus | — | — | Dimir, Tron |
| Searing Blaze | — | — | Red |
| Spinning Darkness | — | — | Gardens |
| Thorn of the Black Rose | B1-F1 | B1-N0 | Dimir |
| Unmake | — | — | Gardens |
| Vandalblast | — | — | Red |
| Witch's Cottage | B1-F1 | B1-N0 | Gardens |
| Writhing Chrysalis | — | — | Tron |

Benchmark abbreviations refer to the exact six source lists in [decks/benchmarks/benchmark_sources.json](decks/benchmarks/benchmark_sources.json). Grixis has no statically missing identity, but this does not certify its cards, pilot, or games.

A useful dependency order is to establish Ferocity and the shared sacrifice-draw support, then the A-family interaction set, the Rats/black-control set, and the white recovery set, while qualifying the actual opponent pool needed for the first interactive batch. This is an implementation planning order, not an outcome-based preference for a family. No architecture may be declared weaker because its missing code has not been implemented.

### Missing count for every frozen list

| Deck | Missing canonical identities |
|---|---:|
| Dimir | 9 |
| Esper | 2 |
| Gardens | 10 |
| Grixis | 0 |
| Red | 3 |
| Tron | 2 |
| A1-F2 | 2 |
| A2-F3 | 3 |
| A3-F4 | 1 |
| B1-F1 | 8 |
| B2-F2 | 5 |
| B3-F3 | 5 |
| C1-F2 | 6 |
| C2-F3 | 6 |
| C3-F4 | 6 |
| A1-N0 | 1 |
| A2-N0 | 2 |
| A3-N0 | 0 |
| B1-N0 | 7 |
| B2-N0 | 4 |
| B3-N0 | 4 |
| C1-N0 | 6 |
| C2-N0 | 5 |
| C3-N0 | 5 |

## Scenario qualification and acceptance limits

The scan finds declared dedicated scenario classes for 38 of 135 identities in 3020 scenario-source files inspected. Their exact class names, source paths, hashes, and literal-name evidence are recorded. A class declaration is a source inventory fact. This audit executed **zero** deterministic gameplay fixtures, compiled-registry loads, functional trials, interactive development games, evaluation games, or confirmation games. It establishes **zero newly accepted full-engine card qualifications**. It does not erase or contradict a separately recorded project qualification receipt.

All 24 lists remain blocked from full-engine admission in this inventory until the exact source version, dependency build, runtime registry contents, card behavior, and equivalent bounded pilots have independent acceptance receipts. Existing source for Shaman, Toxin, and Not Dead After All does not certify flash/Aura behavior, deathtouch/lifelink, Clue costs, death triggers, last-known information, or all possible response windows. Unsupported modes must remain visible and cannot be treated as benign omissions.

One inventory regeneration encountered a missing raw banned-list HTML after the rules auditor replaced the raw publication artifact with the scoped official-ban extraction. Regeneration was corrected to use `pauper-ban-snapshot.json`; this was an audit packaging dependency, with no gameplay or random trial involved.

Any change to a deck hash, source record, main implementation, relevant scenario, source version, or dependency identity requires a refreshed inventory and an explicit qualification scope. The snapshot does not automatically follow later commits. Neither this audit nor the benchmark source results answer whether Ferocity improves a deck.
