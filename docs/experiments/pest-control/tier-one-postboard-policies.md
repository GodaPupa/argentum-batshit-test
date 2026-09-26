# Pest Tier-1 deterministic postboard policy inputs

These are the independently reviewed boarding choices within the existing
[five-matchup stopping rule](tier-one-qualification-stopping-rule.md). The exact ten resulting
60-card decks, residual 15-card sideboards, original identities and frozen result digests are in
[the policy inputs](tier-one-postboard-policy-inputs.json). The Kotlin policy selects only by the
two frozen deck identities and uses the independently qualified exchange compiler from source
`232f4904f3929fec917f87b9565f8adb4047487c`.

**Status: independent strategy review accepted; exact boarding digests frozen; all ten construction
cases passed and independently audited.** The [separate freeze receipt](tier-one-postboard-policy-freeze.json)
records the non-author coordinator review of `fa55b5bec5c29c0feabd850fc9fdda023cc20ab7` and freezes
the immutable input snapshot without changing any of its ten choices or sixty identity digests.
The [construction receipt](postboard-policy-evidence/remote-36259438559/receipt.json) preserves the
original run36259438559 artifact and the separate non-author `/root/manual` artifact review.
The choices below are prospective judgments about the public lists. No matchup result, pilot
trial, seed or future draw was used to select or revise them. The same exchange applies on the
play and draw and in either seat. Original preboard decks and all six complete-75 card pools are
unchanged. An empty exchange is an explicit policy where the submitted sideboard has no suitable
improvement to the deck's role.

## Exact exchanges and strategic reasons

Quantities are copies moved between the original zones, simultaneously. Every row leaves exactly
60 cards in the main deck and 15 in the sideboard.

| Matchup | Deck | Into main | Into sideboard |
|---|---|---|---|
| Mono-Red Madness | Pest | 3 Suffocating Fumes; 2 Pulse of Murasa | 2 Chainer's Edict; 2 Bone Shards; 1 Generous Ent |
| Mono-Red Madness | Red | None | None |
| Grixis Affinity | Pest | 3 Nature's Claim; 3 Snuff Out; 2 Pulse of Murasa; 2 Tamiyo's Safekeeping | 4 Weather the Storm; 2 Bone Shards; 2 Chainer's Edict; 2 Fierce Witchstalker |
| Grixis Affinity | Grixis | 4 Duress; 2 Unexpected Fangs | 2 Nihil Spellbomb; 2 Fanatical Offering; 1 Utrom Monitor; 1 Galvanic Blast |
| Mono-Blue Terror | Pest | 3 Snuff Out; 2 Pulse of Murasa; 2 Tamiyo's Safekeeping | 4 Weather the Storm; 2 Bone Shards; 1 Fierce Witchstalker |
| Mono-Blue Terror | Blue | 3 Gut Shot; 1 Murmuring Mystic | 2 Sleep of the Dead; 1 Artful Dodge; 1 Deem Inferior |
| Monster Tron | Pest | 3 Nature's Claim; 3 Snuff Out; 2 Tamiyo's Safekeeping | 4 Weather the Storm; 2 Fierce Witchstalker; 1 Bone Shards; 1 Chainer's Edict |
| Monster Tron | Tron | 1 Breath Weapon; 2 Scour from Existence; 2 Call Damage Control | 2 Candy Trail; 1 Unfathomable Truths; 1 Bramble Wurm; 1 Rooftop Percher |
| Spy Combo | Pest | 3 Suffocating Fumes; 3 Snuff Out | 2 Chainer's Edict; 2 Bone Shards; 2 Fierce Witchstalker |
| Spy Combo | Spy | 1 Mesmeric Fiend; 1 Nyxborn Hydra; 1 Acorn Harvest | 3 Masked Vandal |

### Mono-Red Madness

Pest retains all four Weather the Storm and its early life-gain engine. Fumes answers low-toughness
creatures, including Sneaky Snacker, while Pulse recovers a creature or land and gains life. The
exchange removes slower edicts and removal with an additional card/permanent cost, plus one
expensive finisher. Two landcycling Ents and the unchanged 21 lands remain.

Red retains its pressure, burn and discard/draw engine. Its five blue-specific blasts have no
blue opponent here. Smash to Smithereens has Food-token targets but does not answer Pest's creature
engine, and those tokens may be sacrificed in response. Relic attacks Red's own Snacker recursion
while Pest has only limited graveyard recovery available. Campfire supplies life or a library
reload rather than advancing Red's burn plan. These tradeoffs support the explicit no-exchange
policy; no replacement cards are invented to improve the submitted 75.

