package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.*
import com.wingedsheep.engine.handlers.effects.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.*
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PendingEntryCopyTest : ScenarioTestBase() {
    private val copier = card("Pending Artifact Copier") {
        manaCost = "{3}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
        replacementEffect(EntersAsCopy(exceptions = CopyExceptions(addedCardTypes = setOf(CardType.ARTIFACT))))
    }
    private val chooser = card("Copied Entry Choice") {
        manaCost = "{W}"; typeLine = "Creature — Bear"; power = 2; toughness = 3
        replacementEffect(EntersWithChoice(ChoiceType.COLOR))
        triggeredAbility { trigger = Triggers.EntersBattlefield; effect = Effects.GainLife(2) }
    }
    private fun restored(state: GameState): GameState {
        val json = Json { allowStructuredMapKeys = true; serializersModule = engineSerializersModule }
        return json.decodeFromString<GameState>(json.encodeToString(state))
    }
    private fun choose(state: GameState, ids: List<com.wingedsheep.sdk.model.EntityId>) =
        ContinuationHandler(EngineServices(cardRegistry)).resume(restored(state), CardsSelectedResponse(state.pendingDecision!!.id, ids))
    private fun enter(state: GameState, ids: List<com.wingedsheep.sdk.model.EntityId>) =
        EffectExecutorRegistry(cardRegistry = cardRegistry).execute(state,
            MoveCollectionEffect("entries", CardDestination.ToZone(Zone.BATTLEFIELD)),
            EffectContext(sourceId = null, controllerId = state.turnOrder.first(), pipeline = PipelineState(storedCollections = mapOf("entries" to ids))))
    init {
        cardRegistry.register(copier); cardRegistry.register(chooser)
        test("copy and copied color choices are serialized before identity placement and one ETB") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, chooser.name).withCardInGraveyard(1, copier.name).build()
            val source = g.findPermanent(chooser.name)!!; val id = g.findCardsInGraveyard(1, copier.name).single()
            val paused = enter(g.state, listOf(id))
            val colored = choose(paused.state, listOf(source))
            colored.error shouldBe null
            (colored.state.pendingDecision is ChooseColorDecision) shouldBe true
            (id in colored.state.getBattlefield()) shouldBe false
            colored.state.getEntity(id)?.get<CardComponent>()?.name shouldBe copier.name
            colored.state.stack.size shouldBe 0
            val done = ContinuationHandler(EngineServices(cardRegistry)).resume(restored(colored.state), ColorChosenResponse(colored.state.pendingDecision!!.id, Color.BLUE))
            done.error shouldBe null
            done.state.getEntity(id)?.get<CardComponent>()?.name shouldBe chooser.name
            done.state.getEntity(id)?.get<CardComponent>()?.typeLine?.cardTypes?.contains(CardType.ARTIFACT) shouldBe true
            done.state.getEntity(id)?.chosenColor() shouldBe Color.BLUE
            done.state.stack.size shouldBe 1
            done.events.filterIsInstance<ZoneChangeEvent>().count { it.entityId == id && it.toZone == Zone.BATTLEFIELD } shouldBe 1
        }
        test("batch copy candidates stay snapshotted and neither entrant becomes visible before all choices") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears")
                .withCardInGraveyard(1, copier.name).withCardInGraveyard(1, copier.name).build()
            val source = g.findPermanent("Grizzly Bears")!!; val ids = g.findCardsInGraveyard(1, copier.name)
            val paused = enter(g.state, ids); val first = choose(paused.state, listOf(source))
            first.error shouldBe null
            ids.any { it in first.state.getBattlefield() } shouldBe false
            (first.state.pendingDecision as SelectCardsDecision).options.any { it in ids } shouldBe false
            val done = choose(first.state, listOf(source))
            done.error shouldBe null
            ids.all { done.state.getEntity(it)?.get<CardComponent>()?.name == "Grizzly Bears" } shouldBe true
            done.events.filterIsInstance<ZoneChangeEvent>().count { it.toZone == Zone.BATTLEFIELD } shouldBe 2
        }
        test("declining copy leaves printed identity without typed exceptions") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears").withCardInGraveyard(1, copier.name).build()
            val id = g.findCardsInGraveyard(1, copier.name).single()
            val done = choose(enter(g.state, listOf(id)).state, emptyList())
            done.error shouldBe null
            done.state.getEntity(id)?.get<CardComponent>()?.name shouldBe copier.name
            done.state.getEntity(id)?.get<CardComponent>()?.typeLine?.cardTypes?.contains(CardType.ARTIFACT) shouldBe false
        }
        test("a stale announced copy source is never replaced with its new visit") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears").withCardInGraveyard(1, copier.name).build()
            val source = g.findPermanent("Grizzly Bears")!!; val id = g.findCardsInGraveyard(1, copier.name).single()
            val paused = enter(g.state, listOf(id)); val player = g.state.turnOrder.first()
            val changed = paused.state.moveToZone(source, ZoneKey(player, Zone.BATTLEFIELD), ZoneKey(player, Zone.EXILE))
                .moveToZone(source, ZoneKey(player, Zone.EXILE), ZoneKey(player, Zone.BATTLEFIELD))
            val done = choose(changed, listOf(source))
            done.error shouldBe null
            (id in done.state.getBattlefield()) shouldBe false
        }
        test("a stale entrant is not moved when its copy decision resumes") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears").withCardInGraveyard(1, copier.name).build()
            val source = g.findPermanent("Grizzly Bears")!!; val id = g.findCardsInGraveyard(1, copier.name).single()
            val paused = enter(g.state, listOf(id))
            val changed = ZoneTransitionService.moveToZone(paused.state, id, Zone.EXILE).state
            val done = choose(changed, listOf(source))
            done.error shouldBe null
            (id in done.state.getBattlefield()) shouldBe false
        }
        test("copying excludes counters and tapped status and restores printed identity on departure") {
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears").withCardInGraveyard(1, copier.name).build()
            val source = g.findPermanent("Grizzly Bears")!!; val id = g.findCardsInGraveyard(1, copier.name).single()
            val changed = g.state.updateEntity(source) { it.with(TappedComponent).with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, 3)) }
            val done = choose(enter(changed, listOf(id)).state, listOf(source))
            done.error shouldBe null
            done.state.getEntity(id)?.get<TappedComponent>() shouldBe null
            (done.state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
            val departed = ZoneTransitionService.moveToZone(done.state, id, Zone.HAND)
            departed.state.getEntity(id)?.get<CardComponent>()?.name shouldBe copier.name
        }
        test("cast pipeline resumes once after serialized copy choice and copied ETB") {
            val entrySpell = card("Pending Copy Entry Spell") {
                manaCost = "{0}"; typeLine = "Sorcery"
                spell { effect = Effects.Composite(GatherCardsEffect(CardSource.FromZone(Zone.GRAVEYARD), storeAs = "entries"),
                    MoveCollectionEffect("entries", CardDestination.ToZone(Zone.BATTLEFIELD)), Effects.GainLife(3)) }
            }
            cardRegistry.register(entrySpell)
            val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, chooser.name)
                .withCardInGraveyard(1, copier.name).withCardInHand(1, entrySpell.name).withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val source = g.findPermanent(chooser.name)!!
            g.castSpell(1, entrySpell.name).error shouldBe null; g.resolveStack()
            g.state = restored(g.state)
            g.submitDecision(CardsSelectedResponse(g.state.pendingDecision!!.id, listOf(source))).error shouldBe null
            g.getLifeTotal(1) shouldBe 20
            g.state = restored(g.state)
            g.submitDecision(ColorChosenResponse(g.state.pendingDecision!!.id, Color.RED)).error shouldBe null
            g.getLifeTotal(1) shouldBe 23
            g.state.stack.size shouldBe 1
            g.resolveStack(); g.getLifeTotal(1) shouldBe 25
            g.state.continuationStack.size shouldBe 0
        }
        test("unsupported copy consumers delegate the whole batch before any partial operation") {
            val riders = listOf(EntersAsCopy(tappedIfCopied = true), EntersAsCopy(exileCopiedCard = true),
                EntersAsCopy(filterByTotalManaSpent = true),
                EntersAsCopy(additionalCounters = com.wingedsheep.sdk.scripting.values.DynamicAmount.Fixed(1)))
            for ((index, replacement) in riders.withIndex()) {
                val unsupported = card("Pending Unsupported Copy $index") {
                    manaCost = "{1}"; typeLine = "Creature — Shapeshifter"; power = 0; toughness = 5
                    replacementEffect(replacement)
                }
                cardRegistry.register(unsupported)
                val g = scenario().withPlayers("A", "B").withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardInGraveyard(1, copier.name).withCardInGraveyard(1, unsupported.name).build()
                val ids = g.findCardsInGraveyard(1, copier.name) + g.findCardsInGraveyard(1, unsupported.name)
                val result = PreEntryCoordinator(cardRegistry).prepare(g.state,
                    MoveCollectionEffect("entries", CardDestination.ToZone(Zone.BATTLEFIELD)),
                    EffectContext(sourceId = null, controllerId = g.state.turnOrder.first(), pipeline = PipelineState(storedCollections = mapOf("entries" to ids)))) { _, _, _ ->
                    error("Unsupported batch cannot commit through the identity-only adapter")
                }
                result shouldBe null
                g.state.pendingDecision shouldBe null
                ids.any { it in g.state.getBattlefield() } shouldBe false
            }
        }
    }
}
