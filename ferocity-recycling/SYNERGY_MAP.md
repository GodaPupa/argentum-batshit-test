# Ferocity Recycling — bounded synergy map v0.1

Prepared before randomized development outcomes. The map explores functions within the three declared families, not additional families or a mandate to include every attractive interaction. Exact card texts and common witnesses are in `sources/`; rules qualifications are in `RULES_LEGALITY_AUDIT.md`. A described legal line is not evidence that the pilot or engine can execute it.

## Functional card-pool audit

| Function | Audited cards/packages | Strategic question and cost |
|---|---|---|
| Creature damage sweepers | Krark-Clan Shaman; Crypt Rats | Shaman requires disposable artifacts and misses flying creatures; Rats requires black mana, hits all creatures, and damages both players. Ferocity does not make either wipe one-sided. |
| Entry/death value | Refurbished Familiar; Baleful Strix; Shambling Ghast; Accursed Marauder; Auramancer; Spirited Companion; Thraben Inspector | Prefer cards that matter independently. Familiar flies over Shaman and pressures hands; Strix draws on entry, flies, and already has deathtouch; Ghast trades weakly unless its death mode matters; Marauder's sacrifice instruction can hurt its own controller; Companion/Inspector replace themselves but expose bodies to sweepers. |
| Sacrifice draw and competing card flow | Eviscerator's Insight; Reckoner's Bargain; Fanatical Offering; Thoughtcast | Insight supplies costly later flashback; Bargain buys life using the sacrificed permanent's actual mana value; Offering costs three mana and creates a Map. Thoughtcast offers two cards without sacrificing a permanent but requires blue and enough actual artifacts for affinity. Deadly Dispute is banned. Sacrifice and mana remain costs even if Ferocity returns the creature. |
| Artifact fodder/recovery | Ichor Wellspring; Blood Fountain; Lembas; Myr Retriever; Candy Trail; Nihil Spellbomb; artifact lands | Wellspring rewards entry and death; Fountain supplies a Blood and later creature recovery; Lembas buffers life but must be sacrificed through its own ability to gain life. Retriever has a real opportunity cost as a low-pressure 1/1 and cannot recover Ferocity. |
| Permanent selection/recovery | Malevolent Rumble; Evolution Witness; Auramancer; Kor Skyfisher; Omen of the Dead | Rumble can select Ferocity because it is a permanent. Witness requires counters to trigger recovery. Auramancer can recover it after a return; Skyfisher can rescue an attached Aura or rebuy other entry abilities. These effects require mana and replace other threats/interaction. |
| Closing games | Refurbished Familiar; Bone Picker; Myr Enforcer; Troll of Khazad-dûm; Avenging Hunter; Thorn of the Black Rose; Makeshift Munitions | Flyers fit Shaman; large ground creatures do not survive deathtouch sweeps. Rats' repeated damage is a clock only while its own pilot survives. Munitions converts actual spare material and mana into damage; it is not an automatic win condition. |
| Racing/lifegain | Toxin Analysis; Reckoner's Bargain; Lembas; Campfire; Pulse of Murasa; Weather the Storm; Generous Ent | Ferocity provides no lifegain. Bargain/Ent/Lembas make life an actual resource; Campfire spends a card plus repeated mana. Pulse gains six and returns a creature/land to hand but cannot recover Ferocity. |
| Efficient standalone interaction | Cast Down; Defile; Snuff Out; Galvanic Blast; Accursed Marauder; Journey to Nowhere; new SLZ Cut Down/Dispatch | Compare clean removal against elaborate wipe preparation. Snuff Out's alternative life cost worsens some races. Dispatch requires actual metalcraft for exile; Cut Down is size-limited. Current SLZ admission was checked separately. |
| Graveyard resilience | Ordinary threats/removal; cheap new copies; Blood Fountain; Omen; Skyfisher; Nihil Spellbomb; Soul-Guide Lantern | Recovery can be stopped in its real window. A deck with a live standalone clock and card flow can function after hate; adding more recursive cards is not by itself resilience. |
| Mana | Basics; Drossforge/Goldmire/Mistvault/Silverbluff Bridge; Vault of Whispers/Great Furnace/Ancient Den/Seat of the Synod; Darksteel Citadel; Khalni Garden; Jungle Hollow; Haunted Mire; Bojuka Bog; Witch's Cottage | Tapped lands cost turns; artifact lands are mana, affinity support, and potential sacrificial assets, but cannot supply all three benefits after being sacrificed. Each splash must earn its colored sources. Rats' activation excludes colorless Spawn mana. |

