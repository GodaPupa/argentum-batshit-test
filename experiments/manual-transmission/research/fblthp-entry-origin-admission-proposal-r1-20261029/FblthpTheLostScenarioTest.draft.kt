package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.akh.cards.VizierOfTheMenagerie
import com.wingedsheep.mtg.sets.definitions.war.cards.FblthpTheLost
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Fixed card scenarios; no Manual initializer, policies, admission, or official games. */
class FblthpTheLostScenarioTest : FunSpec({
    val libraryEntry = card("Fblthp Test Library Entry") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            effect = Effects.Pipeline {
                val top = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(1)))
                move(top, CardDestination.ToZone(Zone.BATTLEFIELD))
            }
        }
    }
    val opponentLibraryEntry = card("Fblthp Test Opponent Library Entry") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            effect = Effects.Pipeline {
                val top = gather(CardSource.TopOfLibrary(DynamicAmount.Fixed(1), Player.AnOpponent))
                move(top, CardDestination.ToZone(Zone.BATTLEFIELD, Player.You))
            }
        }
    }
    val bounce = card("Fblthp Test Ability Bounce") {
        manaCost = "{0}"
        typeLine = "Creature — Human Wizard"
        power = 1
        toughness = 1
        activatedAbility {
            cost = Costs.Free
            val creature = target("target creature", Targets.Creature)
            effect = Effects.ReturnToHand(creature)
        }
    }
    val blink = card("Fblthp Test Ability Blink") {
        manaCost = "{0}"
        typeLine = "Creature — Human Wizard"
        power = 1
        toughness = 1
        activatedAbility {
            cost = Costs.Free
            target("target creature", Targets.Creature)
            effect = Effects.Pipeline {
                val selected = gather(CardSource.ChosenTargets)
                val exiled = moveTracked(selected, CardDestination.ToZone(Zone.EXILE))
                move(exiled, CardDestination.ToZone(Zone.BATTLEFIELD))
            }
        }
    }
    val retarget = card("Fblthp Test Retarget") {
        manaCost = "{0}"
        typeLine = "Instant"
        spell {
            target("target spell or ability", Targets.SpellOrAbilityWithSingleTarget)
            effect = Effects.ChangeTarget()
        }
    }
    val abilityTarget = card("Fblthp Test Ability Target") {
        manaCost = "{0}"
        typeLine = "Creature — Human Wizard"
        power = 1
        toughness = 1
        activatedAbility {
            cost = Costs.Free
            val creature = target("target creature", Targets.Creature)
            effect = Effects.ModifyStats(0, 1, creature)
        }
    }

    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(FblthpTheLost, VizierOfTheMenagerie,
            libraryEntry, opponentLibraryEntry, bounce, blink, retarget, abilityTarget))
        it.initMirrorMatch(deck = Deck.of("Island" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun libraryEnter(d: GameTestDriver): EntityId {
        val id = d.putCardOnTopOfLibrary(d.player1, "Fblthp, the Lost")
        val entry = d.putCardInHand(d.player1, libraryEntry.name)
        d.castSpell(d.player1, entry).error shouldBe null
        d.bothPass().error shouldBe null
        d.findPermanent(d.player1, "Fblthp, the Lost") shouldBe id
        return id
    }
    fun activate(d: GameTestDriver, card: CardDefinition, source: EntityId, target: EntityId) {
        d.submit(ActivateAbility(d.player1, source, card.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Permanent(target)))).error shouldBe null
    }
    fun fblthpTriggers(d: GameTestDriver) = d.state.stack.mapNotNull {
        d.state.getEntity(it)?.get<TriggeredAbilityOnStackComponent>()
    }.filter { it.sourceName == "Fblthp, the Lost" }

    test("cast from hand draws exactly one card") {
        val d = driver()
        val fblthp = d.putCardInHand(d.player1, "Fblthp, the Lost")
        d.giveMana(d.player1, Color.BLUE, 2)
        d.castSpell(d.player1, fblthp).error shouldBe null
        d.bothPass().error shouldBe null
        val hand = d.getHandSize(d.player1)
        fblthpTriggers(d).size shouldBe 1
        d.bothPass().error shouldBe null
        d.getHandSize(d.player1) shouldBe hand + 1
    }

    test("cast from library draws exactly two cards") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player1, "Vizier of the Menagerie")
        val fblthp = d.putCardOnTopOfLibrary(d.player1, "Fblthp, the Lost")
        d.giveMana(d.player1, Color.BLUE, 2)
        d.castSpell(d.player1, fblthp).error shouldBe null
        d.bothPass().error shouldBe null
        val hand = d.getHandSize(d.player1)
        fblthpTriggers(d).size shouldBe 1
        d.bothPass().error shouldBe null
        d.getHandSize(d.player1) shouldBe hand + 2
    }

    test("direct library entry draws exactly two cards") {
        val d = driver()
        libraryEnter(d)
        val hand = d.getHandSize(d.player1)
        fblthpTriggers(d).size shouldBe 1
        d.bothPass().error shouldBe null
        d.getHandSize(d.player1) shouldBe hand + 2
    }

    test("opponent library entry under our control draws exactly one card") {
        val d = driver()
        val fblthp = d.putCardOnTopOfLibrary(d.player2, "Fblthp, the Lost")
        val entry = d.putCardInHand(d.player1, opponentLibraryEntry.name)
        d.castSpell(d.player1, entry).error shouldBe null
        d.bothPass().error shouldBe null
        d.findPermanent(d.player1, "Fblthp, the Lost") shouldBe fblthp
        val hand = d.getHandSize(d.player1)
        fblthpTriggers(d).size shouldBe 1
        d.bothPass().error shouldBe null
        d.getHandSize(d.player1) shouldBe hand + 1
    }

    test("library entry still draws two after Fblthp leaves before the trigger resolves") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player1, bounce.name)
        val fblthp = libraryEnter(d)
        activate(d, bounce, source, fblthp)
        d.bothPass().error shouldBe null
        d.findPermanent(d.player1, "Fblthp, the Lost") shouldBe null
        d.getHand(d.player1).contains(fblthp) shouldBe true
        val hand = d.getHandSize(d.player1)
        d.bothPass().error shouldBe null
        d.getHandSize(d.player1) shouldBe hand + 2
    }

    test("blink gives the new exile-entry trigger one card and preserves the old library trigger's two") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player1, blink.name)
        val fblthp = libraryEnter(d)
        val hand = d.getHandSize(d.player1)
        activate(d, blink, source, fblthp)
        d.bothPass().error shouldBe null
        fblthpTriggers(d).size shouldBe 2
        d.bothPass().error shouldBe null
        d.getHandSize(d.player1) shouldBe hand + 1
        d.bothPass().error shouldBe null
        d.getHandSize(d.player1) shouldBe hand + 3
    }

    test("initial spell targeting shuffles Fblthp before that spell resolves") {
        val d = driver()
        val fblthp = d.putCreatureOnBattlefield(d.player2, "Fblthp, the Lost")
        val bolt = d.putCardInHand(d.player1, "Lightning Bolt")
        d.giveMana(d.player1, Color.RED, 1)
        d.castSpell(d.player1, bolt, listOf(fblthp)).error shouldBe null
        fblthpTriggers(d).size shouldBe 1
        d.bothPass().error shouldBe null
        d.findPermanent(d.player2, "Fblthp, the Lost") shouldBe null
        d.state.getLibrary(d.player2).contains(fblthp) shouldBe true
        d.state.stack.contains(bolt) shouldBe true
        d.bothPass().error shouldBe null
        d.getGraveyardCardNames(d.player1).contains("Lightning Bolt") shouldBe true
    }

    test("a spell redirected to Fblthp causes the same self shuffle") {
        val d = driver()
        val fblthp = d.putCreatureOnBattlefield(d.player2, "Fblthp, the Lost")
        val bolt = d.putCardInHand(d.player1, "Lightning Bolt")
        d.giveMana(d.player1, Color.RED, 1)
        d.castSpell(d.player1, bolt, listOf(d.player2)).error shouldBe null
        val redirect = d.putCardInHand(d.player1, retarget.name)
        d.castSpellWithTargets(d.player1, redirect, listOf(ChosenTarget.Spell(bolt))).error shouldBe null
        d.bothPass().error shouldBe null
        (d.state.pendingDecision as? SelectCardsDecision).shouldNotBeNull()
        d.submitCardSelection(d.player1, listOf(fblthp)).error shouldBe null
        fblthpTriggers(d).size shouldBe 1
        d.bothPass().error shouldBe null
        d.findPermanent(d.player2, "Fblthp, the Lost") shouldBe null
        d.state.getLibrary(d.player2).contains(fblthp) shouldBe true
        d.state.stack.contains(bolt) shouldBe true
    }

    test("targeting Fblthp with an ability does not cause a shuffle") {
        val d = driver()
        val source = d.putCreatureOnBattlefield(d.player1, abilityTarget.name)
        val fblthp = d.putCreatureOnBattlefield(d.player2, "Fblthp, the Lost")
        activate(d, abilityTarget, source, fblthp)
        fblthpTriggers(d).size shouldBe 0
        d.bothPass().error shouldBe null
        d.findPermanent(d.player2, "Fblthp, the Lost") shouldBe fblthp
        d.state.getLibrary(d.player2).contains(fblthp) shouldBe false
    }

    test("a spell targeting another creature does not shuffle Fblthp") {
        val d = driver()
        val fblthp = d.putCreatureOnBattlefield(d.player2, "Fblthp, the Lost")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val bolt = d.putCardInHand(d.player1, "Lightning Bolt")
        d.giveMana(d.player1, Color.RED, 1)
        d.castSpell(d.player1, bolt, listOf(bear)).error shouldBe null
        fblthpTriggers(d).size shouldBe 0
        d.bothPass().error shouldBe null
        d.findPermanent(d.player2, "Fblthp, the Lost") shouldBe fblthp
    }
})
