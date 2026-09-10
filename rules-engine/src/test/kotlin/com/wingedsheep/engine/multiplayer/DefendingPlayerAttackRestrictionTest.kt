package com.wingedsheep.engine.multiplayer

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.CantAttackUnless
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * "This creature can't attack unless defending player controls an Island" (Dandân, Island Fish
 * Jasconius, Merchant Ship, Sea Monster, …) must be evaluated against the player actually being
 * attacked, not any opponent (CR 508.1 attack legality; the condition is
 * [Conditions.DefendingPlayerControlsLandType] → `Exists(Player.DefendingPlayer, …)`).
 *
 * Regression net for the audit finding that `CantAttackUnlessDefenderRule` computed the
 * defending player and then never used it — the old `Exists(Player.EachOpponent, …)` shape let
 * these creatures attack an Island-less player in multiplayer as long as *some* opponent
 * controlled an Island.
 */
class DefendingPlayerAttackRestrictionTest : FunSpec({

    val serpent = card("Test Serpent") {
        manaCost = "{U}{U}"
        typeLine = "Creature — Serpent"
        power = 4
        toughness = 1
        staticAbility {
            ability = CantAttackUnless(Conditions.DefendingPlayerControlsLandType("Island"))
        }
    }
    val island = CardDefinition.basicLand("Island", Subtype("Island"))
    val bear = CardDefinition.creature(
        name = "Filler Bear",
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = setOf(Subtype("Bear")),
        power = 2,
        toughness = 2
    )

    fun registry() = CardRegistry().also {
        it.register(serpent); it.register(island); it.register(bear)
    }

    fun GameState.withPermanent(def: CardDefinition, owner: EntityId): Pair<GameState, EntityId> {
        val id = EntityId.generate()
        val container = ComponentContainer.of(
            CardComponent(
                cardDefinitionId = def.name,
                name = def.name,
                manaCost = def.manaCost,
                typeLine = def.typeLine,
                baseStats = def.creatureStats,
                ownerId = owner
            ),
            OwnerComponent(owner),
            ControllerComponent(owner)
        )
        return withEntity(id, container).addToZone(ZoneKey(owner, Zone.BATTLEFIELD), id) to id
    }

    /** Three players: A (active) with the serpent; B controls an Island; C controls none. */
    fun setup(): Triple<GameState, List<EntityId>, EntityId> {
        val deck = Deck(cards = List(40) { "Filler Bear" })
        val init = GameInitializer(registry()).initializeGame(
            GameConfig(
                players = (1..3).map { PlayerConfig("Player $it", deck, 20) },
                skipMulligans = true,
                startingPlayerIndex = 0
            )
        )
        val players = init.playerIds
        var state = init.state
        val (s1, serpentId) = state.withPermanent(serpent, players[0])
        val (s2, _) = s1.withPermanent(island, players[1])
        state = s2.copy(phase = Phase.COMBAT, step = Step.DECLARE_ATTACKERS)
            .withPriority(players[0])
        return Triple(state, players, serpentId)
    }

    test("cannot attack the defender who controls no Island, even though another opponent does") {
        val (state, players, serpentId) = setup()
        val result = ActionProcessor(registry())
            .process(state, DeclareAttackers(players[0], mapOf(serpentId to players[2]))).result
        result.error shouldNotBe null
    }

    test("can attack the defender who controls an Island") {
        val (state, players, serpentId) = setup()
        val result = ActionProcessor(registry())
            .process(state, DeclareAttackers(players[0], mapOf(serpentId to players[1]))).result
        result.error shouldBe null
    }
})
