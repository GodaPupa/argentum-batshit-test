package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** CR 603.2/603.4/603.6a: observe each entry instruction, not the final spell state. */
class EntryObserverBoundaryScenarioTest : FunSpec({
    val observer = card("Entry Boundary Observer") {
        manaCost = "{0}"; typeLine = "Enchantment"
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            effect = Effects.GainLife(3)
        }
    }
    val restrictor = card("Entry Boundary Restrictor") {
        manaCost = "{0}"; typeLine = "Enchantment"
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            triggerRestriction = Conditions.ControlArtifact
            effect = Effects.GainLife(3)
        }
    }
    val intervening = card("Entry Boundary Intervening") {
        manaCost = "{0}"; typeLine = "Enchantment"
        triggeredAbility {
            trigger = Triggers.OtherCreatureEnters
            interveningIf = Conditions.ControlArtifact
            effect = Effects.GainLife(3)
        }
    }
    val suppressor = card("Entry Boundary Suppressor") {
        manaCost = "{0}"; typeLine = "Artifact"
        staticAbility { ability = SuppressEntersTriggers() }
    }
    val artifact = card("Entry Boundary Artifact") {
        manaCost = "{0}"; typeLine = "Artifact"
    }
    val body = card("Entry Boundary Body") {
        manaCost = "{0}"; typeLine = "Creature — Bear"; power = 2; toughness = 2
    }
    val bear = CreateTokenEffect(power = 2, toughness = 2,
        colors = setOf(Color.GREEN), creatureTypes = setOf("Bear"))
    fun afterEntry(name: String, departing: Boolean) = card("Entry Boundary Move $name $departing") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { effect = Effects.Pipeline {
            run(bear)
            val cards = gather(if (departing)
                CardSource.BattlefieldMatching(GameObjectFilter.Any.named(name))
                else CardSource.TopOfLibrary(DynamicAmount.Fixed(1)))
            move(cards, CardDestination.ToZone(if (departing) Zone.EXILE else Zone.BATTLEFIELD))
        } }
    }
    val together = card("Entry Boundary Together") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { effect = Effects.Pipeline {
            val cards = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(2)))
            move(cards, CardDestination.ToZone(Zone.BATTLEFIELD))
        } }
    }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun restore(d: GameTestDriver) {
        val raw = json.encodeToString(GameState.serializer(), d.state)
        val restored = json.decodeFromString(GameState.serializer(), raw)
        json.encodeToString(GameState.serializer(), restored) shouldBe raw
        d.replaceState(restored)
    }
    fun fixture() = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(observer, restrictor, intervening, suppressor, artifact, body, together))
        initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    for (departing in listOf(false, true)) {
        test("entry observes only observers present at its instruction; departing=$departing") {
            val d = fixture(); val spell = afterEntry(observer.name, departing)
            d.registerCards(listOf(spell))
            val id = if (departing) d.putPermanentOnBattlefield(d.player1, observer.name)
                else d.putCardOnTopOfLibrary(d.player1, observer.name)
            d.castSpell(d.player1, d.putCardInHand(d.player1, spell.name)).error shouldBe null
            d.bothPass().error shouldBe null
            d.state.getBattlefield().contains(id) shouldBe !departing
            d.stackSize shouldBe if (departing) 1 else 0
            restore(d)
            if (departing) d.bothPass().error shouldBe null
            d.getLifeTotal(d.player1) shouldBe if (departing) 23 else 20
        }
        test("entry suppression uses suppressors present at its instruction; departing=$departing") {
            val d = fixture(); val spell = afterEntry(suppressor.name, departing)
            d.registerCards(listOf(spell)); d.putPermanentOnBattlefield(d.player1, observer.name)
            val id = if (departing) d.putPermanentOnBattlefield(d.player1, suppressor.name)
                else d.putCardOnTopOfLibrary(d.player1, suppressor.name)
            d.castSpell(d.player1, d.putCardInHand(d.player1, spell.name)).error shouldBe null
            d.bothPass().error shouldBe null
            d.state.getBattlefield().contains(id) shouldBe !departing
            d.stackSize shouldBe if (departing) 0 else 1
            restore(d)
            if (!departing) d.bothPass().error shouldBe null
            d.getLifeTotal(d.player1) shouldBe if (departing) 20 else 23
        }
        for (twice in listOf(false, true)) {
            test("entry condition is checked at the event and only intervening-if rechecks; departing=$departing, twice=$twice") {
                val d = fixture(); val spell = afterEntry(artifact.name, departing)
                d.registerCards(listOf(spell))
                d.putPermanentOnBattlefield(d.player1, if (twice) intervening.name else restrictor.name)
                val id = if (departing) d.putPermanentOnBattlefield(d.player1, artifact.name)
                    else d.putCardOnTopOfLibrary(d.player1, artifact.name)
                d.castSpell(d.player1, d.putCardInHand(d.player1, spell.name)).error shouldBe null
                d.bothPass().error shouldBe null
                d.state.getBattlefield().contains(id) shouldBe !departing
                d.stackSize shouldBe if (departing) 1 else 0
                restore(d)
                if (departing) d.bothPass().error shouldBe null
                d.getLifeTotal(d.player1) shouldBe if (departing && !twice) 23 else 20
            }
        }
    }
    for (providerFirst in listOf(false, true)) {
        for (suppressed in listOf(false, true)) {
            test("simultaneous entry sees coentrant observers and suppressors; providerFirst=$providerFirst, suppressed=$suppressed") {
                val d = fixture()
                if (suppressed) d.putPermanentOnBattlefield(d.player1, observer.name)
                val provider = if (suppressed) suppressor else observer
                val ordered = if (providerFirst) listOf(provider.name, body.name) else listOf(body.name, provider.name)
                val ids = ordered.reversed().map { d.putCardOnTopOfLibrary(d.player1, it) }
                d.castSpell(d.player1, d.putCardInHand(d.player1, together.name)).error shouldBe null
                d.bothPass().error shouldBe null
                ids.all { it in d.state.getBattlefield() } shouldBe true
                d.stackSize shouldBe if (suppressed) 0 else 1
                if (!suppressed) d.bothPass().error shouldBe null
                d.getLifeTotal(d.player1) shouldBe if (suppressed) 20 else 23
            }
        }
    }
    for (separate in listOf(false, true)) {
        for (once in listOf(false, true)) {
            test("entry batches follow instructions while once-per-turn spans them; separate=$separate, once=$once") {
                val d = fixture()
                val batchObserver = card("Entry Boundary Batch $once") {
                    manaCost = "{0}"; typeLine = "Enchantment"
                    triggeredAbility {
                        trigger = Triggers.OneOrMorePermanentsEnter(GameObjectFilter.Creature)
                        oncePerTurn = once
                        effect = Effects.GainLife(3)
                    }
                }
                d.registerCards(listOf(batchObserver))
                val id = d.putPermanentOnBattlefield(d.player1, batchObserver.name)
                val services = EngineServices(d.cardRegistry)
                val result = services.effectExecutorRegistry.execute(d.state,
                    if (separate) Effects.Composite(bear, bear)
                    else bear.copy(count = DynamicAmount.Fixed(2)),
                    EffectContext(sourceId = null, controllerId = d.player1))
                result.isSuccess shouldBe true
                result.events.filterIsInstance<ZoneChangeEvent>().count { it.toZone == Zone.BATTLEFIELD } shouldBe 2
                val triggers = services.triggerDetector.detectTriggers(result.state, result.events)
                    .filter { it.sourceId == id }
                triggers.size shouldBe if (separate && !once) 2 else 1
            }
        }
    }
})
