# Ferocity Recycling — benchmark and gauntlet source audit

Retrieval date: **2026-09-26 UTC**. Source-selection checkpoint: **v1**.

This audit selects five contemporary pressure decks and one additional close
Shaman/artifact comparator. All six archived lists are exact **60 main / 15
sideboard** source configurations. Selection precedes all Ferocity Recycling
interactive outcomes. These files do not admit an engine, qualify a pilot, freeze
sideboarding plans, or authorize an evaluation sample.

## Selected source configurations

| Use | Exact source configuration | Published event evidence | Reason for inclusion |
| --- | --- | --- | --- |
| Fast aggression | [Mono Red Madness — MisterTwin](https://www.mtggoldfish.com/deck/7970867) | September 24 Challenge 32; 1st, reported 8–1 | Burn, noncombat damage triggers, recursive flying pressure, and a fast clock test life-total costs. |
| Blue tempo and counterspells | [Dimir Faeries — barff](https://www.mtggoldfish.com/deck/7971728) | September 25 Challenge 32; 2nd, 7–2 | Flying creatures, counters, removal, ninjutsu, and monarch pressure test whether the Ferocity deck functions when Shaman cannot clear the opposing air force. |
| Artifact pressure and resource advantage | [Esper Affinity — seasonofmists](https://www.mtggoldfish.com/deck/7971724) | September 25 Challenge 32; 1st, 8–1 | Affinity development, flying threats, draw, discard, and Dispatch test tempo and material recovery. |
| Removal-heavy attrition; close Rats benchmark | [Golgari Gardens — John1111](https://www.mtggoldfish.com/deck/7965699) | September 20 RC Super Qualifier; 3rd, reported 9–2 | Crypt Rats, sacrifice draw, artifact material, removal, main-deck graveyard hate, and life recovery supply a relevant established comparison. |
| Slower engine that punishes a weak clock | [Tron — kuldothared](https://www.mtggoldfish.com/deck/7965705) | September 20 RC Super Qualifier; 4th, reported 8–3 | Mana development, repeated large threats, draw, and life recovery test whether a sweep converts into an eventual win. “Monster Tron” is the descriptive subtype used in this project; the source label is “Tron.” |
| Additional close Shaman/artifact benchmark; outside the five-pressure aggregate | [Grixis Affinity — barba94](https://www.mtggoldfish.com/deck/7970843) | September 24 Challenge 32; 8th, reported 5–2 | Shaman, Toxin Analysis, Familiar, Wellspring, Blood Fountain, Munitions, affinity threats, and Reckoner's Bargain form a particularly relevant established package for family A. |

The strategic reasons in the final column are research interpretations of the
archived lists, not measurements of matchup performance. In particular, none of
these matchups is preclassified as favorable to Ferocity.

The gauntlet contains opponents that cannot be treated as losses for the opponent
merely because a ground sweeper resolves. Dimir and Esper have substantial flying
pressure; Red has direct damage and recursive pressure. Tron remains a resource
and finishing test after a sweep. The runner must play the continuation.

## How the selections relate to current evidence

The starting audit examined the [September 25
Challenge](https://www.mtggoldfish.com/tournament/pauper-challenge-32-2026-09-25),
the [September 24
Challenge](https://www.mtggoldfish.com/tournament/pauper-challenge-32-2026-09-24),
and the [September 20 RC Super
Qualifier](https://www.mtggoldfish.com/tournament/pauper-rc-super-qualifier-2026-09-20).
The source results include multiple pressure types rather than only decks that
depend on ground creatures. They also show real recent success for Gardens and
Esper, so the program must not assume a Grixis benchmark is the whole artifact
metagame or that a black/green shell is inherently an inferior alternative.

Jund was considered explicitly. The current [Jund Wildfire source
page](https://www.mtggoldfish.com/archetype/pauper-jund-wildfire) reported
_blaze66's September 19 Challenge runner-up finish, Varo's September 20 RC Super
Qualifier 16th place, and more recent Challenge finishes outside the top eight.
The [Grixis source page](https://www.mtggoldfish.com/deck/7970843) also listed
LuffyDoChapeuDePalha's September 20 third place and September 21 second place.
Grixis was chosen as the additional close comparator because of its direct
Shaman/Toxin/Familiar/artifact package and contemporary results, **not** because
these sparse event records prove Grixis is stronger than every Jund configuration.
This bounded audit does not estimate comparative archetype win rates.

The [current all-decks metagame
page](https://www.mtggoldfish.com/metagame/pauper/full) was checked as context.
Its retrieved rendering did not expose the selected date-range value, and its
published-list sample is not a random sample of all participants. Its displayed
shares are consequently not used as population estimates, opponent weights, or
evidence that a smaller listed share means a weak deck. The five-pressure
aggregation weights and inferential claims belong in the evaluation contract.

### Source count correction

The first Gardens list inspected was [__Noob__'s winning
configuration](https://www.mtggoldfish.com/deck/7965695), reported 11–1 at the
September 20 RC Super Qualifier. Its [exact text
download](https://www.mtggoldfish.com/deck/download/7965695) totals **61 main / 15
sideboard**, including three Lembas. It must not be called a 60-card list or
silently shortened while retaining the original identity.

The selected John1111 list is a separately published exact 60/15 from the same
event, with two Lembas and its own sideboard. Its [exact-printing
download](https://www.mtggoldfish.com/deck/download/7965699?output=mtggoldfish&type=tabletop)
and [XML
download](https://www.mtggoldfish.com/deck/download/7965699?output=dek&type=online)
agree after consolidating repeated basic-land printings. The unselected winner
remains relevant external evidence that a Rats attrition shell can succeed;
its result is not transferred to John1111's list.

## Provenance: official event records and mirrored card lists

The [official MTGO event index](https://www.mtgo.com/decklists) linked the exact
event identifiers. Web text retrieval of individual MTGO event pages produced
timeouts, redirects, or an empty JavaScript page shell. A normal HTTP retrieval
then recovered embedded JSON from the [September 25 official event
page](https://www.mtgo.com/decklist/pauper-challenge-32-2026-09-2512854910).
It confirms 63 players, final ranks, and match records, including seasonofmists
8–1 and barff 7–2. A compact extract is preserved at
`decks/benchmarks/mtgo_20260925_event_extract.json`, with the original retrieved
HTML hash and extraction scope. The embedded data did **not** contain a
`decklists` member.

Normal HTTP retrieval of the [September 24 official
page](https://www.mtgo.com/decklist/pauper-challenge-32-2026-09-2412854905)
and [September 20 official
page](https://www.mtgo.com/decklist/pauper-rc-super-qualifier-2026-09-2012854477)
returned only event metadata in their embedded data. Their detailed placements,
records, and all six deck card payloads therefore rely on MTGGoldfish's explicitly
attributed event mirror. This limitation is recorded in every deck JSON. It must
not be rewritten as a successful direct official card-payload cross-check.

The 32 mirrored published lists from September 25 are not its full 63-player
field. Counts among those 32 must not be described as whole-event field shares.
Similarly, apparently repeated event pages with “(1)” suffixes must not be counted
as independent tournaments without distinct official event IDs.

For Red, the plain-text default download returned HTTP 403. Its advertised
exact-printing text and XML download links succeeded and agreed. No card count
was guessed to fill the gap. For John1111's Gardens, the same two advertised
download formats were used after the default link failed. Other selected lists
were read through their ordinary plain-text download links.

## File format and checks

Each selected source has:

- A JSON record using `id`, `family`, `version`, `main: [{name, count}]`, and
  `sideboard: [{name, count}]`, plus source URLs, event date, retrieval date,
  reported result, admission limitation, and normalized import-file hash.
- An importable text list with a `Sideboard` header.
- An entry in `decks/benchmarks/benchmark_sources.json` distinguishing five
  gauntlet roles from the additional close comparator.

Card spellings in the source are preserved, including `Lorien Revealed` and
`Troll of Khazad-dum`. Any engine mapping to accented Oracle names must use an
explicit alias and retain this source identity. Printing suffixes are removed
from the two exact-printing text exports solely to make them importable.

The archive builder checked every selected mainboard at 60, every sideboard at
15, and four-copy limits across main plus sideboard with basic-land exceptions.
No selected list contains Deadly Dispute. These structural checks are not a
substitute for the project's common-printing, banned-card, release-date, or
platform-admission audit.

No source-list sideboard is an implicit sideboarding plan. Before postboard
testing, both sides require qualified legal substitutions, opponent-sensitive
mulligans and policies, and the declared best-of-three match procedure.

## Admission work still required

Source results are results of human MTGO play, not results of this simulator.
The published record does not certify that a newly written pilot can reproduce
the source deck's skill or finish. Candidate and benchmark policy development
must receive equivalent bounded opportunities, using information actually
available to each player.

The qualification inventory must include the newer benchmark cards as well as
familiar ones: Baleful Strix; Leonardo, Big Brother; Utrom Monitor;
Sewer-veillance Cam; Barrels of Blasting Jelly; and Call Damage Control.
“Printed in an event mirror” is useful provenance, but does not itself replace
verification of current common-printing eligibility or exact card text.

The gauntlet also needs correct affinity, artifact-land counts, ninjutsu,
counterspell targets, graveyard disruption, madness, Sneaky Snacker timing,
monarch, initiative, recursive value, life payments, mana restrictions, and
post-sweeper continuation. Missing behavior must block the affected game
visibly; these lists must not be weakened to avoid an inconvenient feature.

None of the archived benchmark lists contains Ferocity of the Hunt. Their
published tournament results provide no sanctioned evidence for Ferocity.
Ferocity simulations must retain the project's verified-text prerelease label
until the separate legality audit establishes its platform and date admission.

**Execution counts contributed by this audit: deterministic gameplay fixtures
0; randomized functional trials 0; development games 0; frozen evaluation games
0; independent confirmation games 0; postboard matches 0.** This audit contributes
source records and list validation only.
