package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.FloatingEffectData
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.c18.cards.ForgeOfHeroes
import com.wingedsheep.mtg.sets.definitions.dft.cards.VeteranBeastrider
import com.wingedsheep.mtg.sets.definitions.gpt.cards.IzzetGuildmage
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.Duration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/** Deterministic exact-card fixtures, including the two frozen PDH commanders; never matchup games. */
class ForgeOfHeroesScenarioTest : FunSpec({
    val bounce = card("Forge Bounce Fixture") {
        manaCost = "{U}"
        typeLine = "Instant"
        spell {
            val permanent = target("permanent", Targets.Permanent)
            effect = Effects.ReturnToHand(permanent)
        }
    }
    fun fixture(startingSeat: Int = 0) = GameTestDriver().apply {
        registerCards(TestCards.all + listOf(ForgeOfHeroes, IzzetGuildmage, VeteranBeastrider, bounce))
        initMultiplayer(List(2) { Deck.of("Forest" to 99) }, startingPlayer = startingSeat,
            format = Format.Commander(startingLife = 30, commanderDamageThreshold = 16),
            commanders = listOf(IzzetGuildmage.name, VeteranBeastrider.name))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun castActiveCommander(d: GameTestDriver): EntityId {
        val me = d.activePlayer!!
        val id = d.state.getZone(ZoneKey(me, Zone.COMMAND)).single()
        if (me == d.player1) d.giveMana(me, Color.BLUE, 2)
        else {
            d.giveMana(me, Color.GREEN, 1)
            d.giveMana(me, Color.WHITE, 1)
            d.giveColorlessMana(me, 1)
        }
        d.castSpell(me, id).isSuccess shouldBe true
        d.bothPass().error shouldBe null
        d.state.getEntity(id)!!.has<EnteredThisTurnComponent>() shouldBe true
        d.state.getEntity(id)!!.has<CommanderComponent>() shouldBe true
        return id
    }
    fun counterAction(player: EntityId, forge: EntityId, target: EntityId) =
        ActivateAbility(player, forge, ForgeOfHeroes.activatedAbilities[1].id,
            targets = listOf(ChosenTarget.Permanent(target)))
    fun counters(d: GameTestDriver, id: EntityId, kind: CounterType) =
        d.state.getEntity(id)?.get<CountersComponent>()?.getCount(kind) ?: 0

    test("colorless mana is immediate and taps Forge without using the stack") {
        val d = fixture()
        val forge = d.putLandOnBattlefield(d.player1, ForgeOfHeroes.name)
        d.submitSuccess(ActivateAbility(d.player1, forge, ForgeOfHeroes.activatedAbilities[0].id))
        d.state.stack.size shouldBe 0
        d.state.getEntity(d.player1)!!.get<ManaPoolComponent>()!!.total shouldBe 1
        d.state.getEntity(forge)!!.has<TappedComponent>() shouldBe true
    }

    for (seat in 0..1) {
        test("exact nonlegendary PDH commander in seat $seat receives its counter only on resolution") {
            val d = fixture(seat)
            val me = d.activePlayer!!
            val id = castActiveCommander(d)
            d.state.getEntity(id)!!.get<CardComponent>()!!.typeLine.isLegendary shouldBe false
            val forge = d.putLandOnBattlefield(me, ForgeOfHeroes.name)
            d.submitSuccess(counterAction(me, forge, id))
            d.state.stack.size shouldBe 1
            counters(d, id, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 0
            d.bothPass().error shouldBe null
            counters(d, id, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
            counters(d, id, CounterType.LOYALTY) shouldBe 0
        }
    }

    test("an opponent's newly entered commander is a legal target") {
        val d = fixture(1)
        val id = castActiveCommander(d)
        val forge = d.putLandOnBattlefield(d.player1, ForgeOfHeroes.name)
        d.passPriority(d.player2).isSuccess shouldBe true
        d.submitSuccess(counterAction(d.player1, forge, id))
        d.bothPass().error shouldBe null
        counters(d, id, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
    }

    test("ordinary same-name creature and prior-turn commander are rejected without tapping Forge") {
        val d = fixture()
        val id = castActiveCommander(d)
        val ordinary = d.putCardInHand(d.player1, IzzetGuildmage.name)
        d.giveMana(d.player1, Color.BLUE, 2)
        d.castSpell(d.player1, ordinary).isSuccess shouldBe true
        d.bothPass().error shouldBe null
        d.state.getEntity(ordinary)!!.has<EnteredThisTurnComponent>() shouldBe true
        d.state.getEntity(ordinary)!!.has<CommanderComponent>() shouldBe false
        val forge = d.putLandOnBattlefield(d.player1, ForgeOfHeroes.name)
        val before = d.state
        d.submit(counterAction(d.player1, forge, ordinary)).isSuccess shouldBe false
        d.state shouldBe before
        d.passPriorityUntil(Step.UPKEEP, maxPasses = 400)
        d.state.getEntity(id)!!.has<EnteredThisTurnComponent>() shouldBe false
        d.state.getEntity(id)!!.has<CommanderComponent>() shouldBe true
        d.passPriority(d.player2).isSuccess shouldBe true
        val later = d.state
        d.submit(counterAction(d.player1, forge, id)).isSuccess shouldBe false
        d.state shouldBe later
        d.state.getEntity(forge)!!.has<TappedComponent>() shouldBe false
    }

    for (types in listOf(setOf("PLANESWALKER"), setOf("CREATURE", "PLANESWALKER"), setOf("LAND"))) {
        test("counter resolution reads projected types $types rather than printed creature type") {
            val d = fixture()
            val id = castActiveCommander(d)
            val forge = d.putLandOnBattlefield(d.player1, ForgeOfHeroes.name)
            if ("LAND" !in types) d.submitSuccess(counterAction(d.player1, forge, id))
            // A deterministic layer fixture changes characteristics before announcement or resolution.
            // Printed card data and the commander designation stay intact. Initial loyalty avoids
            // the unrelated zero-loyalty SBA when the fixture creates a planeswalker.
            val typeChange = ActiveFloatingEffect(
                id = EntityId.generate(),
                effect = FloatingEffectData(Layer.TYPE, modification = SerializableModification.SetCardTypes(types),
                    affectedEntities = setOf(id)),
                duration = Duration.EndOfTurn,
                sourceId = forge,
                controllerId = d.player1,
                timestamp = 100L
            )
            val typed = d.state.updateEntity(id) { it.with(CountersComponent(mapOf(CounterType.LOYALTY to 3))) }
            d.replaceState(typed.copy(floatingEffects = typed.floatingEffects + typeChange))
            d.state.getEntity(id)!!.get<CardComponent>()!!.typeLine.isCreature shouldBe true
            // The projection stores supertypes and legacy subtype strings alongside card types.
            // Assert the exact card-type membership changed by this fixture, not that other
            // characteristic strings were erased by SetCardTypes.
            val cardTypeNames = CardType.entries.map { it.name }.toSet()
            d.state.projectedState.getTypes(id).intersect(cardTypeNames) shouldBe types
            // A commander that is neither relevant type is still eligible on announcement.
            if ("LAND" in types) d.submitSuccess(counterAction(d.player1, forge, id))
            d.bothPass().error shouldBe null
            counters(d, id, CounterType.PLUS_ONE_PLUS_ONE) shouldBe if ("CREATURE" in types) 1 else 0
            counters(d, id, CounterType.LOYALTY) shouldBe if ("PLANESWALKER" in types) 4 else 3
        }
    }

    test("commander leaving in response invalidates the target and preserves the paid tap") {
        val d = fixture()
        val id = castActiveCommander(d)
        val forge = d.putLandOnBattlefield(d.player1, ForgeOfHeroes.name)
        d.submitSuccess(counterAction(d.player1, forge, id))
        val spell = d.putCardInHand(d.player1, bounce.name)
        d.giveMana(d.player1, Color.BLUE, 1)
        d.castSpell(d.player1, spell, listOf(id)).isSuccess shouldBe true
        d.bothPass().error shouldBe null
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        d.submitYesNo(d.player1, false).error shouldBe null
        d.bothPass().error shouldBe null
        (id in d.state.getZone(ZoneKey(d.player1, Zone.HAND))) shouldBe true
        counters(d, id, CounterType.PLUS_ONE_PLUS_ONE) shouldBe 0
        d.state.stack.size shouldBe 0
        d.state.getEntity(forge)!!.has<TappedComponent>() shouldBe true
    }

    test("pending Forge ability serializes and replays identical state and ordered events") {
        val d = fixture()
        val id = castActiveCommander(d)
        val forge = d.putLandOnBattlefield(d.player1, ForgeOfHeroes.name)
        d.submitSuccess(counterAction(d.player1, forge, id))
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), d.state))
        val processor = ActionProcessor(d.cardRegistry)
        var original = d.state
        var replayed = restored
        repeat(2) {
            val action = PassPriority(original.priorityPlayerId!!)
            val first = processor.process(original, action).result
            val second = processor.process(replayed, action).result
            first.error shouldBe null
            second shouldBe first
            original = first.state
            replayed = second.state
        }
        original.stack.size shouldBe 0
        original.getEntity(id)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 1
    }
})
