package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json

/** Two exact selected-card source/life-loss fixtures; original NotDeadAfterAll cases are untouched. */
class FerocityWickedRoleSourceTest : ScenarioTestBase() {
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }

    init {
        // ScenarioTestBase appends TestCards overrides; restore the actual canonical source.
        cardRegistry.register(com.wingedsheep.mtg.sets.definitions.lea.cards.LightningBolt)
        for (seat in listOf(1, 2)) {
            test("actual Wicked Role death retains its public source and drains only the opponent seat $seat") {
                val game = scenario().withPlayers().withRngSeed(202609261100L + seat)
                    .withCardOnBattlefield(seat, "Krark-Clan Shaman")
                    .withCardInHand(seat, "Not Dead After All")
                    .withCardInHand(seat, "Lightning Bolt").withCardInHand(seat, "Lightning Bolt")
                    .withLandsOnBattlefield(seat, "Swamp", 1).withLandsOnBattlefield(seat, "Mountain", 2)
                    .withCardInLibrary(1, "Mountain").withCardInLibrary(2, "Mountain")
                    .withActivePlayer(seat).withPriorityPlayer(seat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val controller = if (seat == 1) game.player1Id else game.player2Id
                val opponent = if (seat == 1) game.player2Id else game.player1Id
                val shaman = game.findPermanent("Krark-Clan Shaman")!!
                val firstShaman = game.state.objectRef(shaman)!!
                game.castSpell(seat, "Not Dead After All", shaman).error shouldBe null
                settle(game)
                game.castSpell(seat, "Lightning Bolt", shaman).error shouldBe null
                settle(game)
                game.state.objectRef(shaman) shouldNotBe firstShaman
                val role = game.findPermanent("Wicked Role")!!
                val roleObject = game.state.objectRef(role)!!
                game.state.getEntity(role)!!.get<AttachedToComponent>()!!.targetId shouldBe shaman

                game.castSpell(seat, "Lightning Bolt", shaman).error shouldBe null
                val departureEvents = mutableListOf<GameEvent>()
                var guard = 0
                while (game.state.stack.none { game.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()?.sourceId == role } && guard++ < 10) {
                    game.state.pendingDecision shouldBe null
                    val result = game.passPriority(); result.error shouldBe null; departureEvents += result.events
                }
                game.state.getEntity(role) shouldBe null
                game.state.getEntity(shaman) shouldNotBe null // The real creature card remains in its graveyard.
                game.state.getGraveyard(controller).contains(shaman) shouldBe true
                departureEvents.filterIsInstance<ZoneChangeEvent>().single { it.entityId == role && it.toZone == Zone.GRAVEYARD }
                    .oldObject shouldBe roleObject
                val trigger = game.state.stack.mapNotNull { game.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>() }
                    .single { it.sourceId == role }
                val snapshot = requireNotNull(trigger.lastKnownSourceSnapshot)
                snapshot.objectRef shouldBe roleObject
                snapshot.wasToken shouldBe true
                snapshot.ownerId shouldBe controller
                snapshot.controllerId shouldBe controller
                snapshot.colors shouldBe emptySet<String>()
                game.getLifeTotal(seat) shouldBe 20
                game.getLifeTotal(3 - seat) shouldBe 20

                val encoded = json.encodeToString(GameState.serializer(), game.state)
                game.state = json.decodeFromString(GameState.serializer(), encoded)
                json.encodeToString(GameState.serializer(), game.state) shouldBe encoded
                val adapter = ObservationAdapter(cardRegistry)
                val enumerator = LegalActionEnumerator.create(cardRegistry)
                val observedActors = mutableSetOf<com.wingedsheep.sdk.model.EntityId>()
                val resolutionEvents = mutableListOf<GameEvent>()
                repeat(2) { index ->
                    val actor = game.state.priorityPlayerId!!
                    observedActors += actor
                    val input = adapter.build(game.state, actor,
                        enumerator.enumerate(game.state, actor, EnumerationMode.FULL),
                        ActorEpoch("selected-token-v1", "wicked-seat-$seat", index.toLong()), 1100L + seat)
                    val source = requireNotNull(input.observation.stack.single { it.sourceId == role }.source)
                    source.origin shouldBe ActorObjectIdentity(roleObject.entityId, roleObject.generation)
                    source.mode shouldBe ActorSourceMode.DEPARTED_BATTLEFIELD
                    source.originalObjectIsCurrent shouldBe false
                    source.currentVisibleObject shouldBe null
                    val characteristics = requireNotNull(source.characteristics)
                    characteristics.name shouldBe "Wicked Role" // Existing engine registry/display identity.
                    characteristics.ownerId shouldBe controller
                    characteristics.controllerId shouldBe controller
                    characteristics.colors shouldBe emptySet<String>()
                    characteristics.types shouldBe setOf("ENCHANTMENT")
                    characteristics.subtypes.containsAll(setOf("Aura", "Role")) shouldBe true
                    characteristics.lifelink shouldBe false
                    val result = game.passPriority(); result.error shouldBe null; resolutionEvents += result.events
                }
                observedActors shouldBe game.state.turnOrder.toSet()
                game.state.stack shouldBe emptyList()
                game.state.pendingDecision shouldBe null
                game.getLifeTotal(seat) shouldBe 20
                game.getLifeTotal(3 - seat) shouldBe 19
                resolutionEvents.filterIsInstance<LifeChangedEvent>().single() shouldBe
                    LifeChangedEvent(opponent, 20, 19, LifeChangeReason.LIFE_LOSS)
                resolutionEvents.filterIsInstance<DamageDealtEvent>() shouldBe emptyList()
                game.findPermanent("Wicked Role") shouldBe null
                game.findPermanent("Krark-Clan Shaman") shouldBe null
                game.state.gameOver shouldBe false
            }
        }
    }

    private fun settle(game: TestGame) {
        game.resolveStack().forEach { it.error shouldBe null }
        game.state.stack shouldBe emptyList()
        game.state.pendingDecision shouldBe null
    }
}
