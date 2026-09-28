package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.iko.cards.KinnanBonderProdigy
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Kinnan, Bonder Prodigy — exact engine qualification for the Manual Phase-2 dependency.
 *
 * These cases intentionally qualify the printed mechanics rather than merely proving that the card
 * name can be resolved by the registry. The Manual four-seat strategic fixture may only consume this
 * card support after these source-bound cases pass.
 */
class KinnanBonderProdigyScenarioTest : FunSpec({

    val cards = TestCards.all + KinnanBonderProdigy

    fun driver(): GameTestDriver {
        val d = GameTestDriver()
        d.registerCards(cards)
        d.initMirrorMatch(deck = Deck.of("Forest" to 40), skipMulligans = true, startingPlayer = 0)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d
    }

    fun GameTestDriver.tapForMana(player: EntityId, source: EntityId, cardName: String) {
        val ability = cardRegistry.getCard(cardName)!!.script.activatedAbilities.first { it.isManaAbility }
        submit(ActivateAbility(player, source, ability.id)).error shouldBe null
    }

    fun GameTestDriver.pool(player: EntityId): ManaPoolComponent =
        state.getEntity(player)?.get<ManaPoolComponent>() ?: ManaPoolComponent()

    test("tapping a nonland permanent for mana adds one more mana of a produced type") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player1, "Kinnan, Bonder Prodigy")
        val ring = d.putPermanentOnBattlefield(d.player1, "Sol Ring")

        d.tapForMana(d.player1, ring, "Sol Ring")

        withClue("Sol Ring produces two colorless and Kinnan mirrors one additional colorless") {
            d.pool(d.player1).colorless shouldBe 3
        }
    }

    test("tapping a land for mana does not receive Kinnan's bonus") {
        val d = driver()
        d.putCreatureOnBattlefield(d.player1, "Kinnan, Bonder Prodigy")
        val forest = d.putLandOnBattlefield(d.player1, "Forest")

        d.tapForMana(d.player1, forest, "Forest")

        withClue("the NonlandPermanent filter excludes lands") {
            d.pool(d.player1).green shouldBe 1
        }
    }

    test("{5}{G}{U} may put one non-Human creature from the top five onto the battlefield") {
        val d = driver()
        val kinnan = d.putCreatureOnBattlefield(d.player1, "Kinnan, Bonder Prodigy")

        // Five deterministic looked-at cards. Kinnan itself is a Human creature and therefore must
        // be visible but ineligible; Grizzly Bears is the only eligible non-Human creature.
        val eligibleBear = d.putCardOnTopOfLibrary(d.player1, "Grizzly Bears")
        val humanKinnan = d.putCardOnTopOfLibrary(d.player1, "Kinnan, Bonder Prodigy")
        d.putCardOnTopOfLibrary(d.player1, "Forest")
        d.putCardOnTopOfLibrary(d.player1, "Island")
        d.putCardOnTopOfLibrary(d.player1, "Shock")

        d.giveColorlessMana(d.player1, 5)
        d.giveMana(d.player1, Color.GREEN, 1)
        d.giveMana(d.player1, Color.BLUE, 1)

        val ability = d.cardRegistry.getCard("Kinnan, Bonder Prodigy")!!
            .script.activatedAbilities.single { !it.isManaAbility }

        d.submit(ActivateAbility(d.player1, kinnan, ability.id)).error shouldBe null

        // Put the activated ability through the real stack; it pauses at the filtered private look.
        d.bothPass().error shouldBe null
        val decision = d.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()

        withClue("the only selectable looked-at creature is the non-Human Grizzly Bears") {
            decision.options.contains(eligibleBear) shouldBe true
            decision.options.contains(humanKinnan) shouldBe false
        }

        d.submitCardSelection(decision.playerId, listOf(eligibleBear)).error shouldBe null
        while (d.state.stack.isNotEmpty() && d.pendingDecision == null) {
            d.bothPass().error shouldBe null
        }

        withClue("the chosen non-Human creature entered the battlefield") {
            d.findPermanent(d.player1, "Grizzly Bears") shouldNotBe null
        }
        withClue("the ineligible Human remained among the cards returned to the library") {
            d.state.getLibrary(d.player1).contains(humanKinnan) shouldBe true
        }
        withClue("the chosen card is no longer in the library") {
            d.state.getLibrary(d.player1).contains(eligibleBear) shouldBe false
        }
    }
})
