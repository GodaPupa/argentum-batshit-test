package com.wingedsheep.engine.mechanics.combat

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.combat.MustAttackDefenderThisTurnComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain

class MustAttackDefenderThisTurnTest : FunSpec({
    val creature = CardDefinition.creature(
        name = "Encore requirement witness",
        manaCost = ManaCost.parse("{1}{U}"),
        subtypes = setOf(Subtype("Salamander")),
        power = 2,
        toughness = 2,
    )

    fun registry() = CardRegistry().also { it.register(creature) }

    fun board(): Triple<GameState, List<EntityId>, EntityId> {
        val deck = Deck(cards = List(40) { creature.name })
        val initialized = GameInitializer(registry()).initializeGame(
            GameConfig(
                players = (1..3).map { PlayerConfig("Player $it", deck, 20) },
                skipMulligans = true,
                startingPlayerIndex = 0,
            )
        )
        val players = initialized.playerIds
        val token = EntityId.generate()
        val card = CardComponent(
            cardDefinitionId = creature.name,
            name = creature.name,
            manaCost = creature.manaCost,
            typeLine = creature.typeLine,
            baseStats = creature.creatureStats,
            ownerId = players[0],
        )
        val state = initialized.state
            .withEntity(
                token,
                ComponentContainer.of(
                    card,
                    OwnerComponent(players[0]),
                    ControllerComponent(players[0]),
                    MustAttackDefenderThisTurnComponent(players[2]),
                )
            )
            .addToZone(ZoneKey(players[0], Zone.BATTLEFIELD), token)
            .copy(
                phase = Phase.COMBAT,
                step = Step.DECLARE_ATTACKERS,
                activePlayerId = players[0],
                priorityPlayerId = players[0],
            )
        return Triple(state, players, token)
    }

    test("assigned Encore defender makes the token a mandatory attacker") {
        val (state, players, token) = board()
        val cards = registry()
        CombatManager(
            cards,
            com.wingedsheep.engine.mechanics.mana.ManaAbilitySideEffectExecutor(cards)
        ).getMandatoryAttackers(state, players[0]) shouldContain token
    }

    test("assigned Encore token cannot attack a different opponent while assigned opponent is legal") {
        val (state, players, token) = board()
        val result = ActionProcessor(registry()).process(
            state,
            DeclareAttackers(players[0], mapOf(token to players[1]))
        ).result
        result.isSuccess.shouldBeFalse()
    }

    test("assigned Encore token may attack its corresponding opponent") {
        val (state, players, token) = board()
        val result = ActionProcessor(registry()).process(
            state,
            DeclareAttackers(players[0], mapOf(token to players[2]))
        ).result
        result.isSuccess.shouldBeTrue()
    }
})
