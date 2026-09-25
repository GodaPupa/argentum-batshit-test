package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PermanentAttachedEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh3.cards.ColossalDreadmask
import com.wingedsheep.mtg.sets.definitions.rav.cards.DoublingSeason
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifyStats
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.TriggerBinding
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class ColossalDreadmaskScenarioTest : FunSpec({
    val removal = card("Dreadmask Fixture Disenchant") {
        manaCost = "{W}"
        typeLine = "Instant"
        spell {
            val target = target("artifact", Targets.Artifact)
            effect = Effects.Destroy(target)
        }
    }
    val observer = card("Dreadmask Zero Power Observer") {
        manaCost = "{W}"
        typeLine = "Enchantment"
        triggeredAbility {
            trigger = Triggers.entersBattlefield(
                GameObjectFilter.Creature.youControl().powerAtMost(0), TriggerBinding.ANY)
            effect = Effects.GainLife(1)
        }
    }
    val anthem = card("Dreadmask Fixture Anthem") {
        manaCost = "{1}{W}"
        typeLine = "Enchantment"
        staticAbility { ability = ModifyStats(1, 1, GroupFilter.AllCreaturesYouControl) }
    }
    fun fixture(): GameTestDriver = GameTestDriver().also { d ->
        d.registerCards(TestCards.all + listOf(ColossalDreadmask, DoublingSeason, removal, observer, anthem))
        d.initMirrorMatch(Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun germs(d: GameTestDriver) = d.getPermanents(d.activePlayer!!).filter {
        val c = d.state.getEntity(it)!!
        c.has<TokenComponent>() && c.get<CardComponent>()!!.typeLine.hasSubtype(Subtype("Germ"))
    }
    fun castMask(d: GameTestDriver) {
        val me = d.activePlayer!!
        val mask = d.putCardInHand(me, ColossalDreadmask.name)
        d.giveMana(me, Color.GREEN, 2)
        d.giveColorlessMana(me, 4)
        d.castSpell(me, mask).isSuccess shouldBe true
        d.bothPass() // Equipment resolves; living weapon is on the stack.
    }
    fun finishLivingWeapon(d: GameTestDriver) {
        d.bothPass()
        val decision = d.pendingDecision
        if (decision is SelectCardsDecision) {
            d.submitCardSelection(decision.playerId, listOf(decision.options.last())).isSuccess shouldBe true
        }
    }
    test("living weapon attaches before the 0/0 dies, yields a black 6/6 trampler, and ETB sees zero power") {
        val d = fixture()
        val me = d.activePlayer!!
        d.putPermanentOnBattlefield(me, observer.name)
        castMask(d)
        finishLivingWeapon(d)
        val germ = germs(d).single()
        val mask = d.findPermanent(me, ColossalDreadmask.name)!!
        d.state.getEntity(mask)!!.get<AttachedToComponent>()!!.targetId shouldBe germ
        d.state.getEntity(germ)!!.get<CardComponent>()!!.colors shouldBe setOf(Color.BLACK)
        d.state.projectedState.getPower(germ) shouldBe 6
        d.state.projectedState.getToughness(germ) shouldBe 6
        d.state.projectedState.hasKeyword(germ, Keyword.TRAMPLE) shouldBe true
        d.bothPass() // The token-enter trigger observes 0/0 before the attachment.
        d.getLifeTotal(me) shouldBe 21
    }
    test("a preexisting anthem makes the entering Germ 1/1 and the zero-power observer does not trigger") {
        val d = fixture()
        val me = d.activePlayer!!
        d.putPermanentOnBattlefield(me, anthem.name)
        d.putPermanentOnBattlefield(me, observer.name)
        castMask(d)
        finishLivingWeapon(d)
        val germ = germs(d).single()
        d.state.projectedState.getPower(germ) shouldBe 7
        d.state.projectedState.getToughness(germ) shouldBe 7
        d.state.stack.size shouldBe 0
        d.getLifeTotal(me) shouldBe 20
    }
    test("doubled Germ creation lets controller choose either token; only chosen Germ survives") {
        val d = fixture()
        val me = d.activePlayer!!
        d.putPermanentOnBattlefield(me, DoublingSeason.name)
        castMask(d)
        d.bothPass()
        val decision = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        decision.options.size shouldBe 2
        decision.minSelections shouldBe 1
        decision.maxSelections shouldBe 1
        val chosen = decision.options.last()
        d.submitCardSelection(me, listOf(chosen)).isSuccess shouldBe true
        germs(d) shouldBe listOf(chosen)
        val mask = d.findPermanent(me, ColossalDreadmask.name)!!
        d.state.getEntity(mask)!!.get<AttachedToComponent>()!!.targetId shouldBe chosen
        d.state.projectedState.getToughness(chosen) shouldBe 6
    }
    test("equip pays 3GG, moves to a controlled creature and leaves the unequipped Germ to die") {
        val d = fixture()
        val me = d.activePlayer!!
        castMask(d)
        finishLivingWeapon(d)
        val germ = germs(d).single()
        val mask = d.findPermanent(me, ColossalDreadmask.name)!!
        val host = d.putCreatureOnBattlefield(me, "Centaur Courser")
        val enemy = d.putCreatureOnBattlefield(d.getOpponent(me), "Centaur Courser")
        val ability = ColossalDreadmask.activatedAbilities.single().id
        fun equip(id: com.wingedsheep.sdk.model.EntityId) = ActivateAbility(me, mask, ability,
            targets = listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent(id)))
        d.giveColorlessMana(me, 5)
        d.submit(equip(host)).isSuccess shouldBe false
        d.giveMana(me, Color.GREEN, 2)
        d.submit(equip(enemy)).isSuccess shouldBe false
        d.submit(equip(host)).isSuccess shouldBe true
        d.bothPass()
        d.state.getEntity(mask)!!.get<AttachedToComponent>()!!.targetId shouldBe host
        d.state.projectedState.getPower(host) shouldBe 9
        d.state.projectedState.getToughness(host) shouldBe 9
        d.state.projectedState.hasKeyword(host, Keyword.TRAMPLE) shouldBe true
        d.getPermanents(me).contains(germ) shouldBe false
    }
    test("removing Equipment in response still creates a Germ but never attaches the graveyard card") {
        val d = fixture()
        val me = d.activePlayer!!
        castMask(d)
        val mask = d.findPermanent(me, ColossalDreadmask.name)!!
        val spell = d.putCardInHand(me, removal.name)
        d.giveMana(me, Color.WHITE, 1)
        d.castSpell(me, spell, listOf(mask)).isSuccess shouldBe true
        d.bothPass()
        val eventStart = d.events.size
        finishLivingWeapon(d)
        d.state.getGraveyard(me).contains(mask) shouldBe true
        d.state.getEntity(mask)!!.get<AttachedToComponent>() shouldBe null
        germs(d).size shouldBe 0
        d.events.drop(eventStart).filterIsInstance<PermanentAttachedEvent>().size shouldBe 0
    }
})
