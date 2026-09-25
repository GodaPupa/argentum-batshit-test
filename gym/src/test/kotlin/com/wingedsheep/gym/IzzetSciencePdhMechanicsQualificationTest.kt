package com.wingedsheep.gym

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.gym.matchup.IZZET_SCIENCE_COMMANDER
import com.wingedsheep.gym.matchup.VETERAN_BEASTRIDER_COMMANDER
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.Json

/**
 * Scoped, seed-free capability fixtures: the two exact commander definitions with 99 basic lands.
 * These are not the frozen matchup decks, sampled games, executable pilots, or readiness admission.
 */
class IzzetSciencePdhMechanicsQualificationTest : FunSpec({
    val commanders = listOf(IZZET_SCIENCE_COMMANDER, VETERAN_BEASTRIDER_COMMANDER)
    val pdh = Format.Commander(startingLife = 30, commanderDamageThreshold = 16)
    val removal = card("PDH Qualification Removal Fixture") {
        manaCost = "{W}"
        typeLine = "Instant"
        spell {
            val creature = target("creature", Targets.Creature)
            effect = Effects.Destroy(creature)
        }
    }
    val noncombat = card("PDH Qualification Source Damage Fixture") {
        manaCost = "{R}"
        typeLine = "Instant"
        spell {
            val creature = target("source creature", Targets.CreatureYouControl)
            val player = target("player", Targets.Player)
            effect = Effects.DealDamage(16, player, damageSource = creature)
        }
    }
    fun fixture(firstSeat: Int = 0) = GameTestDriver().apply {
        MtgSetCatalog.all.forEach { set -> registerCards(set.cards); registerCards(set.basicLands) }
        registerCards(listOf(removal, noncombat))
        initMultiplayer(List(2) { Deck.of("Forest" to 99) }, startingLife = 20,
            startingPlayer = firstSeat, format = pdh, commanders = commanders)
    }
    fun commander(d: GameTestDriver, owner: EntityId) =
        d.state.getEntity(owner)!!.get<CommanderRegistryComponent>()!!.commanderIds.single()
    fun payPrintedCost(d: GameTestDriver, seat: Int) {
        val owner = if (seat == 0) d.player1 else d.player2
        if (seat == 0) d.giveMana(owner, Color.BLUE, 2)
        else {
            d.giveMana(owner, Color.GREEN, 1)
            d.giveMana(owner, Color.WHITE, 1)
            d.giveColorlessMana(owner, 1)
        }
    }
    fun affordableCommanderCast(d: GameTestDriver, owner: EntityId, id: EntityId) =
        d.legalActions(owner).any { it.affordable && (it.action as? CastSpell)?.cardId == id }

    test("exact nonlegendary commanders initialize in their own command zones at 30 life") {
        val d = fixture()
        d.state.format shouldBe pdh
        for ((seat, owner) in listOf(d.player1, d.player2).withIndex()) {
            d.getLifeTotal(owner) shouldBe 30
            val id = commander(d, owner)
            d.state.getZone(ZoneKey(owner, Zone.COMMAND)) shouldBe listOf(id)
            val entity = d.state.getEntity(id)!!
            entity.get<CardComponent>()!!.name shouldBe commanders[seat]
            entity.get<CardComponent>()!!.typeLine.isLegendary shouldBe false
            entity.get<CommanderComponent>()!!.ownerId shouldBe owner
            entity.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 0
            val ordinary = d.state.getZone(ZoneKey(owner, Zone.HAND)) + d.state.getZone(ZoneKey(owner, Zone.LIBRARY))
            ordinary.size shouldBe 99
            (id in ordinary) shouldBe false
        }
        (commander(d, d.player1) == commander(d, d.player2)) shouldBe false
    }

    for (seat in 0..1) {
        test("${commanders[seat]} is offered legally, casts for printed cost, and requires two more mana on recast") {
            val d = fixture(seat)
            val me = d.activePlayer!!
            d.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val id = commander(d, me)
            affordableCommanderCast(d, me, id) shouldBe false
            payPrintedCost(d, seat)
            affordableCommanderCast(d, me, id) shouldBe true
            d.castSpell(me, id).isSuccess shouldBe true
            d.bothPass().error shouldBe null
            (id in d.state.getBattlefield()) shouldBe true
            d.state.getEntity(id)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 1
            val destroy = d.putCardInHand(me, removal.name)
            d.giveMana(me, Color.WHITE, 1)
            d.castSpell(me, destroy, listOf(id)).isSuccess shouldBe true
            d.bothPass().error shouldBe null
            val decision = d.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
            decision.playerId shouldBe me
            d.submitYesNo(me, true).error shouldBe null
            d.state.getZone(ZoneKey(me, Zone.COMMAND)) shouldBe listOf(id)
            payPrintedCost(d, seat)
            affordableCommanderCast(d, me, id) shouldBe false
            val beforeRejected = d.state
            d.castSpell(me, id).isSuccess shouldBe false
            d.state shouldBe beforeRejected
            d.giveColorlessMana(me, 2)
            affordableCommanderCast(d, me, id) shouldBe true

            // Replaying this precise paid command-zone action from a serialized state must
            // retain format, commander identity, tax and events. No future draws are inspected.
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
            val restored = json.decodeFromString(GameState.serializer(), json.encodeToString(GameState.serializer(), d.state))
            restored shouldBe d.state
            val cast = CastSpell(me, id, paymentStrategy = PaymentStrategy.FromPool)
            val processor = ActionProcessor(d.cardRegistry)
            val originalResult = processor.process(d.state, cast).result
            val replayedResult = processor.process(restored, cast).result
            originalResult.error shouldBe null
            replayedResult shouldBe originalResult
            d.replaceState(replayedResult.state)
            d.bothPass().error shouldBe null
            d.state.getEntity(id)!!.get<CommanderComponent>()!!.castsFromCommandZone shouldBe 2
            (id in d.state.getBattlefield()) shouldBe true
        }
    }

    test("the actual commander owner can decline command-zone return") {
        val d = fixture()
        val me = d.player1
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val id = commander(d, me)
        payPrintedCost(d, 0)
        d.castSpell(me, id).isSuccess shouldBe true
        d.bothPass().error shouldBe null
        val destroy = d.putCardInHand(me, removal.name)
        d.giveMana(me, Color.WHITE, 1)
        d.castSpell(me, destroy, listOf(id)).isSuccess shouldBe true
        d.bothPass().error shouldBe null
        d.pendingDecision.shouldBeInstanceOf<YesNoDecision>().playerId shouldBe me
        d.submitYesNo(me, false).error shouldBe null
        (id in d.state.getZone(ZoneKey(me, Zone.GRAVEYARD))) shouldBe true
        d.state.getZone(ZoneKey(me, Zone.COMMAND)).size shouldBe 0
        d.pendingDecision shouldBe null
    }

    for (priorDamage in listOf(13, 14)) {
        test("actual Guildmage combat reaches ${priorDamage + 2} commander damage under the 16 threshold") {
            val d = fixture()
            val me = d.player1
            val enemy = d.player2
            d.passPriorityUntil(Step.PRECOMBAT_MAIN)
            val id = commander(d, me)
            payPrintedCost(d, 0)
            d.castSpell(me, id).isSuccess shouldBe true
            d.bothPass().error shouldBe null
            d.removeSummoningSickness(id)
            d.replaceState(d.state.recordCommanderDamage(id, enemy, priorDamage))
            d.passPriorityUntil(Step.DECLARE_ATTACKERS)
            d.declareAttackers(me, listOf(id), enemy).isSuccess shouldBe true
            d.passPriorityUntil(Step.COMBAT_DAMAGE)
            d.state.commanderDamageOf(id, enemy) shouldBe priorDamage + 2
            (d.state.gameOver || d.state.getEntity(enemy)?.get<PlayerLostComponent>() != null) shouldBe (priorDamage == 14)
            d.getLifeTotal(enemy) shouldBe 28
        }
    }

    test("16 noncombat damage by the actual commander never increments commander combat damage") {
        val d = fixture()
        val me = d.player1
        val enemy = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val id = commander(d, me)
        payPrintedCost(d, 0)
        d.castSpell(me, id).isSuccess shouldBe true
        d.bothPass().error shouldBe null
        val spell = d.putCardInHand(me, noncombat.name)
        d.giveMana(me, Color.RED, 1)
        d.castSpell(me, spell, listOf(id, enemy)).isSuccess shouldBe true
        d.bothPass().error shouldBe null
        d.getLifeTotal(enemy) shouldBe 14
        d.state.commanderDamageOf(id, enemy) shouldBe 0
        d.state.gameOver shouldBe false
    }
})