### Grixis Affinity

Pest adds cheap artifact removal and nonblack-creature removal, protection for a developed payoff,
and recovery for attrition. Nature's Claim can answer artifact threats but does not destroy the
indestructible Bridges; Snuff Out cannot kill a black Refurbished Familiar. The pilot must respect
those restrictions, actual costs and available responses. Weather is less central against this
resource deck. Trimming additional-cost removal and two four-drops keeps room for interaction
without changing the mana base or the eight growing three-mana threats.

Grixis uses Duress to contest Pest's noncreature interaction and Unexpected Fangs to make a threat
harder to race. It retains both Krark-Clan Shamans and both Toxin Analyses, all three Cast Down,
three Galvanic Blasts, all Myr Enforcers and Familiars, and its main draw/recursion engine. The cuts
reduce narrow graveyard hate and some redundant sacrifice/draw and top-end slots. This is a
resource-and-board-control plan for Grixis, not an instruction to abandon its reachable sweepers.

### Mono-Blue Terror

Pest trades Weather and additional-cost removal for instant interaction, creature recovery and
protection from targeted bounce/tap effects. Safekeeping does not protect a spell from Counterspell.
Snuff Out still owes any applicable ward payment. Both Chainer's Edicts remain as a separate
answer to a sparse threat board, and all three landcycling Ents remain.

Blue gains free removal for Essence Warden/Carrier Thrall and a persistent Murmuring Mystic threat.
It retains all eight Terror/Serpent threats, all four Counterspells, all four Dispels, one Artful
Dodge, one Deem Inferior, all cantrips and its original mana base. The two Sleep of the Dead copies
and one copy each of the redundant evasion/bounce effects make room. Annul and Hydroblast do not
answer the opposing main strategy; Spreading Seas is not substituted for the list's cheap draw
engine against a deck with many basic lands.

### Monster Tron

Pest remains the pressure deck while adding cheap answers to nonblack creatures and destructible
artifacts, with protection against targeted removal. It keeps a mixture of targeted and sacrifice
removal. The two slow four-drops and Weather slots give way to interaction; neither land count nor
landcycling Ent count changes.

Tron gains a third Breath Weapon for the small-creature engine, unconditional Scour from Existence
for a grown payoff, and Call Damage Control to recover threats and artifacts. Its twelve Tron
lands, other lands, Crop Rotations, Ancient Stirrings, Expedition Maps, landcycling creatures,
mana filters, four Maelstrom Colossus and four Pinnacle Kill-Ships remain. Two Candy Trails, one
draw spell and two large creatures make room. Scour's full cost and Call's actual target/mode
rules remain part of qualification; no shortcut payment or recovery behavior is assumed.

### Spy Combo

Pest shifts sorcery-speed removal and two slow threats toward disrupting the mana creatures at
instant speed. Fumes does not kill every defender, and Snuff Out cannot target Balustrade Spy or
Lotleth Giant because they are black. All four Weather the Storm remain available against a
spell-heavy combo turn. The plan grants no ability to see hidden cards or select a response after
learning an unexposed outcome.

Spy keeps its land-removal, mana-defender, library-emptying and reanimation package. It replaces
Masked Vandal's low-value Food interaction with a fourth Mesmeric Fiend, a third Nyxborn Hydra
and Acorn Harvest. Fiend contests interaction; Hydra supplies a non-reanimation threat; Harvest
supplies bodies and a graveyard-accessible way to produce more sacrifice material. Nylea's
Disciple, Flaring Pain and graveyard hate are not inserted merely because they are present in
the sideboard. Their cards remain preserved in the exact 75 and their existing qualification is
not removed.

## Acceptance and continuation boundary

Independent strategy review accepted these exact inputs and their digest freeze before execution.
Source `3add5c455608ddd5115acfbde7c23408f802de46` then passed all ten construction cases, one per
table row, against the fixed input manifest and existing exchange compiler. The original XML has
ten distinct passing cases with no failures, errors or skips; the non-author artifact audit verified
the exact source and all twelve file pins. This accepts the bounded construction component only.

The receiving source still needs the complete sideboard pilots, every reachable choice and
interaction, replay/runtime and journal checks, canonical post-block receiving qualification,
and independent admission. A pilot's preference never removes a reachable interaction from that
work. Card registry presence or this policy table alone does not establish combined support.

The existing stopping rule continues to permit only one balanced four-game postboard smoke per
frozen matchup after its prerequisites pass, with no automatic postboard replication. Historical
81 games remain quarantined. This change creates no seed vector, official initialization, action,
outcome, replacement authority or deck-strength conclusion.