Myr Retriever and a fragile artifact-trigger clock were considered but do not enter the first artifact family's candidate allocation merely to advertise recycling. Evolution Witness was considered but is excluded from the first Rats family because its initial cast/adapt/recast-Ferocity line is expensive. Auramancer is confined to the deliberately different white recovery family. No-Ferocity comparisons remove cards whose only purpose was the lost package.

The current SLZ admission changes were incorporated before the initial development freeze. Baleful Strix and Thoughtcast motivate a Grixis configuration inside the artifact-control family, while Cut Down and Dispatch enter appropriate black and white shells. This does not add a fourth family. Ferocity on Strix supplies preservation and +1 power, but its deathtouch is redundant; that overlap must be counted as a cost when comparing the combined package.

## Resource-accounting conventions

Every sequence states its starting battlefield and hand. A creature already on the battlefield is an invested card, not a free resource; its original casting cost is shown separately where relevant. A returned creature counts once as retained material. The Ferocity card and mana used to retain it are always charged. A Clue is an artifact with a paid future draw option, not already a drawn card. Graveyard cards and tokens are tracked by zone and identity.

All lines assume legal targets, available priority, and no response unless the response section says otherwise. Normal state-based actions and trigger processing occur after each resolution or completed cost payment. Damage is never converted directly into a declared win without checking life totals, prevention, actual opposing threats, and the game-ending rules.

## S1 — one Ferocity Shaman sweep

**Start:** your ordinary 1/1 Krark-Clan Shaman and at least one controlled artifact are on the battlefield; Ferocity is in hand; two mana including B or G are available. Shaman's previous cast cost was R.

**Sequence:** cast Ferocity targeting Shaman for `1{B/G}`; after it resolves, activate Shaman by sacrificing the artifact. On resolution Shaman deals one damage to every nonflying creature. Its deathtouch destroys susceptible friendly and opposing ground creatures, including itself. Ferocity's death trigger is stacked after state-based actions; the unattached Aura goes to its owner's graveyard. The trigger returns Shaman tapped under its owner's control.

**Net:** spend Ferocity, two mana, and one artifact; retain one tapped, fresh Shaman with no Aura or deathtouch. Lose any other susceptible friendly ground creatures. If the artifact was Wellspring, resolve its actual death draw trigger above the Shaman ability and count that one card. A Familiar/Bone Picker already flying survives; a Myr Enforcer dies to the deathtouch pulse. This is one return, not an infinite or self-sustaining engine.

**Windows/failures:** the opponent can remove the target in response to Ferocity, remove the Aura before the pulse, remove the source, prevent damage, or exile the dead Shaman while the return trigger is pending. Source removal after Ferocity resolved retains deathtouch for an already pending pulse through last-known information. Source removal before Ferocity resolves never grants it. Indestructible and flying remain relevant.

**Simpler comparison:** Toxin costs B and generates the artifact that can feed the pulse. It does not retain Shaman, but it gains actual life from damage and can preserve the existing artifact if the new Clue is sacrificed. A direct removal spell avoids preparation and collateral loss when only one creature needs answering.

## S2 — queued pulses are not free repeatability

**Start:** Ferocity already enchants Shaman; two artifacts are available. **Sequence:** activate twice before the first pulse resolves, sacrificing a separate artifact each time. The top pulse kills the old Shaman and other susceptible ground creatures. Its death return resolves before the lower pulse. The lower pulse still originates from the old Shaman and uses that source's last-known deathtouch; it can kill the newly returned Shaman and any newly created ground creatures.

**Net:** two artifacts spent and generally no Shaman left after the second pulse. Ferocity did not grant a second return to its new battlefield identity. The same identity issue applies to multiple Crypt Rats activations. The pilot may need two pulses to answer death-spawned threats, but must charge for losing the returned creature.

**Failure modes/comparison:** an engine that transfers the old ability to the new source, loses LKI, or silently moves the return below the lower pulse produces false outcomes. Toxin's old source likewise retains deathtouch/lifelink for queued pulses; its life gains use the recipients still present for each pulse. Each pulse has its own response window.

