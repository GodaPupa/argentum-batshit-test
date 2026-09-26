package com.wingedsheep.gym.matchup

/**
 * Prospective public-identity boarding choices for the five existing Pest Tier-1 matchups.
 * Independent strategy review, exact digest freeze and receiving qualification are separate.
 * No hand, seat, play/draw assignment, game, seed, future draw or outcome is an input.
 */
object PestControlTierOnePostboardPolicies {
    fun exchangeFor(deck: PestTierOneDeck, opponent: PestTierOneDeck): PestPostboardExchange = when {
        deck == PestTierOneDeck.PEST_CONTROL && opponent == PestTierOneDeck.MONO_RED_MADNESS ->
            PestPostboardExchange(
                toMain = linkedMapOf("Suffocating Fumes" to 3, "Pulse of Murasa" to 2),
                toSideboard = linkedMapOf("Chainer's Edict" to 2, "Bone Shards" to 2, "Generous Ent" to 1),
            )
        deck == PestTierOneDeck.MONO_RED_MADNESS && opponent == PestTierOneDeck.PEST_CONTROL ->
            PestPostboardExchange(
                toMain = emptyMap(),
                toSideboard = emptyMap(),
            )
        deck == PestTierOneDeck.PEST_CONTROL && opponent == PestTierOneDeck.GRIXIS_AFFINITY ->
            PestPostboardExchange(
                toMain = linkedMapOf("Nature's Claim" to 3, "Snuff Out" to 3, "Pulse of Murasa" to 2, "Tamiyo's Safekeeping" to 2),
                toSideboard = linkedMapOf("Weather the Storm" to 4, "Bone Shards" to 2, "Chainer's Edict" to 2, "Fierce Witchstalker" to 2),
            )
        deck == PestTierOneDeck.GRIXIS_AFFINITY && opponent == PestTierOneDeck.PEST_CONTROL ->
            PestPostboardExchange(
                toMain = linkedMapOf("Duress" to 4, "Unexpected Fangs" to 2),
                toSideboard = linkedMapOf("Nihil Spellbomb" to 2, "Fanatical Offering" to 2, "Utrom Monitor" to 1, "Galvanic Blast" to 1),
            )
        deck == PestTierOneDeck.PEST_CONTROL && opponent == PestTierOneDeck.MONO_BLUE_TERROR ->
            PestPostboardExchange(
                toMain = linkedMapOf("Snuff Out" to 3, "Pulse of Murasa" to 2, "Tamiyo's Safekeeping" to 2),
                toSideboard = linkedMapOf("Weather the Storm" to 4, "Bone Shards" to 2, "Fierce Witchstalker" to 1),
            )
        deck == PestTierOneDeck.MONO_BLUE_TERROR && opponent == PestTierOneDeck.PEST_CONTROL ->
            PestPostboardExchange(
                toMain = linkedMapOf("Gut Shot" to 3, "Murmuring Mystic" to 1),
                toSideboard = linkedMapOf("Sleep of the Dead" to 2, "Artful Dodge" to 1, "Deem Inferior" to 1),
            )
        deck == PestTierOneDeck.PEST_CONTROL && opponent == PestTierOneDeck.MONSTER_TRON ->
            PestPostboardExchange(
                toMain = linkedMapOf("Nature's Claim" to 3, "Snuff Out" to 3, "Tamiyo's Safekeeping" to 2),
                toSideboard = linkedMapOf("Weather the Storm" to 4, "Fierce Witchstalker" to 2, "Bone Shards" to 1, "Chainer's Edict" to 1),
            )
        deck == PestTierOneDeck.MONSTER_TRON && opponent == PestTierOneDeck.PEST_CONTROL ->
            PestPostboardExchange(
                toMain = linkedMapOf("Breath Weapon" to 1, "Scour from Existence" to 2, "Call Damage Control" to 2),
                toSideboard = linkedMapOf("Candy Trail" to 2, "Unfathomable Truths" to 1, "Bramble Wurm" to 1, "Rooftop Percher" to 1),
            )
        deck == PestTierOneDeck.PEST_CONTROL && opponent == PestTierOneDeck.SPY_COMBO ->
            PestPostboardExchange(
                toMain = linkedMapOf("Suffocating Fumes" to 3, "Snuff Out" to 3),
                toSideboard = linkedMapOf("Chainer's Edict" to 2, "Bone Shards" to 2, "Fierce Witchstalker" to 2),
            )
        deck == PestTierOneDeck.SPY_COMBO && opponent == PestTierOneDeck.PEST_CONTROL ->
            PestPostboardExchange(
                toMain = linkedMapOf("Mesmeric Fiend" to 1, "Nyxborn Hydra" to 1, "Acorn Harvest" to 1),
                toSideboard = linkedMapOf("Masked Vandal" to 3),
            )
        else -> throw IllegalArgumentException("Only the five frozen Pest/opponent pairs have a boarding policy")
    }

    /** Pure construction only; this does not select a seed, freeze a pilot or grant admission. */
    fun prepare(deck: PestTierOneDeck, opponent: PestTierOneDeck): PreparedPestPostboardDeck =
        PestControlTierOnePostboardExchange.prepare(deck, exchangeFor(deck, opponent))
}
