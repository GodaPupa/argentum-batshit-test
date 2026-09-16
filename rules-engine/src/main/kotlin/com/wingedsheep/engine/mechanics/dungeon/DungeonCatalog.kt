package com.wingedsheep.engine.mechanics.dungeon

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.AddCountersEffect
import com.wingedsheep.sdk.scripting.effects.CantAttackEffect
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.CreatePredefinedTokenEffect
import com.wingedsheep.sdk.scripting.effects.CreateTokenEffect
import com.wingedsheep.sdk.scripting.effects.DrainLifeEffect
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.ForEachPlayerEffect
import com.wingedsheep.sdk.scripting.effects.GainLifeEffect
import com.wingedsheep.sdk.scripting.effects.LoseLifeEffect
import com.wingedsheep.sdk.scripting.effects.ModifyStatsEffect
import com.wingedsheep.sdk.scripting.effects.PayOrSufferEffect
import com.wingedsheep.sdk.scripting.effects.SacrificeEffect
import com.wingedsheep.sdk.scripting.effects.ScryEffect
import com.wingedsheep.sdk.scripting.effects.SelectTargetEffect
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.values.DynamicAmount

data class DungeonRoom(
    val id: String,
    val name: String,
    val nextRoomIds: List<String>,
    val effect: Effect,
)

data class DungeonDefinition(
    val id: String,
    val name: String,
    val firstRoomId: String,
    val rooms: Map<String, DungeonRoom>,
)

/** Canonical AFR ordinary-dungeon topology and room instructions. */
object DungeonCatalog {
    const val LOST_MINE = "lost-mine-of-phandelver"
    const val MAD_MAGE = "dungeon-of-the-mad-mage"
    const val TOMB = "tomb-of-annihilation"

    val ordinaryDungeons: List<DungeonDefinition> = listOf(
        lostMine(),
        madMage(),
        tomb(),
    )

    private val byId = ordinaryDungeons.associateBy { it.id }

    fun dungeon(id: String): DungeonDefinition? = byId[id]

    private fun room(
        id: String,
        name: String,
        next: List<String> = emptyList(),
        effect: Effect,
    ) = DungeonRoom(id, name, next, effect)

    private fun chooseCreatureThen(effect: (EffectTarget) -> Effect): Effect {
        val slot = "dungeonRoomTarget"
        return CompositeEffect(
            listOf(
                SelectTargetEffect(TargetCreature(filter = TargetFilter.Creature), slot),
                effect(EffectTarget.PipelineTarget(slot)),
            )
        )
    }

    private fun chooseYourCreatureThen(effect: (EffectTarget) -> Effect): Effect {
        val slot = "dungeonRoomTarget"
        return CompositeEffect(
            listOf(
                SelectTargetEffect(TargetCreature(filter = TargetFilter.CreatureYouControl), slot),
                effect(EffectTarget.PipelineTarget(slot)),
            )
        )
    }

    private fun lostMine(): DungeonDefinition {
        val rooms = listOf(
            room("cave-entrance", "Cave Entrance", listOf("goblin-lair", "mine-tunnels"), ScryEffect(1)),
            room(
                "goblin-lair", "Goblin Lair", listOf("storeroom", "dark-pool"),
                CreateTokenEffect(1, 1, 1, setOf(Color.RED), setOf("Goblin")),
            ),
            room(
                "mine-tunnels", "Mine Tunnels", listOf("dark-pool", "fungi-cavern"),
                CreatePredefinedTokenEffect("Treasure"),
            ),
            room(
                "storeroom", "Storeroom", listOf("temple-of-dumathoin"),
                chooseYourCreatureThen { AddCountersEffect(Counters.PLUS_ONE_PLUS_ONE, 1, it) },
            ),
            room(
                "dark-pool", "Dark Pool", listOf("temple-of-dumathoin"),
                DrainLifeEffect(1),
            ),
            room(
                "fungi-cavern", "Fungi Cavern", listOf("temple-of-dumathoin"),
                chooseCreatureThen { ModifyStatsEffect(-4, 0, it, Duration.UntilYourNextTurn) },
            ),
            room("temple-of-dumathoin", "Temple of Dumathoin", effect = DrawCardsEffect(1)),
        ).associateBy { it.id }
        return DungeonDefinition(LOST_MINE, "Lost Mine of Phandelver", "cave-entrance", rooms)
    }