## S3 — Ferocity Crypt Rats, with an honest life total

**Start:** ordinary Crypt Rats on the battlefield, Ferocity in hand, at least `1{B/G}` plus B available. Rats' initial casting cost was `2B`. **Sequence:** enchant Rats, then activate X=1 using black mana. It deals one to each creature, including flyers and itself, and each player. Susceptible creatures die. Rats returns tapped if its trigger resolves and the game has not already ended.

**Net:** Ferocity and three activation/preparation mana spent; one life lost by each player; one fresh tapped Rats retained; all susceptible friendly creatures lost. For general X, add X black mana and X damage to each player. The returned Rats can activate immediately because there is no tap cost, but has no deathtouch/lifelink and needs new black mana.

**Windows/failures:** the Aura, source, and graveyard windows are all relevant. Being behind on life is not solved by returning Rats. Both players at lethal life may draw before return triggers resolve. A Spawn generated by Rumble cannot pay X; a Treasure producing B can.

**Simpler comparison:** B for Toxin plus B for X=1 costs one less mana than Ferocity plus activation, loses Rats, creates a Clue, and gains life from the actual damage to all affected creatures and both players. Lifelink's simultaneous gain can change whether the Rats pilot survives. Toxin plus Not Dead After All uses two spells and two black mana before activation, but adds both return and Wicked Role benefits. Compare complete costs and card use.

## S4 — Familiar or Ghast plus sacrifice draw

**Start:** Familiar or Ghast on the battlefield; Ferocity and Insight/Bargain in hand; four mana including the required hybrid and B. **Sequence:** enchant the creature for two; cast sacrifice draw for `1B`, sacrificing it as the additional cost. The creature is already dead when anyone can respond. Stack its own death trigger, if any, and Ferocity's return above the draw spell in the legal controller/APNAP order. Returning Familiar creates a new entry trigger; returning Ghast does not itself create a Treasure. Resolve those triggers and then the draw spell.

**Net:** two spells spent from hand, two cards drawn; one creature retained tapped; Ferocity is in the graveyard. Familiar forces an actual opponent discard or draws a card if that opponent cannot discard. Ghast gives one chosen death mode: Treasure or a legal creature's -1/-1. Bargain additionally gains life equal to the sacrificed card's mana value: four for Familiar even when affinity paid only B, one for Ghast. Insight supplies a later flashback option, but flashback still costs `4B` and a sacrifice.

**Windows/failures:** Aura-target response creates the usual exposure. Countering the draw spell does not refund the sacrificed creature; it also does not erase already-triggered return/death abilities. Graveyard removal can stop return. Ghast's mode must be chosen legally, and the -1/-1 mode is not damage/deathtouch.

**Simpler comparison:** Not Dead After All plus the same sacrifice draw costs three mana and can return the creature with a Wicked Role, at the same two-spell expenditure. Ordinary Wellspring sacrifice draw already converts an artifact into cards without needing an Aura. A Familiar cast again from hand still pays its actual post-sacrifice affinity cost.

## S5 — Auramancer recovers the spent Aura

**Start:** Auramancer on the battlefield; Ferocity and Eviscerator's Insight in hand; four mana with `1{B/G}` and `1B`. Auramancer's earlier cast cost was `2W`.

**Sequence:** attach Ferocity, then cast Insight sacrificing Auramancer. Ferocity goes to the graveyard when state-based actions see it unattached. Its return trigger resolves above Insight. Auramancer re-enters tapped, and its new entry trigger can target that Ferocity in the graveyard. Resolve the entry trigger to return Ferocity to hand, then resolve Insight to draw two.

**Net:** four mana; Insight moves from hand to graveyard; Ferocity ends back in hand; two cards are drawn; Auramancer remains as a tapped new object. Relative to the starting hand, this is one additional card, with the full four-mana and two-card setup charged. Repeating with the same Insight through flashback costs seven mana (two for the Aura plus five for flashback), consumes the graveyard Insight into exile, and still requires sacrificing Auramancer. Repeating with another Insight requires another actual draw spell.

