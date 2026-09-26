package com.wingedsheep.gameserver.session

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardEntityFactory
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.mechanics.combat.CombatDefenders
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.combat.AttackingComponent
import com.wingedsheep.engine.state.components.combat.BlockingComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.gameserver.ScenarioTestBase
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.BlockTax
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.springframework.web.socket.WebSocketSession

/** Two fixed session boundaries: declaration permission (CR 805.10d) and payment (CR 605.3a). */
class PostBlockDeclarationSessionTest : ScenarioTestBase() {
    private val tax = card("Session Block Tax Fixture") {
        manaCost = "{0}"
        typeLine = "Artifact"
        staticAbility { ability = BlockTax(DynamicAmount.Fixed(1)) }
    }

    private data class Fixture(
        val session: GameSession,
        val players: List<EntityId>,
        val attacker: EntityId,
        val blockers: Map<EntityId, EntityId>,
        val forests: Map<EntityId, EntityId>,
    )

    private fun setup(seed: Long, taxed: Boolean): Fixture {
        val ids = (1..4).map { EntityId.of("session-block-$it") }
        val initialized = GameInitializer(cardRegistry).initializeGame(
            GameConfig(
                players = ids.mapIndexed { index, id ->
                    PlayerConfig("Seat ${index + 1}", Deck(cards = List(40) { "Forest" }), playerId = id)
                },
                format = Format.TwoHeadedGiant(),
                teams = listOf(listOf(0, 1), listOf(2, 3)),
                startingPlayerIndex = 0,
                skipMulligans = true,
                seed = seed,
            )
        )
        initialized.seed shouldBe seed
        initialized.playerIds shouldBe ids
        var state = initialized.state
        fun put(owner: EntityId, name: String, zone: Zone = Zone.BATTLEFIELD): EntityId {
            val (id, allocated) = state.newEntity()
            state = allocated.withEntity(id, CardEntityFactory.create(cardRegistry.requireCard(name), owner))
                .addToZone(ZoneKey(owner, zone), id)
            return id
        }

        val attacker = put(ids[0], "Craw Wurm")
        val blockers = ids.drop(2).associateWith { put(it, "Grizzly Bears") }
        val forests = ids.associateWith { put(it, "Forest") }
        ids.forEach { put(it, "Giant Growth", Zone.HAND) }
        if (taxed) put(ids[0], tax.name)
        state = state.copy(phase = Phase.COMBAT, step = Step.DECLARE_BLOCKERS)
            .updateEntity(ids[0]) { it.with(AttackersDeclaredThisCombatComponent) }
            .updateEntity(attacker) { it.with(AttackingComponent(defenderId = ids[2])) }
            .withPriority(ids[2])

        val session = GameSession(sessionId = "fixed-post-block-$seed", cardRegistry = cardRegistry, maxPlayers = 4)
        val sessions = ids.associateWith { id ->
            val socket = mockk<WebSocketSession>(relaxed = true) {
                every { this@mockk.id } returns "socket-${id.value}"
            }
            PlayerSession(socket, id, id.value)
        }
        session.injectStateForTesting(state, sessions)
        return Fixture(session, ids, attacker, blockers, forests)
    }

    private fun GameState.assertNoPriority() {
        priorityTeam shouldBe emptyList()
        turnOrder.forEach { hasPriority(it) shouldBe false }
    }

    init {
        beforeSpec { cardRegistry.register(tax) }

        test("GS-PB01 both defending teammates get their own declaration without ordinary priority") {
            val (session, ids, attacker, blockers, _) = setup(0xFE000096, taxed = false)
            val state = session.getStateForTesting().shouldNotBeNull()
            state.turnOrder shouldBe ids
            state.activePlayerId shouldBe ids[0]
            state.priorityPlayerId shouldBe ids[2]
            state.assertNoPriority()
            ids.take(2).forEach { session.getLegalActions(it) shouldBe emptyList() }
            ids.drop(2).forEach { defender ->
                CombatDefenders.canDeclareBlockers(state, defender) shouldBe true
                val offer = session.getLegalActions(defender).single()
                val action = offer.action.shouldBeInstanceOf<DeclareBlockers>()
                action.playerId shouldBe defender
                offer.actionType shouldBe "DeclareBlockers"
                offer.validBlockers shouldBe listOf(blockers.getValue(defender))
                offer.isManaAbility shouldBe false
            }

            // The non-baton teammate's advertised seat is accepted by the real server/engine path.
            val teammateOffer = session.getLegalActions(ids[3]).single().action.shouldBeInstanceOf<DeclareBlockers>()
            session.executeAction(
                ids[3], teammateOffer.copy(blockers = mapOf(blockers.getValue(ids[3]) to listOf(attacker)))
            ).shouldBeInstanceOf<GameSession.ActionResult.Success>()
            val after = session.getStateForTesting().shouldNotBeNull()
            after.getEntity(blockers.getValue(ids[3])).shouldNotBeNull()
                .get<BlockingComponent>().shouldNotBeNull().blockedAttackerIds shouldBe listOf(attacker)
            after.assertNoPriority()
            session.getLegalActions(ids[3]) shouldBe emptyList()
            session.getLegalActions(ids[2]).single().action.playerId shouldBe ids[2]
        }

        test("GS-PB02 block-tax payment exposes only the payer's mana actions before declarations resume") {
            val (session, ids, attacker, blockers, forests) = setup(0xFE000097, taxed = true)
            val payer = ids[3]
            session.executeAction(
                payer, DeclareBlockers(payer, mapOf(blockers.getValue(payer) to listOf(attacker)))
            ).shouldBeInstanceOf<GameSession.ActionResult.PausedForDecision>()
            val paused = session.getStateForTesting().shouldNotBeNull()
            val payment = paused.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>()
            payment.playerId shouldBe payer
            payment.requiredCost shouldBe "{1}"
            paused.priorityPlayerId shouldBe null
            paused.assertNoPriority()
            ids.filter { it != payer }.forEach { session.getLegalActions(it) shouldBe emptyList() }
            val mana = session.getLegalActions(payer)
            mana.isNotEmpty() shouldBe true
            mana.forEach {
                it.action.playerId shouldBe payer
                it.isManaAbility shouldBe true
                it.action.shouldBeInstanceOf<ActivateAbility>()
            }
            val forestAbility = mana.single {
                (it.action as ActivateAbility).sourceId == forests.getValue(payer)
            }
            forestAbility.isAffordable shouldBe true
            session.executeAction(payer, forestAbility.action)
                .shouldBeInstanceOf<GameSession.ActionResult.PausedForDecision>()
            val after = session.getStateForTesting().shouldNotBeNull()
            after.pendingDecision.shouldBeInstanceOf<SelectManaSourcesDecision>().playerId shouldBe payer
            after.getEntity(forests.getValue(payer)).shouldNotBeNull().has<TappedComponent>() shouldBe true
            after.getEntity(payer).shouldNotBeNull().get<ManaPoolComponent>().shouldNotBeNull().green shouldBe 1
            after.assertNoPriority()
            ids.filter { it != payer }.forEach { session.getLegalActions(it) shouldBe emptyList() }
            session.getLegalActions(payer).forEach {
                it.action.playerId shouldBe payer
                it.isManaAbility shouldBe true
            }
        }
    }
}
