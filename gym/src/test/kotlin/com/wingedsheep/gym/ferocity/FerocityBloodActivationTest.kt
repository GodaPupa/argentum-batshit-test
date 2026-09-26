package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json

/** Six bounded mechanics fixtures; Blood plus Temper is a cross-pool stress case, not an A3 line. */
class FerocityBloodActivationTest : ScenarioTestBase() {
    private val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true; encodeDefaults = true }

    init {
        for (seat in listOf(1, 2)) {
            test("Blood pays every cost before response priority and draws only on resolution seat $seat") {
                val f = fixture(seat, "Mountain", 1, 202609261102L + seat)
                val library = f.game.state.getLibrary(f.controller).toList()
                val result = activate(f)
                result.error shouldBe null
                assertCostsPaid(f, result, Zone.GRAVEYARD, library)
                f.game.state.stack.size shouldBe 1
                val ability = f.game.state.getEntity(f.game.state.stack.single())!!.get<ActivatedAbilityOnStackComponent>()!!
                ability.sourceId shouldBe f.blood
                ability.lastKnownSourceSnapshot!!.objectRef shouldBe f.bloodObject
                ability.lastKnownSourceSnapshot!!.wasToken shouldBe true

                val actor = f.game.state.priorityPlayerId!!
                val input = ObservationAdapter(cardRegistry).build(f.game.state, actor,
                    LegalActionEnumerator.create(cardRegistry).enumerate(f.game.state, actor, EnumerationMode.FULL),
                    ActorEpoch("selected-token-v1", "blood-cost-seat-$seat", 0), 1102L + seat)
                val source = requireNotNull(input.observation.stack.single().source)
                source.origin shouldBe ActorObjectIdentity(f.bloodObject.entityId, f.bloodObject.generation)
                source.mode shouldBe ActorSourceMode.DEPARTED_BATTLEFIELD
                source.originalObjectIsCurrent shouldBe false
                source.currentVisibleObject shouldBe null
                source.characteristics!!.name shouldBe "Blood"
                source.characteristics!!.types shouldBe setOf("ARTIFACT")
                source.characteristics!!.ownerId shouldBe f.controller
                source.characteristics!!.colors shouldBe emptySet<String>()

                val events = settle(f.game)
                val draw = events.filterIsInstance<CardsDrawnEvent>().single()
                draw.playerId shouldBe f.controller
                draw.count shouldBe 1
                draw.cardIds shouldBe listOf(library.first())
                f.game.state.getHand(f.controller) shouldBe draw.cardIds
                f.game.state.getLibrary(f.controller) shouldBe library.drop(1)
                f.game.state.getGraveyard(f.controller).contains(f.discard) shouldBe true
                f.game.state.getEntity(f.blood) shouldBe null
                availableMountains(f) shouldBe 0
                f.game.getLifeTotal(1) shouldBe 20
                f.game.getLifeTotal(2) shouldBe 20
                f.game.state.gameOver shouldBe false
            }
        }

        for (seat in listOf(1, 2)) {
            test("Blood discard Madness resolves Temper before its draw with all costs paid seat $seat") {
                val f = fixture(seat, "Fiery Temper", 2, 202609261104L + seat)
                val library = f.game.state.getLibrary(f.controller).toList()
                val events = mutableListOf<GameEvent>()
                val activation = activate(f); activation.error shouldBe null; events += activation.events
                assertCostsPaid(f, activation, Zone.EXILE, library)
                availableMountains(f) shouldBe 1
                assertMadnessAboveDraw(f)
                roundTrip(f.game)

                // Resolving the top trigger must stop for its owner's real cast choice.
                events += settleUntilChoice(f.game)
                (f.game.state.pendingDecision as YesNoDecision).playerId shouldBe f.controller
                f.game.state.getLibrary(f.controller) shouldBe library
                f.game.state.getHand(f.controller) shouldBe emptyList()
                val accept = f.game.answerYesNo(true); accept.error shouldBe null; events += accept.events
                (f.game.state.pendingDecision as ChooseTargetsDecision).playerId shouldBe f.controller
                val target = f.game.selectTargets(listOf(f.opponent)); target.error shouldBe null; events += target.events
                f.game.state.pendingDecision shouldBe null
                f.game.state.stack.size shouldBe 2
                f.game.state.getEntity(f.game.state.stack.last())!!.get<SpellOnStackComponent>() shouldNotBe null
                f.game.state.stack.last() shouldBe f.discard
                f.game.state.getEntity(f.game.state.stack.first())!!.get<ActivatedAbilityOnStackComponent>()!!.sourceId shouldBe f.blood
                availableMountains(f) shouldBe 0
                f.game.state.getEntity(f.controller)!!.get<ManaPoolComponent>()!!.total shouldBe 0
                f.game.state.getLibrary(f.controller) shouldBe library
                events.filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()

                // Stop after Temper leaves the stack: Blood has still not drawn at this point.
                var guard = 0
                while (f.game.state.stack.contains(f.discard) && guard++ < 4) {
                    f.game.state.pendingDecision shouldBe null
                    val pass = f.game.passPriority(); pass.error shouldBe null; events += pass.events
                }
                f.game.state.stack.size shouldBe 1
                f.game.state.getLibrary(f.controller) shouldBe library
                f.game.state.getHand(f.controller) shouldBe emptyList()
                f.game.getLifeTotal(3 - seat) shouldBe 17
                f.game.state.getGraveyard(f.controller).contains(f.discard) shouldBe true
                events.filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()
                events += settle(f.game)
                val damage = events.filterIsInstance<DamageDealtEvent>().single()
                damage.sourceId shouldBe f.discard
                damage.targetId shouldBe f.opponent
                damage.amount shouldBe 3
                damage.isCombatDamage shouldBe false
                val draw = events.filterIsInstance<CardsDrawnEvent>().single()
                draw.playerId shouldBe f.controller
                draw.count shouldBe 1
                draw.cardIds shouldBe listOf(library.first())
                (events.indexOf(damage) < events.indexOf(draw)) shouldBe true
                f.game.state.getHand(f.controller) shouldBe draw.cardIds
                f.game.state.getLibrary(f.controller) shouldBe library.drop(1)
                f.game.state.getExile(f.controller).contains(f.discard) shouldBe false
                f.game.state.getEntity(f.blood) shouldBe null
                f.game.getLifeTotal(seat) shouldBe 20
                f.game.state.gameOver shouldBe false
            }
        }

        test("declining Blood-discarded Madness puts Temper in graveyard before the pending draw") {
            val f = fixture(1, "Fiery Temper", 2, 202609261107L)
            val library = f.game.state.getLibrary(f.controller).toList()
            val activation = activate(f); activation.error shouldBe null
            assertCostsPaid(f, activation, Zone.EXILE, library)
            assertMadnessAboveDraw(f)
            val events = activation.events.toMutableList()
            events += settleUntilChoice(f.game)
            (f.game.state.pendingDecision as YesNoDecision).playerId shouldBe f.controller
            val decline = f.game.answerYesNo(false); decline.error shouldBe null; events += decline.events
            f.game.state.pendingDecision shouldBe null
            f.game.state.getExile(f.controller).contains(f.discard) shouldBe false
            f.game.state.getGraveyard(f.controller).contains(f.discard) shouldBe true
            f.game.state.getLibrary(f.controller) shouldBe library
            f.game.state.getHand(f.controller) shouldBe emptyList()
            f.game.state.stack.size shouldBe 1
            events += settle(f.game)
            val draw = events.filterIsInstance<CardsDrawnEvent>().single()
            draw.playerId shouldBe f.controller
            draw.count shouldBe 1
            draw.cardIds shouldBe listOf(library.first())
            val declinedMove = events.filterIsInstance<ZoneChangeEvent>().single {
                it.entityId == f.discard && it.fromZone == Zone.EXILE && it.toZone == Zone.GRAVEYARD
            }
            (events.indexOf(declinedMove) < events.indexOf(draw)) shouldBe true
            events.filterIsInstance<DamageDealtEvent>() shouldBe emptyList()
            availableMountains(f) shouldBe 1
            f.game.state.getHand(f.controller) shouldBe draw.cardIds
            f.game.state.getLibrary(f.controller) shouldBe library.drop(1)
            f.game.state.getEntity(f.blood) shouldBe null
            f.game.getLifeTotal(1) shouldBe 20
            f.game.getLifeTotal(2) shouldBe 20
            f.game.state.gameOver shouldBe false
        }

        test("Blood rejects missing mana missing discard and tapped source without a partial payment") {
            for (kind in listOf("missing-mana", "missing-discard", "tapped-source")) {
                val f = fixture(1, if (kind == "missing-discard") null else "Mountain",
                    if (kind == "missing-mana") 0 else 1, 202609261108L)
                // This branch is an explicitly synthetic tapped starting condition; the token
                // itself was still created by the real Blood Fountain action and trigger.
                if (kind == "tapped-source") f.game.state = f.game.state.updateEntity(f.blood) { it.with(TappedComponent) }
                val before = f.game.state
                val result = activate(f)
                result.error shouldNotBe null
                result.state shouldBe before
                f.game.state shouldBe before
                result.events shouldBe emptyList()
                f.game.state.stack shouldBe emptyList()
                f.game.state.pendingDecision shouldBe null
                f.game.state.getEntity(f.blood) shouldNotBe null
            }
        }
    }

    private data class BloodFixture(
        val game: TestGame,
        val controller: EntityId,
        val opponent: EntityId,
        val blood: EntityId,
        val bloodObject: ObjectRef,
        val discard: EntityId?,
        val mountains: List<EntityId>,
    )

    private fun fixture(seat: Int, discardName: String?, mountainCount: Int, seed: Long): BloodFixture {
        val builder = scenario().withPlayers().withRngSeed(seed)
            .withCardInHand(seat, "Blood Fountain")
            .withLandsOnBattlefield(seat, "Swamp", 1)
            .withLandsOnBattlefield(seat, "Mountain", mountainCount)
            .withCardInLibrary(1, "Plains").withCardInLibrary(1, "Plains").withCardInLibrary(1, "Plains")
            .withCardInLibrary(2, "Plains").withCardInLibrary(2, "Plains").withCardInLibrary(2, "Plains")
            .withActivePlayer(seat).withPriorityPlayer(seat)
            .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
        if (discardName != null) builder.withCardInHand(seat, discardName)
        val game = builder.build()
        val controller = if (seat == 1) game.player1Id else game.player2Id
        val opponent = if (seat == 1) game.player2Id else game.player1Id
        val discarded = discardName?.let { game.findCardsInHand(seat, it).single() }
        game.castSpell(seat, "Blood Fountain").error shouldBe null
        settle(game)
        val blood = game.findPermanent("Blood")!!
        val mountains = game.state.getBattlefield().filter { game.state.getEntity(it)?.get<CardComponent>()?.name == "Mountain" }
        mountains.size shouldBe mountainCount
        mountains.all { !game.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
        game.state.getEntity(game.findPermanent("Swamp")!!)!!.has<TappedComponent>() shouldBe true
        return BloodFixture(game, controller, opponent, blood, game.state.objectRef(blood)!!, discarded, mountains)
    }

    private fun activate(f: BloodFixture): ExecutionResult = f.game.execute(ActivateAbility(
        f.controller, f.blood, cardRegistry.requireCard("Blood").script.activatedAbilities.single().id,
        costPayment = AdditionalCostPayment(discardedCards = listOfNotNull(f.discard)),
    ))

    private fun assertCostsPaid(f: BloodFixture, result: ExecutionResult, discardZone: Zone, library: List<EntityId>) {
        f.game.state.pendingDecision shouldBe null
        f.game.state.getEntity(f.blood) shouldBe null
        f.game.state.getHand(f.controller) shouldBe emptyList()
        f.game.state.getLibrary(f.controller) shouldBe library
        f.game.state.getEntity(f.controller)!!.get<ManaPoolComponent>()!!.total shouldBe 0
        availableMountains(f) shouldBe f.mountains.size - 1
        result.events.filterIsInstance<TappedEvent>().count { it.entityId == f.blood } shouldBe 1
        result.events.filterIsInstance<CardsDiscardedEvent>().single().cardIds shouldBe listOf(f.discard)
        result.events.filterIsInstance<ZoneChangeEvent>().single {
            it.entityId == f.blood && it.toZone == Zone.GRAVEYARD
        }.oldObject shouldBe f.bloodObject
        result.events.filterIsInstance<ZoneChangeEvent>().single {
            it.entityId == f.discard && it.fromZone == Zone.HAND
        }.toZone shouldBe discardZone
        result.events.filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()
        val cards = if (discardZone == Zone.EXILE) f.game.state.getExile(f.controller) else f.game.state.getGraveyard(f.controller)
        cards.contains(f.discard) shouldBe true
    }

    private fun assertMadnessAboveDraw(f: BloodFixture) {
        f.game.state.stack.size shouldBe 2
        f.game.state.getEntity(f.game.state.stack.first())!!.get<ActivatedAbilityOnStackComponent>()!!.sourceId shouldBe f.blood
        f.game.state.getEntity(f.game.state.stack.last())!!.get<TriggeredAbilityOnStackComponent>()!!.sourceId shouldBe f.discard
    }

    private fun availableMountains(f: BloodFixture): Int = f.mountains.count { !f.game.state.getEntity(it)!!.has<TappedComponent>() }

    private fun roundTrip(game: TestGame) {
        val encoded = json.encodeToString(GameState.serializer(), game.state)
        game.state = json.decodeFromString(GameState.serializer(), encoded)
        json.encodeToString(GameState.serializer(), game.state) shouldBe encoded
    }

    private fun settleUntilChoice(game: TestGame): List<GameEvent> {
        val events = game.resolveStack().flatMap { result -> result.error shouldBe null; result.events }
        game.state.pendingDecision shouldNotBe null
        return events
    }

    private fun settle(game: TestGame): List<GameEvent> {
        val events = game.resolveStack().flatMap { result -> result.error shouldBe null; result.events }
        game.state.stack shouldBe emptyList()
        game.state.pendingDecision shouldBe null
        return events
    }
}