**Windows/failures:** any of Aura-target removal, countering Ferocity, removing the graveyard creature, countering the return/entry ability where supported, or removing the targeted Aura from the graveyard breaks the relevant part. Instant-speed graveyard hate remains usable before Auramancer's entry trigger resolves. Graveyard shroud effects matter differently to Ferocity's untargeted return and Auramancer's targeted recovery.

**Simpler comparison:** an independently useful draw/removal/threat package may make spending four mana to preserve and re-use a 2/2 unnecessary. Not Dead After All saves the Auramancer for one mana but Auramancer cannot retrieve that instant. The no-Ferocity white shell must replace dependent Auramancer slots, not retain them as intentional dead weight.

**Loop claim limit:** an unlimited free sacrifice outlet would still require a new two-mana Aura cast each cycle. Ashnod's Altar alone produces colorless mana and cannot supply the hybrid colored payment. No selected package restores every resource indefinitely; no infinite engine is claimed.

## S6 — permanent selection and recovery

**Rumble:** pay `1G` and reveal the actual top four; choose at most one permanent for hand and put the rest into the graveyard, then create a 0/1 Eldrazi Spawn. Ferocity is eligible for selection; Toxin and Not Dead After All are not. Choosing Ferocity forgoes another revealed permanent, and selecting it is not an extra card beyond Rumble's ordinary selection. Spawn mana is colorless and sacrifice removes a creature/fodder asset. Rumble is a source of access and resources, not a guarantee that an Aura target exists.

**Witness:** casting Evolution Witness costs `2G`; adapting costs `1G` and places two counters only if it has none. Its counter trigger can recover Ferocity to hand, after which recasting Ferocity costs two. First cast-plus-adapt-plus-Aura totals seven mana. A Ferocity return resets Witness to a new object with no counters, but does not place counters or trigger recovery by itself; another adapt and its colored mana are needed. Exile or graveyard removal can interrupt recovery. The simpler alternative is another threat/selection spell or Pulse of Murasa when recovering a creature and gaining life is the useful job.

**Skyfisher/Omen:** Skyfisher can return an attached Ferocity to its owner's hand; the surviving creature immediately loses that Aura's bonuses and protection. Alternatively, returning Omen permits another B Omen cast to return a creature card to hand, followed by the creature's own recasting cost. Skyfisher costs `1W`, and its mandatory own-permanent bounce is real tempo. The Omen/Skyfisher package functions without Ferocity and is therefore a legitimate no-Ferocity architecture rather than Ferocity-specific credit.

## S7 — artifact recovery and game closure

Myr Retriever's death returns **another** target artifact card to hand. It cannot target itself, Ferocity, or an artifact that has not reached the graveyard by target selection. Ferocity can retain a sacrificed Retriever, but the Aura is then spent; the artifact still needs its casting cost. If Retriever is sacrificed to Shaman while Ferocity enchants Retriever, the return occurs above Shaman's pulse; an ordinary 1-damage pulse can then kill the fresh 1/1 Retriever again. Count both real death triggers where legal, the final loss of Retriever, and all actual targets. This is not the deathtouch Shaman line because that Shaman was not enchanted.

Makeshift Munitions requires its initial `1R` cast and then one mana plus one artifact or creature per damage. Returning that creature through Ferocity spends a two-mana Aura per preservation unless an independently paid recovery line exists. Retaining Munitions and a Shaman is not a deterministic kill without enough mana, fodder, turns, and uninterrupted life pressure. Large attacks/flyers may be the more efficient finish. The program measures actual wins and stalled games instead of assigning a value score to successful recycling.

## Fixed disruption scenarios and diagnostic boundaries

The initial mechanical/policy scenarios must separately cover early ground pressure, a protected or indestructible threat, flying pressure, removal while Ferocity is on the stack, Aura removal before a pending pulse, a counterspell, graveyard hate during return, queued pulses creating a second self-wipe, and recovery after a sweep. Include a noncreature engine whose clock is unaffected by clearing creatures.

Record the cards and mana spent, friendly and opposing losses, actual return and subsequent action, life change, and whether the line left a credible clock. A successful fixture demonstrates only that particular mechanic or policy decision. It is not a randomized matchup sample; a card's presence in a win does not establish causal improvement. Candidate and no-Ferocity pilots require equivalent development, and any counterfactual credit requires the predeclared qualified replay method.
