package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.Deck

const val IZZET_SCIENCE_V07_CONTROL_SHA256 =
    "726f5e9458b46dda30b33a6ce9f3c3237b25d6e11b81dac85308c3065c108a01"
const val IZZET_SCIENCE_COMMANDER = "Izzet Guildmage"
const val VETERAN_BEASTRIDER_COMMANDER = "Veteran Beastrider"

private val IZZET_V07_MAIN = linkedMapOf(
    "Snow-Covered Island" to 19, "Snow-Covered Mountain" to 7,
    "Command Tower" to 1, "Ash Barrens" to 1, "Evolving Wilds" to 1,
    "Terramorphic Expanse" to 1, "Izzet Boilerworks" to 1, "Volatile Fjord" to 1,
    "Swiftwater Cliffs" to 1, "Silverbluff Bridge" to 1, "Lonely Sandbar" to 1,
    "Forgotten Cave" to 1, "Everflowing Chalice" to 1, "Fellwar Stone" to 1,
    "Mind Stone" to 1, "Star Compass" to 1, "Sky Diamond" to 1, "Fire Diamond" to 1,
    "Izzet Signet" to 1, "Network Terminal" to 1, "Desperate Ritual" to 1,
    "Lava Spike" to 1, "Ideas Unbound" to 1, "Eye of Nowhere" to 1,
    "Dramatic Reversal" to 1, "High Tide" to 1, "Snap" to 1, "Seething Song" to 1,
    "Muddle the Mixture" to 1, "Merchant Scroll" to 1, "Dizzy Spell" to 1,
    "Drift of Phantasms" to 1, "Ponder" to 1, "Preordain" to 1, "Brainstorm" to 1,
    "Consider" to 1, "Opt" to 1, "Impulse" to 1, "Strategic Planning" to 1,
    "Faithless Looting" to 1, "Thrill of Possibility" to 1, "Frantic Search" to 1,
    "Treasure Cruise" to 1, "Counterspell" to 1, "Arcane Denial" to 1, "Negate" to 1,
    "Dispel" to 1, "Memory Lapse" to 1, "Deprive" to 1, "Prohibit" to 1,
    "Spell Pierce" to 1, "Turn Aside" to 1, "Lightning Bolt" to 1,
    "Galvanic Blast" to 1, "Skred" to 1, "Flame Slash" to 1, "Fire // Ice" to 1,
    "Into the Roil" to 1, "Blink of an Eye" to 1, "Echoing Truth" to 1, "Abrade" to 1,
    "Shattering Pulse" to 1, "Capsize" to 1, "Rolling Thunder" to 1,
    "Kaervek's Torch" to 1, "Goblin Electromancer" to 1,
    "Ornithopter of Paradise" to 1, "Silver Myr" to 1, "Iron Myr" to 1,
    "Archaeomancer" to 1, "Mnemonic Wall" to 1, "Izzet Chronarch" to 1,
    "Murmuring Mystic" to 1, "Pieces of the Puzzle" to 1, "Lose Focus" to 1,
)

