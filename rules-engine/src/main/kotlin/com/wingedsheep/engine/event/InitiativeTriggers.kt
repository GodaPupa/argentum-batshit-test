package com.wingedsheep.engine.event

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.UndercityRoom
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.TriggeredAbility
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.ShuffleLibraryEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

internal object InitiativeTriggers {
    private fun synthetic(
        playerId: EntityId,
        description: String,
        effect: com.wingedsheep.sdk.scripting.effects.Effect,
        target: com.wingedsheep.sdk.scripting.targets.TargetRequirement? = null,
        context: TriggerContext = TriggerContext(triggeringPlayerId = playerId)
    ): PendingTrigger = PendingTrigger(
        ability = TriggeredAbility.create(
            trigger = EventPattern.StepEvent(Step.UPKEEP, Player.You),
            binding = TriggerBinding.ANY,
            effect = effect,
            targetRequirement = target,
            descriptionOverride = description
        ),
        sourceId = playerId,
        sourceName = "The Initiative",
        controllerId = playerId,
        triggerContext = context
    )

    fun venture(playerId: EntityId, reason: String): PendingTrigger = synthetic(
        playerId = playerId,
        description = reason,
        effect = Effects.VentureIntoUndercity(EffectTarget.SpecificEntity(playerId))
    )

    fun take(playerId: EntityId): PendingTrigger = synthetic(
        playerId = playerId,
        description = "That player takes the initiative",
        effect = Effects.TakeInitiative(EffectTarget.SpecificEntity(playerId))
    )

    fun room(playerId: EntityId, room: UndercityRoom): PendingTrigger {
        val (effect, target) = roomEffect(room)
        return synthetic(
            playerId = playerId,
            description = "${room.displayName} — ${effect.description}",
            effect = effect,
            target = target
        )
    }

    private fun roomEffect(
        room: UndercityRoom
    ): Pair<com.wingedsheep.sdk.scripting.effects.Effect, com.wingedsheep.sdk.scripting.targets.TargetRequirement?> =
        when (room) {
            UndercityRoom.SECRET_ENTRANCE ->
                Patterns.Library.searchLibrary(
                    filter = GameObjectFilter.BasicLand,
                    count = 1,
                    reveal = true
                ) to null
            UndercityRoom.FORGE ->
                Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 2, EffectTarget.ContextTarget(0)) to
                    Targets.Creature
            UndercityRoom.LOST_WELL ->
                Effects.Scry(2) to null
            UndercityRoom.TRAP ->
                Effects.LoseLife(5, EffectTarget.ContextTarget(0)) to Targets.Player
            UndercityRoom.ARENA ->
                Effects.Goad(EffectTarget.ContextTarget(0)) to Targets.Creature
            UndercityRoom.STASH ->
                Effects.CreateTreasure(1) to null
            UndercityRoom.ARCHIVES ->
                Effects.DrawCards(1) to null
            UndercityRoom.CATACOMBS ->
                Effects.CreateToken(
                    power = 4,
                    toughness = 1,
                    colors = setOf(Color.BLACK),
                    creatureTypes = setOf("Skeleton"),
                    keywords = setOf(Keyword.MENACE)
                ) to null
            UndercityRoom.THRONE_OF_THE_DEAD_THREE ->
                throneEffect() to null
        }

    private fun throneEffect(): com.wingedsheep.sdk.scripting.effects.Effect = Effects.Pipeline(
        descriptionOverride = "Reveal the top ten cards of your library. Put a creature card from among them " +
            "onto the battlefield with three +1/+1 counters on it. It gains hexproof until your next turn. " +
            "Then shuffle."
    ) {
        val revealed = gather(
            CardSource.TopOfLibrary(DynamicAmount.Fixed(10)),
            revealed = false,
            name = "undercityThroneReveal"
        )
        // Throne reveals the whole top ten before the creature is chosen. The creature choice is not
        // optional when one or more creatures are present; if none are present, the move simply
        // does nothing and the library is shuffled.
        reveal(revealed)
        val creatures = filter(
            revealed,
            GameObjectFilter.Creature,
            name = "undercityThroneCreatures"
        )
        ifNotEmpty(creatures) {
            val creature = chooseExactly(
                count = 1,
                from = creatures,
                prompt = "Choose a creature card to put onto the battlefield",
                showAllCards = true,
                name = "undercityThroneCreature"
            )
            move(
                creature,
                CardDestination.ToZone(Zone.BATTLEFIELD),
                revealed = true
            )
            run(Effects.AddCountersToCollection(creature.key, Counters.PLUS_ONE_PLUS_ONE, 3))
            run(
                Effects.GrantKeyword(
                    Keyword.HEXPROOF,
                    EffectTarget.PipelineTarget(creature.key),
                    Duration.UntilYourNextTurn
                )
            )
        }
        run(ShuffleLibraryEffect())
    }
}