    private fun madMage(): DungeonDefinition {
        val rooms = listOf(
            room("yawning-portal", "Yawning Portal", listOf("dungeon-level"), GainLifeEffect(1)),
            room("dungeon-level", "Dungeon Level", listOf("goblin-bazaar", "twisted-caverns"), ScryEffect(1)),
            room("goblin-bazaar", "Goblin Bazaar", listOf("lost-level"), CreatePredefinedTokenEffect("Treasure")),
            room(
                "twisted-caverns", "Twisted Caverns", listOf("lost-level"),
                chooseCreatureThen { CantAttackEffect(it, Duration.UntilYourNextTurn) },
            ),
            room("lost-level", "Lost Level", listOf("runestone-caverns", "muirals-graveyard"), ScryEffect(2)),
            room(
                "runestone-caverns", "Runestone Caverns", listOf("deep-mines"),
                Patterns.Exile.impulse(count = 2),
            ),
            room(
                "muirals-graveyard", "Muiral's Graveyard", listOf("deep-mines"),
                CreateTokenEffect(2, 1, 1, setOf(Color.BLACK), setOf("Skeleton")),
            ),
            room("deep-mines", "Deep Mines", listOf("mad-wizards-lair"), ScryEffect(3)),
            room(
                "mad-wizards-lair", "Mad Wizard's Lair",
                effect = Effects.Pipeline(
                    descriptionOverride =
                        "Draw three cards and reveal them. You may cast one of them without paying its mana cost"
                ) {
                    val cards = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(3)))
                    reveal(cards, fromZone = com.wingedsheep.sdk.core.Zone.LIBRARY, toZone = com.wingedsheep.sdk.core.Zone.HAND)
                    toHand(cards, revealed = true)
                    val chosen = chooseUpTo(
                        count = 1,
                        from = cards,
                        prompt = "You may choose one of those cards to cast without paying its mana cost",
                        showAllCards = true,
                    )
                    run(Effects.CastFromCollectionWithoutPayingCost(chosen.key))
                },
            ),
        ).associateBy { it.id }
        return DungeonDefinition(MAD_MAGE, "Dungeon of the Mad Mage", "yawning-portal", rooms)
    }

    private fun tomb(): DungeonDefinition {
        val rooms = listOf(
            room(
                "trapped-entry", "Trapped Entry", listOf("veils-of-fear", "oubliette"),
                ForEachPlayerEffect(Player.Each, listOf(LoseLifeEffect(1, EffectTarget.Controller))),
            ),
            room(
                "veils-of-fear", "Veils of Fear", listOf("sandfall-cell"),
                ForEachPlayerEffect(
                    Player.Each,
                    listOf(
                        PayOrSufferEffect(
                            cost = Costs.pay.Discard(),
                            suffer = LoseLifeEffect(2, EffectTarget.Controller),
                        )
                    ),
                ),
            ),
            room(
                "sandfall-cell", "Sandfall Cell", listOf("cradle-of-the-death-god"),
                ForEachPlayerEffect(
                    Player.Each,
                    listOf(
                        PayOrSufferEffect(
                            cost = Costs.pay.Sacrifice(
                                GameObjectFilter.Creature or GameObjectFilter.ArtifactOrLand
                            ),
                            suffer = LoseLifeEffect(2, EffectTarget.Controller),
                        )
                    ),
                ),
            ),
            room(
                "oubliette", "Oubliette", listOf("cradle-of-the-death-god"),
                CompositeEffect(
                    listOf(
                        Effects.Discard(),
                        SacrificeEffect(GameObjectFilter.Creature),
                        SacrificeEffect(GameObjectFilter.Artifact),
                        SacrificeEffect(GameObjectFilter.Land),
                    ),
                    descriptionOverride = "Discard a card and sacrifice a creature, an artifact, and a land",
                ),
            ),
            room(
                "cradle-of-the-death-god", "Cradle of the Death God",
                effect = CreateTokenEffect(
                    count = 1,
                    power = 4,
                    toughness = 4,
                    colors = setOf(Color.BLACK),
                    creatureTypes = setOf("God", "Horror"),
                    keywords = setOf(Keyword.DEATHTOUCH),
                    name = "The Atropal",
                    legendary = true,
                ),
            ),
        ).associateBy { it.id }
        return DungeonDefinition(TOMB, "Tomb of Annihilation", "trapped-entry", rooms)
    }
}