private val VETERAN_BEASTRIDER_MAIN = linkedMapOf(
    "Avacyn's Pilgrim" to 1, "Boreal Druid" to 1, "Elvish Mystic" to 1,
    "Fyndhorn Elves" to 1, "Gene Pollinator" to 1, "Jaspera Sentinel" to 1,
    "Llanowar Elves" to 1, "Nyxborn Hydra" to 1, "Beastrider Vanguard" to 1,
    "Druid of the Cowl" to 1, "Gold Myr" to 1, "Goobbue Gardener" to 1,
    "Heart Warden" to 1, "Ilysian Caryatid" to 1, "Leafkin Druid" to 1,
    "Nightshade Dryad" to 1, "Ornithopter of Paradise" to 1, "Poison Dart Frog" to 1,
    "Rootrider Faun" to 1, "Three Tree Rootweaver" to 1,
    "Ulvenwald Captive // Ulvenwald Abomination" to 1, "Whisperer of the Wilds" to 1,
    "Wose Pathfinder" to 1, "Crusader of Odric" to 1, "Deepwood Denizen" to 1,
    "Heliod's Pilgrim" to 1, "Llanowar Visionary" to 1, "Scion of the Wild" to 1,
    "Brightwood Tracker" to 1, "Owlbear" to 1, "Shrine Steward" to 1,
    "Totem-Guide Hartebeest" to 1, "Alabaster Host Intercessor" to 1,
    "Balamb T-Rexaur" to 1, "Eagles of the North" to 1, "Elvish Aberration" to 1,
    "Generous Ent" to 1, "Orchard Strider" to 1, "Salt Road Packbeast" to 1,
    "Shepherding Spirits" to 1, "Slavering Branchsnapper" to 1, "Timberland Ancient" to 1,
    "Guardian Naga // Banishing Coils" to 1, "Shardless Outlander" to 1,
    "Bushwhack" to 1, "Stave Off" to 1, "Vines of Vastwood" to 1, "Bite Down" to 1,
    "Coordinated Maneuver" to 1, "Cosmic Hunger" to 1, "Destroy Evil" to 1,
    "Master's Rebuke" to 1, "Ram Through" to 1, "Sheltering Word" to 1,
    "Slash of Light" to 1, "Thraben Charm" to 1, "Afterlife" to 1, "Crib Swap" to 1,
    "Generous Gift" to 1, "Prismatic Strands" to 1, "Shower of Arrows" to 1,
    "Bonder's Ornament" to 1, "Magnifying Glass" to 1, "Colossal Dreadmask" to 1,
    "Pinnacle Kill-Ship" to 1, "Spirit Link" to 1, "Benevolent Blessing" to 1,
    "Cho-Manno's Blessing" to 1, "Temporal Isolation" to 1, "Armadillo Cloak" to 1,
    "Snake Umbra" to 1, "Arctic Treeline" to 1, "Blossoming Sands" to 1,
    "Command Tower" to 1, "Forest" to 12, "Forge of Heroes" to 1, "Opal Palace" to 1,
    "Path of Ancestry" to 1, "Plains" to 5, "Radiant Grove" to 1,
    "Selesnya Sanctuary" to 1, "Suburban Sanctuary" to 1, "The Fair Basilica" to 1,
    "The Hunter Maze" to 1,
)

data class IzzetVeteranEngineReadiness(
    val scope: String = "REGISTRY_AND_PDH_ENGINE_COVERAGE_ONLY",
    val officialGamesAuthorized: Int = 0,
    val officialSeedsConsumed: Int = 0,
    val outcomeExposure: Int = 0,
)

object IzzetScienceVeteranBeastriderEngineReadiness {
    val izzetCounts: Map<String, Int> get() = IZZET_V07_MAIN.toMap()
    val veteranCounts: Map<String, Int> get() = VETERAN_BEASTRIDER_MAIN.toMap()

    fun izzetDeck(): Deck = Deck.of(*IZZET_V07_MAIN.map { it.key to it.value }.toTypedArray())
    fun veteranDeck(): Deck = Deck.of(*VETERAN_BEASTRIDER_MAIN.map { it.key to it.value }.toTypedArray())

    fun validationErrors(
        readiness: IzzetVeteranEngineReadiness,
        registry: CardRegistry? = null,
    ): List<String> = buildList {
        if (IZZET_V07_MAIN.values.sum() != 99) add("Izzet main must contain 99 cards")
        if (VETERAN_BEASTRIDER_MAIN.values.sum() != 99) add("Veteran main must contain 99 cards")
        if (readiness.scope != "REGISTRY_AND_PDH_ENGINE_COVERAGE_ONLY") add("scope mismatch")
        if (readiness.officialGamesAuthorized != 0) add("official games must remain unauthorized")
        if (readiness.officialSeedsConsumed != 0) add("official seeds must remain unconsumed")
        if (readiness.outcomeExposure != 0) add("outcome exposure must remain zero")
        registry?.let { cards ->
            (IZZET_V07_MAIN.keys + IZZET_SCIENCE_COMMANDER).forEach { name ->
                if (cards.getCard(name) == null) add("unresolved Izzet card: $name")
            }
            (VETERAN_BEASTRIDER_MAIN.keys + VETERAN_BEASTRIDER_COMMANDER).forEach { name ->
                if (cards.getCard(name) == null) add("unresolved Veteran Beastrider card: $name")
            }
        }
    }

    fun executionBlockers(registry: CardRegistry): List<String> = buildList {
        addAll(validationErrors(IzzetVeteranEngineReadiness(), registry))
        add("PDH commander-zone initialization not qualified")
        add("PDH commander recast/tax semantics not qualified")
        add("16-damage commander-loss accounting not qualified in gameplay engine")
        add("30-life PDH game initialization not qualified")
        add("Phase-29 event-ledger extraction from full engine game not qualified")
    }
}
